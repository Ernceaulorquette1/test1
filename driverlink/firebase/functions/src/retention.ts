import { getFirestore, Timestamp } from "firebase-admin/firestore";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { logger } from "firebase-functions/v2";
import { loadConfig, REGION } from "./config";

/**
 * Política de retención configurable (adminConfig/app.locationRetentionDays):
 * elimina recorridos operacionales antiguos. Se ejecuta de madrugada en hora de Chile.
 */
export const purgeOldLocations = onSchedule({ schedule: "every day 03:30", timeZone: "America/Santiago", region: REGION }, async () => {
  const config = await loadConfig();
  const cutoff = Timestamp.fromMillis(Date.now() - config.locationRetentionDays * 24 * 60 * 60 * 1000);
  const db = getFirestore();
  let total = 0;
  for (let round = 0; round < 10; round++) {
    const snap = await db.collectionGroup("locationUpdates").where("createdAt", "<", cutoff).limit(400).get();
    if (snap.empty) break;
    const batch = db.batch();
    snap.docs.forEach((d) => batch.delete(d.ref));
    await batch.commit();
    total += snap.size;
  }
  logger.info("Ubicaciones antiguas eliminadas", { total });
});
