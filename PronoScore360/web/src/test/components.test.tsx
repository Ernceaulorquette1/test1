import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { CategoryBadge, Empty, ErrorBox, MatchCard, Pager, ProbBar, pct } from "../components/ui";
import { marketLabel } from "../components/format";
import { translate } from "../i18n";

const fx: any = { id: 7, sport: "football", league: { id: 1, name: "Liga Demo", country: null, logo_url: null }, home_team: { id: 1, name: "Demo FC Aurora", is_demo: true },
  away_team: { id: 2, name: "Demo United Boreal", is_demo: true }, kickoff: "2026-03-01T15:00:00Z", status: "scheduled", home_score: null, away_score: null, is_demo: true, data_source: "DEMO" };

describe("componentes de UI", () => {
  it("ProbBar muestra el porcentaje real y es accesible", () => {
    render(<ProbBar value={0.62} label="Victoria local" />);
    expect(screen.getByText("62.0 %")).toBeInTheDocument();
    expect(screen.getByRole("progressbar", { name: "Victoria local" })).toHaveAttribute("aria-valuenow", "62");
  });
  it("pct no inventa ni redondea hacia arriba", () => { expect(pct(0.6149)).toBe("61.5 %"); expect(pct(0.6149, 0)).toBe("61 %"); });
  it("CategoryBadge usa las categorías documentadas", () => {
    render(<><CategoryBadge category="high_probability" /><CategoryBadge category="high_uncertainty" /><CategoryBadge category="insufficient_data" /></>);
    expect(screen.getByText("Alta probabilidad estimada")).toBeInTheDocument();
    expect(screen.getByText("Alta incertidumbre")).toBeInTheDocument();
    expect(screen.getByText("Datos insuficientes")).toBeInTheDocument();
  });
  it("MatchCard enlaza al detalle y marca los datos DEMO", () => {
    render(<MemoryRouter><MatchCard f={fx} /></MemoryRouter>);
    expect(screen.getByRole("link")).toHaveAttribute("href", "/match/7");
    expect(screen.getByText("DEMO")).toBeInTheDocument();
    expect(screen.getByText("Ver análisis")).toBeInTheDocument();
  });
  it("Empty y ErrorBox muestran mensajes claros", () => {
    const { rerender } = render(<Empty title="Sin partidos" />);
    expect(screen.getByText("Sin partidos")).toBeInTheDocument();
    rerender(<ErrorBox error={{ response: { status: 500, data: { error: { code: "internal_error", message: "Error interno" } } } }} />);
    expect(screen.getByRole("alert")).toHaveTextContent("Error interno");
  });
  it("Pager deshabilita límites", () => {
    const fn = vi.fn();
    render(<Pager page={1} total={50} pageSize={10} onChange={fn} />);
    expect(screen.getByText("‹ Anterior")).toBeDisabled();
    screen.getByText("Siguiente ›").click();
    expect(fn).toHaveBeenCalledWith(2);
  });
  it("etiquetas de mercado legibles", () => {
    expect(marketLabel("1X2", "home", "Aurora", "Boreal")).toBe("Victoria Aurora");
    expect(marketLabel("OU:2.5", "over")).toBe("Goles totales: Más de 2.5");
    expect(marketLabel("SPREAD:home:-3.5", "cover", "LAL")).toContain("LAL -3.5");
    expect(marketLabel("CORNERS_TOTAL:9.5", "under")).toContain("Menos de 9.5");
  });
  it("i18n con fallback a español", () => {
    expect(translate("nav.home", "en")).toBe("Home");
    expect(translate("nav.selections", "fr")).toBe("Selecciones");   // sin traducción -> español
    expect(translate("clave.inexistente", "ht")).toBe("clave.inexistente");
  });
});
