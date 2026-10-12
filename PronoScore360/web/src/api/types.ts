export interface Plan {
  code: "FREE" | "PRO"; name: string; daily_analysis_limit: number | null; favorites_limit: number | null; history_days: number | null;
  selections_enabled: boolean; allowed_market_groups: string[]; features: Record<string, boolean | number>; prices: Record<string, { amount: number; currency: string; note?: string }>;
  google_product_id?: string | null;
}
export interface Usage { plan: string; date: string; timezone: string; limit: number | null; used: number; remaining: number | null; resets_at: string }
export interface User {
  id: number; email: string; display_name: string; role: "USER" | "ADMIN"; email_verified: boolean; country: string; language: string;
  timezone: string; theme: string; plan: Plan; subscription_status: string | null; subscription_ends_at: string | null; usage: Usage; is_demo: boolean;
}
export interface Tokens { access_token: string; refresh_token: string; token_type: string; expires_in: number }
export interface AuthResponse { user: User; tokens: Tokens }
export interface Team { id: number; name: string; short_name?: string | null; logo_url?: string | null; is_demo: boolean }
export interface Fixture {
  id: number; sport: "football" | "basketball"; league: { id: number; name: string; country: string | null; logo_url: string | null };
  home_team: Team; away_team: Team; kickoff: string; kickoff_local?: string; status: string; home_score: number | null; away_score: number | null;
  is_demo: boolean; data_source: string;
}
export interface Page<T> { items: T[]; total: number; page: number; page_size: number }
export interface Prediction {
  id: number; market: string; selection: string; key: string | null; group: string; probability: number; probability_pct: number;
  category: string; uncertainty: string; data_points: number; updated_at: string; is_pre_match: boolean; result: "hit" | "miss" | null;
}
export interface Analysis {
  fixture: Fixture; disclaimer: string; usage: Usage | null; message?: string; reason?: string;
  analysis: { id: number; status: string; quality: string; n_home: number; n_away: number; generated_at: string; is_pre_match: boolean; model: string; model_version: string; is_demo: boolean; category_criteria: Record<string, string> };
  predictions: Prediction[]; locked_groups: string[]; locked_count?: number; exact_score: any | null; drivers?: { factor: string; value: number | string; note?: string }[] | null; upgrade_hint?: string | null;
}
export interface ApiErrorInfo { status: number; code: string; message: string; details?: any }
