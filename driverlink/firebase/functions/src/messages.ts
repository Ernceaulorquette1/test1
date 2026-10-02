import { FieldValue, getFirestore } from "firebase-admin/firestore";
import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { REGION } from "./config";
import { notifyUser } from "./notifications";

/** Actualiza la actividad del canal y avisa al autor del mensaje respondido (sin spam). */
async function handleNewMessage(channelPath: string, message: FirebaseFirestore.DocumentData) {
  const db = getFirestore();
  await db.doc(channelPath).update({ lastMessageAt: FieldValue.serverTimestamp() }).catch(() => undefined);
  const replyToId = message.replyToId as string | undefined;
  if (!replyToId) return;
  const original = await db.doc(`${channelPath}/messages/${replyToId}`).get();
  const authorId = original.get("senderId") as string | undefined;
  if (!authorId || authorId === message.senderId) return;
  // Si el autor bloqueó a quien responde, no se notifica.
  const blocked = await db.doc(`users/${authorId}/blockedUsers/${message.senderId}`).get();
  if (blocked.exists) return;
  await notifyUser(authorId, "REPLIES", `${message.senderName} te respondió`, (message.text as string | undefined)?.slice(0, 120) ?? "");
}

export const onCommunityMessageCreated = onDocumentCreated(
  { document: "channels/{channelId}/messages/{messageId}", region: REGION },
  async (event) => {
    const data = event.data?.data();
    if (data) await handleNewMessage(`channels/${event.params.channelId}`, data);
  },
);

export const onCompanyMessageCreated = onDocumentCreated(
  { document: "companies/{companyId}/channels/{channelId}/messages/{messageId}", region: REGION },
  async (event) => {
    const data = event.data?.data();
    if (data) await handleNewMessage(`companies/${event.params.companyId}/channels/${event.params.channelId}`, data);
  },
);
