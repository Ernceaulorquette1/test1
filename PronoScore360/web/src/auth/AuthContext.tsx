import { createContext, ReactNode, useCallback, useContext, useEffect, useMemo, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { Auth, Me } from "../api/endpoints";
import { getTokens, setTokens } from "../api/client";
import type { AuthResponse, User } from "../api/types";

interface Ctx {
  user: User | null; loading: boolean; isAdmin: boolean; isPro: boolean;
  login: (email: string, password: string) => Promise<void>; register: (b: object) => Promise<void>;
  logout: () => Promise<void>; refreshUser: () => Promise<void>; setUser: (u: User) => void;
}
const AuthCtx = createContext<Ctx | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState<boolean>(!!getTokens());
  const qc = useQueryClient();

  const refreshUser = useCallback(async () => { setUser(await Me.get()); }, []);

  useEffect(() => {
    if (!getTokens()) return;
    Me.get().then(setUser).catch(() => setTokens(null)).finally(() => setLoading(false));
  }, []);
  useEffect(() => {
    const onLogout = () => { setUser(null); qc.clear(); };
    window.addEventListener("ps:logout", onLogout);
    return () => window.removeEventListener("ps:logout", onLogout);
  }, [qc]);

  const accept = (r: AuthResponse) => { setTokens(r.tokens); setUser(r.user); qc.clear(); };
  const value = useMemo<Ctx>(() => ({
    user, loading, isAdmin: user?.role === "ADMIN", isPro: user?.plan.code === "PRO",
    login: async (e, p) => accept(await Auth.login(e, p)),
    register: async (b) => accept(await Auth.register(b)),
    logout: async () => {
      const t = getTokens();
      try { if (t) await Auth.logout(t.refresh_token); } catch { /* sesión ya inválida */ }
      setTokens(null); setUser(null); qc.clear();
    },
    refreshUser, setUser,
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }), [user, loading, refreshUser, qc]);
  return <AuthCtx.Provider value={value}>{children}</AuthCtx.Provider>;
}

export function useAuth(): Ctx {
  const c = useContext(AuthCtx);
  if (!c) throw new Error("useAuth fuera de AuthProvider");
  return c;
}
