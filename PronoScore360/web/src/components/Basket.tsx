import { createContext, ReactNode, useContext, useEffect, useState } from "react";

export interface BasketItem { fixture_id: number; key: string; label: string; match: string; probability: number }
const Ctx = createContext<{ items: BasketItem[]; add: (i: BasketItem) => void; remove: (i: BasketItem) => void; clear: () => void; has: (fixtureId: number, key: string) => boolean }>(
  { items: [], add: () => {}, remove: () => {}, clear: () => {}, has: () => false });

/** Cesta de selecciones en edición (sólo cliente). Se guarda en el servidor al pulsar «Guardar». */
export function BasketProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<BasketItem[]>(() => { try { return JSON.parse(sessionStorage.getItem("ps360.basket") ?? "[]"); } catch { return []; } });
  useEffect(() => { try { sessionStorage.setItem("ps360.basket", JSON.stringify(items)); } catch { /* noop */ } }, [items]);
  const same = (a: BasketItem, b: BasketItem) => a.fixture_id === b.fixture_id && a.key === b.key;
  return (<Ctx.Provider value={{ items,
    add: (i) => setItems((s) => (s.some((x) => same(x, i)) ? s : [...s, i])),
    remove: (i) => setItems((s) => s.filter((x) => !same(x, i))), clear: () => setItems([]),
    has: (f, k) => items.some((x) => x.fixture_id === f && x.key === k) }}>{children}</Ctx.Provider>);
}
export const useBasket = () => useContext(Ctx);
