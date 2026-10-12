"""Generador sintético reproducible SOLO para pruebas del motor (no son datos reales)."""
from datetime import datetime, timedelta
import numpy as np
from pronoscore_analytics.football import MatchRecord
from pronoscore_analytics.basketball import GameRecord


def football_league(n_teams=8, rounds=6, seed=1, start=datetime(2025, 8, 1)):
    rng = np.random.default_rng(seed)
    teams = [f"T{i}" for i in range(n_teams)]
    att = {t: np.exp(rng.normal(0, 0.25)) for t in teams}
    dfn = {t: np.exp(rng.normal(0, 0.25)) for t in teams}
    out, day = [], 0
    for _ in range(rounds):
        for h in teams:
            for a in teams:
                if h == a:
                    continue
                lh = 1.35 * 1.25 * att[h] * dfn[a]; la = 1.35 * att[a] * dfn[h]
                hg, ag = rng.poisson(lh), rng.poisson(la)
                hc, ac = rng.poisson(5.5 * att[h]), rng.poisson(4.5 * att[a])
                out.append(MatchRecord(start + timedelta(days=day // 4), h, a, int(hg), int(ag),
                                       int(hc), int(ac), int(rng.poisson(2)), int(rng.poisson(2))))
                day += 1
    return out, att, dfn


def basketball_league(n_teams=8, rounds=6, seed=2, start=datetime(2025, 10, 20)):
    rng = np.random.default_rng(seed)
    teams = [f"B{i}" for i in range(n_teams)]
    off = {t: 112 + rng.normal(0, 3) for t in teams}; dfn = {t: 112 + rng.normal(0, 3) for t in teams}
    out, day = [], 0
    for _ in range(rounds):
        for h in teams:
            for a in teams:
                if h == a:
                    continue
                pace = rng.normal(99, 3)
                eh = pace * (off[h] + dfn[a] - 112) / 100 + 1.3; ea = pace * (off[a] + dfn[h] - 112) / 100 - 1.3
                out.append(GameRecord(start + timedelta(days=day // 4), h, a, int(rng.normal(eh, 11)),
                                      int(rng.normal(ea, 11)), pace, pace))
                day += 1
    return out
