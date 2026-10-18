/**
 * Integración frontend/backend: usa el cliente HTTP y las pantallas REALES contra el backend en marcha
 * (modo DEMO, base de datos PostgreSQL). Ejecutar:  npm run test:integration
 * Requiere:  backend en http://localhost:8000 con SEED_DEMO_ON_START=true  (o API_URL=...)
 */
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import axios from "axios";
import App from "../App";
import { API_URL, getTokens, setTokens } from "../api/client";
import { Auth, Catalog, Me, Pred } from "../api/endpoints";
import { AuthProvider } from "../auth/AuthContext";
import { BasketProvider } from "../components/Basket";
import { ThemeProvider } from "../theme/ThemeContext";

const FREE = { email: "free@pronoscore-demo.example.com", password: "Demo#Free2026" };
const PRO = { email: "pro@pronoscore-demo.example.com", password: "Demo#Pro2026" };

let backendUp = false;
beforeAll(async () => { try { await axios.get(`${API_URL}/health`, { timeout: 3000 }); backendUp = true; } catch { backendUp = false; } });
beforeEach(() => { setTokens(null); sessionStorage.clear(); });
const itLive = (name: string, fn: () => Promise<void>, timeout?: number) => it(name, async () => { if (!backendUp) { console.warn(`SKIP (backend no disponible en ${API_URL}): ${name}`); return; } await fn(); }, timeout);

describe("integración con el backend real", () => {
  itLive("registro, perfil y límite de cuenta nueva", async () => {
    const email = `web-${Date.now()}@pronoscore-test.example.com`;
    const r = await Auth.register({ email, password: "Integra#2026ab", accept_terms: true });
    setTokens(r.tokens);
    const me = await Me.get();
    expect(me.email).toBe(email); expect(me.plan.code).toBe("FREE"); expect(me.usage.limit).toBe(5);
    await expect(Pred.list({})).rejects.toMatchObject({ response: { status: 402 } });   // Pro-only en el servidor
  });

  itLive("login demo, calendario real y análisis que descuenta UNA consulta", async () => {
    const r = await Auth.login(FREE.email, FREE.password); setTokens(r.tokens);
    const fx = await Catalog.fixtures({ sport: "football", status: "scheduled", page_size: 3 });
    expect(fx.items.length).toBeGreaterThan(0);
    const before = (await Me.get()).usage;
    const a1 = await Pred.analysis(fx.items[0].id);
    expect(a1.analysis.status).toBe("ok");
    const p = a1.predictions.filter((x) => x.market === "1X2").reduce((s, x) => s + x.probability, 0);
    expect(p).toBeGreaterThan(0.99); expect(p).toBeLessThan(1.01);
    const used1 = (await Me.get()).usage.used;
    await Pred.analysis(fx.items[0].id);                         // repetido el mismo día: no descuenta
    expect((await Me.get()).usage.used).toBe(used1);
    expect(used1).toBeGreaterThanOrEqual(before.used);
  });

  itLive("flujo de UI completo: login → calendario → partido → analizar → pronósticos", async () => {
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(<QueryClientProvider client={qc}><ThemeProvider><MemoryRouter initialEntries={["/login"]}><AuthProvider><BasketProvider><App /></BasketProvider></AuthProvider></MemoryRouter></ThemeProvider></QueryClientProvider>);
    await userEvent.type(await screen.findByLabelText("Correo electrónico"), PRO.email);
    await userEvent.type(screen.getByLabelText("Contraseña"), PRO.password);
    await userEvent.click(screen.getByRole("button", { name: "Iniciar sesión" }));
    expect(await screen.findByText(/Próximos partidos/, {}, { timeout: 10000 })).toBeInTheDocument();
    const cards = await screen.findAllByRole("link", { name: /contra/ }, { timeout: 10000 });
    await userEvent.click(cards[0]);
    await userEvent.click(await screen.findByRole("button", { name: "Analizar partido" }, { timeout: 10000 }));
    expect(await screen.findByText(/Resultado 1X2|Probabilidad de victoria/, {}, { timeout: 15000 })).toBeInTheDocument();
    expect((await screen.findAllByText(/ %$/)).length).toBeGreaterThanOrEqual(2);
    expect((await screen.findAllByText(/partidos · incertidumbre/)).length).toBeGreaterThan(3);   // filas de pronóstico reales de la API
    expect(screen.getByText(/Calidad:/)).toBeInTheDocument();
    expect(getTokens()).not.toBeNull();
  }, 40000);

  itLive("refresco de token tras expirar el access token", async () => {
    const r = await Auth.login(PRO.email, PRO.password);
    setTokens({ ...r.tokens, access_token: "token.invalido.xyz" });       // simula expiración
    const me = await Me.get();                                            // interceptor renueva y reintenta
    expect(me.email).toBe(PRO.email);
    expect(getTokens()?.access_token).not.toBe("token.invalido.xyz");
  });

  itLive("el rol ADMIN se aplica en el servidor aunque el frontend lo oculte", async () => {
    const r = await Auth.login(FREE.email, FREE.password); setTokens(r.tokens);
    const { Admin } = await import("../api/endpoints");
    await expect(Admin.dashboard()).rejects.toMatchObject({ response: { status: 403 } });
    const a = await Auth.login("admin@pronoscore-demo.example.com", "Demo#Admin2026"); setTokens(a.tokens);
    expect((await Admin.dashboard()).users.total).toBeGreaterThan(2);
  });
});
