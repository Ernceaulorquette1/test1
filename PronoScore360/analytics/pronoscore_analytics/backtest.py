"""Backtesting walk-forward sin fuga temporal.

Para cada partido de evaluación (cronológico) se entrena el modelo SOLO con
partidos anteriores a su fecha (reentrenando cada `refit_days`) y se registra la
probabilidad prevista antes de conocer el resultado.
"""
from __future__ import annotations

from collections import defaultdict
from datetime import datetime, timedelta
from typing import Sequence

import numpy as np

from . import basketball as bb
from . import football as fb
from . import metrics as M


def split_temporal(items: Sequence, key, cutoff: datetime):
    train = [i for i in items if key(i) < cutoff]
    test = [i for i in items if key(i) >= cutoff]
    return train, test


def _f(x):
    """NaN -> None para poder serializar a JSON."""
    return None if x is None or (isinstance(x, float) and x != x) else x


def _summarize(rows: dict[str, list[tuple[float, int]]]) -> dict:
    out = {}
    for market, pairs in rows.items():
        p = [a for a, _ in pairs]; y = [b for _, b in pairs]
        out[market] = {"n": len(pairs), "brier": _f(M.brier_score(p, y)), "log_loss": _f(M.log_loss(p, y)),
                       "hit_rate_p60": _f(M.hit_rate(p, y, 0.6)), "ece": _f(M.expected_calibration_error(p, y)),
                       "base_rate": _f(float(np.mean(y))) if y else None}
    return out


def backtest_football(matches: Sequence[fb.MatchRecord], test_start: datetime, refit_days: int = 7,
                      params: fb.FootballParams | None = None) -> dict:
    params = params or fb.FootballParams()
    matches = sorted(matches, key=lambda m: m.date)
    test = [m for m in matches if m.date >= test_start]
    rows: dict[str, list[tuple[float, int]]] = defaultdict(list)
    raw: list[dict] = []
    model, last_fit = None, None
    skipped = 0
    for m in test:
        if model is None or last_fit is None or (m.date - last_fit) >= timedelta(days=refit_days):
            model = fb.FootballModel(params=params).fit(matches, as_of=m.date); last_fit = m.date
        # el modelo entrenado en `last_fit` sólo conoce partidos < last_fit <= m.date  → sin fuga
        res = fb.predict_match(model, m.home, m.away)
        if res["status"] != "ok":
            skipped += 1; continue
        tot = m.home_goals + m.away_goals
        outcome = "home" if m.home_goals > m.away_goals else "draw" if m.home_goals == m.away_goals else "away"
        for k in ("home", "draw", "away"):
            rows[f"1X2:{k}"].append((res["markets"]["1X2"][k], int(outcome == k)))
        for line in fb.TOTAL_LINES:
            rows[f"OU:{line}:over"].append((res["markets"][f"OU:{line}"]["over"], int(tot > line)))
        rows["BTTS:yes"].append((res["markets"]["BTTS"]["yes"], int(m.home_goals > 0 and m.away_goals > 0)))
        raw.append({"date": m.date.isoformat(), "home": m.home, "away": m.away,
                    "p_home": res["markets"]["1X2"]["home"], "p_draw": res["markets"]["1X2"]["draw"],
                    "p_away": res["markets"]["1X2"]["away"], "outcome": outcome})
    # 1X2 multiclase (Brier multiclase) vs baseline de frecuencias de entrenamiento
    mc = _multiclass_brier(raw)
    return {"sport": "football", "n_test": len(test), "n_evaluated": len(raw), "n_skipped_insufficient": skipped,
            "markets": _summarize(rows), "multiclass_brier_1x2": mc, "predictions": raw}


def _multiclass_brier(raw: list[dict]) -> float:
    if not raw:
        return None
    tot = 0.0
    for r in raw:
        for k, key in (("home", "p_home"), ("draw", "p_draw"), ("away", "p_away")):
            tot += (r[key] - (1.0 if r["outcome"] == k else 0.0)) ** 2
    return tot / len(raw)


def backtest_basketball(games: Sequence[bb.GameRecord], test_start: datetime, refit_days: int = 3,
                        params: bb.BasketballParams | None = None) -> dict:
    params = params or bb.BasketballParams(n_sims=4000)
    games = sorted(games, key=lambda g: g.date)
    test = [g for g in games if g.date >= test_start]
    rows: dict[str, list[tuple[float, int]]] = defaultdict(list)
    model, last_fit, skipped, mae_total = None, None, 0, []
    for g in test:
        if model is None or last_fit is None or (g.date - last_fit) >= timedelta(days=refit_days):
            model = bb.BasketballModel(params=params).fit(games, as_of=g.date); last_fit = g.date
        res = bb.predict_game(model, g.home, g.away, g.date)
        if res["status"] != "ok":
            skipped += 1; continue
        s = res["sim"]
        rows["ML:home"].append((s["p_home_win"], int(g.home_pts > g.away_pts)))
        line = round(s["expected_total"] * 2) / 2 + 0.5 * (round(s["expected_total"] * 2) / 2 % 1 == 0)
        h, a = bb.samples_from_params(res["params"])
        rows["TOTAL:over(centered)"].append((float(np.mean(h + a > line)), int(g.home_pts + g.away_pts > line)))
        mae_total.append(abs(s["expected_total"] - (g.home_pts + g.away_pts)))
    out = {"sport": "basketball", "n_test": len(test), "n_evaluated": len(rows.get("ML:home", [])),
           "n_skipped_insufficient": skipped, "markets": _summarize(rows)}
    out["mae_total_points"] = float(np.mean(mae_total)) if mae_total else None
    return out
