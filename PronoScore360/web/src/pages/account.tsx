import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router-dom";
import { apiError } from "../api/client";
import { Billing, Me } from "../api/endpoints";
import { useAuth } from "../auth/AuthContext";
import { LANGS, setLang } from "../i18n";
import { ErrorBox, PageTitle, Spinner } from "../components/ui";
import { useTheme } from "../theme/ThemeContext";

const money = (p?: { amount: number; currency: string }) => p ? (p.currency === "CLP" ? `$${p.amount.toLocaleString("es-CL")} CLP` : `US$${p.amount.toFixed(2)}`) : "—";

export function Plans() {
  const { user, isPro, refreshUser } = useAuth();
  const plans = useQuery({ queryKey: ["plans"], queryFn: Billing.plans });
  const status = useQuery({ queryKey: ["status"], queryFn: () => import("../api/endpoints").then((m) => m.Catalog.status()) });
  const [msg, setMsg] = useState<string>();
  const demo = useMutation({ mutationFn: Billing.demoActivate, onSuccess: async () => { await refreshUser(); setMsg("Pro de PRUEBA activado (simulador de desarrollo; no hay cobro real)."); }, onError: (e) => setMsg(apiError(e).message) });
  const web = useMutation({ mutationFn: Billing.checkout, onError: (e) => setMsg(apiError(e).message) });
  if (plans.isLoading) return <Spinner />;
  if (plans.error) return <ErrorBox error={plans.error} />;
  const price = (code: string) => { const p = plans.data!.find((x) => x.code === code)?.prices ?? {}; return money(p[user!.country] ?? p.DEFAULT); };
  const rows: [string, (c: "FREE" | "PRO") => string][] = [
    ["Análisis de partidos por día", (c) => (plans.data!.find((p) => p.code === c)!.daily_analysis_limit ?? "Ilimitados").toString()],
    ["Mercados disponibles", (c) => (c === "FREE" ? "Básicos (1X2, doble oportunidad, 2.5 goles, ambos marcan)" : "Todos (goles, córners, tarjetas, NBA)")],
    ["Marcadores probables", (c) => (c === "FREE" ? "El más probable" : "Top 5 + rangos NBA")],
    ["Filtros de probabilidad y ranking", (c) => (c === "FREE" ? "—" : "✔")], ["Selecciones y combinadas", (c) => (c === "FREE" ? "—" : "✔")],
    ["Favoritos", (c) => String(plans.data!.find((p) => p.code === c)!.favorites_limit)], ["Historial", (c) => (c === "FREE" ? "7 días" : "Completo")],
    ["Alertas de partidos", (c) => (c === "FREE" ? "—" : "✔")], ["Rendimiento del modelo", (c) => (c === "FREE" ? "—" : "✔")],
  ];
  return (
    <>
      <PageTitle sub="Sin letra pequeña: el plan se activa solo tras verificar el pago en el servidor.">Planes</PageTitle>
      {msg && <div className="alert alert-info" role="status">{msg}</div>}
      <div className="row g-3">
        {(["FREE", "PRO"] as const).map((c) => (
          <div className="col-md-6" key={c}><div className={`ps-card p-4 h-100 ${user!.plan.code === c ? "border-success border-2" : ""}`}>
            <h2 className="h4">{c === "FREE" ? "Gratis" : "Pro"} {user!.plan.code === c && <span className="badge text-bg-success">Tu plan</span>}</h2>
            <div className="display-6 mb-3">{c === "FREE" ? "$0" : price("PRO")}<small className="fs-6 ps-muted">{c === "PRO" && " / mes"}</small></div>
            <ul className="list-unstyled">{rows.map(([l, fn]) => <li key={l} className="mb-1"><strong>{l}:</strong> {fn(c)}</li>)}</ul>
            {c === "PRO" && !isPro && (<div className="d-grid gap-2">
              {status.data?.environment !== "production" && <button className="btn btn-success" onClick={() => demo.mutate()} disabled={demo.isPending}>Activar Pro de prueba (simulador local)</button>}
              <button className="btn btn-outline-primary" onClick={() => web.mutate()}>Suscribirme (pasarela web)</button>
              <small className="ps-muted">En Android la compra se hace con Google Play Billing y se verifica en el servidor. Precios regionales según tu tienda.</small></div>)}
          </div></div>))}
      </div>
    </>
  );
}

export function Subscription() {
  const { user, refreshUser, isPro } = useAuth();
  const qc = useQueryClient();
  const mine = useQuery({ queryKey: ["mysub"], queryFn: Billing.mine });
  const tx = useQuery({ queryKey: ["tx"], queryFn: Billing.transactions });
  const ev = useMutation({ mutationFn: (a: string) => Billing.demoEvent(a), onSuccess: async () => { await refreshUser(); qc.invalidateQueries({ queryKey: ["mysub"] }); } });
  if (mine.isLoading) return <Spinner />;
  if (mine.error) return <ErrorBox error={mine.error} />;
  const subs = mine.data.subscriptions;
  const isDemo = subs[0]?.provider === "demo";
  return (
    <>
      <PageTitle>Suscripción</PageTitle>
      <div className="ps-card p-3 mb-3"><p className="mb-1">Plan actual: <strong>{user!.plan.name}</strong> · estado: <strong>{user!.subscription_status ?? "sin suscripción"}</strong></p>
        {user!.subscription_ends_at && <p className="mb-1">Acceso hasta: {new Date(user!.subscription_ends_at).toLocaleString("es")}</p>}
        {isPro && !isDemo && <p className="small ps-muted mb-0">Para cancelar o cambiar el método de pago usa Google Play → Suscripciones (Android) o tu pasarela. Conservas Pro hasta el fin del periodo.</p>}</div>
      {subs.length > 0 && <div className="ps-card p-3 mb-3"><h2 className="h6">Historial de suscripciones</h2><table className="table table-sm mb-0"><thead><tr><th>Proveedor</th><th>Estado</th><th>Fin de periodo</th><th>Prueba</th></tr></thead><tbody>{subs.map((s: any) => <tr key={s.id}><td>{s.provider}</td><td>{s.status}</td><td>{s.current_period_end ? new Date(s.current_period_end).toLocaleDateString("es") : "—"}</td><td>{s.is_test ? "sí" : "no"}</td></tr>)}</tbody></table></div>}
      {isDemo && (<div className="ps-card p-3 mb-3 border-warning"><h2 className="h6">Simulador de eventos (sólo desarrollo)</h2><p className="small ps-muted">Simula lo que enviaría el servidor de pagos. No mueve dinero.</p>
        <div className="d-flex gap-2 flex-wrap">{[["renew", "Renovar"], ["cancel", "Cancelar"], ["grace", "Periodo de gracia"], ["hold", "En espera"], ["expire", "Vencer"], ["refund", "Reembolsar"]].map(([a, l]) => <button key={a} className="btn btn-sm btn-outline-secondary" onClick={() => ev.mutate(a)}>{l}</button>)}</div></div>)}
      {tx.data && tx.data.length > 0 && <div className="ps-card p-3"><h2 className="h6">Transacciones</h2><table className="table table-sm mb-0"><tbody>{tx.data.map((t: any) => <tr key={t.id}><td>{new Date(t.created_at).toLocaleDateString("es")}</td><td>{t.provider}</td><td>{t.amount} {t.currency}</td><td>{t.status}</td><td>{t.is_test && <span className="badge text-bg-warning">prueba</span>}</td></tr>)}</tbody></table></div>}
    </>
  );
}

export function Profile() {
  const { user, setUser, logout } = useAuth();
  const nav = useNavigate();
  const [msg, setMsg] = useState<{ ok: boolean; text: string }>();
  const { register, handleSubmit, formState: { isSubmitting } } = useForm({ defaultValues: { display_name: user!.display_name, country: user!.country, timezone: user!.timezone } });
  const pw = useForm<{ current_password: string; new_password: string }>();
  const del = useForm<{ password: string; confirm: boolean }>();
  return (
    <>
      <PageTitle sub={user!.email}>Perfil</PageTitle>
      {msg && <div className={`alert alert-${msg.ok ? "success" : "danger"}`} role="status">{msg.text}</div>}
      <div className="row g-3">
        <div className="col-lg-6"><form className="ps-card p-3" onSubmit={handleSubmit(async (v) => { try { setUser(await Me.patch(v)); setMsg({ ok: true, text: "Perfil actualizado" }); } catch (e) { setMsg({ ok: false, text: apiError(e).message }); } })}>
          <h2 className="h6">Datos</h2>
          <div className="mb-2"><label className="form-label" htmlFor="dn">Nombre</label><input id="dn" className="form-control" {...register("display_name")} /></div>
          <div className="mb-2"><label className="form-label" htmlFor="co">País (código ISO)</label><input id="co" maxLength={2} className="form-control" {...register("country")} /></div>
          <div className="mb-2"><label className="form-label" htmlFor="tz">Zona horaria</label><input id="tz" className="form-control" {...register("timezone")} /><div className="form-text">Define cuándo se renuevan tus consultas diarias. Sólo puede cambiarse cada 7 días.</div></div>
          <p className="small">Plan: <strong>{user!.plan.name}</strong> · Correo {user!.email_verified ? "verificado ✔" : "sin verificar"}</p>
          <button className="btn btn-primary" disabled={isSubmitting}>Guardar</button></form></div>
        <div className="col-lg-6"><form className="ps-card p-3 mb-3" onSubmit={pw.handleSubmit(async (v) => { try { await Me.changePassword(v); setMsg({ ok: true, text: "Contraseña cambiada; las otras sesiones se cerraron." }); pw.reset(); } catch (e) { setMsg({ ok: false, text: apiError(e).message }); } })}>
          <h2 className="h6">Cambiar contraseña</h2>
          <input type="password" autoComplete="current-password" aria-label="Contraseña actual" placeholder="Contraseña actual" className="form-control mb-2" {...pw.register("current_password", { required: true })} />
          <input type="password" autoComplete="new-password" aria-label="Nueva contraseña" placeholder="Nueva contraseña (mín. 10)" className="form-control mb-2" {...pw.register("new_password", { required: true, minLength: 10 })} />
          <button className="btn btn-outline-primary">Cambiar</button></form>
          <form className="ps-card p-3 border-danger" onSubmit={del.handleSubmit(async (v) => { if (!confirm("Esto elimina tu cuenta y todos tus datos de forma permanente. ¿Continuar?")) return; try { await Me.remove({ password: v.password, confirm: true }); await logout(); nav("/welcome"); } catch (e) { setMsg({ ok: false, text: apiError(e).message }); } })}>
            <h2 className="h6 text-danger">Eliminar cuenta</h2><p className="small">Borra tu perfil, favoritos, selecciones e historial. Irreversible.</p>
            <input type="password" aria-label="Contraseña para eliminar" placeholder="Tu contraseña" className="form-control mb-2" {...del.register("password")} /><button className="btn btn-danger">Eliminar mi cuenta</button></form></div>
      </div>
    </>
  );
}

export function Settings() {
  const { mode, setMode } = useTheme();
  const { user, setUser } = useAuth();
  return (
    <>
      <PageTitle>Configuración</PageTitle>
      <div className="ps-card p-3 mb-3"><h2 className="h6">Tema</h2><div className="btn-group" role="group" aria-label="Tema">{[["light", "Claro"], ["dark", "Oscuro"], ["system", "Sistema"]].map(([k, l]) => <button key={k} className={`btn btn-sm ${mode === k ? "btn-primary" : "btn-outline-primary"}`} onClick={() => { setMode(k as any); Me.patch({ theme: k }).then(setUser).catch(() => undefined); }}>{l}</button>)}</div></div>
      <div className="ps-card p-3 mb-3"><h2 className="h6">Idioma</h2><select className="form-select w-auto" aria-label="Idioma" defaultValue={user!.language} onChange={(e) => { setLang(e.target.value as any); Me.patch({ language: e.target.value }).then(setUser).catch(() => undefined); }}>{LANGS.map((l) => <option key={l.code} value={l.code}>{l.label}</option>)}</select>
        <div className="form-text">Español completo; inglés, francés y criollo haitiano con traducción parcial (el resto se muestra en español).</div></div>
    </>
  );
}
