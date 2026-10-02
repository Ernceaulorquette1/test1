import { FieldValue, getFirestore } from "firebase-admin/firestore";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { DEFAULT_CONFIG, REGION } from "./config";
import { asRecord, requireRole } from "./guards";

/** Canales comunitarios base. Agregar regiones/zonas = crear documentos, sin publicar la app. */
const BASE_CHANNELS = [
  { id: "santiago", name: "Santiago", region: "Región Metropolitana", city: "Santiago", zone: null, mode: "TEXT" },
  { id: "aeropuerto", name: "Aeropuerto", region: "Región Metropolitana", city: "Santiago", zone: "Pudahuel", mode: "TEXT" },
  { id: "santiago-centro", name: "Santiago Centro", region: "Región Metropolitana", city: "Santiago", zone: "Santiago", mode: "TEXT" },
  { id: "providencia", name: "Providencia", region: "Región Metropolitana", city: "Santiago", zone: "Providencia", mode: "TEXT" },
  { id: "maipu", name: "Maipú", region: "Región Metropolitana", city: "Santiago", zone: "Maipú", mode: "TEXT" },
  { id: "valparaiso", name: "Valparaíso", region: "Región de Valparaíso", city: "Valparaíso", zone: null, mode: "TEXT" },
  { id: "vina-del-mar", name: "Viña del Mar", region: "Región de Valparaíso", city: "Viña del Mar", zone: null, mode: "TEXT" },
  { id: "concepcion", name: "Concepción", region: "Región del Biobío", city: "Concepción", zone: null, mode: "TEXT" },
  { id: "radio-santiago", name: "Radio Santiago", region: "Región Metropolitana", city: "Santiago", zone: null, mode: "RADIO" },
  { id: "radio-aeropuerto", name: "Radio Aeropuerto", region: "Región Metropolitana", city: "Santiago", zone: "Pudahuel", mode: "RADIO" },
  { id: "radio-valparaiso", name: "Radio Valparaíso", region: "Región de Valparaíso", city: "Valparaíso", zone: null, mode: "RADIO" },
];

const PLATFORMS = [
  { id: "UBER", name: "Uber" }, { id: "DIDI", name: "DiDi" }, { id: "CABIFY", name: "Cabify" }, { id: "OTHER", name: "Otra" },
];

/**
 * ADMIN: carga datos base de producción (canales, plataformas, configuración).
 * Idempotente. NO crea datos de demostración.
 */
export const seedBaseData = onCall({ region: REGION }, async (request) => {
  requireRole(request, ["ADMIN"]);
  const db = getFirestore();
  const batch = db.batch();
  BASE_CHANNELS.forEach((c, i) => batch.set(db.doc(`channels/${c.id}`), {
    name: c.name, description: "", type: "COMMUNITY", mode: c.mode, region: c.region, city: c.city, zone: c.zone,
    order: i, active: true, createdAt: FieldValue.serverTimestamp(),
  }, { merge: true }));
  PLATFORMS.forEach((p) => batch.set(db.doc(`platforms/${p.id}`), { name: p.name, oauthAvailable: false }, { merge: true }));
  batch.set(db.doc("adminConfig/app"), DEFAULT_CONFIG, { merge: true });
  await batch.commit();
  return { channels: BASE_CHANNELS.length };
});

/** ADMIN: crea o actualiza un canal comunitario (nuevas regiones, zonas o radios). */
export const upsertChannel = onCall({ region: REGION }, async (request) => {
  requireRole(request, ["ADMIN"]);
  const d = asRecord(request.data);
  const types = ["PUBLIC", "COMMUNITY", "EMERGENCY"];
  if (typeof d.id !== "string" || !/^[a-z0-9-]{2,40}$/.test(d.id)) throw new HttpsError("invalid-argument", "id");
  if (typeof d.name !== "string" || d.name.length === 0) throw new HttpsError("invalid-argument", "name");
  if (!types.includes(String(d.type ?? "COMMUNITY"))) throw new HttpsError("invalid-argument", "type");
  await getFirestore().doc(`channels/${d.id}`).set({
    name: d.name, description: d.description ?? "", type: d.type ?? "COMMUNITY", mode: d.mode === "RADIO" ? "RADIO" : "TEXT",
    region: d.region ?? "", city: d.city ?? "", zone: d.zone ?? null, order: Number(d.order ?? 100),
    active: d.active !== false, updatedAt: FieldValue.serverTimestamp(),
  }, { merge: true });
  return { ok: true };
});
