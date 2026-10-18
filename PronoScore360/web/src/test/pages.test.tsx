import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { vi } from "vitest";
import * as ep from "../api/endpoints";
import { RequireAdmin, RequireAuth } from "../auth/guards";
import { Plans } from "../pages/account";
import { Login, Register } from "../pages/public";
import MatchDetail from "../pages/match";
import { Predictions, Selections } from "../pages/lists";
import { FREE, mkUser, PRO, renderApp } from "./utils";

vi.mock("../api/endpoints");
const m = vi.mocked(ep, true);

beforeEach(() => { vi.resetAllMocks(); m.Catalog.status.mockResolvedValue({ is_demo_mode: true, announcements: [], environment: "local" } as any); m.Auth.demoAccounts.mockRejectedValue(new Error("no")); });

describe("autenticación y guardas", () => {
  it("Login valida campos y llama a la API real del cliente", async () => {
    const login = vi.fn().mockResolvedValue(undefined);
    m.Auth.login.mockResolvedValue({ user: mkUser(), tokens: { access_token: "a", refresh_token: "r", token_type: "bearer", expires_in: 900 } });
    renderApp(<Login />, { route: "/login", path: "/login" });
    await userEvent.click(screen.getByRole("button", { name: "Iniciar sesión" }));
    expect(await screen.findByText("Ingresa tu correo")).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText("Correo electrónico"), "ana@x.co");
    await userEvent.type(screen.getByLabelText("Contraseña"), "Secreta#12345");
    await userEvent.click(screen.getByRole("button", { name: "Iniciar sesión" }));
    await waitFor(() => expect(m.Auth.login).toHaveBeenCalledWith("ana@x.co", "Secreta#12345"));
    expect(login).not.toHaveBeenCalled();
  });
  it("Login muestra el error del servidor", async () => {
    m.Auth.login.mockRejectedValue({ response: { status: 401, data: { error: { code: "invalid_credentials", message: "Correo o contraseña incorrectos" } } } });
    renderApp(<Login />, { route: "/login", path: "/login" });
    await userEvent.type(screen.getByLabelText("Correo electrónico"), "ana@x.co");
    await userEvent.type(screen.getByLabelText("Contraseña"), "mala");
    await userEvent.click(screen.getByRole("button", { name: "Iniciar sesión" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Correo o contraseña incorrectos");
  });
  it("Registro exige contraseña fuerte y aceptar términos", async () => {
    renderApp(<Register />, { route: "/register", path: "/register" });
    await userEvent.type(screen.getByLabelText("Correo electrónico"), "ana@x.co");
    await userEvent.type(screen.getByLabelText("Contraseña"), "corta");
    await userEvent.click(screen.getByRole("button", { name: "Crear cuenta" }));
    expect(await screen.findByText("Mínimo 10 caracteres")).toBeInTheDocument();
    expect(await screen.findByText("Debes aceptar para continuar")).toBeInTheDocument();
    expect(m.Auth.register).not.toHaveBeenCalled();
  });
  it("RequireAuth redirige a login sin sesión", async () => {
    renderApp(<RequireAuth><div>SECRETO</div></RequireAuth>, { route: "/privado", path: "/privado" });
    expect(await screen.findByText("LOGIN PAGE")).toBeInTheDocument();
    expect(screen.queryByText("SECRETO")).not.toBeInTheDocument();
  });
  it("RequireAdmin no muestra el panel a usuarios normales", async () => {
    m.Me.get.mockResolvedValue(mkUser());
    renderApp(<RequireAdmin><div>PANEL ADMIN</div></RequireAdmin>, { route: "/admin", path: "/admin", user: mkUser() });
    expect(await screen.findByText("HOME PAGE")).toBeInTheDocument();
  });
  it("RequireAdmin permite a ADMIN", async () => {
    m.Me.get.mockResolvedValue(mkUser({ role: "ADMIN" }));
    renderApp(<RequireAdmin><div>PANEL ADMIN</div></RequireAdmin>, { route: "/admin", path: "/admin", user: mkUser({ role: "ADMIN" }) });
    expect(await screen.findByText("PANEL ADMIN")).toBeInTheDocument();
  });
});

const fixture = { id: 5, sport: "football", league: { id: 1, name: "Liga Demo", country: null, logo_url: null }, home_team: { id: 1, name: "Demo FC Aurora", is_demo: true }, away_team: { id: 2, name: "Demo United Boreal", is_demo: true },
  kickoff: "2026-03-01T15:00:00Z", status: "scheduled", home_score: null, away_score: null, is_demo: true, data_source: "DEMO", injuries: [] };
const pred = (id: number, market: string, selection: string, p: number, group = "basic") => ({ id, market, selection, key: `${market}:${selection}`, group, probability: p, probability_pct: p * 100, category: "medium_probability", uncertainty: "medium", data_points: 18, updated_at: "2026-02-28T10:00:00Z", is_pre_match: true, result: null });
const analysis = (extra: any = {}) => ({ fixture, disclaimer: "d", usage: null, analysis: { id: 1, status: "ok", quality: "medium", n_home: 18, n_away: 17, generated_at: "2026-02-28T10:00:00Z", is_pre_match: true, model: "football_poisson_dc", model_version: "1.0.0", is_demo: true, category_criteria: {} },
  predictions: [pred(1, "1X2", "home", 0.5), pred(2, "1X2", "draw", 0.27), pred(3, "1X2", "away", 0.23)], locked_groups: ["goals", "corners"], locked_count: 20,
  exact_score: { type: "football", expected_goals: { home: 1.6, away: 1.1 }, most_likely: { home: 1, away: 1, probability: 0.11 }, top_scores: [{ home: 1, away: 1, probability: 0.11 }], locked_extra_scores: 4, explanation: "Poisson", note: "nota" }, ...extra });

describe("detalle de partido", () => {
  const setup = (user = mkUser()) => { m.Catalog.fixture.mockResolvedValue(fixture); m.Me.get.mockResolvedValue(user); return renderApp(<MatchDetail />, { route: "/match/5", path: "/match/:id", user }); };
  it("NO consume una consulta hasta pulsar «Analizar partido»", async () => {
    setup();
    expect(await screen.findByText("Analizar partido")).toBeInTheDocument();
    expect(screen.getByText(/te quedan 3/)).toBeInTheDocument();
    expect(m.Pred.analysis).not.toHaveBeenCalled();
  });
  it("muestra probabilidades reales, calidad de datos y bloqueos del plan Gratis", async () => {
    m.Pred.analysis.mockResolvedValue(analysis() as any);
    setup();
    await userEvent.click(await screen.findByText("Analizar partido"));
    expect(await screen.findByText("50.0 %")).toBeInTheDocument();
    expect(screen.getByText(/18 \/ 17 partidos previos/)).toBeInTheDocument();
    expect(screen.getByText(/Bloqueado en tu plan: Goles, Córners/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Ver plan Pro" })).toBeInTheDocument();
    expect(screen.queryByText("+ Selección")).not.toBeInTheDocument();       // Pro only
  });
  it("muestra «Datos insuficientes» cuando el servidor no publica pronóstico", async () => {
    m.Pred.analysis.mockResolvedValue(analysis({ analysis: { ...analysis().analysis, status: "insufficient_data" }, predictions: [], message: "Datos insuficientes", reason: "Se requieren al menos 5 partidos" }) as any);
    setup();
    await userEvent.click(await screen.findByText("Analizar partido"));
    expect(await screen.findByText(/Se requieren al menos 5 partidos/)).toBeInTheDocument();
    expect(screen.queryByText(/%/)).not.toBeInTheDocument();
  });
  it("informa el límite diario (429) sin romper la pantalla", async () => {
    m.Pred.analysis.mockRejectedValue({ response: { status: 429, data: { error: { code: "quota_exceeded", message: "Alcanzaste el límite de 5 partidos" } } } });
    setup();
    await userEvent.click(await screen.findByText("Analizar partido"));
    expect(await screen.findByRole("alert")).toHaveTextContent("Alcanzaste el límite de 5 partidos");
  });
  it("Pro puede añadir mercados a la selección", async () => {
    const pro = mkUser({ plan: PRO });
    m.Pred.analysis.mockResolvedValue(analysis({ locked_groups: [] }) as any);
    setup(pro);
    await userEvent.click(await screen.findByText("Analizar partido"));
    const btns = await screen.findAllByText("+ Selección");
    await userEvent.click(btns[0]);
    expect(await screen.findByText("✓ En selección")).toBeInTheDocument();
  });
  it("el marcador exacto se presenta con su (baja) probabilidad", async () => {
    m.Pred.analysis.mockResolvedValue(analysis() as any);
    setup();
    await userEvent.click(await screen.findByText("Analizar partido"));
    await userEvent.click(await screen.findByRole("tab", { name: "Marcadores" }));
    expect(await screen.findByText(/probabilidad de acertarlo exacto/i)).toBeInTheDocument();
    expect(screen.getByText("(11.0 %)")).toBeInTheDocument();
  });
});

describe("planes, pronósticos y selecciones", () => {
  it("Plans muestra el precio de Chile en CLP y de otros países en USD", async () => {
    m.Billing.plans.mockResolvedValue([FREE, PRO]); m.Me.get.mockResolvedValue(mkUser());
    const { unmount } = renderApp(<Plans />, { route: "/plans", path: "/plans", user: mkUser() });
    expect(await screen.findByText(/\$9\.990 CLP/)).toBeInTheDocument();
    unmount();
    m.Me.get.mockResolvedValue(mkUser({ country: "US" }));
    renderApp(<Plans />, { route: "/plans", path: "/plans", user: mkUser({ country: "US" }) });
    expect(await screen.findByText(/US\$9\.99/)).toBeInTheDocument();
  });
  it("Predictions bloquea el listado para el plan Gratis", async () => {
    m.Me.get.mockResolvedValue(mkUser());
    renderApp(<Predictions />, { route: "/predictions", path: "/predictions", user: mkUser() });
    expect(await screen.findByText(/del plan Pro/)).toBeInTheDocument();
    expect(m.Pred.list).not.toHaveBeenCalled();
  });
  it("Predictions (Pro) lista resultados y explica el vacío sin forzar", async () => {
    const pro = mkUser({ plan: PRO });
    m.Me.get.mockResolvedValue(pro);
    m.Pred.meta.mockResolvedValue({ probability_range: { min: 0.6, max: 0.95 } });
    m.Pred.performance.mockResolvedValue({ items: [], note: "" });
    m.Pred.list.mockResolvedValue({ items: [], total: 0, page: 1, page_size: 20 });
    renderApp(<Predictions />, { route: "/predictions", path: "/predictions", user: pro });
    expect(await screen.findByText("Ningún pronóstico supera estos filtros")).toBeInTheDocument();
    expect(m.Pred.list).toHaveBeenCalledWith(expect.objectContaining({ min_probability: "0.6" }));
  });
  it("Selecciones muestra advertencia cuando la probabilidad conjunta no es calculable", async () => {
    const pro = mkUser({ plan: PRO });
    m.Me.get.mockResolvedValue(pro); m.Lists.groups.mockResolvedValue([]);
    sessionStorage.setItem("ps360.basket", JSON.stringify([{ fixture_id: 1, key: "1X2:home", label: "Victoria A", match: "A vs B", probability: 0.5 }, { fixture_id: 1, key: "CORNERS_TOTAL:9.5:over", label: "Córners", match: "A vs B", probability: 0.5 }]));
    m.Lists.evaluate.mockResolvedValue({ items: [], joint: { computable: false, probability: null, reason: "No se puede calcular con precisión la probabilidad total" } });
    renderApp(<Selections />, { route: "/selections", path: "/selections", user: pro });
    await userEvent.click(await screen.findByText("Calcular probabilidad conjunta"));
    expect(await screen.findByText(/No se puede calcular con precisión/)).toBeInTheDocument();
    sessionStorage.clear();
  });
});
