import { useQuery } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link, Navigate, useLocation, useNavigate, useSearchParams } from "react-router-dom";
import { Auth } from "../api/endpoints";
import { apiError } from "../api/client";
import { useAuth } from "../auth/AuthContext";
import { Logo, Spinner } from "../components/ui";
import { LANGS, setLang, useT } from "../i18n";

export function Splash() {
  const { user, loading } = useAuth();
  const nav = useNavigate();
  useEffect(() => { if (loading) return; const id = setTimeout(() => nav(user ? "/home" : "/welcome", { replace: true }), 900); return () => clearTimeout(id); }, [user, loading, nav]);
  return (<div className="vh-100 d-flex flex-column justify-content-center align-items-center" style={{ background: "#0b1f3a", color: "#fff" }} role="status" aria-label="Cargando PronoScore360">
    <Logo size={96} /><h1 className="h2 mt-3 fw-bold">PronoScore<span style={{ color: "#1fbf75" }}>360</span></h1><div className="spinner-border spinner-border-sm mt-3" aria-hidden="true" /></div>);
}

export function Welcome() {
  const t = useT();
  return (
    <div className="container py-5" style={{ maxWidth: 920 }}>
      <div className="d-flex justify-content-end gap-2 mb-3">
        <select className="form-select form-select-sm w-auto" aria-label="Idioma" defaultValue="es" onChange={(e) => setLang(e.target.value as any)}>{LANGS.map((l) => <option key={l.code} value={l.code}>{l.label}</option>)}</select>
      </div>
      <div className="ps-hero p-4 p-md-5 text-center">
        <Logo size={72} /><h1 className="display-6 fw-bold mt-3">{t("welcome.title")}</h1>
        <p className="lead">{t("welcome.sub")}</p>
        <div className="d-flex gap-2 justify-content-center flex-wrap"><Link className="btn btn-success btn-lg" to="/register">{t("auth.register")}</Link><Link className="btn btn-outline-light btn-lg" to="/login">{t("auth.login")}</Link></div>
      </div>
      <div className="row g-3 mt-2">
        {[["⚽", "Fútbol internacional", "Poisson + Dixon-Coles: 1X2, goles, ambos marcan, córners, tarjetas y marcadores probables."],
          ["🏀", "NBA y baloncesto", "Eficiencia por posesión y simulaciones Monte Carlo: ganador, totales, hándicap y rangos."],
          ["📊", "Transparencia", "Cada pronóstico muestra su probabilidad real, datos usados y fecha. Si faltan datos: «Datos insuficientes»."]].map(([i, h, d]) => (
          <div className="col-md-4" key={h}><div className="ps-card p-3 h-100"><div style={{ fontSize: 32 }} aria-hidden="true">{i}</div><h2 className="h5">{h}</h2><p className="ps-muted mb-0">{d}</p></div></div>))}
      </div>
      <p className="small ps-muted text-center mt-4">PronoScore360 es una plataforma informativa. No permite apuestas ni depósitos de dinero. Las probabilidades son estimaciones y no garantizan resultados.</p>
    </div>
  );
}

function AuthShell({ title, children }: { title: string; children: React.ReactNode }) {
  return (<div className="container py-5" style={{ maxWidth: 460 }}><div className="text-center mb-3"><Link to="/welcome"><Logo size={56} /></Link></div><div className="ps-card p-4"><h1 className="h4 mb-3">{title}</h1>{children}</div></div>);
}

export function Login() {
  const t = useT();
  const { login, user } = useAuth();
  const nav = useNavigate();
  const loc = useLocation() as any;
  const [err, setErr] = useState<string>();
  const { register, handleSubmit, setValue, formState: { errors, isSubmitting } } = useForm<{ email: string; password: string }>();
  const demo = useQuery({ queryKey: ["demo-accounts"], queryFn: Auth.demoAccounts, retry: false });
  if (user) return <Navigate to="/home" replace />;
  const submit = handleSubmit(async (v) => { setErr(undefined); try { await login(v.email, v.password); nav(loc.state?.from ?? "/home", { replace: true }); } catch (e) { setErr(apiError(e).message); } });
  return (
    <AuthShell title={t("auth.login")}>
      {err && <div className="alert alert-danger" role="alert">{err}</div>}
      <form onSubmit={submit} noValidate>
        <div className="mb-3"><label htmlFor="email" className="form-label">{t("auth.email")}</label>
          <input id="email" type="email" autoComplete="username" className={`form-control ${errors.email ? "is-invalid" : ""}`} {...register("email", { required: "Ingresa tu correo", pattern: { value: /^\S+@\S+\.\S+$/, message: "Correo inválido" } })} />
          {errors.email && <div className="invalid-feedback">{errors.email.message}</div>}</div>
        <div className="mb-3"><label htmlFor="password" className="form-label">{t("auth.password")}</label>
          <input id="password" type="password" autoComplete="current-password" className={`form-control ${errors.password ? "is-invalid" : ""}`} {...register("password", { required: "Ingresa tu contraseña" })} />
          {errors.password && <div className="invalid-feedback">{errors.password.message}</div>}</div>
        <button className="btn btn-primary w-100" disabled={isSubmitting}>{isSubmitting ? "Ingresando…" : t("auth.login")}</button>
      </form>
      <div className="d-flex justify-content-between mt-3 small"><Link to="/forgot-password">{t("auth.forgot")}</Link><Link to="/register">{t("auth.register")}</Link></div>
      {demo.data && (<div className="mt-4 border-top pt-3"><div className="small fw-semibold mb-2">Cuentas de demostración (sólo entorno local)</div>
        <div className="d-flex gap-2 flex-wrap">{demo.data.map((a) => (<button key={a.role} type="button" className="btn btn-sm btn-outline-secondary" onClick={() => { setValue("email", a.email); setValue("password", a.password); }}>{a.role === "admin" ? "Admin" : a.role === "pro" ? "Pro" : "Gratis"}</button>))}</div></div>)}
    </AuthShell>
  );
}

export function Register() {
  const t = useT();
  const { register: signup, user } = useAuth();
  const nav = useNavigate();
  const [err, setErr] = useState<string>();
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<{ email: string; password: string; display_name: string; country: string; accept_terms: boolean }>({ defaultValues: { country: "CL" } });
  if (user) return <Navigate to="/home" replace />;
  const submit = handleSubmit(async (v) => {
    setErr(undefined);
    try { await signup({ ...v, language: "es", timezone: Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC" }); nav("/home", { replace: true }); } catch (e) { setErr(apiError(e).message); }
  });
  return (
    <AuthShell title={t("auth.register")}>
      {err && <div className="alert alert-danger" role="alert">{err}</div>}
      <form onSubmit={submit} noValidate>
        <div className="mb-3"><label htmlFor="display_name" className="form-label">Nombre</label><input id="display_name" className="form-control" {...register("display_name")} /></div>
        <div className="mb-3"><label htmlFor="email" className="form-label">{t("auth.email")}</label>
          <input id="email" type="email" className={`form-control ${errors.email ? "is-invalid" : ""}`} {...register("email", { required: "Ingresa tu correo", pattern: { value: /^\S+@\S+\.\S+$/, message: "Correo inválido" } })} />
          {errors.email && <div className="invalid-feedback">{errors.email.message}</div>}</div>
        <div className="mb-3"><label htmlFor="password" className="form-label">{t("auth.password")}</label>
          <input id="password" type="password" autoComplete="new-password" className={`form-control ${errors.password ? "is-invalid" : ""}`}
            {...register("password", { required: "Crea una contraseña", minLength: { value: 10, message: "Mínimo 10 caracteres" }, validate: (v) => (/[a-z]/.test(v) && /[A-Z]/.test(v) && /\d/.test(v)) || "Incluye mayúsculas, minúsculas y números" })} />
          {errors.password ? <div className="invalid-feedback">{errors.password.message}</div> : <div className="form-text">Mínimo 10 caracteres con mayúsculas, minúsculas y números.</div>}</div>
        <div className="mb-3"><label htmlFor="country" className="form-label">País</label>
          <select id="country" className="form-select" {...register("country")}>{[["CL", "Chile"], ["US", "Estados Unidos"], ["AR", "Argentina"], ["CO", "Colombia"], ["MX", "México"], ["PE", "Perú"], ["ES", "España"], ["HT", "Haití"], ["FR", "Francia"]].map(([c, n]) => <option key={c} value={c}>{n}</option>)}</select></div>
        <div className="form-check mb-3"><input id="terms" type="checkbox" className={`form-check-input ${errors.accept_terms ? "is-invalid" : ""}`} {...register("accept_terms", { required: "Debes aceptar para continuar" })} />
          <label htmlFor="terms" className="form-check-label small">Acepto los <Link to="/terms">Términos</Link> y la <Link to="/privacy">Política de privacidad</Link></label>
          {errors.accept_terms && <div className="invalid-feedback d-block">{errors.accept_terms.message}</div>}</div>
        <button className="btn btn-success w-100" disabled={isSubmitting}>{isSubmitting ? "Creando…" : t("auth.register")}</button>
      </form>
      <div className="mt-3 small text-center"><Link to="/login">¿Ya tienes cuenta? {t("auth.login")}</Link></div>
    </AuthShell>
  );
}

export function ForgotPassword() {
  const [sent, setSent] = useState(false);
  const { register, handleSubmit, formState: { errors } } = useForm<{ email: string }>();
  return (
    <AuthShell title="Recuperar contraseña">
      {sent ? <div className="alert alert-success" role="status">Si el correo existe, te enviamos instrucciones. Revisa tu bandeja.</div> : (
        <form onSubmit={handleSubmit(async (v) => { await Auth.forgot(v.email).catch(() => undefined); setSent(true); })} noValidate>
          <div className="mb-3"><label htmlFor="email" className="form-label">Correo electrónico</label>
            <input id="email" type="email" className={`form-control ${errors.email ? "is-invalid" : ""}`} {...register("email", { required: "Ingresa tu correo" })} /></div>
          <button className="btn btn-primary w-100">Enviar instrucciones</button></form>)}
      <div className="mt-3 small text-center"><Link to="/login">Volver a iniciar sesión</Link></div>
    </AuthShell>
  );
}

export function ResetPassword() {
  const [sp] = useSearchParams();
  const nav = useNavigate();
  const [err, setErr] = useState<string>();
  const { register, handleSubmit, formState: { errors } } = useForm<{ password: string }>();
  return (
    <AuthShell title="Nueva contraseña">
      {err && <div className="alert alert-danger" role="alert">{err}</div>}
      <form onSubmit={handleSubmit(async (v) => { try { await Auth.reset(sp.get("token") ?? "", v.password); nav("/login"); } catch (e) { setErr(apiError(e).message); } })} noValidate>
        <div className="mb-3"><label htmlFor="password" className="form-label">Nueva contraseña</label>
          <input id="password" type="password" className={`form-control ${errors.password ? "is-invalid" : ""}`} {...register("password", { required: true, minLength: { value: 10, message: "Mínimo 10 caracteres" } })} />
          {errors.password && <div className="invalid-feedback">{errors.password.message ?? "Requerido"}</div>}</div>
        <button className="btn btn-primary w-100">Guardar contraseña</button></form>
    </AuthShell>
  );
}

export function VerifyEmail() {
  const [sp] = useSearchParams();
  const [state, setState] = useState<"loading" | "ok" | "error">("loading");
  useEffect(() => { Auth.verify(sp.get("token") ?? "").then(() => setState("ok")).catch(() => setState("error")); }, [sp]);
  return (<AuthShell title="Verificación de correo">{state === "loading" ? <Spinner /> : state === "ok" ? <div className="alert alert-success">¡Correo verificado!</div> : <div className="alert alert-danger">El enlace es inválido o expiró.</div>}<Link to="/home">Ir al inicio</Link></AuthShell>);
}
