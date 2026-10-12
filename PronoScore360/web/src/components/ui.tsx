import { ReactNode } from "react";
import { Link } from "react-router-dom";
import { apiError } from "../api/client";
import type { Fixture } from "../api/types";

export function Spinner({ label = "Cargando…" }: { label?: string }) {
  return (<div className="text-center py-5" role="status" aria-live="polite"><div className="spinner-border text-primary" aria-hidden="true" /><div className="ps-muted mt-2">{label}</div></div>);
}

export function ErrorBox({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const e = apiError(error);
  return (
    <div className="alert alert-danger d-flex justify-content-between align-items-start gap-2" role="alert">
      <div><strong>Algo salió mal.</strong> {e.message}{e.code === "network" ? "" : <small className="d-block ps-muted">código: {e.code}</small>}</div>
      {onRetry && <button className="btn btn-sm btn-outline-danger" onClick={onRetry}>Reintentar</button>}
    </div>
  );
}

export function Empty({ title = "No hay resultados", children }: { title?: string; children?: ReactNode }) {
  return (<div className="text-center py-5 ps-muted"><div style={{ fontSize: 40 }} aria-hidden="true">⚽🏀</div><h2 className="h6 mt-2">{title}</h2>{children}</div>);
}

export const pct = (p: number, d = 1) => `${(p * 100).toFixed(d)} %`;

export function ProbBar({ value, label }: { value: number; label?: string }) {
  const cls = value >= 0.7 ? "hi" : value >= 0.55 ? "mid" : "lo";
  return (
    <div>
      {label && <div className="d-flex justify-content-between small"><span>{label}</span><strong>{pct(value)}</strong></div>}
      <div className="ps-prob" role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={Math.round(value * 100)} aria-label={label ?? "Probabilidad"}>
        <span className={cls} style={{ width: `${Math.max(2, value * 100)}%` }} />
      </div>
    </div>
  );
}

const CATS: Record<string, [string, string]> = {
  high_probability: ["Alta probabilidad estimada", "success"], medium_probability: ["Probabilidad media", "warning"],
  high_uncertainty: ["Alta incertidumbre", "danger"], insufficient_data: ["Datos insuficientes", "secondary"],
};
export function CategoryBadge({ category }: { category: string }) {
  const [label, color] = CATS[category] ?? [category, "secondary"];
  return <span className={`badge text-bg-${color}`}>{label}</span>;
}

export function TeamBadge({ name }: { name: string }) {
  const initials = name.replace(/^Demo\s+/i, "").split(/\s+/).slice(0, 2).map((w) => w[0]).join("").toUpperCase();
  return <span className="ps-badge-team" aria-hidden="true">{initials}</span>;
}

export function DemoTag() { return <span className="badge text-bg-warning ms-1" title="Datos ficticios para pruebas">DEMO</span>; }

export function fmtDate(iso: string, tz?: string) {
  return new Date(iso).toLocaleString("es", { weekday: "short", day: "2-digit", month: "short", hour: "2-digit", minute: "2-digit", timeZone: tz });
}

export function MatchCard({ f, tz }: { f: Fixture; tz?: string }) {
  const done = f.status === "finished";
  return (
    <Link to={`/match/${f.id}`} className="text-decoration-none text-reset" aria-label={`${f.home_team.name} contra ${f.away_team.name}`}>
      <div className="ps-card clickable p-3 h-100">
        <div className="d-flex justify-content-between small ps-muted mb-2">
          <span>{f.league.name}{f.is_demo && <DemoTag />}</span><span>{fmtDate(f.kickoff, tz)}</span>
        </div>
        {[f.home_team, f.away_team].map((t, i) => (
          <div key={t.id} className="d-flex align-items-center gap-2 mb-1">
            <TeamBadge name={t.name} /><span className="flex-grow-1 fw-semibold">{t.name}</span>
            {done && <strong>{i === 0 ? f.home_score : f.away_score}</strong>}
          </div>
        ))}
        <div className="small mt-2">{done ? <span className="badge text-bg-secondary">Finalizado</span> : <span className="badge text-bg-primary">Ver análisis</span>}</div>
      </div>
    </Link>
  );
}

export function Pager({ page, total, pageSize, onChange }: { page: number; total: number; pageSize: number; onChange: (p: number) => void }) {
  const pages = Math.max(1, Math.ceil(total / pageSize));
  if (pages <= 1) return null;
  return (
    <nav aria-label="Paginación" className="d-flex justify-content-center gap-2 my-3">
      <button className="btn btn-outline-primary btn-sm" disabled={page <= 1} onClick={() => onChange(page - 1)}>‹ Anterior</button>
      <span className="align-self-center small">Página {page} de {pages}</span>
      <button className="btn btn-outline-primary btn-sm" disabled={page >= pages} onClick={() => onChange(page + 1)}>Siguiente ›</button>
    </nav>
  );
}

export function UpgradeNotice({ children }: { children?: ReactNode }) {
  return (
    <div className="ps-locked p-3 my-3 d-flex justify-content-between align-items-center gap-3 flex-wrap">
      <div>🔒 {children ?? "Esta función es parte del plan Pro."}</div>
      <Link className="btn btn-success btn-sm" to="/plans">Ver plan Pro</Link>
    </div>
  );
}

export function Disclaimer() {
  return <p className="small ps-muted mt-4">Estimaciones estadísticas informativas; no garantizan resultados. PronoScore360 no permite apuestas ni depósitos. <Link to="/terms">Términos</Link> · <Link to="/privacy">Privacidad</Link></p>;
}

export function Logo({ size = 32 }: { size?: number }) { return <img src="/logo.svg" width={size} height={size} alt="" />; }

export function PageTitle({ children, sub }: { children: ReactNode; sub?: ReactNode }) {
  return (<div className="mb-3"><h1 className="h3 mb-0">{children}</h1>{sub && <div className="ps-muted">{sub}</div>}</div>);
}
