import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { Catalog, Lists, Pred } from "../api/endpoints";
import { apiError } from "../api/client";
import type { Analysis, Prediction } from "../api/types";
import { useAuth } from "../auth/AuthContext";
import { useBasket } from "../components/Basket";
import { CategoryBadge, DemoTag, Disclaimer, ErrorBox, fmtDate, pct, ProbBar, Spinner, TeamBadge, UpgradeNotice } from "../components/ui";
import { GROUP_LABEL, marketLabel, QUALITY_LABEL, UNCERTAINTY_LABEL } from "../components/format";

export default function MatchDetail() {
  const id = Number(useParams().id);
  const { user, isPro, refreshUser } = useAuth();
  const qc = useQueryClient();
  const [run, setRun] = useState(false);
  const [tab, setTab] = useState<"pred" | "score" | "stats" | "why">("pred");
  const fx = useQuery({ queryKey: ["fixture", id], queryFn: () => Catalog.fixture(id) });
  const an = useQuery({ queryKey: ["analysis", id], queryFn: async () => { const r = await Pred.analysis(id); refreshUser(); qc.invalidateQueries({ queryKey: ["history"] }); return r; }, enabled: run, retry: false });
  if (fx.isLoading) return <Spinner />;
  if (fx.error) return <ErrorBox error={fx.error} onRetry={() => fx.refetch()} />;
  const f = fx.data;
  const u = user!.usage;
  const err = an.error ? apiError(an.error) : null;
  return (
    <>
      <div className="ps-hero p-4 mb-3">
        <div className="small opacity-75">{f.league.name}{f.is_demo && <DemoTag />} · {fmtDate(f.kickoff, user!.timezone)}</div>
        <div className="row align-items-center text-center my-3">
          <div className="col-5"><TeamBadge name={f.home_team.name} /><div className="fw-bold mt-1">{f.home_team.name}</div></div>
          <div className="col-2 h3 mb-0">{f.status === "finished" ? `${f.home_score}-${f.away_score}` : "vs"}</div>
          <div className="col-5"><TeamBadge name={f.away_team.name} /><div className="fw-bold mt-1">{f.away_team.name}</div></div>
        </div>
        {f.injuries?.length > 0 && <div className="small">🩹 Bajas: {f.injuries.map((i: any) => `${i.player} (${i.reason})`).join(", ")}</div>}
      </div>

      {!run && (
        <div className="ps-card p-3 mb-3 d-flex justify-content-between align-items-center flex-wrap gap-2">
          <div>
            <strong>Análisis del partido</strong>
            <div className="small ps-muted">{user!.plan.code === "PRO" ? "Consultas ilimitadas con tu plan Pro." : `Usa 1 de tus ${u.limit} consultas diarias (te quedan ${u.remaining}). Abrir de nuevo el mismo partido hoy no descuenta otra.`}</div>
          </div>
          <button className="btn btn-success" onClick={() => setRun(true)} disabled={!isPro && u.remaining === 0}>Analizar partido</button>
        </div>)}

      {an.isLoading && <Spinner label="Calculando análisis…" />}
      {err && (err.code === "quota_exceeded" ? <div className="alert alert-warning" role="alert">{err.message} <Link to="/plans" className="alert-link">Ver Pro</Link></div> : <ErrorBox error={an.error} />)}
      {an.data && <AnalysisView a={an.data} tab={tab} setTab={setTab} fixtureId={id} />}
      {run && an.data && an.data.analysis.status !== "ok" && <div className="alert alert-secondary"><strong>Datos insuficientes.</strong> {an.data.reason}</div>}
      {tab === "stats" && !an.data && null}
      <Disclaimer />
    </>
  );
}

function AnalysisView({ a, tab, setTab, fixtureId }: { a: Analysis; tab: string; setTab: (t: any) => void; fixtureId: number }) {
  const { isPro } = useAuth();
  if (a.analysis.status !== "ok") return null;
  const f = a.fixture;
  const tabs: [string, string][] = [["pred", "Pronósticos"], ["score", "Marcadores"], ["stats", "Estadísticas"], ["why", "Explicación"]];
  return (
    <>
      <div className="small ps-muted mb-2">Actualizado: {fmtDate(a.analysis.generated_at)} · Datos: {a.analysis.n_home} / {a.analysis.n_away} partidos previos · Calidad: <strong>{QUALITY_LABEL[a.analysis.quality]}</strong>{!a.analysis.is_pre_match && " · ⚠ análisis posterior al inicio (no es una predicción previa)"}</div>
      <ul className="nav nav-pills mb-3" role="tablist">{tabs.map(([k, l]) => (<li className="nav-item" key={k} role="presentation"><button role="tab" aria-selected={tab === k} className={`nav-link ${tab === k ? "active" : ""}`} onClick={() => setTab(k)}>{l}</button></li>))}</ul>
      {tab === "pred" && <PredictionsTab a={a} />}
      {tab === "score" && <ScoreTab a={a} />}
      {tab === "stats" && <StatsTab fixtureId={fixtureId} home={f.home_team.name} away={f.away_team.name} sport={f.sport} />}
      {tab === "why" && <WhyTab a={a} />}
      {!isPro && <UpgradeNotice>{a.upgrade_hint ?? "Pro desbloquea todos los mercados y marcadores."} {a.locked_count ? `(${a.locked_count} pronósticos bloqueados)` : ""}</UpgradeNotice>}
    </>
  );
}

function PredRow({ p, home, away, fixtureId, match }: { p: Prediction; home: string; away: string; fixtureId: number; match: string }) {
  const { isPro } = useAuth();
  const basket = useBasket();
  const label = marketLabel(p.market, p.selection, home, away);
  const inB = p.key ? basket.has(fixtureId, p.key) : false;
  const save = useMutation({ mutationFn: () => Pred.save(p.id) });
  return (
    <div className="py-2 border-bottom">
      <div className="d-flex justify-content-between align-items-center gap-2 flex-wrap">
        <span>{label}</span>
        <span className="d-flex align-items-center gap-2"><CategoryBadge category={p.category} />
          {isPro && p.key && <button className={`btn btn-sm ${inB ? "btn-success" : "btn-outline-secondary"}`} aria-pressed={inB} title="Añadir a selecciones" onClick={() => inB ? basket.remove({ fixture_id: fixtureId, key: p.key!, label, match, probability: p.probability }) : basket.add({ fixture_id: fixtureId, key: p.key!, label, match, probability: p.probability })}>{inB ? "✓ En selección" : "+ Selección"}</button>}
          {isPro && <button className="btn btn-sm btn-outline-warning" title="Guardar pronóstico" onClick={() => save.mutate()} disabled={save.isSuccess}>{save.isSuccess ? "★" : "☆"}</button>}</span>
      </div>
      <ProbBar value={p.probability} label={undefined} />
      <div className="d-flex justify-content-between small ps-muted"><span>{pct(p.probability)} · {p.data_points} partidos · incertidumbre {UNCERTAINTY_LABEL[p.uncertainty]}</span></div>
    </div>
  );
}

function PredictionsTab({ a }: { a: Analysis }) {
  const f = a.fixture;
  const groups = Array.from(new Set(a.predictions.map((p) => p.group)));
  const match = `${f.home_team.name} vs ${f.away_team.name}`;
  const x2 = a.predictions.filter((p) => p.market === "1X2" || p.market === "ML");
  return (
    <>
      {x2.length > 0 && <div className="ps-card p-3 mb-3"><h2 className="h6">{f.sport === "football" ? "Resultado 1X2" : "Probabilidad de victoria"}</h2><div className="row g-3">{x2.map((p) => <div className="col-md" key={p.id}><ProbBar value={p.probability} label={marketLabel(p.market, p.selection, f.home_team.name, f.away_team.name)} /></div>)}</div></div>}
      {groups.map((g) => (
        <div className="ps-card p-3 mb-3" key={g}><h2 className="h6">{GROUP_LABEL[g] ?? g}</h2>
          {a.predictions.filter((p) => p.group === g).map((p) => <PredRow key={p.id} p={p} home={f.home_team.name} away={f.away_team.name} fixtureId={f.id} match={match} />)}</div>))}
      {a.locked_groups.length > 0 && <div className="ps-locked p-3 mb-3">🔒 Bloqueado en tu plan: {a.locked_groups.map((g) => GROUP_LABEL[g] ?? g).join(", ")}</div>}
    </>
  );
}

function ScoreTab({ a }: { a: Analysis }) {
  const s = a.exact_score;
  if (!s) return <div className="alert alert-secondary">Datos insuficientes para estimar marcadores.</div>;
  if (s.type === "football") {
    const data = s.top_scores.map((t: any) => ({ name: `${t.home}-${t.away}`, p: +(t.probability * 100).toFixed(2) }));
    return (
      <div className="ps-card p-3">
        <h2 className="h6">Marcador más probable: <span className="badge text-bg-primary fs-6">{s.most_likely.home}-{s.most_likely.away}</span> <small className="ps-muted">({pct(s.most_likely.probability)})</small></h2>
        <p className="mb-2">Goles esperados: <strong>{a.fixture.home_team.name} {s.expected_goals.home}</strong> · <strong>{a.fixture.away_team.name} {s.expected_goals.away}</strong></p>
        <div style={{ height: 240 }} role="img" aria-label="Gráfico de probabilidad de los marcadores más probables"><ResponsiveContainer><BarChart data={data}><CartesianGrid strokeDasharray="3 3" /><XAxis dataKey="name" /><YAxis unit="%" /><Tooltip formatter={(v: any) => `${v} %`} /><Bar dataKey="p" fill="#1fbf75" /></BarChart></ResponsiveContainer></div>
        <table className="table table-sm mt-2"><thead><tr><th>Marcador</th><th>Probabilidad de acertarlo exacto</th></tr></thead><tbody>{s.top_scores.map((t: any) => <tr key={`${t.home}-${t.away}`}><td>{t.home}-{t.away}</td><td>{pct(t.probability, 2)}</td></tr>)}</tbody></table>
        {s.locked_extra_scores > 0 && <UpgradeNotice>Pro muestra los 5 marcadores más probables.</UpgradeNotice>}
        <p className="small ps-muted mb-0">{s.note} {s.explanation}</p>
      </div>);
  }
  return (
    <div className="ps-card p-3">
      <h2 className="h6">Puntos esperados</h2>
      <div className="row text-center my-2"><div className="col"><div className="display-6">{s.expected_points.home}</div><div>{a.fixture.home_team.name}</div></div><div className="col"><div className="display-6">{s.expected_points.away}</div><div>{a.fixture.away_team.name}</div></div></div>
      <p>Marcador central estimado: <strong>{s.central_score.home}-{s.central_score.away}</strong> <span className="badge text-bg-warning">Estimación, no predicción exacta</span><br /><small className="ps-muted">Probabilidad de acertar ese marcador exacto: {pct(s.central_score.exact_hit_probability, 2)}</small></p>
      <div className="row g-3"><div className="col-md-6"><ProbBar value={s.p_home_win} label={`Gana ${a.fixture.home_team.name}`} /></div><div className="col-md-6"><ProbBar value={s.p_away_win} label={`Gana ${a.fixture.away_team.name}`} /></div></div>
      <p className="mt-2 mb-1">Total esperado: <strong>{s.expected_total}</strong> · Diferencia esperada: <strong>{s.expected_margin}</strong></p>
      {s.range_total_80 ? (<><p className="mb-1">Rango probable (80 %): {a.fixture.home_team.name} {s.range_home_80[0]}–{s.range_home_80[1]} · {a.fixture.away_team.name} {s.range_away_80[0]}–{s.range_away_80[1]} · Total {s.range_total_80[0]}–{s.range_total_80[1]}</p>
        <div style={{ height: 240 }} role="img" aria-label="Distribución del total de puntos"><ResponsiveContainer><BarChart data={s.total_distribution.map((d: any) => ({ name: `${d.from}`, p: +(d.probability * 100).toFixed(2) }))}><CartesianGrid strokeDasharray="3 3" /><XAxis dataKey="name" /><YAxis unit="%" /><Tooltip formatter={(v: any) => `${v} %`} /><Bar dataKey="p" fill="#12305a" /></BarChart></ResponsiveContainer></div></>) : <UpgradeNotice>Pro muestra rangos probables y la distribución de puntos.</UpgradeNotice>}
      <p className="small ps-muted mb-0">{s.note} {s.explanation}</p>
    </div>);
}

function StatsTab({ fixtureId, home, away, sport }: { fixtureId: number; home: string; away: string; sport: string }) {
  const [n, setN] = useState("10");
  const st = useQuery({ queryKey: ["fxstats", fixtureId], queryFn: () => Catalog.fixtureStats(fixtureId) });
  if (st.isLoading) return <Spinner />;
  if (st.error) return <ErrorBox error={st.error} />;
  const form = st.data.form[n];
  const unit = sport === "football" ? "goles" : "puntos";
  const chart = [{ name: "A favor", [home]: form.home_team.avg_for, [away]: form.away_team.avg_for }, { name: "En contra", [home]: form.home_team.avg_against, [away]: form.away_team.avg_against }];
  return (
    <div className="ps-card p-3">
      <div className="btn-group mb-3" role="group" aria-label="Ventana">{["5", "10", "20"].map((k) => <button key={k} className={`btn btn-sm ${n === k ? "btn-primary" : "btn-outline-primary"}`} onClick={() => setN(k)}>Últimos {k}</button>)}</div>
      <div style={{ height: 220 }} role="img" aria-label={`Promedio de ${unit} a favor y en contra`}><ResponsiveContainer><BarChart data={chart}><CartesianGrid strokeDasharray="3 3" /><XAxis dataKey="name" /><YAxis /><Tooltip /><Bar dataKey={home} fill="#12305a" /><Bar dataKey={away} fill="#1fbf75" /></BarChart></ResponsiveContainer></div>
      <div className="row mt-3">{[[home, form.home_team], [away, form.away_team]].map(([name, t]: any) => (<div className="col-md-6" key={name}><h3 className="h6">{name}</h3><div className="mb-1">{t.form.map((r: string, i: number) => <span key={i} className={`badge me-1 text-bg-${r === "W" ? "success" : r === "D" ? "warning" : "danger"}`}>{r}</span>)}</div>
        <ul className="small list-unstyled"><li>G {t.wins} · E {t.draws} · P {t.losses} ({t.matches} partidos)</li><li>Local: {t.home.matches} partidos · Visitante: {t.away.matches}</li>{t.corners && <li>Córners (fav/contra): {t.corners.avg_for} / {t.corners.avg_against}</li>}{t.cards && <li>Tarjetas (fav/contra): {t.cards.avg_for} / {t.cards.avg_against}</li>}</ul></div>))}</div>
      <h3 className="h6">Enfrentamientos directos</h3>
      {st.data.head_to_head.length ? <ul className="small">{st.data.head_to_head.map((h: any, i: number) => <li key={i}>{new Date(h.date).toLocaleDateString("es")}: {h.home} {h.score} {h.away}</li>)}</ul> : <p className="ps-muted small">Sin enfrentamientos previos registrados.</p>}
      <p className="small ps-muted mb-0">{st.data.note}</p>
    </div>);
}

function WhyTab({ a }: { a: Analysis }) {
  return (
    <div className="ps-card p-3">
      <h2 className="h6">¿Cómo se calculó?</h2>
      <p>{a.exact_score?.explanation}</p>
      <p className="small">Modelo: <code>{a.analysis.model}</code> v{a.analysis.model_version}{a.analysis.is_demo && " · datos DEMO"}</p>
      {a.drivers ? (<table className="table table-sm"><thead><tr><th>Variable</th><th>Valor</th><th>Lectura</th></tr></thead><tbody>{a.drivers.map((d) => <tr key={d.factor}><td>{d.factor}</td><td>{d.value}</td><td className="ps-muted">{d.note}</td></tr>)}</tbody></table>) : <UpgradeNotice>Pro muestra las variables principales del modelo.</UpgradeNotice>}
      <h3 className="h6 mt-3">Categorías (criterios)</h3>
      <ul className="small">{Object.entries(a.analysis.category_criteria).map(([k, v]) => <li key={k}><CategoryBadge category={k} /> {v}</li>)}</ul>
      <p className="small ps-muted mb-0">{a.disclaimer}</p>
    </div>);
}
