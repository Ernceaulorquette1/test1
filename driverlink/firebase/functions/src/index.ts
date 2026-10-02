import { initializeApp } from "firebase-admin/app";
import { setGlobalOptions } from "firebase-functions/v2";
import { REGION } from "./config";

initializeApp();
// Límite de instancias para controlar costos.
setGlobalOptions({ region: REGION, maxInstances: 10 });

export { onUserCreated, onUserWritten, deleteAccount, setUserRole, setAccountSuspended, reviewVerification } from "./users";
export { startTrial, verifyPlayPurchase, expireSubscriptions } from "./subscriptions";
export { createSosEvent, cancelSosEvent, updateSosEvent, onSosUpdated } from "./sos";
export { onAlertCreated, onAlertVoteCreated, expireAlerts } from "./alerts";
export { onReportCreated } from "./moderation";
export { createCompany, requestJoinCompany, reviewJoinRequest, regenerateInviteCode } from "./companies";
export { onCommunityMessageCreated, onCompanyMessageCreated } from "./messages";
export { purgeOldLocations } from "./retention";
export { seedBaseData, upsertChannel } from "./admin";
