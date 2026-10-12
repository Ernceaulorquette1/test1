import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { NavLink, Route, Routes } from "react-router-dom";
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { apiError } from "../../api/client";
import { Admin as A } from "../../api/endpoints";
import { ErrorBox, PageTitle, pct, Spinner } from "../../components/ui";

const TABS: [string, string][] = [["", "Dashboard"], ["users", "Usuarios"], ["plans", "Planes y límites"], ["providers", "Proveedores API"], ["jobs", "Tareas"], ["models", "Modelos"], ["logs", "Logs"], ["announcements", "Avisos"]];

export default function AdminPanel() {
  return (
    <>
      <PageTitle sub="Acceso restringido: todos los endpoints validan el rol ADMIN en el servidor.">Panel administrativo</PageTitle>
      <ul className="nav nav-tabs mb-3 flex-wrap">{TABS.map(([p, l]) => <li className="nav-item" key={p}><NavLink end={p === ""} className="nav-link" to={`/admin/${p}`}>{l}</NavLink></li>)}</ul>
      <Routes>
        <Route index element={<Dashboard />} /><Route path="users" element={<Users />} /><Route path="plans" element={<PlansAdmin />} />
        <Route path="providers" element={<Providers />} /><Route path="jobs" element={<Jobs />} /><Route path="models" element={<Models />} />
        <Route path="logs" element={<Logs />} /><Route path="announcements" element={<Announcements />} />
      </Routes>
    </>
  );
}

const Stat = ({ label, value, sub }: { label: string; value: any; sub?: string }) => (<div className="col-6 col-md-3"><div className="ps-card p-3 h-100"><div className="small ps-muted">{label}</div><div className="h3 mb-0">{value}</div>{sub && <div className="small ps-muted">{sub}</div>}</div></div>);

function Dashboard() {
  const q = useQuery({ queryKey: ["adm-dash"], queryFn: A.dashboard });
  if (q.isLoading) return <Spinner />;
  if (q.error) return <ErrorBox error={q.error} />;
  const d = q.data;
  return (
    <>
      <div className="row g-3">
        <Stat label="Usuarios" value={d.users.total} sub={`+${d.users.new_7d} en 7 días`} /><Stat label="Gratis" value={d.users.free} /><Stat label="Pro" value={d.users.pro} />
        <Stat label="Suscripciones activas" value={d.subscriptions.active} sub={`${d.subscriptions.canceled} canceladas/vencidas`} />
        <Stat label="Análisis hoy" value={d.usage.analyses_today} sub={`${d.usage.analyses_7d} en 7 días`} /><Stat label="Pronósticos" value={d.predictions.total} sub={`${d.predictions.analyses} análisis`} />
        <Stat label="Peticiones a proveedor hoy" value={d.provider.requests_today} sub={`modo ${d.provider.mode}`} /><Stat label="Errores 24 h" value={d.errors_24h} />
      </div>
      <div className="ps-card p-3 mt-3"><h2 className="h6">Ingresos registrados</h2>{d.revenue.length ? <ul className="mb-0">{d.revenue.map((r: any, i: number) => <li key={i}>{r.amount} {r.currency} {r.is_test && <span className="badge text-bg-warning">PRUEBA</span>}</li>)}</ul> : <span className="ps-muted">Sin transacciones.</span>}</div>
      <div className="ps-card p-3 mt-3"><h2 className="h6">Suscripciones por estado</h2><div style={{ height: 200 }} role="img" aria-label="Suscripciones por estado"><ResponsiveContainer><BarChart data={Object.entries(d.subscriptions.by_status).map(([k, v]) => ({ k, v }))}><CartesianGrid strokeDasharray="3 3" /><XAxis dataKey="k" /><YAxis allowDecimals={false} /><Tooltip /><Bar dataKey="v" fill="#12305a" /></BarChart></ResponsiveContainer></div></div>
      <div className="ps-card p-3 mt-3"><h2 className="h6">Tareas últimas 24 h</h2>{Object.entries(d.jobs_24h).map(([k, v]) => <span key={k} className="badge text-bg-secondary me-2">{k}: {v as number}</span>)}</div>
    </>
  );
}

function Users() {
  const [qs, setQs] = useState("");
  const qc = useQueryClient();
  const q = useQuery({ queryKey: ["adm-users", qs], queryFn: () => A.users({ q: qs || undefined, page_size: 50 }) });
  const patch = useMutation({ mutationFn: ({ id, b }: { id: number; b: object }) => A.patchUser(id, b), onSuccess: () => qc.invalidateQueries({ queryKey: ["adm-users"] }) });
  return (
    <>
      <input className="form-control mb-3" placeholder="Buscar por correo…" aria-label="Buscar usuario" value={qs} onChange={(e) => setQs(e.target.value)} />
      {patch.error && <ErrorBox error={patch.error} />}
      {q.isLoading ? <Spinner /> : q.error ? <ErrorBox error={q.error} /> : (
        <div className="table-responsive"><table className="table table-sm"><thead><tr><th>ID</th><th>Correo</th><th>Rol</th><th>Plan</th><th>Activo</th><th>Alta</th><th /></tr></thead>
          <tbody>{q.data.items.map((u: any) => (<tr key={u.id}><td>{u.id}</td><td>{u.email}{u.is_demo && <span className="badge text-bg-warning ms-1">demo</span>}</td><td>{u.role}</td><td>{u.plan}</td><td>{u.is_active ? "✔" : "✖"}</td><td>{new Date(u.created_at).toLocaleDateString("es")}</td>
            <td className="text-nowrap"><button className="btn btn-sm btn-outline-secondary me-1" onClick={() => patch.mutate({ id: u.id, b: { is_active: !u.is_active } })}>{u.is_active ? "Desactivar" : "Activar"}</button>
              <button className="btn btn-sm btn-outline-secondary" onClick={() => patch.mutate({ id: u.id, b: { role: u.role === "ADMIN" ? "USER" : "ADMIN" } })}>{u.role === "ADMIN" ? "Quitar admin" : "Hacer admin"}</button></td></tr>))}</tbody></table><div className="small ps-muted">{q.data.total} usuarios</div></div>)}
    </>
  );
}

function PlansAdmin() {
  const q = useQuery({ queryKey: ["adm-plans"], queryFn: A.plans });
  const qc = useQueryClient();
  const save = useMutation({ mutationFn: ({ code, b }: { code: string; b: object }) => A.patchPlan(code, b), onSuccess: () => qc.invalidateQueries({ queryKey: ["adm-plans"] }) });
  if (q.isLoading) return <Spinner />;
  if (q.error) return <ErrorBox error={q.error} />;
  return (
    <div className="row g-3">{q.data!.map((p: any) => (<PlanForm key={p.code} p={p} onSave={(b: object) => save.mutate({ code: p.code, b })} saving={save.isPending} err={save.error} ok={save.isSuccess && save.variables?.code === p.code} />))}</div>
  );
}
function PlanForm({ p, onSave, saving, err, ok }: any) {
  const [v, setV] = useState({ daily: p.daily_analysis_limit ?? "", fav: p.favorites_limit ?? "", hist: p.history_days ?? "", heavy: p.heavy_calcs_per_hour, prices: JSON.stringify(p.prices, null, 1) });
  const [pe, setPe] = useState<string>();
  return (
    <div className="col-lg-6"><div className="ps-card p-3"><h2 className="h6">{p.name} ({p.code})</h2>
      {[["Análisis por día (vacío = ilimitado)", "daily"], ["Favoritos máx.", "fav"], ["Días de historial (vacío = completo)", "hist"], ["Cálculos costosos / hora", "heavy"]].map(([l, k]) => (<div className="mb-2" key={k}><label className="form-label small" htmlFor={`${p.code}-${k}`}>{l}</label><input id={`${p.code}-${k}`} type="number" min={0} className="form-control form-control-sm" value={(v as any)[k]} onChange={(e) => setV({ ...v, [k]: e.target.value })} /></div>))}
      <label className="form-label small" htmlFor={`${p.code}-prices`}>Precios (JSON por país)</label><textarea id={`${p.code}-prices`} rows={5} className="form-control form-control-sm font-monospace mb-2" value={v.prices} onChange={(e) => setV({ ...v, prices: e.target.value })} />
      {pe && <div className="text-danger small">{pe}</div>}{err && <ErrorBox error={err} />}{ok && <div className="text-success small">Guardado ✔ (auditado)</div>}
      <button className="btn btn-primary btn-sm" disabled={saving} onClick={() => { try { const prices = JSON.parse(v.prices); setPe(undefined);
        onSave({ ...(v.daily === "" ? { unlimited_daily: true } : { daily_analysis_limit: Number(v.daily) }), favorites_limit: v.fav === "" ? undefined : Number(v.fav), history_days: v.hist === "" ? undefined : Number(v.hist), heavy_calcs_per_hour: Number(v.heavy), prices }); } catch { setPe("JSON de precios inválido"); } }}>Guardar</button></div></div>
  );
}

function Providers() {
  const q = useQuery({ queryKey: ["adm-prov"], queryFn: A.providers, refetchInterval: 30000 });
  if (q.isLoading) return <Spinner />;
  if (q.error) return <ErrorBox error={q.error} />;
  return (<>
    <div className="row g-3">{q.data.providers.map((p: any) => (<div className="col-md-6" key={p.provider}><div className="ps-card p-3"><h2 className="h6">{p.provider} {p.configured ? <span className="badge text-bg-success">clave configurada</span> : <span className="badge text-bg-secondary">sin clave</span>}</h2>
      <p className="mb-1">Modo: <strong>{p.mode}</strong> · Consumo hoy: <strong>{p.used_today_budget}</strong> / {p.budget_per_day} (reserva {p.reserve})</p>
      <div className="progress mb-2" style={{ height: 8 }}><div className="progress-bar" role="progressbar" aria-label="Consumo diario" style={{ width: `${Math.min(100, (p.used_today_budget / p.budget_per_day) * 100)}%` }} /></div>
      <ul className="small mb-0">{p.daily.map((d: any) => <li key={d.day}>{d.day}: {d.requests} peticiones ({d.ok} OK)</li>)}{!p.daily.length && <li className="ps-muted">Sin peticiones registradas</li>}</ul></div></div>))}</div>
    <h2 className="h6 mt-3">Últimas peticiones</h2>
    <div className="table-responsive"><table className="table table-sm"><thead><tr><th>Hora</th><th>Proveedor</th><th>Endpoint</th><th>Estado</th><th>Caché</th><th>Error</th></tr></thead><tbody>{q.data.recent.map((r: any, i: number) => <tr key={i}><td>{new Date(r.at).toLocaleTimeString("es")}</td><td>{r.provider}</td><td>{r.endpoint}</td><td>{r.ok ? "OK" : r.status ?? "ERR"}</td><td>{r.from_cache ? "sí" : ""}</td><td>{r.error}</td></tr>)}{!q.data.recent.length && <tr><td colSpan={6} className="ps-muted">Modo DEMO: no se consulta ningún proveedor.</td></tr>}</tbody></table></div>
    <p className="small ps-muted">{q.data.notes}</p></>);
}

function Jobs() {
  const q = useQuery({ queryKey: ["adm-jobs"], queryFn: A.jobs, refetchInterval: 15000 });
  const qc = useQueryClient();
  const run = useMutation({ mutationFn: A.runJob, onSuccess: () => qc.invalidateQueries({ queryKey: ["adm-jobs"] }) });
  const reseed = useMutation({ mutationFn: A.reseed, onSuccess: () => qc.invalidateQueries() });
  if (q.isLoading) return <Spinner />;
  if (q.error) return <ErrorBox error={q.error} />;
  return (<>
    <div className="ps-card p-3 mb-3"><h2 className="h6">Ejecutar ahora</h2><div className="d-flex gap-2 flex-wrap">{q.data.available.map((j: string) => <button key={j} className="btn btn-sm btn-outline-primary" disabled={run.isPending} onClick={() => run.mutate(j)}>{j}</button>)}
      <button className="btn btn-sm btn-outline-warning" onClick={() => confirm("Regenerar los datos DEMO?") && reseed.mutate()}>Regenerar datos DEMO</button></div>
      {run.data && <div className="alert alert-secondary mt-2 mb-0 py-1">Resultado: {JSON.stringify(run.data)}</div>}{run.error && <ErrorBox error={run.error} />}{reseed.error && <ErrorBox error={reseed.error} />}</div>
    <div className="table-responsive"><table className="table table-sm"><thead><tr><th>Tarea</th><th>Estado</th><th>Ítems</th><th>Inicio</th><th>Origen</th><th>Mensaje</th></tr></thead><tbody>{q.data.items.map((j: any) => <tr key={j.id}><td>{j.job}</td><td><span className={`badge text-bg-${j.status === "success" ? "success" : j.status === "failed" ? "danger" : "secondary"}`}>{j.status}</span></td><td>{j.items}</td><td>{new Date(j.started_at).toLocaleString("es")}</td><td>{j.by}</td><td className="small">{j.message}</td></tr>)}</tbody></table></div></>);
}

function Models() {
  const q = useQuery({ queryKey: ["adm-models"], queryFn: A.models });
  const qc = useQueryClient();
  const bt = useMutation({ mutationFn: A.backtest, onSuccess: () => qc.invalidateQueries({ queryKey: ["adm-models"] }) });
  if (q.isLoading) return <Spinner />;
  if (q.error) return <ErrorBox error={q.error} />;
  return (<>
    <div className="d-flex gap-2 mb-3"><button className="btn btn-sm btn-primary" disabled={bt.isPending} onClick={() => bt.mutate("football")}>Backtest fútbol</button><button className="btn btn-sm btn-primary" disabled={bt.isPending} onClick={() => bt.mutate("basketball")}>Backtest NBA</button>{bt.isPending && <Spinner label="Ejecutando backtest…" />}</div>
    {bt.error && <div className="alert alert-danger">{apiError(bt.error).message}</div>}
    {q.data.versions.map((v: any) => (<div className="ps-card p-3 mb-3" key={v.id}><h2 className="h6">{v.model} v{v.version}</h2>
      {v.backtest?.n_evaluated ? <><p className="small mb-1">Backtest walk-forward ({v.backtest.computed_at?.slice(0, 16)}): {v.backtest.n_evaluated} partidos evaluados · Brier 1X2 multiclase: {v.backtest.multiclass_brier_1x2?.toFixed?.(4) ?? "—"} {v.backtest.mae_total_points != null && `· MAE total puntos: ${v.backtest.mae_total_points.toFixed(1)}`}</p>
        <div className="table-responsive"><table className="table table-sm"><thead><tr><th>Mercado</th><th>N</th><th>Brier</th><th>Log-loss</th><th>ECE</th><th>Acierto p≥60 %</th></tr></thead><tbody>{Object.entries(v.backtest.markets).map(([m, s]: any) => <tr key={m}><td>{m}</td><td>{s.n}</td><td>{s.brier?.toFixed(4)}</td><td>{s.log_loss?.toFixed(4)}</td><td>{s.ece?.toFixed(3)}</td><td>{s.hit_rate_p60 != null ? pct(s.hit_rate_p60) : "—"}</td></tr>)}</tbody></table></div></> : <p className="ps-muted small mb-0">Sin backtest ejecutado.</p>}</div>))}
    <h2 className="h6">Calidad en producción/demo (pronósticos previos resueltos)</h2>
    <div className="table-responsive"><table className="table table-sm"><thead><tr><th>Deporte</th><th>Liga</th><th>Corte</th><th>N</th><th>Prob. media</th><th>Ocurrencia</th><th>Brier</th><th>Log-loss</th></tr></thead><tbody>{q.data.evaluations.map((e: any, i: number) => <tr key={i}><td>{e.sport}</td><td>{e.league}</td><td>{e.slice}</td><td>{e.n}</td><td>{pct(e.mean_probability)}</td><td>{pct(e.hit_rate)}</td><td>{e.brier.toFixed(3)}</td><td>{e.log_loss.toFixed(3)}</td></tr>)}</tbody></table></div></>);
}

function Logs() {
  const err = useQuery({ queryKey: ["adm-err"], queryFn: A.errors });
  const aud = useQuery({ queryKey: ["adm-aud"], queryFn: A.audit });
  const subs = useQuery({ queryKey: ["adm-subs"], queryFn: A.subscriptions });
  return (<>
    <h2 className="h6">Errores</h2>{err.isLoading ? <Spinner /> : err.data?.items.length ? <ul className="small">{err.data.items.map((e: any) => <li key={e.id}>{new Date(e.at).toLocaleString("es")} [{e.source}] {e.message}</li>)}</ul> : <p className="ps-muted">Sin errores registrados.</p>}
    <h2 className="h6 mt-3">Suscripciones</h2><div className="table-responsive"><table className="table table-sm"><tbody>{subs.data?.items.map((s: any) => <tr key={s.id}><td>{s.user}</td><td>{s.plan}</td><td>{s.provider}</td><td>{s.status}</td><td>{s.is_test && "prueba"}</td></tr>)}</tbody></table></div>
    <h2 className="h6 mt-3">Auditoría</h2><div className="table-responsive"><table className="table table-sm"><thead><tr><th>Fecha</th><th>Actor</th><th>Acción</th><th>Entidad</th><th>Detalle</th></tr></thead><tbody>{aud.data?.items.map((a: any) => <tr key={a.id}><td>{new Date(a.at).toLocaleString("es")}</td><td>{a.actor ?? "—"}</td><td>{a.action}</td><td>{a.entity} {a.entity_id}</td><td className="small">{JSON.stringify(a.detail)}</td></tr>)}</tbody></table></div></>);
}

function Announcements() {
  const q = useQuery({ queryKey: ["adm-ann"], queryFn: A.announcements });
  const qc = useQueryClient();
  const [t, setT] = useState(""); const [b, setB] = useState(""); const [l, setL] = useState("info");
  const add = useMutation({ mutationFn: () => A.createAnnouncement({ title: t, body: b, level: l }), onSuccess: () => { setT(""); setB(""); qc.invalidateQueries({ queryKey: ["adm-ann"] }); qc.invalidateQueries({ queryKey: ["status"] }); } });
  return (<>
    <div className="ps-card p-3 mb-3"><h2 className="h6">Nuevo aviso</h2><div className="row g-2"><div className="col-md-4"><input className="form-control" aria-label="Título" placeholder="Título" value={t} onChange={(e) => setT(e.target.value)} /></div><div className="col-md-5"><input className="form-control" aria-label="Mensaje" placeholder="Mensaje" value={b} onChange={(e) => setB(e.target.value)} /></div>
      <div className="col-md-1"><select className="form-select" aria-label="Nivel" value={l} onChange={(e) => setL(e.target.value)}><option>info</option><option>warning</option><option>critical</option></select></div><div className="col-md-2"><button className="btn btn-primary w-100" disabled={!t || add.isPending} onClick={() => add.mutate()}>Publicar</button></div></div></div>
    <ul className="list-group">{q.data?.map((a: any) => <li className="list-group-item d-flex justify-content-between" key={a.id}><span><strong>{a.title}</strong> {a.body} <span className="badge text-bg-secondary">{a.level}</span></span><button className="btn btn-sm btn-outline-danger" onClick={async () => { await A.deleteAnnouncement(a.id); qc.invalidateQueries({ queryKey: ["adm-ann"] }); qc.invalidateQueries({ queryKey: ["status"] }); }}>Eliminar</button></li>)}</ul></>);
}
