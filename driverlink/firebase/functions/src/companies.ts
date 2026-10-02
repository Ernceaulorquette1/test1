import { FieldValue, getFirestore } from "firebase-admin/firestore";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { loadConfig, REGION } from "./config";
import { asRecord, requireAuth } from "./guards";
import { notifyUser } from "./notifications";
import { generateInviteCode, INVITE_CODE, isNonEmptyString, isValidRut, normalizeRut } from "./validation";

const db = () => getFirestore();
const ADMIN_ROLES = ["OWNER", "ADMIN"];

async function requireCompanyRole(companyId: string, uid: string, roles: string[]) {
  const member = await db().doc(`companies/${companyId}/members/${uid}`).get();
  if (!member.exists || member.get("status") !== "ACTIVE" || !roles.includes(member.get("role"))) {
    throw new HttpsError("permission-denied", "company-role");
  }
}

async function uniqueInviteCode(): Promise<string> {
  for (let i = 0; i < 10; i++) {
    const code = generateInviteCode();
    if (!(await db().doc(`companyInvites/${code}`).get()).exists) return code;
  }
  throw new HttpsError("resource-exhausted", "invite-code");
}

/** Crea la empresa, el miembro OWNER, el código de invitación y los canales por defecto. */
export const createCompany = onCall({ region: REGION }, async (request) => {
  const uid = requireAuth(request);
  const config = await loadConfig();
  if (!config.companyFeaturesEnabled) throw new HttpsError("failed-precondition", "feature-disabled");
  const d = asRecord(request.data);
  const rut = typeof d.rut === "string" ? normalizeRut(d.rut) : null;
  if (!rut || !isValidRut(rut)) throw new HttpsError("invalid-argument", "RUT");
  for (const field of ["legalName", "tradeName", "email", "phone", "address"]) {
    if (!isNonEmptyString(d[field], 120)) throw new HttpsError("invalid-argument", field);
  }
  const existing = await db().collection("companies").where("rut", "==", rut).limit(1).get();
  if (!existing.empty) throw new HttpsError("already-exists", "rut");

  const user = (await db().doc(`users/${uid}`).get()).data() ?? {};
  const companyRef = db().collection("companies").doc();
  const code = await uniqueInviteCode();
  const batch = db().batch();
  batch.set(companyRef, {
    rut, legalName: d.legalName, tradeName: d.tradeName, email: d.email, phone: d.phone, address: d.address,
    status: "PENDING_REVIEW", plan: "FLOTAS_BASE", ownerId: uid, createdAt: FieldValue.serverTimestamp(),
  });
  batch.set(companyRef.collection("members").doc(uid), {
    userId: uid, companyId: companyRef.id, displayName: `${user.firstName ?? ""} ${user.lastName ?? ""}`.trim(),
    role: "OWNER", status: "ACTIVE", joinedAt: FieldValue.serverTimestamp(), requestedAt: FieldValue.serverTimestamp(),
  });
  batch.set(companyRef.collection("private").doc("invite"), { code, createdAt: FieldValue.serverTimestamp() });
  batch.set(db().doc(`companyInvites/${code}`), { companyId: companyRef.id, active: true, createdAt: FieldValue.serverTimestamp() });
  const channels: [string, string][] = [
    ["General", "TEXT"], ["Despacho", "TEXT"], ["Conductores", "TEXT"], ["Emergencias", "TEXT"],
    ["Radio General", "RADIO"], ["Radio Despacho", "RADIO"], ["Radio Emergencias", "RADIO"],
  ];
  channels.forEach(([name, mode], i) => batch.set(companyRef.collection("channels").doc(), {
    name, description: "", type: "PRIVATE_COMPANY", mode, order: i, active: true, createdAt: FieldValue.serverTimestamp(),
  }));
  await batch.commit();
  return { companyId: companyRef.id };
});

/** Solicitud de ingreso: conocer el código NO da acceso; queda PENDING hasta aprobación. */
export const requestJoinCompany = onCall({ region: REGION }, async (request) => {
  const uid = requireAuth(request);
  const { code } = asRecord(request.data) as { code?: string };
  const normalized = (code ?? "").trim().toUpperCase();
  if (!INVITE_CODE.test(normalized)) throw new HttpsError("invalid-argument", "code");
  const invite = await db().doc(`companyInvites/${normalized}`).get();
  if (!invite.exists || invite.get("active") !== true) throw new HttpsError("not-found", "code");
  const companyId = invite.get("companyId") as string;
  const memberRef = db().doc(`companies/${companyId}/members/${uid}`);
  const member = await memberRef.get();
  if (member.exists && member.get("status") !== "REMOVED") throw new HttpsError("already-exists", "member");
  const user = (await db().doc(`users/${uid}`).get()).data() ?? {};
  await memberRef.set({
    userId: uid, companyId, displayName: `${user.firstName ?? ""} ${user.lastName ?? ""}`.trim(),
    role: "DRIVER", status: "PENDING", joinedAt: null, requestedAt: FieldValue.serverTimestamp(),
  });
  const admins = await db().collection(`companies/${companyId}/members`).where("role", "in", ADMIN_ROLES).get();
  await Promise.all(admins.docs.map((a) => notifyUser(a.id, "ADMIN", "Nueva solicitud", "Un conductor solicitó unirse a tu empresa.")));
  return { ok: true };
});

/** OWNER/ADMIN aprueba o rechaza una solicitud de ingreso. */
export const reviewJoinRequest = onCall({ region: REGION }, async (request) => {
  const uid = requireAuth(request);
  const { companyId, userId, approve } = asRecord(request.data) as { companyId?: string; userId?: string; approve?: boolean };
  if (!companyId || !userId || typeof approve !== "boolean") throw new HttpsError("invalid-argument", "payload");
  if (userId === uid) throw new HttpsError("invalid-argument", "self");
  await requireCompanyRole(companyId, uid, ADMIN_ROLES);
  const ref = db().doc(`companies/${companyId}/members/${userId}`);
  const member = await ref.get();
  if (!member.exists || member.get("status") !== "PENDING") throw new HttpsError("failed-precondition", "not-pending");
  await ref.update(approve
    ? { status: "ACTIVE", joinedAt: FieldValue.serverTimestamp(), reviewedBy: uid }
    : { status: "REMOVED", reviewedBy: uid });
  await notifyUser(userId, "ADMIN", "DriverLink Flotas",
    approve ? "Tu solicitud para unirte a la empresa fue aprobada." : "Tu solicitud para unirte a la empresa fue rechazada.");
  return { ok: true };
});

/** Invalida el código anterior y genera uno nuevo. */
export const regenerateInviteCode = onCall({ region: REGION }, async (request) => {
  const uid = requireAuth(request);
  const { companyId } = asRecord(request.data) as { companyId?: string };
  if (!companyId) throw new HttpsError("invalid-argument", "companyId");
  await requireCompanyRole(companyId, uid, ADMIN_ROLES);
  const privateRef = db().doc(`companies/${companyId}/private/invite`);
  const old = (await privateRef.get()).get("code") as string | undefined;
  const code = await uniqueInviteCode();
  const batch = db().batch();
  if (old) batch.set(db().doc(`companyInvites/${old}`), { active: false }, { merge: true });
  batch.set(db().doc(`companyInvites/${code}`), { companyId, active: true, createdAt: FieldValue.serverTimestamp() });
  batch.set(privateRef, { code, createdAt: FieldValue.serverTimestamp() });
  await batch.commit();
  return { code };
});
