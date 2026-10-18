import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Link } from "react-router-dom";
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { apiError } from "../api/client";
import { Lists, Me, Pred } from "../api/endpoints";
import { useAuth } from "../auth/AuthContext";
import { useBasket } from "../components/Basket";
import { CategoryBadge, Disclaimer, Empty, ErrorBox, fmtDate, MatchCard, PageTitle, Pager, pct, ProbBar, Spinner, UpgradeNotice } from "../components/ui";
import { GROUP_LABEL, marketLabel } from "../components/format";

export function Predictions() {
  const { isPro, user } = useAuth();
  const [f, setF] = useState<any>({ sport: "", market: "", min_probability: "0.6", uncertainty: "", category: "", min_matches: "", page: 1 });
  const meta = useQuery({ queryKey: ["pred-meta"], queryFn: Pred.meta, enabled: isPro });
  const params = Object.fromEntries(Object.entries(f).filter(([, v]) => v !== "" && v != null));
  const q = useQuery({ queryKey: ["pred-list", params], queryFn: () => Pred.list({ ...params, page_size: 20 }), enabled: isPro, placeholderData: keepPreviousData, retry: false });
  const perf = useQuery({ queryKey: ["perf"], queryFn: () => Pred.performance(), enabled: isPro });
  const set = (k: string, v: any) => setF((s: any) => ({ ...s, [k]: v, page: k === "page" ? v : 1 }));
  if (!isPro) return (<><PageTitle>Pronósticos destacados</PageTitle><UpgradeNotice>El listado completo con filtros de probabilidad, incertidumbre y calidad de datos es del plan Pro. En el plan Gratis puedes analizar hasta {user!.usage.limit} partidos al día desde el calendario.</UpgradeNotice></>);
  const range = meta.data?.probability_range ?? { min: 0.6, max: 0.95 };
  const allPerf = perf.data?.items.filter((i: any) => i.market === "ALL" && i.league === "Todas") ?? [];
  return (
    <>
      <PageTitle sub="Mejor respaldo estadístico primero. Los filtros nunca fuerzan resultados: si nada supera el umbral, la lista queda vacía.">Pronósticos</PageTitle>
      <div className="ps-card p-3 mb-3"><div className="row g-2">
        <div className="col-6 col-md-2"><label className="form-label small" htmlFor="f-sport">Deporte</label><select id="f-sport" className="form-select form-select-sm" value={f.sport} onChange={(e) => set("sport", e.target.value)}><option value="">Todos</option><option value="football">Fútbol</option><option value="basketball">NBA</option></select></div>
        <div className="col-6 col-md-3"><label className="form-label small" htmlFor="f-market">Mercado</label><select id="f-market" className="form-select form-select-sm" value={f.market} onChange={(e) => set("market", e.target.value)}><option value="">Todos</option>{Object.entries(GROUP_LABEL).map(([k, l]) => <option key={k} value={k}>{l}</option>)}</select></div>
        <div className="col-12 col-md-3"><label className="form-label small" htmlFor="f-prob">Probabilidad mínima: <strong>{Math.round(Number(f.min_probability) * 100)} %</strong></label><input id="f-prob" type="range" className="form-range" min={range.min} max={range.max} step={0.01} value={f.min_probability} onChange={(e) => set("min_probability", e.target.value)} /></div>
        <div className="col-6 col-md-2"><label className="form-label small" htmlFor="f-unc">Incertidumbre</label><select id="f-unc" className="form-select form-select-sm" value={f.uncertainty} onChange={(e) => set("uncertainty", e.target.value)}><option value="">Cualquiera</option><option value="low">Baja</option><option value="medium">Media</option><option value="high">Alta</option></select></div>
        <div className="col-6 col-md-2"><label className="form-label small" htmlFor="f-n">Mín. partidos</label><input id="f-n" type="number" min={0} className="form-control form-control-sm" value={f.min_matches} onChange={(e) => set("min_matches", e.target.value)} /></div>
        <div className="col-12 col-md-4"><label className="form-label small" htmlFor="f-cat">Categoría</label><select id="f-cat" className="form-select form-select-sm" value={f.category} onChange={(e) => set("category", e.target.value)}><option value="">Todas</option><option value="high_probability">Alta probabilidad estimada</option><option value="medium_probability">Probabilidad media</option><option value="high_uncertainty">Alta incertidumbre</option></select></div>
      </div></div>
      {q.isLoading ? <Spinner /> : q.error ? <ErrorBox error={q.error} onRetry={() => q.refetch()} /> : !q.data?.items.length ? <Empty title="Ningún pronóstico supera estos filtros"><div>Es normal: no se fuerzan resultados. Baja el umbral o cambia los filtros.</div></Empty> : (
        <>
          <div className="small ps-muted mb-2">{q.data.total} pronósticos</div>
          <div className="list-group mb-2">{q.data.items.map((p: any) => (
            <Link key={p.id} to={`/match/${p.fixture.id}`} className="list-group-item list-group-item-action">
              <div className="d-flex justify-content-between flex-wrap gap-1"><strong>{p.fixture.home_team.name} vs {p.fixture.away_team.name}</strong><span className="small ps-muted">{fmtDate(p.fixture.kickoff, user!.timezone)}</span></div>
              <div className="d-flex justify-content-between align-items-center"><span>{marketLabel(p.market, p.selection, p.fixture.home_team.name, p.fixture.away_team.name)}</span><span><CategoryBadge category={p.category} /> <strong>{pct(p.probability)}</strong></span></div>
              <ProbBar value={p.probability} /><div className="small ps-muted">{p.data_points} partidos usados · {fmtDate(p.updated_at)}</div>
            </Link>))}</div>
          <Pager page={f.page} total={q.data.total} pageSize={20} onChange={(p) => set("page", p)} /></>)}
      {allPerf.length > 0 && (<div className="ps-card p-3 mt-4"><h2 className="h6">Rendimiento histórico del modelo (pronósticos previos ya resueltos)</h2>
        <div style={{ height: 200 }} role="img" aria-label="Probabilidad media prevista frente a frecuencia observada"><ResponsiveContainer><BarChart data={allPerf.map((i: any) => ({ name: i.sport === "football" ? "Fútbol" : "NBA", Prevista: +(i.mean_probability * 100).toFixed(1), Observada: +(i.hit_rate * 100).toFixed(1) }))}><CartesianGrid strokeDasharray="3 3" /><XAxis dataKey="name" /><YAxis unit="%" /><Tooltip /><Bar dataKey="Prevista" fill="#12305a" /><Bar dataKey="Observada" fill="#1fbf75" /></BarChart></ResponsiveContainer></div>
        <table className="table table-sm mb-0"><thead><tr><th>Deporte</th><th>N</th><th>Brier</th><th>Log-loss</th></tr></thead><tbody>{allPerf.map((i: any) => <tr key={i.sport}><td>{i.sport}</td><td>{i.n}</td><td>{i.brier}</td><td>{i.log_loss}</td></tr>)}</tbody></table>
        <p className="small ps-muted mb-0">{perf.data.note}</p></div>)}
      <Disclaimer />
    </>
  );
}

export function Selections() {
  const { isPro } = useAuth();
  const basket = useBasket();
  const qc = useQueryClient();
  const [name, setName] = useState("Mi selección");
  const groups = useQuery({ queryKey: ["groups"], queryFn: Lists.groups, enabled: isPro });
  const ev = useMutation({ mutationFn: () => Lists.evaluate(basket.items.map((i) => ({ fixture_id: i.fixture_id, key: i.key }))) });
  const save = useMutation({ mutationFn: () => Lists.createGroup(name, basket.items.map((i) => ({ fixture_id: i.fixture_id, key: i.key }))), onSuccess: () => { basket.clear(); ev.reset(); qc.invalidateQueries({ queryKey: ["groups"] }); } });
  const [shared, setShared] = useState<string>();
  if (!isPro) return (<><PageTitle>Selecciones</PageTitle><UpgradeNotice>La herramienta de selecciones y probabilidad conjunta es parte del plan Pro.</UpgradeNotice></>);
  const j = ev.data?.joint;
  return (
    <>
      <PageTitle sub="Combina mercados de varios partidos. La probabilidad conjunta se calcula con el modelo, sin multiplicar eventos correlacionados.">Selecciones y combinadas</PageTitle>
      <div className="row g-3">
        <div className="col-lg-7"><div className="ps-card p-3">
          <h2 className="h6">Selección actual ({basket.items.length})</h2>
          {!basket.items.length ? <Empty title="Aún no hay selecciones"><div>Abre un partido, analízalo y pulsa «+ Selección» en un mercado.</div></Empty> : (
            <ul className="list-group mb-3">{basket.items.map((i) => { const r = ev.data?.items.find((x: any) => x.fixture.id === i.fixture_id && x.key === i.key); return (
              <li key={i.fixture_id + i.key} className="list-group-item d-flex justify-content-between align-items-center"><div><div className="fw-semibold">{i.match}</div><div className="small">{i.label}</div></div>
                <div className="d-flex align-items-center gap-2"><strong>{pct(r?.probability ?? i.probability)}</strong><button className="btn btn-sm btn-outline-danger" aria-label={`Quitar ${i.label}`} onClick={() => { basket.remove(i); ev.reset(); }}>✕</button></div></li>); })}</ul>)}
          <div className="d-flex gap-2 flex-wrap"><button className="btn btn-primary" disabled={!basket.items.length || ev.isPending} onClick={() => ev.mutate()}>Calcular probabilidad conjunta</button>
            <input className="form-control w-auto" aria-label="Nombre" value={name} onChange={(e) => setName(e.target.value)} /><button className="btn btn-success" disabled={!basket.items.length || save.isPending} onClick={() => save.mutate()}>Guardar</button><button className="btn btn-outline-secondary" onClick={() => { basket.clear(); ev.reset(); }}>Vaciar</button></div>
          {ev.error && <div className="mt-3"><ErrorBox error={ev.error} /></div>}
          {j && (<div className={`alert mt-3 ${j.computable ? "alert-info" : "alert-warning"}`} role="status">{j.computable ? (<><div className="h4 mb-1">{pct(j.probability)}</div><div>Probabilidad conjunta estimada.</div><ul className="small mb-0 mt-1">{j.notes?.map((n: string) => <li key={n}>{n}</li>)}</ul></>) : <>⚠ {j.reason}</>}</div>)}
        </div></div>
        <div className="col-lg-5"><div className="ps-card p-3"><h2 className="h6">Guardadas</h2>
          {groups.isLoading ? <Spinner /> : !groups.data?.length ? <p className="ps-muted mb-0">Sin selecciones guardadas.</p> : groups.data.map((g: any) => (
            <div key={g.id} className="border-bottom py-2"><div className="d-flex justify-content-between"><strong>{g.name}</strong>
              <span>{g.result ? <span className={`badge text-bg-${g.result === "hit" ? "success" : "danger"}`}>{g.result === "hit" ? "Acertada" : "Fallada"}</span> : <span className="badge text-bg-secondary">Pendiente</span>}</span></div>
              <div className="small ps-muted">{g.joint_computable ? `Conjunta al guardar: ${pct(g.joint_probability_at_save)}` : "Conjunta no calculable con precisión"}</div>
              <ul className="small mb-1">{g.items.map((i: any) => <li key={i.id}>{i.fixture.home_team.name} vs {i.fixture.away_team.name} — {i.key} {i.result && (i.result === "hit" ? "✅" : "❌")}</li>)}</ul>
              <button className="btn btn-sm btn-outline-primary me-1" onClick={async () => setShared((await Lists.share(g.id)).text)}>Compartir</button>
              <button className="btn btn-sm btn-outline-danger" onClick={async () => { await Lists.deleteGroup(g.id); qc.invalidateQueries({ queryKey: ["groups"] }); }}>Eliminar</button></div>))}
          {shared && <div className="mt-2"><textarea className="form-control" rows={6} readOnly value={shared} aria-label="Resumen para compartir" /><button className="btn btn-sm btn-primary mt-1" onClick={() => navigator.clipboard?.writeText(shared)}>Copiar texto</button>{" "}{"share" in navigator && <button className="btn btn-sm btn-outline-primary mt-1" onClick={() => navigator.share({ text: shared })}>Compartir…</button>}</div>}
        </div></div>
      </div>
      <Disclaimer />
    </>
  );
}

export function Favorites() {
  const { user } = useAuth();
  const qc = useQueryClient();
  const q = useQuery({ queryKey: ["favorites"], queryFn: Lists.favorites });
  if (q.isLoading) return <Spinner />;
  if (q.error) return <ErrorBox error={q.error} onRetry={() => q.refetch()} />;
  return (
    <>
      <PageTitle sub={`${q.data.used} de ${q.data.limit ?? "∞"} favoritos`}>Favoritos</PageTitle>
      {!q.data.items.length ? <Empty title="Aún no tienes favoritos"><Link to="/teams">Busca equipos</Link></Empty> : <div className="row g-3">{q.data.items.map((f: any) => (
        <div className="col-md-6 col-lg-4" key={f.id}>{f.kind === "fixture" && f.item ? <MatchCard f={f.item} tz={user!.timezone} /> : <div className="ps-card p-3 d-flex justify-content-between align-items-center"><span>{f.kind === "team" ? "👕" : "🏆"} {f.item?.name}</span>
          <button className="btn btn-sm btn-outline-danger" onClick={async () => { await Lists.removeFavorite(f.id); qc.invalidateQueries({ queryKey: ["favorites"] }); }}>Quitar</button></div>}</div>))}</div>}
      {!user!.plan.features.alerts && <UpgradeNotice>Las alertas de partidos de tus favoritos son una función Pro.</UpgradeNotice>}
    </>
  );
}

export function History() {
  const { isPro } = useAuth();
  const [page, setPage] = useState(1);
  const q = useQuery({ queryKey: ["history", page], queryFn: () => Me.history(page), placeholderData: keepPreviousData });
  const cmp = useQuery({ queryKey: ["comparison"], queryFn: Me.comparison, enabled: isPro });
  return (
    <>
      <PageTitle sub={q.data?.history_days ? `Últimos ${q.data.history_days} días (plan Gratis)` : "Historial completo"}>Historial</PageTitle>
      {q.isLoading ? <Spinner /> : q.error ? <ErrorBox error={q.error} /> : !q.data?.items.length ? <Empty title="Aún no analizaste partidos" /> : (<>
        <div className="list-group">{q.data.items.map((h) => (<Link key={h.date + h.fixture.id} to={`/match/${h.fixture.id}`} className="list-group-item list-group-item-action d-flex justify-content-between"><span>{h.fixture.home_team.name} vs {h.fixture.away_team.name}{h.fixture.status === "finished" && <strong> · {h.fixture.home_score}-{h.fixture.away_score}</strong>}</span><span className="small ps-muted">{h.date}</span></Link>))}</div>
        <Pager page={page} total={q.data.total} pageSize={q.data.page_size} onChange={setPage} /></>)}
      <h2 className="h5 mt-4">Comparación de pronósticos y resultados</h2>
      {!isPro ? <UpgradeNotice>Compara tus pronósticos guardados con los resultados reales (Pro).</UpgradeNotice> : cmp.isLoading ? <Spinner /> : cmp.error ? <ErrorBox error={cmp.error} /> : !cmp.data.items.length ? <Empty title="Guarda pronósticos (☆) desde un partido para compararlos aquí" /> : (
        <div className="ps-card p-3"><p>Resueltos: <strong>{cmp.data.summary.settled}</strong> de {cmp.data.summary.saved} · Aciertos: <strong>{cmp.data.summary.hits}</strong>{cmp.data.summary.hit_rate != null && ` (${pct(cmp.data.summary.hit_rate, 0)})`}</p>
          <table className="table table-sm"><thead><tr><th>Partido</th><th>Mercado</th><th>Prob.</th><th>Resultado</th></tr></thead><tbody>{cmp.data.items.map((i: any) => <tr key={i.saved_id}><td>{i.fixture.home_team.name} vs {i.fixture.away_team.name}{i.final_score && ` (${i.final_score})`}</td><td>{marketLabel(i.market, i.selection, i.fixture.home_team.name, i.fixture.away_team.name)}</td><td>{pct(i.probability)}</td><td>{i.result ? <span className={`badge text-bg-${i.result === "hit" ? "success" : "danger"}`}>{i.result === "hit" ? "Acierto" : "Fallo"}</span> : "Pendiente"}</td></tr>)}</tbody></table>
          <p className="small ps-muted mb-0">{cmp.data.summary.note}</p></div>)}
    </>
  );
}

export function Notifications() {
  const qc = useQueryClient();
  const q = useQuery({ queryKey: ["notifications"], queryFn: Lists.notifications });
  if (q.isLoading) return <Spinner />;
  if (q.error) return <ErrorBox error={q.error} />;
  return (
    <>
      <PageTitle>Notificaciones</PageTitle>
      <button className="btn btn-sm btn-outline-primary mb-3" onClick={async () => { await Lists.readAll(); qc.invalidateQueries({ queryKey: ["notifications"] }); }}>Marcar todas como leídas</button>
      {!q.data.items.length ? <Empty title="Sin notificaciones" /> : <div className="list-group">{q.data.items.map((n: any) => (<div key={n.id} className={`list-group-item ${n.read ? "" : "list-group-item-primary"}`}><div className="d-flex justify-content-between"><strong>{n.title}</strong><span className="small ps-muted">{fmtDate(n.created_at)}</span></div><div>{n.body}</div>
        {n.data?.fixture_id && <Link to={`/match/${n.data.fixture_id}`} className="small">Ver partido</Link>}{!n.read && <button className="btn btn-link btn-sm" onClick={async () => { await Lists.readNotification(n.id); qc.invalidateQueries({ queryKey: ["notifications"] }); }}>Marcar leída</button>}</div>))}</div>}
    </>
  );
}
