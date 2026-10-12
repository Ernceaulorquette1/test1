import { useCallback, useEffect, useState } from "react";

export type Lang = "es" | "en" | "fr" | "ht";
export const LANGS: { code: Lang; label: string }[] = [{ code: "es", label: "Español" }, { code: "en", label: "English" }, { code: "fr", label: "Français" }, { code: "ht", label: "Kreyòl ayisyen" }];

/** Español es el idioma base completo; los demás se completan progresivamente (fallback a español). */
const dict: Record<Lang, Record<string, string>> = {
  es: {
    "nav.home": "Inicio", "nav.football": "Fútbol", "nav.nba": "NBA", "nav.predictions": "Pronósticos", "nav.selections": "Selecciones",
    "nav.favorites": "Favoritos", "nav.history": "Historial", "nav.teams": "Equipos", "nav.plans": "Planes", "nav.profile": "Perfil",
    "nav.settings": "Configuración", "nav.notifications": "Notificaciones", "nav.admin": "Administración", "nav.logout": "Salir", "nav.help": "Ayuda",
    "common.loading": "Cargando…", "common.retry": "Reintentar", "common.empty": "No hay resultados.", "common.save": "Guardar", "common.cancel": "Cancelar",
    "auth.login": "Iniciar sesión", "auth.register": "Crear cuenta", "auth.email": "Correo electrónico", "auth.password": "Contraseña",
    "auth.forgot": "¿Olvidaste tu contraseña?", "welcome.title": "Análisis deportivo basado en datos", "welcome.sub": "Probabilidades estimadas con modelos estadísticos para fútbol y NBA. Sin apuestas.",
    "disclaimer": "Estimaciones estadísticas informativas. No garantizan resultados. PronoScore360 no permite apuestas.", "upgrade": "Pasar a Pro",
    "insufficient": "Datos insuficientes", "demo": "Modo DEMO: los datos deportivos son ficticios.",
  },
  en: {
    "nav.home": "Home", "nav.football": "Football", "nav.nba": "NBA", "nav.predictions": "Predictions", "nav.selections": "Selections", "nav.favorites": "Favorites",
    "nav.history": "History", "nav.teams": "Teams", "nav.plans": "Plans", "nav.profile": "Profile", "nav.settings": "Settings", "nav.notifications": "Notifications",
    "nav.admin": "Admin", "nav.logout": "Log out", "nav.help": "Help", "common.loading": "Loading…", "common.retry": "Retry", "common.empty": "No results.",
    "auth.login": "Log in", "auth.register": "Create account", "auth.email": "Email", "auth.password": "Password", "auth.forgot": "Forgot your password?",
    "welcome.title": "Data-driven sports analysis", "welcome.sub": "Probabilities estimated with statistical models for football and NBA. No betting.",
    "disclaimer": "Informational statistical estimates. No guaranteed results. PronoScore360 does not allow betting.", "upgrade": "Go Pro",
    "insufficient": "Insufficient data", "demo": "DEMO mode: sports data is fictitious.",
  },
  fr: {
    "nav.home": "Accueil", "nav.football": "Football", "nav.nba": "NBA", "nav.predictions": "Pronostics", "nav.favorites": "Favoris", "nav.history": "Historique",
    "nav.plans": "Offres", "nav.profile": "Profil", "nav.settings": "Paramètres", "nav.logout": "Déconnexion", "auth.login": "Connexion", "auth.register": "Créer un compte",
    "auth.email": "E-mail", "auth.password": "Mot de passe", "welcome.title": "Analyse sportive basée sur les données", "upgrade": "Passer à Pro", "insufficient": "Données insuffisantes",
  },
  ht: {
    "nav.home": "Akèy", "nav.football": "Foutbòl", "nav.nba": "NBA", "nav.predictions": "Pwonostik", "nav.favorites": "Favori", "nav.plans": "Plan", "nav.profile": "Pwofil",
    "nav.logout": "Soti", "auth.login": "Konekte", "auth.register": "Kreye kont", "auth.email": "Imèl", "auth.password": "Modpas", "welcome.title": "Analiz espò ki baze sou done", "upgrade": "Pase Pro",
    "insufficient": "Done pa sifi",
  },
};

let current: Lang = (() => { try { return (localStorage.getItem("ps360.lang") as Lang) || "es"; } catch { return "es"; } })();
const listeners = new Set<() => void>();
export function setLang(l: Lang) { current = l; try { localStorage.setItem("ps360.lang", l); } catch { /* noop */ } document.documentElement.lang = l === "ht" ? "ht" : l; listeners.forEach((f) => f()); }
export function translate(key: string, lang: Lang = current): string { return dict[lang][key] ?? dict.es[key] ?? key; }
export function useT() {
  const [, force] = useState(0);
  useEffect(() => { const f = () => force((n) => n + 1); listeners.add(f); return () => { listeners.delete(f); }; }, []);
  return useCallback((key: string) => translate(key), []);
}
export const currentLang = () => current;
