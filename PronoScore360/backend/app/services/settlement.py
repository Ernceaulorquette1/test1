"""Resolución de pronósticos contra resultados y métricas de evaluación por modelo/mercado."""
from __future__ import annotations

from collections import defaultdict
from datetime import datetime, timezone
from typing import Optional

from pronoscore_analytics import metrics as M
from sqlalchemy import delete, select
from sqlalchemy.orm import Session

from ..db.models import (Fixture, FixtureAnalysis, HistoricalResult, Prediction, PredictionEvaluation, PredictionOutcome)


def evaluate(sport: str, market: str, selection: str, hs: int, as_: int, extra: dict) -> Optional[bool]:
    """True/False si se puede resolver con los datos disponibles; None si faltan datos."""
    p = market.split(":")
    try:
        if sport == "football":
            if market == "1X2":
                return {"home": hs > as_, "draw": hs == as_, "away": hs < as_}[selection]
            if market == "DC":
                return {"1X": hs >= as_, "12": hs != as_, "X2": hs <= as_}[selection]
            if market == "BTTS":
                both = hs > 0 and as_ > 0
                return both if selection == "yes" else not both
            if p[0] == "OU":
                v = (hs + as_ > float(p[1])); return v if selection == "over" else not v
            if market == "HT_1X2":
                h, a = extra.get("ht_home"), extra.get("ht_away")
                if h is None or a is None:
                    return None
                return {"home": h > a, "draw": h == a, "away": h < a}[selection]
            if p[0] in ("CORNERS_TOTAL", "CORNERS_HOME", "CORNERS_AWAY", "CARDS_TOTAL", "CARDS_HOME", "CARDS_AWAY"):
                kind = "corners" if p[0].startswith("CORNERS") else "cards"
                h, a = extra.get(f"home_{kind}"), extra.get(f"away_{kind}")
                if h is None or a is None:
                    return None
                val = {"TOTAL": h + a, "HOME": h, "AWAY": a}[p[0].split("_")[1]]
                v = val > float(p[1]); return v if selection == "over" else not v
            return None
        # baloncesto
        if market == "ML":
            return (hs > as_) if selection == "home" else (as_ > hs)
        if p[0] == "TOTAL":
            v = hs + as_ > float(p[1]); return v if selection == "over" else not v
        if p[0] == "TEAM_TOTAL":
            val = hs if p[1] == "home" else as_
            v = val > float(p[2]); return v if selection == "over" else not v
        if p[0] == "SPREAD":
            v = (hs - as_) + float(p[2]) > 0
            return v if selection == "cover" else not v
    except (KeyError, IndexError, ValueError):
        return None
    return None


def settle_fixture(db: Session, fixture: Fixture) -> int:
    """Crea PredictionOutcome sólo para pronósticos PREVIOS al partido (is_pre_match=True)."""
    if fixture.status != "finished" or fixture.home_score is None:
        return 0
    hist = db.scalar(select(HistoricalResult).where(HistoricalResult.fixture_id == fixture.id))
    extra = hist.extra if hist else {}
    sport = fixture.league.sport.code
    done = set(db.scalars(select(PredictionOutcome.prediction_id).join(Prediction).where(
        Prediction.fixture_id == fixture.id)).all())
    n = 0
    for pr in db.scalars(select(Prediction).where(Prediction.fixture_id == fixture.id, Prediction.is_pre_match.is_(True))):
        if pr.id in done:
            continue
        res = evaluate(sport, pr.market, pr.selection, fixture.home_score, fixture.away_score, extra)
        if res is None:
            continue
        db.add(PredictionOutcome(prediction_id=pr.id, hit=res, actual={"home": fixture.home_score, "away": fixture.away_score}))
        n += 1
    db.flush()
    return n


def recompute_evaluations(db: Session) -> int:
    """Agrega métricas por (versión de modelo, deporte, liga, mercado:selección) + corte p>=0.60."""
    db.execute(delete(PredictionEvaluation))
    rows = db.execute(select(Prediction, PredictionOutcome, FixtureAnalysis.model_version_id)
                      .join(PredictionOutcome, PredictionOutcome.prediction_id == Prediction.id)
                      .join(FixtureAnalysis, FixtureAnalysis.id == Prediction.analysis_id)
                      .where(Prediction.is_pre_match.is_(True))).all()
    groups: dict[tuple, list[tuple[float, int]]] = defaultdict(list)
    for pr, oc, mv in rows:
        pair = (float(pr.probability), int(oc.hit))
        key_m = f"{pr.market}:{pr.selection}"
        for league in (pr.league_id, None):
            groups[(mv, pr.sport_id, league, key_m)].append(pair)
            groups[(mv, pr.sport_id, league, "ALL")].append(pair)
            if pair[0] >= 0.60:
                groups[(mv, pr.sport_id, league, "ALL@>=0.60")].append(pair)
    count = 0
    for (mv, sport, league, market), pairs in groups.items():
        p = [a for a, _ in pairs]; y = [b for _, b in pairs]
        db.add(PredictionEvaluation(model_version_id=mv, sport_id=sport, league_id=league, market=market, n=len(pairs),
                                    hit_rate=sum(y) / len(y), brier=M.brier_score(p, y), log_loss=M.log_loss(p, y),
                                    mean_probability=sum(p) / len(p)))
        count += 1
    db.flush()
    return count
