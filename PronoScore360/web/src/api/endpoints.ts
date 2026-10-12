import { api } from "./client";
import type { Analysis, AuthResponse, Fixture, Page, Plan, User } from "./types";

const get = async <T,>(url: string, params?: object) => (await api.get<T>(url, { params })).data;
const post = async <T,>(url: string, body?: object) => (await api.post<T>(url, body)).data;

export const Auth = {
  register: (b: object) => post<AuthResponse>("/auth/register", b),
  login: (email: string, password: string) => post<AuthResponse>("/auth/login", { email, password }),
  forgot: (email: string) => post("/auth/forgot-password", { email }),
  reset: (token: string, new_password: string) => post("/auth/reset-password", { token, new_password }),
  verify: (token: string) => post("/auth/verify-email", { token }),
  logout: (refresh_token: string) => post("/auth/logout", { refresh_token }),
  demoAccounts: () => get<{ role: string; email: string; password: string }[]>("/auth/demo-accounts"),
};
export const Me = {
  get: () => get<User>("/me"),
  patch: (b: object) => api.patch<User>("/me", b).then((r) => r.data),
  changePassword: (b: object) => post("/me/change-password", b),
  remove: (b: object) => api.request({ url: "/me", method: "DELETE", data: b }),
  history: (page = 1) => get<Page<{ date: string; viewed_at: string; fixture: Fixture }> & { history_days: number | null }>("/me/history", { page }),
  comparison: () => get<any>("/me/comparison"),
};
export const Catalog = {
  status: () => get<any>("/status"),
  fixtures: (p: object) => get<Page<Fixture>>("/fixtures", p),
  fixture: (id: number) => get<any>(`/fixtures/${id}`),
  fixtureStats: (id: number) => get<any>(`/fixtures/${id}/stats`),
  teams: (p: object) => get<Page<any>>("/teams", p),
  teamStats: (id: number, last: number) => get<any>(`/teams/${id}/stats`, { last }),
  leagues: (p: object = {}) => get<Page<any>>("/leagues", p),
  standings: (league_id: number) => get<any>("/standings", { league_id }),
};
export const Pred = {
  analysis: (id: number) => get<Analysis>(`/fixtures/${id}/analysis`),
  list: (p: object) => get<Page<any>>("/predictions", p),
  meta: () => get<any>("/predictions/meta"),
  performance: (sport?: string) => get<any>("/models/performance", { sport }),
  nbaLines: (id: number, p: object) => get<any>(`/nba/games/${id}/lines`, p),
  save: (pid: number) => post(`/saved-predictions/${pid}`),
};
export const Lists = {
  favorites: () => get<any>("/favorites"),
  addFavorite: (kind: string, ref_id: number) => post("/favorites", { kind, ref_id }),
  removeFavorite: (id: number) => api.delete(`/favorites/${id}`),
  evaluate: (items: { fixture_id: number; key: string }[]) => post<any>("/selections/evaluate", { items }),
  groups: () => get<any[]>("/selections"),
  createGroup: (name: string, items: object[]) => post<any>("/selections", { name, items }),
  deleteGroup: (id: number) => api.delete(`/selections/${id}`),
  share: (id: number) => get<{ text: string }>(`/selections/${id}/share`),
  notifications: () => get<any>("/notifications"),
  readNotification: (id: number) => post(`/notifications/${id}/read`),
  readAll: () => post("/notifications/read-all"),
};
export const Billing = {
  plans: () => get<Plan[]>("/plans"),
  mine: () => get<any>("/subscriptions/me"),
  demoActivate: () => post<any>("/subscriptions/demo/activate"),
  demoEvent: (action: string) => post<any>("/subscriptions/demo/event", { action }),
  checkout: () => post<any>("/subscriptions/checkout", { plan_code: "PRO" }),
  transactions: () => get<any[]>("/subscriptions/transactions"),
};
export const Admin = {
  dashboard: () => get<any>("/admin/dashboard"),
  users: (p: object) => get<any>("/admin/users", p),
  patchUser: (id: number, b: object) => api.patch(`/admin/users/${id}`, b).then((r) => r.data),
  plans: () => get<any[]>("/admin/plans"),
  patchPlan: (code: string, b: object) => api.patch(`/admin/plans/${code}`, b).then((r) => r.data),
  providers: () => get<any>("/admin/providers"),
  jobs: () => get<any>("/admin/jobs"),
  runJob: (job: string) => post<any>("/admin/jobs/run", { job }),
  models: () => get<any>("/admin/models"),
  backtest: (sport: string) => post<any>("/admin/models/backtest", { sport }),
  errors: () => get<any>("/admin/logs/errors"),
  audit: () => get<any>("/admin/logs/audit"),
  subscriptions: () => get<any>("/admin/subscriptions"),
  announcements: () => get<any[]>("/admin/announcements"),
  createAnnouncement: (b: object) => post("/admin/announcements", b),
  deleteAnnouncement: (id: number) => api.delete(`/admin/announcements/${id}`),
  reseed: () => post<any>("/admin/demo/reseed"),
};
