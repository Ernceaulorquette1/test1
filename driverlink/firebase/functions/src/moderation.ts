import { FieldValue, getFirestore } from "firebase-admin/firestore";
import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { loadConfig, REGION } from "./config";

/**
 * Cada denuncia incrementa un contador en servidor. Un mensaje o audio con suficientes
 * denuncias se oculta automáticamente (hidden=true) a la espera de revisión humana.
 */
export const onReportCreated = onDocumentCreated({ document: "reports/{reportId}", region: REGION }, async (event) => {
  const report = event.data?.data();
  if (!report) return;
  const db = getFirestore();
  const config = await loadConfig();
  const counterRef = db.doc(`moderationCounters/${report.targetType}_${report.targetId}`);
  const count = await db.runTransaction(async (tx) => {
    const snap = await tx.get(counterRef);
    const reporters: string[] = snap.get("reporters") ?? [];
    if (reporters.includes(report.reporterId)) return reporters.length;
    tx.set(counterRef, {
      targetType: report.targetType,
      targetId: report.targetId,
      contextPath: report.contextPath ?? null,
      reporters: FieldValue.arrayUnion(report.reporterId),
      updatedAt: FieldValue.serverTimestamp(),
    }, { merge: true });
    return reporters.length + 1;
  });

  const isMessage = report.targetType === "MESSAGE" || report.targetType === "AUDIO";
  if (isMessage && report.contextPath && count >= config.reportsToHideMessage) {
    await db.doc(`${report.contextPath}/messages/${report.targetId}`)
      .update({ hidden: true, hiddenAt: FieldValue.serverTimestamp() })
      .catch(() => undefined);
  }
});
