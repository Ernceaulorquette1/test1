import { getAuth } from "firebase-admin/auth";
import { FieldValue, getFirestore } from "firebase-admin/firestore";
import { getStorage } from "firebase-admin/storage";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { onDocumentCreated, onDocumentWritten } from "firebase-functions/v2/firestore";
import { REGION } from "./config";
import { asRecord, PlatformRole, requireAuth, requireRole } from "./guards";
import { notifyUser } from "./notifications";

const db = () => getFirestore();

/** Al crear el perfil: suscripción FREE (solo servidor) y reputación inicial. */
export const onUserCreated = onDocumentCreated({ document: "users/{uid}", region: REGION }, async (event) => {
  const uid = event.params.uid;
  await db().doc(`subscriptions/${uid}`).set(
    { status: "FREE", trialUsed: false, startedAt: null, expiresAt: null, source: null, updatedAt: FieldValue.serverTimestamp() },
    { merge: true },
  );
  await event.data?.ref.set(
    { reputation: { alertsCreated: 0, alertsConfirmed: 0, usefulReports: 0, score: 0 } },
    { merge: true },
  );
});

/** Perfil público derivado: solo campos no sensibles (nunca correo ni teléfono). */
export const onUserWritten = onDocumentWritten({ document: "users/{uid}", region: REGION }, async (event) => {
  const uid = event.params.uid;
  const after = event.data?.after.data();
  const ref = db().doc(`publicProfiles/${uid}`);
  if (!after) {
    await ref.delete();
    return;
  }
  await ref.set({
    displayName: `${after.firstName ?? ""} ${after.lastName ?? ""}`.trim(),
    photoUrl: after.photoUrl ?? null,
    city: after.city ?? "",
    platforms: after.platforms ?? [],
    verificationStatus: after.verificationStatus ?? "UNVERIFIED",
    memberSince: after.createdAt ?? null,
  });
});

/** Elimina datos personales y la cuenta. Se conservan registros SOS según política legal. */
export const deleteAccount = onCall({ region: REGION }, async (request) => {
  const uid = requireAuth(request);
  const database = db();
  const userRef = database.doc(`users/${uid}`);
  for (const sub of ["emergencyContacts", "blockedUsers", "devices"]) {
    const docs = await userRef.collection(sub).listDocuments();
    await Promise.all(docs.map((d) => d.delete()));
  }
  await Promise.all([
    userRef.delete(),
    database.doc(`notificationPreferences/${uid}`).delete(),
    database.doc(`verificationRequests/${uid}`).delete(),
    database.doc(`subscriptions/${uid}`).delete(),
  ]);
  const bucket = getStorage().bucket();
  await Promise.all([
    bucket.deleteFiles({ prefix: `profilePhotos/${uid}/` }),
    bucket.deleteFiles({ prefix: `verification/${uid}/` }),
  ]);
  await getAuth().deleteUser(uid);
  return { ok: true };
});

/** ADMIN: asigna rol de plataforma mediante custom claims. */
export const setUserRole = onCall({ region: REGION }, async (request) => {
  requireRole(request, ["ADMIN"]);
  const { userId, role } = asRecord(request.data) as { userId?: string; role?: PlatformRole };
  const roles: PlatformRole[] = ["DRIVER", "MODERATOR", "ADMIN", "SOS_OPERATOR"];
  if (!userId || !role || !roles.includes(role)) throw new HttpsError("invalid-argument", "role");
  const user = await getAuth().getUser(userId);
  await getAuth().setCustomUserClaims(userId, { ...(user.customClaims ?? {}), role });
  await db().doc(`users/${userId}`).update({ role, updatedAt: FieldValue.serverTimestamp() });
  return { ok: true };
});

/** MODERATOR/ADMIN: suspende o reactiva una cuenta (claim suspended + revocación de tokens). */
export const setAccountSuspended = onCall({ region: REGION }, async (request) => {
  const moderatorId = requireRole(request, ["MODERATOR", "ADMIN"]);
  const { userId, suspended, reason } = asRecord(request.data) as { userId?: string; suspended?: boolean; reason?: string };
  if (!userId || typeof suspended !== "boolean") throw new HttpsError("invalid-argument", "userId");
  const user = await getAuth().getUser(userId);
  await getAuth().setCustomUserClaims(userId, { ...(user.customClaims ?? {}), suspended });
  if (suspended) await getAuth().revokeRefreshTokens(userId);
  await db().doc(`users/${userId}`).update({
    accountStatus: suspended ? "SUSPENDED" : "ACTIVE",
    updatedAt: FieldValue.serverTimestamp(),
  });
  await db().collection("moderationActions").add({
    type: suspended ? "SUSPEND" : "REACTIVATE", userId, moderatorId, reason: reason ?? "",
    createdAt: FieldValue.serverTimestamp(),
  });
  return { ok: true };
});

/** MODERATOR/ADMIN: resuelve una solicitud de verificación. El usuario nunca se verifica solo. */
export const reviewVerification = onCall({ region: REGION }, async (request) => {
  const moderatorId = requireRole(request, ["MODERATOR", "ADMIN"]);
  const { userId, approve, comment } = asRecord(request.data) as { userId?: string; approve?: boolean; comment?: string };
  if (!userId || typeof approve !== "boolean") throw new HttpsError("invalid-argument", "userId");
  const status = approve ? "VERIFIED" : "REJECTED";
  const batch = db().batch();
  batch.update(db().doc(`verificationRequests/${userId}`), {
    status, reviewerComment: comment ?? null, reviewedBy: moderatorId, reviewedAt: FieldValue.serverTimestamp(),
  });
  batch.update(db().doc(`users/${userId}`), { verificationStatus: status, updatedAt: FieldValue.serverTimestamp() });
  await batch.commit();
  await notifyUser(userId, "ADMIN", "Verificación DriverLink",
    approve ? "¡Tu cuenta fue verificada!" : "Tu solicitud de verificación fue rechazada. Revisa los detalles en la app.");
  return { ok: true };
});
