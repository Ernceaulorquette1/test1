import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render } from "@testing-library/react";
import { ReactElement } from "react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { AuthProvider } from "../auth/AuthContext";
import { RequireAuth } from "../auth/guards";
import { BasketProvider } from "../components/Basket";
import { ThemeProvider } from "../theme/ThemeContext";
import type { Plan, User } from "../api/types";

export const FREE: Plan = { code: "FREE", name: "Gratis", daily_analysis_limit: 5, favorites_limit: 10, history_days: 7, selections_enabled: false, allowed_market_groups: ["basic"], features: { alerts: false }, prices: {} };
export const PRO: Plan = { ...FREE, code: "PRO", name: "Pro", daily_analysis_limit: null, favorites_limit: 200, history_days: null, selections_enabled: true, allowed_market_groups: ["basic", "goals"], features: { alerts: true, advanced_stats: true },
  prices: { CL: { amount: 9990, currency: "CLP" }, DEFAULT: { amount: 9.99, currency: "USD" } } };
export const mkUser = (o: Partial<User> = {}): User => ({ id: 1, email: "a@b.co", display_name: "Ana", role: "USER", email_verified: true, country: "CL", language: "es", timezone: "America/Santiago", theme: "system",
  plan: FREE, subscription_status: null, subscription_ends_at: null, is_demo: false, usage: { plan: "FREE", date: "2026-01-01", timezone: "America/Santiago", limit: 5, used: 2, remaining: 3, resets_at: "2026-01-02T03:00:00Z" }, ...o });

export function renderApp(ui: ReactElement, { route = "/", path = "*", user }: { route?: string; path?: string; user?: User } = {}) {
  if (user) localStorage.setItem("ps360.tokens", JSON.stringify({ access_token: "a", refresh_token: "r", token_type: "bearer", expires_in: 900 }));
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}><ThemeProvider><MemoryRouter initialEntries={[route]}><AuthProvider><BasketProvider>
      <Routes><Route path={path} element={user ? <RequireAuth>{ui}</RequireAuth> : ui} /><Route path="/login" element={<div>LOGIN PAGE</div>} /><Route path="/home" element={<div>HOME PAGE</div>} /></Routes>
    </BasketProvider></AuthProvider></MemoryRouter></ThemeProvider></QueryClientProvider>);
}
