"""Generación y almacenamiento de análisis (pronósticos) usando PronoScore360 Analytics."""
from __future__ import annotations

from dataclasses import asdict
from datetime import datetime, timedelta, timezone
from typing import Optional

from pronoscore_analytics import basketball as bb
from pronoscore_analytics import football as fb
from pronoscore_analytics import quality as Q
from pronoscore_analytics.version import MODEL_FAMILY_VERSIONS
from sqlalchemy import select
from sqlalchemy.orm import Session

from ..db.models import (Fixture, FixtureAnalysis, HistoricalResult, ModelVersion, Prediction, PredictionModel, Sport)

MODEL_CODES = {"football": "football_poisson_dc", "basketball": "basketball_efficiency_mc"}


def utcnow() -> datetime:
    return datetime.now(timezone.utc)


def market_group(sport: str, market: str) -> str:
    if sport == "football":
        if market in ("1X2", "DC", "BTTS", "OU:2.5"):
            return "basic"
        if market.startswith("OU:"):
            return "goals"
        if market.startswith("CORNERS"):
            return "corners"
        if market.startswith("CARDS"):
            return "cards"
        if market.startswith("HT_"):
            return "halftime"
        return "goals"
    return "basic" if market == "ML" else "nba_lines"


def selection_key(market: str, selection: str) -> Optional[str]:
    """Clave canónica usada por selecciones/combinadas (coincide con la gramática de analytics.joint)."""
    if market.startswith("SPREAD"):
        return market if selection == "cover" else None
    return f"{market}:{selection}"


def ensure_model_version(db: Session, sport_code: str) -> ModelVersion:
    code = MODEL_CODES[sport_code]
    version = MODEL_FAMILY_VERSIONS[code]
    sport = db.scalar(select(Sport).where(Sport.code == sport_code))
    pm = db.scalar(select(PredictionModel).where(PredictionModel.code == code))
    if not pm:
        pm = PredictionModel(code=code, sport_id=sport.id, name=code.replace("_", " ").title(),
                             description="Poisson + Dixon-Coles" if sport_code == "football"
                             else "Eficiencia por posesión + Monte Carlo")
        db.add(pm); db.flush()
    mv = db.scalar(select(ModelVersion).where(ModelVersion.model_id == pm.id, ModelVersion.version == version))
    if not mv:
        params = asdict(fb.FootballParams()) if sport_code == "football" else asdict(bb.BasketballParams())
        mv = ModelVersion(model_id=pm.id, version=version, params=params, is_active=True)
        db.add(mv); db.flush()
    return mv


def _history(db: Session, fixture: Fixture, as_of: datetime):
    rows = db.scalars(select(HistoricalResult).where(HistoricalResult.league_id == fixture.league_id,
                                                     HistoricalResult.played_at < as_of)
                      .order_by(HistoricalResult.played_at)).all()
    return rows


def _football_records(rows, names: dict[int, str]):
    out = []
    for r in rows:
        x = r.extra or {}
        out.append(fb.MatchRecord(r.played_at, names[r.home_team_id], names[r.away_team_id], r.home_score, r.away_score,
                                  x.get("home_corners"), x.get("away_corners"), x.get("home_cards"), x.get("away_cards"),
                                  x.get("ht_home"), x.get("ht_away")))
    return out


def _basketball_records(rows, names):
    return [bb.GameRecord(r.played_at, names[r.home_team_id], names[r.away_team_id], r.home_score, r.away_score,
                          (r.extra or {}).get("home_poss"), (r.extra or {}).get("away_poss")) for r in rows]


def compute_payload(db: Session, fixture: Fixture, as_of: datetime) -> dict:
    """Ejecuta el modelo con datos estrictamente anteriores a `as_of`."""
    rows = _history(db, fixture, as_of)
    sport = fixture.league.sport.code
    ids = {fixture.home_team_id: fixture.home_team.name, fixture.away_team_id: fixture.away_team.name}
    for r in rows:
        for tid in (r.home_team_id, r.away_team_id):
            ids.setdefault(tid, f"team:{tid}")
    # nombres reales no hacen falta: se usa el id como clave estable
    names = {tid: f"t{tid}" for tid in ids}
    home, away = names[fixture.home_team_id], names[fixture.away_team_id]
    if sport == "football":
        model = fb.FootballModel().fit(_football_records(rows, names), as_of)
        return fb.predict_match(model, home, away)
    model = bb.BasketballModel().fit(_basketball_records(rows, names), as_of)
    kick = fixture.kickoff
    res = bb.predict_game(model, home, away, kick)
    if res["status"] == "ok":
        h, a = bb.samples_from_params(res["params"])
        res["markets"] = bb.full_markets(h, a, res["sim"]["expected_total"])
    return res


def create_analysis(db: Session, fixture: Fixture, now: Optional[datetime] = None, *, as_of: Optional[datetime] = None,
                    created_at: Optional[datetime] = None, is_demo: bool = False,
                    force_pre_match: Optional[bool] = None) -> FixtureAnalysis:
    """Crea un análisis nuevo.

    Si el partido ya empezó o terminó, el análisis se marca is_pre_match=False: NO cuenta como
    predicción previa ni entra en las evaluaciones de acierto."""
    now = created_at or now or utcnow()
    sport = fixture.league.sport.code
    pre = (fixture.status == "scheduled" and now < fixture.kickoff) if force_pre_match is None else force_pre_match
    as_of = as_of or min(now, fixture.kickoff)
    payload = compute_payload(db, fixture, as_of)
    if force_pre_match:
        payload["demo_backfilled"] = True   # sólo DEMO: simula una predicción previa con datos anteriores a as_of
    mv = ensure_model_version(db, sport)
    an = FixtureAnalysis(fixture_id=fixture.id, model_version_id=mv.id, status=payload["status"], quality=payload["quality"],
                         n_home=payload["n_home"], n_away=payload["n_away"], payload=_jsonable(payload),
                         is_pre_match=pre, generated_at=now, is_demo=is_demo or fixture.is_demo)
    db.add(an); db.flush()
    if payload["status"] == "ok":
        n = min(payload["n_home"], payload["n_away"])
        for market, sels in payload["markets"].items():
            for sel, p in sels.items():
                key = selection_key(market, sel)
                if key is None and not market.startswith("SPREAD"):
                    continue
                cat = Q.categorize(p, payload["quality"])
                db.add(Prediction(analysis_id=an.id, fixture_id=fixture.id, sport_id=fixture.sport_id,
                                  league_id=fixture.league_id, market=market, selection=sel,
                                  market_group=market_group(sport, market), probability=round(float(p), 6),
                                  category=cat, uncertainty=Q.uncertainty_level(payload["quality"], p), data_points=n,
                                  explanation=payload["explanation"], is_pre_match=pre, created_at=now))
        db.flush()
    return an


def _jsonable(o):
    import numpy as np
    if isinstance(o, dict):
        return {str(k): _jsonable(v) for k, v in o.items()}
    if isinstance(o, (list, tuple)):
        return [_jsonable(v) for v in o]
    if isinstance(o, np.generic):
        return o.item()
    if isinstance(o, np.ndarray):
        return o.tolist()
    return o


def latest_analysis(db: Session, fixture_id: int) -> Optional[FixtureAnalysis]:
    return db.scalar(select(FixtureAnalysis).where(FixtureAnalysis.fixture_id == fixture_id)
                     .order_by(FixtureAnalysis.is_pre_match.desc(), FixtureAnalysis.generated_at.desc()).limit(1))


def get_or_create_analysis(db: Session, fixture: Fixture, now: Optional[datetime] = None) -> FixtureAnalysis:
    """Reutiliza el resultado almacenado (consultas repetidas = mismo resultado)."""
    an = latest_analysis(db, fixture.id)
    if an is not None:
        return an
    return create_analysis(db, fixture, now)


def regenerate_if_stale(db: Session, fixture: Fixture, max_age_hours: int = 12, now: Optional[datetime] = None):
    """Para el job previo al partido: sólo mientras el partido no haya comenzado."""
    now = now or utcnow()
    if fixture.status != "scheduled" or fixture.kickoff <= now:
        return None
    an = latest_analysis(db, fixture.id)
    if an is None or (an.is_pre_match and now - an.generated_at > timedelta(hours=max_age_hours)) or not an.is_pre_match:
        return create_analysis(db, fixture, now)
    return None
