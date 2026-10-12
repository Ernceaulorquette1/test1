"""Modelo de baloncesto/NBA basado en eficiencia por posesión + simulación Monte Carlo.

  posesiones ≈ FGA + 0.44*FTA - OREB + TOV       (si hay box score)
  ORtg = 100 * puntos / posesiones ; DRtg = puntos recibidos por 100 posesiones

Los ratings se estiman con ponderación temporal y contracción. Ajustes por
localía y descanso (back-to-back) son parámetros explícitos del modelo (heurísticos,
documentados; recalibrables con backtesting).
Sólo se usan partidos con fecha < as_of.
"""
from __future__ import annotations

import math
from dataclasses import dataclass, field
from datetime import datetime
from typing import Iterable, Optional

import numpy as np

from . import quality as Q

VERSION = "1.0.0"


@dataclass(frozen=True)
class GameRecord:
    date: datetime
    home: str
    away: str
    home_pts: int
    away_pts: int
    home_poss: Optional[float] = None   # posesiones (propias/estimadas), opcional
    away_poss: Optional[float] = None


def estimate_possessions(fga: float, fta: float, oreb: float, tov: float) -> float:
    return fga + 0.44 * fta - oreb + tov


@dataclass
class BasketballParams:
    half_life_days: float = 60.0
    shrinkage: float = 4.0            # partidos "virtuales" hacia la media
    home_adv_points: float = 2.6      # se re-estima de los datos y se contrae hacia este prior
    b2b_penalty_points: float = 2.0   # penalización por back-to-back (heurística documentada)
    rest_bonus_points: float = 0.5    # 3+ días de descanso vs 1
    default_pace: float = 99.0
    n_sims: int = 20000
    seed: int = 360
    min_games: int = Q.MIN_MATCHES


@dataclass
class TeamRating:
    off: float = 110.0
    dfn: float = 110.0
    pace: float = 99.0
    n_games: int = 0


@dataclass
class BasketballModel:
    params: BasketballParams = field(default_factory=BasketballParams)
    teams: dict[str, TeamRating] = field(default_factory=dict)
    league_rating: float = 110.0
    league_pace: float = 99.0
    home_adv: float = 2.6
    sd_points: float = 11.0
    sd_pace: float = 3.0
    n_training: int = 0
    as_of: Optional[datetime] = None
    pace_from_data: bool = False
    _games: list[GameRecord] = field(default_factory=list)
    _last_game: dict[str, datetime] = field(default_factory=dict)

    def fit(self, games: Iterable[GameRecord], as_of: datetime) -> "BasketballModel":
        p = self.params
        gs = sorted((g for g in games if g.date < as_of), key=lambda g: g.date)
        self._games, self.as_of, self.n_training, self.teams = gs, as_of, len(gs), {}
        self._last_game = {}
        if not gs:
            return self
        decay = math.log(2) / p.half_life_days
        w = np.array([math.exp(-decay * max((as_of - g.date).total_seconds() / 86400, 0)) for g in gs])
        have_poss = all(g.home_poss and g.away_poss for g in gs)
        self.pace_from_data = have_poss
        poss = np.array([((g.home_poss + g.away_poss) / 2) if have_poss else p.default_pace for g in gs])
        hp = np.array([g.home_pts for g in gs], float); ap = np.array([g.away_pts for g in gs], float)
        self.league_pace = float((w * poss).sum() / w.sum())
        self.league_rating = float((w * (hp + ap) / 2 / poss * 100).sum() / w.sum())
        names = sorted({t for g in gs for t in (g.home, g.away)})
        n_g = {t: 0 for t in names}
        off_s = {t: 0.0 for t in names}; def_s = {t: 0.0 for t in names}
        pace_s = {t: 0.0 for t in names}; w_s = {t: 0.0 for t in names}
        # diferencia de rating observada de la localía (en puntos por 100 posesiones)
        ha_num = ha_den = 0.0
        for wi, g, ps in zip(w, gs, poss):
            ortg_h = g.home_pts / ps * 100; ortg_a = g.away_pts / ps * 100
            ha_num += wi * (ortg_h - ortg_a) * ps / 100; ha_den += wi
            for t, o, d in ((g.home, ortg_h, ortg_a), (g.away, ortg_a, ortg_h)):
                off_s[t] += wi * o; def_s[t] += wi * d; pace_s[t] += wi * ps; w_s[t] += wi; n_g[t] += 1
            self._last_game[g.home] = g.date; self._last_game[g.away] = g.date
        obs_ha = ha_num / ha_den
        k_ha = 40.0
        self.home_adv = (obs_ha * ha_den + p.home_adv_points * k_ha) / (ha_den + k_ha)
        k = p.shrinkage
        for t in names:
            off = (off_s[t] + k * self.league_rating) / (w_s[t] + k)
            dfn = (def_s[t] + k * self.league_rating) / (w_s[t] + k)
            pace = (pace_s[t] + k * self.league_pace) / (w_s[t] + k)
            self.teams[t] = TeamRating(off, dfn, pace, n_g[t])
        # dispersión residual de los puntos (por equipo) respecto al esperado simple
        resid = []
        for g, ps in zip(gs, poss):
            th, ta = self.teams[g.home], self.teams[g.away]
            e_h = ps * (th.off + ta.dfn - self.league_rating) / 100 + self.home_adv / 2
            e_a = ps * (ta.off + th.dfn - self.league_rating) / 100 - self.home_adv / 2
            resid.extend([g.home_pts - e_h, g.away_pts - e_a])
        self.sd_points = float(np.clip(np.std(resid, ddof=1), 7.0, 16.0)) if len(resid) > 10 else 11.0
        if have_poss:
            self.sd_pace = float(np.clip(np.std(poss, ddof=1), 1.5, 6.0)) if len(poss) > 5 else 3.0
        return self

    def n_games(self, team: str) -> int:
        return self.teams[team].n_games if team in self.teams else 0

    def rest_days(self, team: str, game_date: datetime) -> Optional[int]:
        last = self._last_game.get(team)
        return max((game_date.date() - last.date()).days, 0) if last else None


def _rest_adjust(rest: Optional[int], p: BasketballParams) -> float:
    if rest is None:
        return 0.0
    if rest <= 1:
        return -p.b2b_penalty_points
    if rest >= 3:
        return p.rest_bonus_points
    return 0.0


def simulate(mean_h: float, mean_a: float, sd_pts: float, pace_rel_sd: float, n: int, seed: int,
             team_corr_pace: float = 1.0) -> tuple[np.ndarray, np.ndarray]:
    """Muestras de puntos (local, visitante) con ritmo compartido (correlación positiva)."""
    rng = np.random.default_rng(seed)
    pace_factor = rng.normal(1.0, pace_rel_sd, n) * team_corr_pace + (1 - team_corr_pace)
    zh = rng.normal(0.0, sd_pts, n); za = rng.normal(0.0, sd_pts, n)
    rh, ra = mean_h * pace_factor + zh, mean_a * pace_factor + za
    h, a = np.rint(rh), np.rint(ra)
    tie = h == a            # la NBA no tiene empates: se resuelven (prórroga) a favor de quien iba por delante sin redondeo
    h = np.where(tie & (rh >= ra), h + 1, h)
    a = np.where(tie & (rh < ra), a + 1, a)
    return h, a


def predict_game(model: BasketballModel, home: str, away: str, game_date: datetime,
                 total_lines: tuple[float, ...] = (), spreads: tuple[float, ...] = ()) -> dict:
    p = model.params
    nh, na = model.n_games(home), model.n_games(away)
    q = Q.data_quality(nh, na)
    base = {"model": "basketball_efficiency_mc", "version": VERSION, "n_home": nh, "n_away": na,
            "n_training": model.n_training, "quality": q,
            "as_of": model.as_of.isoformat() if model.as_of else None}
    if q == "insufficient":
        return {**base, "status": "insufficient_data",
                "reason": f"Se requieren al menos {Q.MIN_MATCHES} partidos previos por equipo "
                          f"(local={nh}, visitante={na})."}
    th, ta = model.teams[home], model.teams[away]
    pace = 0.5 * (th.pace + ta.pace)
    rest_h, rest_a = model.rest_days(home, game_date), model.rest_days(away, game_date)
    adj_h, adj_a = _rest_adjust(rest_h, p), _rest_adjust(rest_a, p)
    lg = model.league_rating
    ortg_h = th.off + ta.dfn - lg
    ortg_a = ta.off + th.dfn - lg
    mean_h = pace * ortg_h / 100 + model.home_adv / 2 + adj_h
    mean_a = pace * ortg_a / 100 - model.home_adv / 2 + adj_a
    pace_rel_sd = model.sd_pace / max(model.league_pace, 1.0) if model.pace_from_data else 0.03
    sd = model.sd_points
    h, a = simulate(mean_h, mean_a, sd, pace_rel_sd, p.n_sims, p.seed)
    return {**base, "status": "ok", "sim": summarize(h, a, mean_h, mean_a),
            "params": {"mean_home": mean_h, "mean_away": mean_a, "sd_points": sd, "pace_rel_sd": pace_rel_sd,
                       "n_sims": p.n_sims, "seed": p.seed},
            "inputs": {"pace": pace, "ortg_home": th.off, "drtg_home": th.dfn, "ortg_away": ta.off,
                       "drtg_away": ta.dfn, "league_rating": lg, "home_adv": model.home_adv,
                       "rest_days_home": rest_h, "rest_days_away": rest_a,
                       "rest_adj_home": adj_h, "rest_adj_away": adj_a,
                       "pace_source": "datos" if model.pace_from_data else "estimado (valor por defecto)"},
            "drivers": [
                {"factor": "Ritmo esperado (posesiones)", "value": round(pace, 1)},
                {"factor": "Ofensiva local (ORtg)", "value": round(th.off, 1)},
                {"factor": "Defensa local (DRtg)", "value": round(th.dfn, 1)},
                {"factor": "Ofensiva visitante (ORtg)", "value": round(ta.off, 1)},
                {"factor": "Defensa visitante (DRtg)", "value": round(ta.dfn, 1)},
                {"factor": "Ventaja de localía (pts)", "value": round(model.home_adv, 2)},
                {"factor": "Descanso local/visitante (días)", "value": f"{rest_h}/{rest_a}"},
            ],
            "explanation": (f"Puntos esperados {mean_h:.1f} - {mean_a:.1f} a partir de eficiencia por posesión, "
                            f"ritmo {pace:.1f}, localía y descanso; {p.n_sims} simulaciones Monte Carlo. "
                            f"El marcador central es una estimación, no un marcador con probabilidad elevada.")}


def summarize(h: np.ndarray, a: np.ndarray, mean_h: float, mean_a: float) -> dict:
    margin = h - a; total = h + a
    center_h, center_a = float(np.median(h)), float(np.median(a))
    # probabilidad de acertar EXACTAMENTE el marcador central (honestamente baja)
    p_exact = float(np.mean((h == round(center_h)) & (a == round(center_a))))
    return {
        "p_home_win": float(np.mean(margin > 0)), "p_away_win": float(np.mean(margin < 0)),  # NBA: sin empates
        "expected_home": float(h.mean()), "expected_away": float(a.mean()),
        "expected_margin": float(margin.mean()), "expected_total": float(total.mean()),
        "central_score": {"home": round(center_h), "away": round(center_a),
                          "exact_hit_probability": p_exact,
                          "note": "Estimación central; la probabilidad de acertar el marcador exacto es la indicada."},
        "range_home_80": [float(np.percentile(h, 10)), float(np.percentile(h, 90))],
        "range_away_80": [float(np.percentile(a, 10)), float(np.percentile(a, 90))],
        "range_total_80": [float(np.percentile(total, 10)), float(np.percentile(total, 90))],
        "margin_sd": float(margin.std()), "total_sd": float(total.std()),
        "total_distribution": _hist(total, 5), "margin_distribution": _hist(margin, 3),
    }


def _hist(x: np.ndarray, width: int) -> list[dict]:
    lo = int(np.floor(x.min() / width) * width); hi = int(np.ceil(x.max() / width) * width)
    edges = np.arange(lo, hi + width, width)
    counts, _ = np.histogram(x, bins=edges)
    return [{"from": int(e), "to": int(e + width), "probability": float(c / len(x))}
            for e, c in zip(edges[:-1], counts) if c > 0]


# ---- mercados bajo demanda sobre las muestras (determinista por semilla) ----

def samples_from_params(params: dict) -> tuple[np.ndarray, np.ndarray]:
    return simulate(params["mean_home"], params["mean_away"], params["sd_points"],
                    params["pace_rel_sd"], params["n_sims"], params["seed"])


def market_probability(h: np.ndarray, a: np.ndarray, market: str) -> Optional[float]:
    """Mercados: 'ML:home|away', 'TOTAL:<line>:over|under', 'TEAM_TOTAL:home|away:<line>:over|under',
    'SPREAD:home|away:<handicap>' (handicap aplicado al equipo, p. ej. SPREAD:home:-5.5 = local cubre -5.5)."""
    mask = market_mask(h, a, market)
    return None if mask is None else float(np.mean(mask))


def market_mask(h: np.ndarray, a: np.ndarray, market: str) -> Optional[np.ndarray]:
    parts = market.split(":")
    try:
        if parts[0] == "ML":
            return (h > a) if parts[1] == "home" else (a > h)
        if parts[0] == "TOTAL":
            line = float(parts[1]); t = h + a
            return t > line if parts[2] == "over" else t < line
        if parts[0] == "TEAM_TOTAL":
            line = float(parts[2]); v = h if parts[1] == "home" else a
            return v > line if parts[3] == "over" else v < line
        if parts[0] == "SPREAD":
            hcap = float(parts[2]); m = (h - a) if parts[1] == "home" else (a - h)
            return (m + hcap) > 0
    except (IndexError, ValueError):
        return None
    return None


def half_line(x: float) -> float:
    return math.floor(x) + 0.5


def full_markets(h: np.ndarray, a: np.ndarray, mean_total: float) -> dict[str, dict[str, float]]:
    """Líneas por defecto centradas en el valor esperado (las líneas personalizadas se piden por API)."""
    out: dict[str, dict[str, float]] = {"ML": {"home": float(np.mean(h > a)), "away": float(np.mean(a > h))}}
    c = half_line(mean_total)
    for d in (-6, -3, 0, 3, 6):
        pov = float(np.mean((h + a) > c + d))
        out[f"TOTAL:{c + d}"] = {"over": pov, "under": 1 - pov}
    for team, arr in (("home", h), ("away", a)):
        tc = half_line(float(arr.mean()))
        for d in (-4, 0, 4):
            pov = float(np.mean(arr > tc + d))
            out[f"TEAM_TOTAL:{team}:{tc + d}"] = {"over": pov, "under": 1 - pov}
    m = h - a
    for hc in (-9.5, -6.5, -3.5, -0.5, 3.5, 6.5, 9.5):
        pc = float(np.mean((m + hc) > 0))
        out[f"SPREAD:home:{hc}"] = {"cover": pc, "no_cover": 1 - pc}
    return out
