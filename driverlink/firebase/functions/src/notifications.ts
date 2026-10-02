import { getFirestore } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import { logger } from "firebase-functions/v2";

export type NotificationCategory =
  | "NEARBY_ALERTS" | "REPLIES" | "CHANNEL_ACTIVITY" | "SOS_UPDATES" | "ADMIN" | "SUBSCRIPTION";

const DEFAULTS: Record<NotificationCategory, boolean> = {
  NEARBY_ALERTS: true, REPLIES: true, CHANNEL_ACTIVITY: false, SOS_UPDATES: true, ADMIN: true, SUBSCRIPTION: true,
};

/** Se envían mensajes de datos: la app decide el canal y respeta el modo silencioso. */
function payload(category: NotificationCategory, title: string, body: string) {
  return { data: { category, title, body }, android: { priority: category === "SOS_UPDATES" ? "high" as const : "normal" as const } };
}

/** Notifica a un usuario respetando sus preferencias y limpiando tokens inválidos. */
export async function notifyUser(uid: string, category: NotificationCategory, title: string, body: string): Promise<void> {
  const db = getFirestore();
  const prefs = (await db.doc(`notificationPreferences/${uid}`).get()).data();
  const enabled = (prefs?.categories?.[category] as boolean | undefined) ?? DEFAULTS[category];
  if (!enabled) return;
  const devices = await db.collection(`users/${uid}/devices`).limit(10).get();
  if (devices.empty) return;
  const tokens = devices.docs.map((d) => d.id);
  const response = await getMessaging().sendEachForMulticast({ tokens, ...payload(category, title, body) });
  const invalid = response.responses
    .map((r, i) => (!r.success && r.error?.code === "messaging/registration-token-not-registered" ? tokens[i] : null))
    .filter((t): t is string => t !== null);
  await Promise.all(invalid.map((t) => db.doc(`users/${uid}/devices/${t}`).delete()));
}

/** Notificación por tema (zona). El cliente se suscribe según sus preferencias. */
export async function notifyTopic(topic: string, category: NotificationCategory, title: string, body: string): Promise<void> {
  try {
    await getMessaging().send({ topic, ...payload(category, title, body) });
  } catch (error) {
    logger.warn("No se pudo notificar al tema", { topic, error });
  }
}
