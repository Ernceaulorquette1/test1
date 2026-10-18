import axios, { AxiosError, InternalAxiosRequestConfig } from "axios";
import type { ApiErrorInfo, Tokens } from "./types";

export const API_URL: string = (import.meta.env.VITE_API_URL as string | undefined) ?? "http://localhost:8000/api/v1";
const KEY = "ps360.tokens";

export function getTokens(): Tokens | null {
  try { const raw = localStorage.getItem(KEY); return raw ? (JSON.parse(raw) as Tokens) : null; } catch { return null; }
}
export function setTokens(t: Tokens | null) {
  try { t ? localStorage.setItem(KEY, JSON.stringify(t)) : localStorage.removeItem(KEY); } catch { /* almacenamiento no disponible */ }
}

export const api = axios.create({ baseURL: API_URL, timeout: 20000 });

api.interceptors.request.use((cfg: InternalAxiosRequestConfig) => {
  const t = getTokens();
  if (t) cfg.headers.set("Authorization", `Bearer ${t.access_token}`);
  return cfg;
});

let refreshing: Promise<Tokens | null> | null = null;
async function refreshTokens(): Promise<Tokens | null> {
  const t = getTokens();
  if (!t) return null;
  try {
    const r = await axios.post<Tokens>(`${API_URL}/auth/refresh`, { refresh_token: t.refresh_token });
    setTokens(r.data);
    return r.data;
  } catch {
    setTokens(null);
    window.dispatchEvent(new Event("ps:logout"));
    return null;
  }
}

api.interceptors.response.use((r) => r, async (error: AxiosError<any>) => {
  const cfg = error.config as (InternalAxiosRequestConfig & { _retried?: boolean }) | undefined;
  const code = error.response?.data?.error?.code;
  if (error.response?.status === 401 && cfg && !cfg._retried && ["token_expired", "invalid_token"].includes(code) && getTokens()) {
    cfg._retried = true;
    refreshing = refreshing ?? refreshTokens().finally(() => { refreshing = null; });   // una sola renovación concurrente
    const t = await refreshing;
    if (t) return api(cfg);
  }
  return Promise.reject(error);
});

export function apiError(e: unknown): ApiErrorInfo {
  const ax = e as AxiosError<any>;
  const err = ax?.response?.data?.error;
  if (err) return { status: ax.response!.status, code: err.code, message: err.message, details: err.details };
  if (ax?.code === "ERR_NETWORK" || ax?.message === "Network Error") return { status: 0, code: "network", message: "No se pudo conectar con el servidor. Revisa tu conexión." };
  return { status: ax?.response?.status ?? 0, code: "unknown", message: (e as Error)?.message ?? "Error inesperado" };
}
