/** Validaciones compartidas por las Cloud Functions (equivalentes a las de la app). */

export function rutCheckDigit(body: string): string {
  let sum = 0;
  let factor = 2;
  for (const digit of body.split("").reverse()) {
    sum += Number(digit) * factor;
    factor = factor === 7 ? 2 : factor + 1;
  }
  const result = 11 - (sum % 11);
  if (result === 11) return "0";
  if (result === 10) return "K";
  return String(result);
}

export function normalizeRut(value: string): string | null {
  const clean = value.toUpperCase().replace(/[^0-9K]/g, "");
  if (clean.length < 2) return null;
  const body = clean.slice(0, -1).replace(/^0+/, "");
  if (!/^\d{1,8}$/.test(body)) return null;
  return `${body}-${clean.slice(-1)}`;
}

export function isValidRut(value: string): boolean {
  const normalized = normalizeRut(value);
  if (!normalized) return false;
  const [body, dv] = normalized.split("-");
  return rutCheckDigit(body) === dv;
}

export const INVITE_CODE = /^DL-[A-Z]{3}-\d{4}$/;
const LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ";

export function generateInviteCode(random: () => number = Math.random): string {
  const letters = Array.from({ length: 3 }, () => LETTERS[Math.floor(random() * LETTERS.length)]).join("");
  const digits = String(Math.floor(random() * 10_000)).padStart(4, "0");
  return `DL-${letters}-${digits}`;
}

export function slug(value: string): string {
  return value
    .toLowerCase()
    .normalize("NFD")
    .replace(/\p{M}/gu, "")
    .replace(/[^a-z0-9]+/g, "_")
    .replace(/^_+|_+$/g, "");
}

export function isNonEmptyString(value: unknown, max: number): value is string {
  return typeof value === "string" && value.trim().length > 0 && value.length <= max;
}
