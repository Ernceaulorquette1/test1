import { AxiosHeaders } from "axios";
import { api, apiError, getTokens, setTokens } from "../api/client";

const tokens = (n: string) => ({ access_token: `acc-${n}`, refresh_token: `ref-${n}`, token_type: "bearer", expires_in: 900 });
const resp = (config: any, status: number, data: any) => ({ data, status, statusText: "", headers: new AxiosHeaders(), config });

describe("cliente API", () => {
  afterEach(() => { api.defaults.adapter = undefined; vi.restoreAllMocks(); });

  it("adjunta el Bearer token", async () => {
    setTokens(tokens("1"));
    let auth = "";
    api.defaults.adapter = async (c) => { auth = String(c.headers.get("Authorization")); return resp(c, 200, {}); };
    await api.get("/me");
    expect(auth).toBe("Bearer acc-1");
  });

  it("renueva el token una sola vez ante token_expired y reintenta", async () => {
    setTokens(tokens("old"));
    const axios = (await import("axios")).default;
    const refresh = vi.spyOn(axios, "post").mockResolvedValue({ data: tokens("new") });
    const seen: string[] = [];
    api.defaults.adapter = async (c) => {
      const a = String(c.headers.get("Authorization")); seen.push(a);
      if (a.includes("old")) return Promise.reject({ isAxiosError: true, config: c, response: resp(c, 401, { error: { code: "token_expired", message: "expiró" } }) });
      return resp(c, 200, { ok: true });
    };
    const [r1, r2] = await Promise.all([api.get("/a"), api.get("/b")]);
    expect(r1.data.ok && r2.data.ok).toBe(true);
    expect(refresh).toHaveBeenCalledTimes(1);                 // renovación concurrente única
    expect(getTokens()?.access_token).toBe("acc-new");
  });

  it("si la renovación falla cierra la sesión", async () => {
    setTokens(tokens("old"));
    const axios = (await import("axios")).default;
    vi.spyOn(axios, "post").mockRejectedValue(new Error("revocado"));
    const onLogout = vi.fn(); window.addEventListener("ps:logout", onLogout);
    api.defaults.adapter = async (c) => Promise.reject({ isAxiosError: true, config: c, response: resp(c, 401, { error: { code: "token_expired", message: "x" } }) });
    await expect(api.get("/me")).rejects.toBeTruthy();
    expect(getTokens()).toBeNull(); expect(onLogout).toHaveBeenCalled();
  });

  it("no intenta renovar errores 401 de credenciales", async () => {
    setTokens(tokens("1"));
    const axios = (await import("axios")).default;
    const refresh = vi.spyOn(axios, "post");
    api.defaults.adapter = async (c) => Promise.reject({ isAxiosError: true, config: c, response: resp(c, 401, { error: { code: "invalid_credentials", message: "mal" } }) });
    await expect(api.post("/auth/login", {})).rejects.toBeTruthy();
    expect(refresh).not.toHaveBeenCalled();
  });

  it("apiError normaliza errores de red y del servidor", () => {
    expect(apiError({ message: "Network Error", code: "ERR_NETWORK" }).code).toBe("network");
    expect(apiError({ response: { status: 429, data: { error: { code: "quota_exceeded", message: "límite", details: { usage: { remaining: 0 } } } } } })).toMatchObject({ status: 429, code: "quota_exceeded", details: { usage: { remaining: 0 } } });
  });
});
