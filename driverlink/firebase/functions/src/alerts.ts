import { FieldValue, getFirestore, Timestamp } from "firebase-admin/firestore";
import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { logger } from "firebase-functions/v2";
import { loadConfig, REGION } from "./config";
import { notifyTopic } from "./notifications";
import { slug } from "./validation";

const LABELS: Record<string, string> = {
  ACCIDENT: "Accidente", TRAFFIC: "Congestión", ROAD_CLOSED: "Ruta cerrada", DANGER: "Peligro",
  SECURITY: "Seguridad", ROAD_PROBLEM: "Problema vial", EMERGENCY: "Emergencia", OTHER: "Reporte",
};

/**
 * Normaliza el vencimiento con la configuración del servidor (el cliente no decide
 * cuánto dura una alerta), suma reputación y notifica a la zona mediante tema FCM.
 */
export const onAlertCreated = onDocumentCreated({ document: "alerts/{alertId}", region: REGION }, async (event) => {
  const alert = event.data?.data();
  if (!alert || !event.data) return;
  const config = await loadConfig();
  const minutes = config.alertExpirationMinutes[alert.category] ?? 60;
  const createdAt = (alert.createdAt as Timestamp | undefined)?.toMillis() ?? Date.now();
  await event.data.ref.update({ expiresAt: Timestamp.fromMillis(createdAt + minutes * 60_000) });
  await getFirestore().doc(`users/${alert.creatorId}`)
    .update({ "reputation.alertsCreated": FieldValue.increment(1) })
    .catch(() => undefined);
  if (alert.city) {
    const where = alert.commune || alert.city;
    await notifyTopic(`alerts_${slug(alert.city)}`, "NEARBY_ALERTS", `${LABELS[alert.category] ?? "Alerta"} en ${where}`,
      (alert.description as string | undefined)?.slice(0, 120) || "Nueva alerta de la comunidad");
  }
});

/**
 * Contadores sensibles: solo el servidor los modifica. Resuelve u oculta la alerta
 * al alcanzar el umbral configurado de "Ya terminó" o "Información incorrecta".
 */
export const onAlertVoteCreated = onDocumentCreated(
  { document: "alerts/{alertId}/confirmations/{voterId}", region: REGION },
  async (event) => {
    const vote = event.data?.get("vote") as string | undefined;
    const field = vote === "CONFIRM" ? "confirmationsCount" : vote === "ENDED" ? "endedCount" : vote === "INCORRECT" ? "incorrectCount" : null;
    if (!field) return;
    const config = await loadConfig();
    const db = getFirestore();
    const ref = db.doc(`alerts/${event.params.alertId}`);
    await db.runTransaction(async (tx) => {
      const snap = await tx.get(ref);
      if (!snap.exists) return;
      const count = (snap.get(field) as number ?? 0) + 1;
      const update: Record<string, unknown> = { [field]: count, updatedAt: FieldValue.serverTimestamp() };
      if (snap.get("status") === "ACTIVE") {
        if (field === "endedCount" && count >= config.alertResolveThreshold) update.status = "RESOLVED";
        if (field === "incorrectCount" && count >= config.alertResolveThreshold) update.status = "HIDDEN";
      }
      tx.update(ref, update);
    });
    if (vote === "CONFIRM") {
      await db.doc(`users/${event.params.voterId}`)
        .update({ "reputation.alertsConfirmed": FieldValue.increment(1) })
        .catch(() => undefined);
    }
  },
);

/** Cada 15 minutos marca como EXPIRED las alertas vencidas (índice status+expiresAt). */
export const expireAlerts = onSchedule({ schedule: "every 15 minutes", region: REGION }, async () => {
  const db = getFirestore();
  const snap = await db.collection("alerts")
    .where("status", "==", "ACTIVE")
    .where("expiresAt", "<=", Timestamp.now())
    .limit(400)
    .get();
  if (snap.empty) return;
  const batch = db.batch();
  snap.docs.forEach((d) => batch.update(d.ref, { status: "EXPIRED", updatedAt: FieldValue.serverTimestamp() }));
  await batch.commit();
  logger.info("Alertas expiradas", { count: snap.size });
});
