import { FieldValue, getFirestore, Timestamp } from "firebase-admin/firestore";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { logger } from "firebase-functions/v2";
import { GoogleAuth } from "google-auth-library";
import { loadConfig, REGION } from "./config";
import { asRecord, requireAuth } from "./guards";
import { notifyUser } from "./notifications";

const DAY_MS = 24 * 60 * 60 * 1000;
const PRO_PRODUCT_ID = "driverlink_pro_monthly";

/**
 * Activa la prueba de DriverLink Pro una sola vez por cuenta. La duración sale de
 * adminConfig/app.trialDays (30 días en lanzamiento, luego 7) sin publicar una nueva app.
 */
export const startTrial = onCall({ region: REGION }, async (request) => {
  const uid = requireAuth(request);
  const config = await loadConfig();
  const ref = getFirestore().doc(`subscriptions/${uid}`);
  await getFirestore().runTransaction(async (tx) => {
    const current = (await tx.get(ref)).data();
    if (current?.trialUsed === true) throw new HttpsError("already-exists", "trial-used");
    if (current?.status === "PRO" || current?.status === "COMPANY") throw new HttpsError("already-exists", "already-premium");
    const now = Date.now();
    tx.set(ref, {
      status: "TRIAL",
      trialUsed: true,
      startedAt: Timestamp.fromMillis(now),
      expiresAt: Timestamp.fromMillis(now + config.trialDays * DAY_MS),
      source: "trial",
      updatedAt: FieldValue.serverTimestamp(),
    }, { merge: true });
    tx.update(getFirestore().doc(`users/${uid}`), { subscriptionStatus: "TRIAL" });
  });
  return { ok: true };
});

/**
 * Valida una compra de Google Play con la Google Play Developer API y otorga PRO.
 * Requiere: adminConfig/app.playPackageName y que la cuenta de servicio de Functions
 * tenga acceso a la API en Play Console. Sin eso responde failed-precondition/not-configured.
 */
export const verifyPlayPurchase = onCall({ region: REGION }, async (request) => {
  const uid = requireAuth(request);
  const { purchaseToken } = asRecord(request.data) as { purchaseToken?: string };
  if (!purchaseToken) throw new HttpsError("invalid-argument", "purchaseToken");
  const config = await loadConfig();
  if (!config.playPackageName) throw new HttpsError("failed-precondition", "not-configured");

  const auth = new GoogleAuth({ scopes: ["https://www.googleapis.com/auth/androidpublisher"] });
  const client = await auth.getClient();
  const url = `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${config.playPackageName}` +
    `/purchases/subscriptionsv2/tokens/${encodeURIComponent(purchaseToken)}`;
  const response = await client.request<{ subscriptionState?: string; lineItems?: { productId?: string; expiryTime?: string }[] }>({ url });
  const item = response.data.lineItems?.find((l) => l.productId === PRO_PRODUCT_ID);
  const active = response.data.subscriptionState === "SUBSCRIPTION_STATE_ACTIVE" ||
    response.data.subscriptionState === "SUBSCRIPTION_STATE_IN_GRACE_PERIOD";
  if (!item?.expiryTime || !active) throw new HttpsError("failed-precondition", "purchase-not-active");

  await getFirestore().doc(`subscriptions/${uid}`).set({
    status: "PRO",
    startedAt: FieldValue.serverTimestamp(),
    expiresAt: Timestamp.fromDate(new Date(item.expiryTime)),
    source: "google_play",
    purchaseToken,
    updatedAt: FieldValue.serverTimestamp(),
  }, { merge: true });
  await getFirestore().doc(`users/${uid}`).update({ subscriptionStatus: "PRO" });
  return { ok: true };
});

/** Diario: marca como EXPIRED los planes vencidos. NO bloquea la cuenta ni la comunidad. */
export const expireSubscriptions = onSchedule({ schedule: "every day 04:00", timeZone: "America/Santiago", region: REGION }, async () => {
  const db = getFirestore();
  const expired = await db.collection("subscriptions")
    .where("status", "in", ["TRIAL", "PRO"])
    .where("expiresAt", "<=", Timestamp.now())
    .limit(500)
    .get();
  for (const doc of expired.docs) {
    await doc.ref.update({ status: "EXPIRED", updatedAt: FieldValue.serverTimestamp() });
    await db.doc(`users/${doc.id}`).update({ subscriptionStatus: "EXPIRED" }).catch(() => undefined);
    await notifyUser(doc.id, "SUBSCRIPTION", "DriverLink Pro",
      "Tu período de DriverLink Pro terminó. Puedes continuar utilizando gratuitamente las funciones de comunidad.");
  }
  logger.info("Suscripciones expiradas", { count: expired.size });
});
