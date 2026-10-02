import { FieldValue, getFirestore, Timestamp } from "firebase-admin/firestore";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { onDocumentUpdated } from "firebase-functions/v2/firestore";
import { loadConfig, REGION, SOS_ACTIVE, SOS_TYPES } from "./config";
import { asRecord, requireAuth, requireRole } from "./guards";
import { notifyTopic, notifyUser } from "./notifications";

const STATUS_TEXT: Record<string, string> = {
  RECEIVED: "Tu solicitud fue recibida por un operador.",
  IN_PROGRESS: "Tu SOS está en atención.",
  RESOLVED: "Tu SOS fue finalizado.",
  CANCELLED: "Tu SOS fue cancelado.",
};

/** Crea un SOS. Valida en servidor que el usuario tenga Premium vigente. */
export const createSosEvent = onCall({ region: REGION }, async (request) => {
  const uid = requireAuth(request);
  const config = await loadConfig();
  if (!config.sosEnabled) throw new HttpsError("failed-precondition", "feature-disabled");

  const { type, latitude, longitude, notes } = asRecord(request.data) as {
    type?: string; latitude?: number | null; longitude?: number | null; notes?: string;
  };
  if (!type || !(SOS_TYPES as readonly string[]).includes(type)) throw new HttpsError("invalid-argument", "type");
  const hasLocation = typeof latitude === "number" && typeof longitude === "number" &&
    Math.abs(latitude) <= 90 && Math.abs(longitude) <= 180;

  const db = getFirestore();
  const sub = (await db.doc(`subscriptions/${uid}`).get()).data();
  const premium = ["TRIAL", "PRO", "COMPANY"].includes(sub?.status) &&
    (!sub?.expiresAt || (sub.expiresAt as Timestamp).toMillis() > Date.now());
  if (!premium) throw new HttpsError("permission-denied", "premium-required");

  // Un solo SOS activo por usuario.
  const active = await db.collection("sosEvents").where("userId", "==", uid)
    .where("status", "in", SOS_ACTIVE).limit(1).get();
  if (!active.empty) return { id: active.docs[0].id };

  const user = (await db.doc(`users/${uid}`).get()).data() ?? {};
  const ref = db.collection("sosEvents").doc();
  await ref.set({
    userId: uid,
    userName: `${user.firstName ?? ""} ${user.lastName ?? ""}`.trim(),
    userPhone: user.phone ?? "",
    type,
    latitude: hasLocation ? latitude : null,
    longitude: hasLocation ? longitude : null,
    status: "CREATED",
    notes: typeof notes === "string" ? notes.slice(0, 500) : "",
    assignedOperatorId: null,
    createdAt: FieldValue.serverTimestamp(),
    updatedAt: FieldValue.serverTimestamp(),
    resolvedAt: null,
  });
  // Los operadores conectados están suscritos al tema sos_operators.
  await notifyTopic("sos_operators", "SOS_UPDATES", "Nuevo SOS", `Tipo: ${type}`);
  return { id: ref.id };
});

/** El dueño cancela su SOS activo. */
export const cancelSosEvent = onCall({ region: REGION }, async (request) => {
  const uid = requireAuth(request);
  const { id } = asRecord(request.data) as { id?: string };
  if (!id) throw new HttpsError("invalid-argument", "id");
  const ref = getFirestore().doc(`sosEvents/${id}`);
  const snap = await ref.get();
  if (!snap.exists) throw new HttpsError("not-found", "sos");
  if (snap.get("userId") !== uid) throw new HttpsError("permission-denied", "owner");
  if (!SOS_ACTIVE.includes(snap.get("status"))) return { ok: true };
  await ref.update({ status: "CANCELLED", updatedAt: FieldValue.serverTimestamp(), resolvedAt: FieldValue.serverTimestamp() });
  return { ok: true };
});

/** Panel SOS: aceptar caso, cambiar estado o agregar notas (rol SOS_OPERATOR/ADMIN). */
export const updateSosEvent = onCall({ region: REGION }, async (request) => {
  const operatorId = requireRole(request, ["SOS_OPERATOR", "ADMIN"]);
  const { id, action, value } = asRecord(request.data) as { id?: string; action?: string; value?: string };
  if (!id || !action) throw new HttpsError("invalid-argument", "id");
  const ref = getFirestore().doc(`sosEvents/${id}`);
  const snap = await ref.get();
  if (!snap.exists) throw new HttpsError("not-found", "sos");

  switch (action) {
    case "accept":
      await ref.update({ status: "RECEIVED", assignedOperatorId: operatorId, updatedAt: FieldValue.serverTimestamp() });
      break;
    case "status": {
      const allowed = ["RECEIVED", "IN_PROGRESS", "RESOLVED", "CANCELLED"];
      if (!value || !allowed.includes(value)) throw new HttpsError("invalid-argument", "status");
      await ref.update({
        status: value,
        assignedOperatorId: snap.get("assignedOperatorId") ?? operatorId,
        updatedAt: FieldValue.serverTimestamp(),
        resolvedAt: value === "RESOLVED" || value === "CANCELLED" ? FieldValue.serverTimestamp() : null,
      });
      break;
    }
    case "note":
      if (!value) throw new HttpsError("invalid-argument", "note");
      await ref.collection("operatorNotes").add({ text: value.slice(0, 1000), operatorId, createdAt: FieldValue.serverTimestamp() });
      await ref.update({ updatedAt: FieldValue.serverTimestamp() });
      break;
    default:
      throw new HttpsError("invalid-argument", "action");
  }
  return { ok: true };
});

/** Notifica al conductor cada cambio de estado de su SOS. */
export const onSosUpdated = onDocumentUpdated({ document: "sosEvents/{sosId}", region: REGION }, async (event) => {
  const before = event.data?.before.data();
  const after = event.data?.after.data();
  if (!before || !after || before.status === after.status) return;
  const text = STATUS_TEXT[after.status as string];
  if (text) await notifyUser(after.userId, "SOS_UPDATES", "SOS DriverLink", text);
});
