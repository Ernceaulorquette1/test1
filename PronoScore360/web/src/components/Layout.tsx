import { useQuery } from "@tanstack/react-query";
import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { Catalog, Lists } from "../api/endpoints";
import { useAuth } from "../auth/AuthContext";
import { LANGS, setLang, useT } from "../i18n";
import { useTheme } from "../theme/ThemeContext";
import { Logo } from "./ui";

export default function Layout() {
  const { user, logout, isAdmin, isPro } = useAuth();
  const t = useT();
  const nav = useNavigate();
  const { mode, setMode } = useTheme();
  const status = useQuery({ queryKey: ["status"], queryFn: Catalog.status, staleTime: 60_000 });
  const notif = useQuery({ queryKey: ["notifications"], queryFn: Lists.notifications, refetchInterval: 60_000 });
  const link = (to: string, key: string) => (<li className="nav-item"><NavLink className="nav-link" to={to}>{t(key)}</NavLink></li>);
  return (
    <>
      <a className="skip-link" href="#main">Saltar al contenido</a>
      {status.data?.is_demo_mode && <div className="ps-demo-banner text-center py-1" role="note">⚠ {t("demo")}</div>}
      {status.data?.announcements?.map((a: any) => (<div key={a.id} className={`alert alert-${a.level === "critical" ? "danger" : a.level} rounded-0 mb-0 py-1 text-center small`}><strong>{a.title}</strong> {a.body}</div>))}
      <nav className="navbar navbar-expand-lg ps-navbar" aria-label="Principal">
        <div className="container">
          <NavLink className="navbar-brand d-flex align-items-center gap-2 fw-bold" to="/home"><Logo />PronoScore<span style={{ color: "#1fbf75" }}>360</span></NavLink>
          <button className="navbar-toggler border-0" data-bs-toggle="collapse" data-bs-target="#nav" aria-controls="nav" aria-label="Menú" onClick={(e) => { document.getElementById("nav")?.classList.toggle("show"); e.currentTarget.setAttribute("aria-expanded", String(document.getElementById("nav")?.classList.contains("show"))); }}>
            <span className="navbar-toggler-icon" style={{ filter: "invert(1)" }} />
          </button>
          <div className="collapse navbar-collapse" id="nav">
            <ul className="navbar-nav me-auto">
              {link("/home", "nav.home")}{link("/football", "nav.football")}{link("/nba", "nav.nba")}{link("/predictions", "nav.predictions")}
              {link("/selections", "nav.selections")}{link("/favorites", "nav.favorites")}{link("/history", "nav.history")}{link("/teams", "nav.teams")}
              {isAdmin && link("/admin", "nav.admin")}
            </ul>
            <div className="d-flex align-items-center gap-2 text-white">
              <span className={`badge ${isPro ? "text-bg-success" : "text-bg-secondary"}`} aria-label={`Plan ${user?.plan.name}`}>{user?.plan.name}</span>
              {!isPro && <NavLink to="/plans" className="btn btn-success btn-sm">{t("upgrade")}</NavLink>}
              <NavLink to="/notifications" className="btn btn-sm btn-outline-light position-relative" aria-label="Notificaciones">🔔{!!notif.data?.unread && <span className="badge text-bg-danger position-absolute top-0 start-100 translate-middle">{notif.data.unread}</span>}</NavLink>
              <select className="form-select form-select-sm w-auto" aria-label="Idioma" defaultValue={user?.language} onChange={(e) => setLang(e.target.value as any)}>{LANGS.map((l) => <option key={l.code} value={l.code}>{l.label}</option>)}</select>
              <button className="btn btn-sm btn-outline-light" aria-label="Cambiar tema" onClick={() => setMode(mode === "dark" ? "light" : "dark")}>{mode === "dark" ? "☀️" : "🌙"}</button>
              <div className="dropdown">
                <NavLink to="/profile" className="btn btn-sm btn-outline-light">{user?.display_name}</NavLink>
              </div>
              <button className="btn btn-sm btn-outline-light" onClick={async () => { await logout(); nav("/"); }}>{t("nav.logout")}</button>
            </div>
          </div>
        </div>
      </nav>
      <main id="main" className="container py-4"><Outlet /></main>
      <footer className="container pb-4 small ps-muted text-center">© PronoScore360 · <NavLink to="/help">{t("nav.help")}</NavLink> · <NavLink to="/privacy">Privacidad</NavLink> · <NavLink to="/terms">Términos</NavLink> · <NavLink to="/settings">{t("nav.settings")}</NavLink></footer>
    </>
  );
}
