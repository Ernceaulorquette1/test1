import { CallableRequest, HttpsError } from "firebase-functions/v2/https";

export type PlatformRole = "DRIVER" | "MODERATOR" | "ADMIN" | "SOS_OPERATOR";

/** Exige sesión y cuenta no suspendida. Devuelve el uid. */
export function requireAuth(request: CallableRequest<unknown>): string {
  const auth = request.auth;
  if (!auth) throw new HttpsError("unauthenticated", "Debes iniciar sesión.");
  if (auth.token.suspended === true) throw new HttpsError("permission-denied", "account-suspended");
  return auth.uid;
}

export function roleOf(request: CallableRequest<unknown>): PlatformRole {
  return ((request.auth?.token.role as PlatformRole | undefined) ?? "DRIVER");
}

/** Exige uno de los roles de plataforma (custom claims asignados por setUserRole). */
export function requireRole(request: CallableRequest<unknown>, roles: PlatformRole[]): string {
  const uid = requireAuth(request);
  if (!roles.includes(roleOf(request))) throw new HttpsError("permission-denied", "role-required");
  return uid;
}

export function asRecord(data: unknown): Record<string, unknown> {
  if (typeof data !== "object" || data === null) throw new HttpsError("invalid-argument", "payload");
  return data as Record<string, unknown>;
}
