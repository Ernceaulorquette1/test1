import { getFirestore } from "firebase-admin/firestore";

/** Región de despliegue (Santiago). Debe coincidir con FUNCTIONS_REGION en la app. */
export const REGION = "southamerica-west1";

export const ALERT_CATEGORIES = [
  "ACCIDENT", "TRAFFIC", "ROAD_CLOSED", "DANGER", "SECURITY", "ROAD_PROBLEM", "EMERGENCY", "OTHER",
] as const;

export const SOS_TYPES = ["ROBBERY", "ACCIDENT", "PASSENGER_ISSUE", "MECHANICAL", "MEDICAL", "OTHER"] as const;
export const SOS_ACTIVE = ["CREATED", "RECEIVED", "IN_PROGRESS"];

/**
 * Configuración de negocio en `adminConfig/app`. Los valores por defecto replican
 * AppConfig de la app y res/xml/remote_config_defaults.xml.
 */
export interface ServerConfig {
  trialDays: number;
  sosEnabled: boolean;
  companyFeaturesEnabled: boolean;
  alertExpirationMinutes: Record<string, number>;
  alertResolveThreshold: number;
  reportsToHideMessage: number;
  locationRetentionDays: number;
  sosRetentionDays: number;
  playPackageName: string | null;
}

export const DEFAULT_CONFIG: ServerConfig = {
  trialDays: 30,
  sosEnabled: true,
  companyFeaturesEnabled: true,
  alertExpirationMinutes: {
    ACCIDENT: 90, TRAFFIC: 45, ROAD_CLOSED: 360, DANGER: 120,
    SECURITY: 120, ROAD_PROBLEM: 720, EMERGENCY: 60, OTHER: 60,
  },
  alertResolveThreshold: 3,
  reportsToHideMessage: 3,
  locationRetentionDays: 30,
  sosRetentionDays: 365,
  playPackageName: null,
};

let cached: { value: ServerConfig; at: number } | null = null;

/** Lee adminConfig/app con caché de 5 minutos por instancia (reduce lecturas). */
export async function loadConfig(): Promise<ServerConfig> {
  if (cached && Date.now() - cached.at < 5 * 60_000) return cached.value;
  const snap = await getFirestore().doc("adminConfig/app").get();
  const data = (snap.data() ?? {}) as Partial<ServerConfig>;
  const value: ServerConfig = {
    ...DEFAULT_CONFIG,
    ...data,
    alertExpirationMinutes: { ...DEFAULT_CONFIG.alertExpirationMinutes, ...(data.alertExpirationMinutes ?? {}) },
  };
  cached = { value, at: Date.now() };
  return value;
}
