"""Modelo de fútbol: Poisson bivariado con corrección Dixon-Coles.

Parámetros por equipo (ataque a_i y defensa d_i) y localía h se estiman por
mínimos de verosimilitud mediante iteraciones multiplicativas (MLE de Poisson)
con ponderación temporal exponencial y contracción (shrinkage) hacia 1.

    lambda_local     = mu_h * h * a_local * d_visitante
    lambda_visitante = mu_a *     a_visit * d_local

Sólo se usan partidos con fecha ESTRICTAMENTE anterior a `as_of` (sin fuga de
información posterior).
"""
from __future__ import annotations

import math
from dataclasses import dataclass, field
from datetime import datetime
from typing import Iterable, Optional

import numpy as np
from scipy import stats

from . import quality as Q

MAX_GOALS = 10
VERSION = "1.0.0"


@dataclass(frozen=True)
class MatchRecord:
    date: datetime
    home: str
    away: str
    home_goals: int
    away_goals: int
    home_corners: Optional[int] = None
    away_corners: Optional[int] = None
    home_cards: Optional[int] = None   # amarillas + 2*rojas ponderadas lo decide el proveedor; aquí "tarjetas totales"
    away_cards: Optional[int] = None
    ht_home_goals: Optional[int] = None
    ht_away_goals: Optional[int] = None


@dataclass
class TeamStrength:
    attack: float = 1.0
    defence: float = 1.0
    n_matches: int = 0
    n_home: int = 0
    n_away: int = 0
    weighted_n: float = 0.0


@dataclass
class FootballParams:
    half_life_days: float = 180.0
    shrinkage: float = 3.0           # pseudo-goles hacia 1.0
    rho: float = -0.08               # parámetro Dixon-Coles (típico negativo)
    iterations: int = 60
    min_matches: int = Q.MIN_MATCHES


def dc_tau(x: int, y: int, lam: float, mu: float, rho: float) -> float:
    if x == 0 and y == 0:
        return 1.0 - lam * mu * rho
    if x == 0 and y == 1:
        return 1.0 + lam * rho
    if x == 1 and y == 0:
        return 1.0 + mu * rho
    if x == 1 and y == 1:
        return 1.0 - rho
    return 1.0


def score_matrix(lam_h: float, lam_a: float, rho: float = 0.0, max_goals: int = MAX_GOALS) -> np.ndarray:
    """Matriz P[i, j] = P(local=i, visitante=j) con corrección DC, renormalizada."""
    k = np.arange(max_goals + 1)
    ph = stats.poisson.pmf(k, lam_h)
    pa = stats.poisson.pmf(k, lam_a)
    m = np.outer(ph, pa)
    if rho:
        for x in (0, 1):
            for y in (0, 1):
                m[x, y] *= max(dc_tau(x, y, lam_h, lam_a, rho), 0.0)
    return m / m.sum()


# ------------------------- mercados desde la matriz -------------------------

def market_1x2(m: np.ndarray) -> dict[str, float]:
    return {
        "home": float(np.tril(m, -1).sum()),
        "draw": float(np.trace(m)),
        "away": float(np.triu(m, 1).sum()),
    }


def market_double_chance(p: dict[str, float]) -> dict[str, float]:
    return {"1X": p["home"] + p["draw"], "12": p["home"] + p["away"], "X2": p["draw"] + p["away"]}


def total_goals_dist(m: np.ndarray) -> np.ndarray:
    n = m.shape[0]
    tot = np.zeros(2 * n - 1)
    for i in range(n):
        for j in range(n):
            tot[i + j] += m[i, j]
    return tot


def market_over_under(m: np.ndarray, line: float) -> dict[str, float]:
    tot = total_goals_dist(m)
    over = float(sum(p for g, p in enumerate(tot) if g > line))
    return {"over": over, "under": 1.0 - over}


def market_btts(m: np.ndarray) -> dict[str, float]:
    no = float(m[0, :].sum() + m[:, 0].sum() - m[0, 0])
    return {"yes": 1.0 - no, "no": no}


def top_scores(m: np.ndarray, k: int = 5) -> list[dict]:
    idx = np.dstack(np.unravel_index(np.argsort(-m, axis=None), m.shape))[0][:k]
    return [{"home": int(i), "away": int(j), "probability": float(m[i, j])} for i, j in idx]


# --------------------- conteos (córners/tarjetas): binomial negativa ---------

def nb_pmf(mean: float, dispersion_var_ratio: float, size: int = 40) -> np.ndarray:
    """PMF binomial negativa con media y razón varianza/media (>=1). Si ~1 usa Poisson."""
    k = np.arange(size + 1)
    if dispersion_var_ratio <= 1.0001 or mean <= 0:
        pmf = stats.poisson.pmf(k, max(mean, 1e-9))
    else:
        var = mean * dispersion_var_ratio
        r = mean * mean / (var - mean)
        p = r / (r + mean)
        pmf = stats.nbinom.pmf(k, r, p)
    return pmf / pmf.sum()


def count_over_under(pmf: np.ndarray, line: float) -> dict[str, float]:
    over = float(sum(p for k, p in enumerate(pmf) if k > line))
    return {"over": over, "under": 1.0 - over}


def convolve(p1: np.ndarray, p2: np.ndarray) -> np.ndarray:
    return np.convolve(p1, p2)


# ------------------------------- modelo --------------------------------------

@dataclass
class FootballModel:
    params: FootballParams = field(default_factory=FootballParams)
    teams: dict[str, TeamStrength] = field(default_factory=dict)
    mu_home: float = 1.4
    mu_away: float = 1.1
    home_adv: float = 1.0
    n_training: int = 0
    as_of: Optional[datetime] = None
    _matches: list[MatchRecord] = field(default_factory=list)
    _weights: list[float] = field(default_factory=list)

    def fit(self, matches: Iterable[MatchRecord], as_of: datetime) -> "FootballModel":
        p = self.params
        ms = [m for m in matches if m.date < as_of]          # SIN fuga de información
        ms.sort(key=lambda m: m.date)
        self._matches = ms
        self.as_of = as_of
        self.n_training = len(ms)
        self.teams = {}
        if not ms:
            return self
        decay = math.log(2) / p.half_life_days
        w = [math.exp(-decay * max((as_of - m.date).total_seconds() / 86400.0, 0.0)) for m in ms]
        self._weights = w
        names = {t for m in ms for t in (m.home, m.away)}
        att = {t: 1.0 for t in names}
        dfn = {t: 1.0 for t in names}
        sw = sum(w)
        mu_h = sum(wi * m.home_goals for wi, m in zip(w, ms)) / sw
        mu_a = sum(wi * m.away_goals for wi, m in zip(w, ms)) / sw
        mu_h, mu_a = max(mu_h, 0.2), max(mu_a, 0.2)
        h = mu_h / mu_a
        base_a = mu_a  # goles visitante base; local = base_a * h
        k = p.shrinkage
        for _ in range(p.iterations):
            # ataque
            gf = {t: 0.0 for t in names}; ef = {t: 0.0 for t in names}
            ga = {t: 0.0 for t in names}; ea = {t: 0.0 for t in names}
            for wi, m in zip(w, ms):
                exp_h_unit = base_a * h * dfn[m.away]       # esperado local por unidad de ataque
                exp_a_unit = base_a * dfn[m.home]
                gf[m.home] += wi * m.home_goals; ef[m.home] += wi * exp_h_unit
                gf[m.away] += wi * m.away_goals; ef[m.away] += wi * exp_a_unit
            for t in names:
                att[t] = (gf[t] + k) / (ef[t] + k)
            for wi, m in zip(w, ms):
                exp_h_unit = base_a * h * att[m.home]       # esperado local por unidad de defensa rival
                exp_a_unit = base_a * att[m.away]
                ga[m.away] += wi * m.home_goals; ea[m.away] += wi * exp_h_unit
                ga[m.home] += wi * m.away_goals; ea[m.home] += wi * exp_a_unit
            for t in names:
                dfn[t] = (ga[t] + k) / (ea[t] + k)
            # normaliza para identificabilidad (media geométrica 1)
            ga_ = math.exp(sum(math.log(att[t]) for t in names) / len(names))
            gd_ = math.exp(sum(math.log(dfn[t]) for t in names) / len(names))
            for t in names:
                att[t] /= ga_; dfn[t] /= gd_
            base_a *= ga_ * gd_
            # localía
            num = sum(wi * m.home_goals for wi, m in zip(w, ms))
            den = sum(wi * base_a * att[m.home] * dfn[m.away] for wi, m in zip(w, ms))
            h = num / den if den > 0 else h
        self.mu_home = base_a * h
        self.mu_away = base_a
        self.home_adv = h
        for t in names:
            n_h = sum(1 for m in ms if m.home == t); n_a = sum(1 for m in ms if m.away == t)
            self.teams[t] = TeamStrength(att[t], dfn[t], n_h + n_a, n_h, n_a,
                                         sum(wi for wi, m in zip(w, ms) if t in (m.home, m.away)))
        return self

    # ---- consulta ----
    def n_matches(self, team: str) -> int:
        return self.teams[team].n_matches if team in self.teams else 0

    def expected_goals(self, home: str, away: str) -> tuple[float, float]:
        th = self.teams.get(home, TeamStrength()); ta = self.teams.get(away, TeamStrength())
        lam_h = self.mu_away * self.home_adv * th.attack * ta.defence
        lam_a = self.mu_away * ta.attack * th.defence
        return lam_h, lam_a

    def _count_stats(self, team: str, kind: str, home_attr: str, away_attr: str):
        """(promedio a favor, promedio en contra, n) ponderado con decaimiento."""
        f = a = sw = 0.0; n = 0
        for wi, m in zip(self._weights, self._matches):
            hv, av = getattr(m, home_attr), getattr(m, away_attr)
            if hv is None or av is None:
                continue
            if m.home == team:
                f += wi * hv; a += wi * av; sw += wi; n += 1
            elif m.away == team:
                f += wi * av; a += wi * hv; sw += wi; n += 1
        return (f / sw, a / sw, n) if sw > 0 else (None, None, 0)

    def _league_mean(self, home_attr: str, away_attr: str):
        vals = [(getattr(m, home_attr), getattr(m, away_attr)) for m in self._matches
                if getattr(m, home_attr) is not None and getattr(m, away_attr) is not None]
        if not vals:
            return None, None, 0
        arr = np.array(vals, dtype=float)
        tot = arr.sum(axis=1)
        return float(arr.mean()), float(tot.var(ddof=1) / max(tot.mean(), 1e-9)) if len(tot) > 2 else 1.0, len(vals)

    def count_market(self, home: str, away: str, home_attr: str, away_attr: str,
                     min_n: int = 8) -> Optional[dict]:
        """Distribución de conteo por equipo (corners/tarjetas) si hay datos suficientes."""
        lg_mean, ratio, n_lg = self._league_mean(home_attr, away_attr)
        if lg_mean is None:
            return None
        fh, ah, nh = self._count_stats(home, "", home_attr, away_attr)
        fa, aa, na = self._count_stats(away, "", home_attr, away_attr)
        if nh < min_n or na < min_n:
            return None
        # media esperada del equipo = promedio de (lo que genera él, lo que concede el rival)
        mh = 0.5 * (fh + aa); ma = 0.5 * (fa + ah)
        ratio = max(ratio, 1.0)
        pmf_h = nb_pmf(mh, max(ratio / 2.0 + 0.5, 1.0))     # dispersión por equipo ≈ mitad de la del total
        pmf_a = nb_pmf(ma, max(ratio / 2.0 + 0.5, 1.0))
        return {"home_mean": mh, "away_mean": ma, "pmf_home": pmf_h, "pmf_away": pmf_a,
                "pmf_total": convolve(pmf_h, pmf_a), "n_home": nh, "n_away": na, "n_league": n_lg}

    def first_half_fraction(self, min_n: int = 30) -> Optional[float]:
        tot = ht = 0; n = 0
        for m in self._matches:
            if m.ht_home_goals is None or m.ht_away_goals is None:
                continue
            tot += m.home_goals + m.away_goals; ht += m.ht_home_goals + m.ht_away_goals; n += 1
        return ht / tot if n >= min_n and tot > 0 else None


# --------------------------- predicción completa -----------------------------

TOTAL_LINES = (0.5, 1.5, 2.5, 3.5, 4.5)
CORNER_TOTAL_LINES = (8.5, 9.5, 10.5, 11.5)
CORNER_TEAM_LINES = (3.5, 4.5, 5.5)
CARD_TOTAL_LINES = (2.5, 3.5, 4.5, 5.5)
CARD_TEAM_LINES = (0.5, 1.5, 2.5)


def predict_match(model: FootballModel, home: str, away: str) -> dict:
    """Devuelve el análisis completo o {'status': 'insufficient_data', ...}."""
    nh, na = model.n_matches(home), model.n_matches(away)
    q = Q.data_quality(nh, na)
    base = {"model": "football_poisson_dc", "version": VERSION,
            "n_home": nh, "n_away": na, "n_training": model.n_training, "quality": q,
            "as_of": model.as_of.isoformat() if model.as_of else None}
    if q == "insufficient":
        return {**base, "status": "insufficient_data",
                "reason": f"Se requieren al menos {Q.MIN_MATCHES} partidos previos por equipo "
                          f"(local={nh}, visitante={na})."}
    lam_h, lam_a = model.expected_goals(home, away)
    rho = model.params.rho
    m = score_matrix(lam_h, lam_a, rho)
    p1x2 = market_1x2(m)
    markets: dict[str, dict[str, float]] = {"1X2": p1x2, "DC": market_double_chance(p1x2), "BTTS": market_btts(m)}
    for line in TOTAL_LINES:
        markets[f"OU:{line}"] = market_over_under(m, line)

    extras: dict = {}
    corners = model.count_market(home, away, "home_corners", "away_corners")
    if corners:
        for line in CORNER_TOTAL_LINES:
            markets[f"CORNERS_TOTAL:{line}"] = count_over_under(corners["pmf_total"], line)
        for line in CORNER_TEAM_LINES:
            markets[f"CORNERS_HOME:{line}"] = count_over_under(corners["pmf_home"], line)
            markets[f"CORNERS_AWAY:{line}"] = count_over_under(corners["pmf_away"], line)
        extras["corners"] = {"home_mean": corners["home_mean"], "away_mean": corners["away_mean"],
                             "n": min(corners["n_home"], corners["n_away"])}
    cards = model.count_market(home, away, "home_cards", "away_cards")
    if cards:
        for line in CARD_TOTAL_LINES:
            markets[f"CARDS_TOTAL:{line}"] = count_over_under(cards["pmf_total"], line)
        for line in CARD_TEAM_LINES:
            markets[f"CARDS_HOME:{line}"] = count_over_under(cards["pmf_home"], line)
            markets[f"CARDS_AWAY:{line}"] = count_over_under(cards["pmf_away"], line)
        extras["cards"] = {"home_mean": cards["home_mean"], "away_mean": cards["away_mean"],
                           "n": min(cards["n_home"], cards["n_away"])}
    frac = model.first_half_fraction()
    if frac:
        mh = score_matrix(lam_h * frac, lam_a * frac, rho)
        markets["HT_1X2"] = market_1x2(mh)
        extras["first_half_fraction"] = frac

    th, ta = model.teams[home], model.teams[away]
    drivers = [
        {"factor": "Ataque local", "value": round(th.attack, 3), "note": ">1 = mejor que la media de la liga"},
        {"factor": "Defensa local", "value": round(th.defence, 3), "note": "<1 = concede menos que la media"},
        {"factor": "Ataque visitante", "value": round(ta.attack, 3), "note": ">1 = mejor que la media"},
        {"factor": "Defensa visitante", "value": round(ta.defence, 3), "note": "<1 = concede menos que la media"},
        {"factor": "Ventaja de localía", "value": round(model.home_adv, 3), "note": "factor multiplicativo de goles del local"},
    ]
    return {**base, "status": "ok", "lambda_home": lam_h, "lambda_away": lam_a, "rho": rho,
            "markets": markets, "top_scores": top_scores(m, 5),
            "exact_score_most_likely": top_scores(m, 1)[0], "drivers": drivers, "extras": extras,
            "explanation": (f"Goles esperados: {lam_h:.2f} (local) y {lam_a:.2f} (visitante), estimados con un "
                            f"modelo Poisson con corrección Dixon-Coles sobre {model.n_training} partidos previos "
                            f"(ponderados por recencia, semivida {model.params.half_life_days:.0f} días).")}
