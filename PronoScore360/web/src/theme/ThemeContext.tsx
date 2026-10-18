import { createContext, ReactNode, useContext, useEffect, useState } from "react";

export type ThemeMode = "light" | "dark" | "system";
const Ctx = createContext<{ mode: ThemeMode; setMode: (m: ThemeMode) => void }>({ mode: "system", setMode: () => {} });

function resolve(mode: ThemeMode): "light" | "dark" {
  if (mode !== "system") return mode;
  return window.matchMedia?.("(prefers-color-scheme: dark)").matches ? "dark" : "light";
}

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [mode, setModeState] = useState<ThemeMode>(() => {
    try { return (localStorage.getItem("ps360.theme") as ThemeMode) || "system"; } catch { return "system"; }
  });
  useEffect(() => { document.documentElement.setAttribute("data-bs-theme", resolve(mode)); }, [mode]);
  const setMode = (m: ThemeMode) => { setModeState(m); try { localStorage.setItem("ps360.theme", m); } catch { /* noop */ } };
  return <Ctx.Provider value={{ mode, setMode }}>{children}</Ctx.Provider>;
}
export const useTheme = () => useContext(Ctx);
