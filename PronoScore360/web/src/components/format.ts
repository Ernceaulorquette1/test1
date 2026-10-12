export const GROUP_LABEL: Record<string, string> = { basic: "Principales", goals: "Goles", corners: "Córners", cards: "Tarjetas", halftime: "Descanso", nba_lines: "Líneas NBA (puntos)" };

/** Etiqueta legible de un mercado/selección. */
export function marketLabel(market: string, selection: string, home = "Local", away = "Visitante"): string {
  const p = market.split(":");
  const side = (s: string) => (s === "home" ? home : s === "away" ? away : s === "draw" ? "Empate" : s);
  const ou = (s: string, line: string) => `${s === "over" ? "Más de" : "Menos de"} ${line}`;
  switch (p[0]) {
    case "1X2": return selection === "draw" ? "Empate" : `Victoria ${side(selection)}`;
    case "DC": return { "1X": `${home} o empate`, "12": `${home} o ${away}`, X2: `Empate o ${away}` }[selection] ?? selection;
    case "BTTS": return selection === "yes" ? "Ambos equipos marcan: Sí" : "Ambos equipos marcan: No";
    case "OU": return `Goles totales: ${ou(selection, p[1])}`;
    case "CORNERS_TOTAL": return `Córners totales: ${ou(selection, p[1])}`;
    case "CORNERS_HOME": return `Córners ${home}: ${ou(selection, p[1])}`;
    case "CORNERS_AWAY": return `Córners ${away}: ${ou(selection, p[1])}`;
    case "CARDS_TOTAL": return `Tarjetas totales: ${ou(selection, p[1])}`;
    case "CARDS_HOME": return `Tarjetas ${home}: ${ou(selection, p[1])}`;
    case "CARDS_AWAY": return `Tarjetas ${away}: ${ou(selection, p[1])}`;
    case "HT_1X2": return `Descanso: ${selection === "draw" ? "Empate" : "Gana " + side(selection)}`;
    case "ML": return `Ganador: ${side(selection)}`;
    case "TOTAL": return `Puntos totales: ${ou(selection, p[1])}`;
    case "TEAM_TOTAL": return `Puntos ${side(p[1])}: ${ou(selection, p[2])}`;
    case "SPREAD": return `${home} ${Number(p[2]) > 0 ? "+" : ""}${p[2]} (hándicap): ${selection === "cover" ? "cubre" : "no cubre"}`;
    default: return `${market} ${selection}`;
  }
}

export const QUALITY_LABEL: Record<string, string> = { high: "Alta (≥20 partidos)", medium: "Media (10-19)", low: "Baja (5-9)", insufficient: "Insuficiente (<5)" };
export const UNCERTAINTY_LABEL: Record<string, string> = { low: "Baja", medium: "Media", high: "Alta" };
export const today = () => new Date().toISOString().slice(0, 10);
