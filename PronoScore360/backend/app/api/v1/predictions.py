from __future__ import annotations

from datetime import date, datetime, time, timedelta, timezone
from typing import Optional
from zoneinfo import ZoneInfo

import numpy as np
from fastapi import APIRouter, Depends, Query
from pronoscore_analytics import basketball as bb
from pronoscore_analytics import quality as Q
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from ...core.errors import AppError, NotFound, PaymentRequired
from ...core.ratelimit import check_rate
from ...db.models import (AppSetting, Fixture, FixtureAnalysis, ModelVersion, Plan, Prediction, PredictionEvaluation,
                          PredictionOutcome, Sport, User)
from ...db.session import get_db
from ...services.analysis import get_or_create_analysis, selection_key
from ...services.plans import consume_analysis, require_feature, usage_summary
from ..deps import Page, current_plan, get_current_user, paginated
from .serializers import fixture_out

router = APIRouter(tags=["Pronósticos"], dependencies=[Depends(get_current_user)])

DISCLAIMER = ("Estimaciones estadísticas con fines informativos. No son una garantía de resultado. "
              "PronoScore360 no ofrece apuestas ni conecta con casas de apuestas.")

CATEGORY_DOCS = {
    Q.CATEGORY_HIGH: "Probabilidad estimada >= 70 % con calidad de datos media o alta (>= 10 partidos previos por equipo).",
    Q.CATEGORY_MEDIUM: "Probabilidad estimada entre 55 % y 70 % con calidad de datos media o alta.",
    Q.CATEGORY_UNCERTAIN: "Probabilidad < 55 % o calidad de datos baja (5-9 partidos previos por equipo).",
    Q.CATEGORY_INSUFFICIENT: f"Menos de {Q.MIN_MATCHES} partidos previos por equipo: no se publica pronóstico.",
}


def prediction_out(p: Prediction, outcome: Optional[PredictionOutcome] = None) -> dict:
    return {"id": p.id, "market": p.market, "selection": p.selection, "key": selection_key(p.market, p.selection),
            "group": p.market_group, "probability": round(float(p.probability), 4),
            "probability_pct": round(float(p.probability) * 100, 1), "category": p.category, "uncertainty": p.uncertainty,
            "data_points": p.data_points, "updated_at": p.created_at.isoformat(), "is_pre_match": p.is_pre_match,
            "result": None if outcome is None else ("hit" if outcome.hit else "miss")}


def exact_score_section(an: FixtureAnalysis, plan: Plan, sport: str) -> Optional[dict]:
    pl = an.payload
    if an.status != "ok":
        return None
    n = int((plan.features or {}).get("exact_score_top_n", 1))
    adv = bool((plan.features or {}).get("advanced_stats"))
    if sport == "football":
        ts = pl["top_scores"]
        return {"type": "football", "expected_goals": {"home": round(pl["lambda_home"], 2), "away": round(pl["lambda_away"], 2)},
                "most_likely": ts[0], "top_scores": ts[:n], "locked_extra_scores": max(len(ts) - n, 0),
                "explanation": pl["explanation"],
                "note": "Cada probabilidad es la de acertar EXACTAMENTE ese marcador (suele ser baja)."}
    s = pl["sim"]
    sec = {"type": "basketball", "expected_points": {"home": round(s["expected_home"], 1), "away": round(s["expected_away"], 1)},
           "central_score": s["central_score"], "p_home_win": round(s["p_home_win"], 4), "p_away_win": round(s["p_away_win"], 4),
           "expected_total": round(s["expected_total"], 1), "expected_margin": round(s["expected_margin"], 1),
           "explanation": pl["explanation"],
           "note": "El marcador central es una estimación; la probabilidad de acertarlo exactamente es muy baja."}
    if adv:
        sec.update(range_home_80=s["range_home_80"], range_away_80=s["range_away_80"], range_total_80=s["range_total_80"],
                   total_distribution=s["total_distribution"], margin_distribution=s["margin_distribution"])
    return sec


def serialize_analysis(db: Session, user: User, plan: Plan, fx: Fixture, an: FixtureAnalysis, usage: Optional[dict]) -> dict:
    base = {"fixture": fixture_out(fx, user.profile.timezone), "disclaimer": DISCLAIMER, "usage": usage,
            "analysis": {"id": an.id, "status": an.status, "quality": an.quality, "n_home": an.n_home, "n_away": an.n_away,
                         "generated_at": an.generated_at.isoformat(), "is_pre_match": an.is_pre_match,
                         "model": an.payload.get("model"), "model_version": an.payload.get("version"),
                         "is_demo": an.is_demo, "category_criteria": CATEGORY_DOCS}}
    if an.status != "ok":
        return {**base, "predictions": [], "locked_groups": [], "exact_score": None,
                "message": "Datos insuficientes", "reason": an.payload.get("reason")}
    preds = db.scalars(select(Prediction).where(Prediction.analysis_id == an.id).order_by(Prediction.id)).all()
    outs = {o.prediction_id: o for o in db.scalars(select(PredictionOutcome).where(PredictionOutcome.prediction_id.in_([p.id for p in preds])))}
    allowed = set(plan.allowed_market_groups or [])
    visible = [prediction_out(p, outs.get(p.id)) for p in preds if p.market_group in allowed]
    locked = sorted({p.market_group for p in preds} - allowed)
    adv = bool((plan.features or {}).get("advanced_stats"))
    return {**base, "predictions": visible, "locked_groups": locked,
            "locked_count": sum(1 for p in preds if p.market_group not in allowed),
            "exact_score": exact_score_section(an, plan, fx.league.sport.code),
            "drivers": an.payload.get("drivers") if adv else None,
            "inputs": an.payload.get("inputs") if adv else None,
            "upgrade_hint": None if adv else "Pro desbloquea todos los mercados, marcadores probables y análisis avanzado."}


def run_analysis(db: Session, user: User, plan: Plan, fixture_id: int, sport: Optional[str] = None):
    fx = db.get(Fixture, fixture_id)
    if not fx or (sport and fx.league.sport.code != sport):
        raise NotFound("Partido no encontrado")
    check_rate(f"analysis:{user.id}", 20)       # límite técnico de cálculos
    an = get_or_create_analysis(db, fx)
    usage = consume_analysis(db, user, fx.id, an.id, plan) if an.status == "ok" else usage_summary(db, user, plan)
    return fx, an, usage


@router.get("/fixtures/{fixture_id}/analysis", summary="Análisis del partido (descuenta 1 consulta/día por partido distinto)")
def analysis(fixture_id: int, user: User = Depends(get_current_user), db: Session = Depends(get_db), plan: Plan = Depends(current_plan)):
    fx, an, usage = run_analysis(db, user, plan, fixture_id)
    return serialize_analysis(db, user, plan, fx, an, usage)


@router.get("/fixtures/{fixture_id}/exact-score", summary="Marcadores probables del partido")
def exact_score(fixture_id: int, user: User = Depends(get_current_user), db: Session = Depends(get_db), plan: Plan = Depends(current_plan)):
    fx, an, usage = run_analysis(db, user, plan, fixture_id)
    if an.status != "ok":
        return {"fixture": fixture_out(fx, user.profile.timezone), "message": "Datos insuficientes",
                "reason": an.payload.get("reason"), "exact_score": None, "usage": usage}
    return {"fixture": fixture_out(fx, user.profile.timezone), "exact_score": exact_score_section(an, plan, fx.league.sport.code),
            "generated_at": an.generated_at.isoformat(), "usage": usage, "disclaimer": DISCLAIMER}


@router.get("/nba/games", summary="Calendario NBA/baloncesto (alias de /fixtures?sport=basketball)")
def nba_games(day: Optional[date] = Query(None, alias="date"), team_id: Optional[int] = None, page: int = 1, page_size: int = 30,
              user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    from .catalog import fixtures
    return fixtures(sport="basketball", league_id=None, team_id=team_id, q=None, day=day, date_from=None, date_to=None,
                    status=None, tz=None, order="asc", page=page, page_size=page_size, user=user, db=db)


@router.get("/nba/games/{fixture_id}/analysis", summary="Análisis NBA")
def nba_analysis(fixture_id: int, user: User = Depends(get_current_user), db: Session = Depends(get_db), plan: Plan = Depends(current_plan)):
    fx, an, usage = run_analysis(db, user, plan, fixture_id, "basketball")
    return serialize_analysis(db, user, plan, fx, an, usage)


@router.get("/nba/games/{fixture_id}/lines", summary="Probabilidades para líneas personalizadas (Pro)")
def nba_lines(fixture_id: int, total: Optional[float] = Query(None, ge=100, le=400),
              home_total: Optional[float] = Query(None, ge=40, le=200), away_total: Optional[float] = Query(None, ge=40, le=200),
              spread: Optional[float] = Query(None, ge=-40, le=40, description="Hándicap del local, p. ej. -5.5"),
              user: User = Depends(get_current_user), db: Session = Depends(get_db), plan: Plan = Depends(current_plan)):
    require_feature(plan, "custom_lines", "Las líneas personalizadas son una función Pro.")
    check_rate(f"heavy:{user.id}", plan.heavy_calcs_per_hour or 1, 3600)
    fx, an, usage = run_analysis(db, user, plan, fixture_id, "basketball")
    if an.status != "ok":
        raise AppError("Datos insuficientes para este partido", code="insufficient_data", status_code=422)
    h, a = bb.samples_from_params(an.payload["params"])
    res = {}
    if total is not None:
        res["total"] = {"line": total, "over": float(np.mean(h + a > total)), "under": float(np.mean(h + a < total))}
    if home_total is not None:
        res["home_total"] = {"line": home_total, "over": float(np.mean(h > home_total)), "under": float(np.mean(h < home_total))}
    if away_total is not None:
        res["away_total"] = {"line": away_total, "over": float(np.mean(a > away_total)), "under": float(np.mean(a < away_total))}
    if spread is not None:
        res["spread"] = {"line": spread, "home_covers": float(np.mean((h - a) + spread > 0)),
                         "away_covers": float(np.mean((a - h) - spread > 0))}
    return {"fixture_id": fx.id, "lines": res, "n_simulations": an.payload["params"]["n_sims"], "usage": usage,
            "disclaimer": DISCLAIMER}


# ---------------- ranking / filtros ----------------
def _bounds(db: Session) -> tuple[float, float]:
    row = db.get(AppSetting, "probability_filter_range")
    v = row.value if row else {"min": 0.6, "max": 0.95}
    return float(v["min"]), float(v["max"])


@router.get("/predictions/meta", summary="Metadatos de filtros y criterios de categorías")
def predictions_meta(db: Session = Depends(get_db)):
    lo, hi = _bounds(db)
    markets = [r for (r,) in db.execute(select(Prediction.market).distinct().order_by(Prediction.market))]
    return {"probability_range": {"min": lo, "max": hi}, "markets": markets,
            "groups": ["basic", "goals", "corners", "cards", "halftime", "nba_lines"],
            "categories": CATEGORY_DOCS, "uncertainty_levels": ["low", "medium", "high"],
            "quality_levels": {"high": ">= 20 partidos previos", "medium": "10-19", "low": "5-9", "insufficient": "< 5"}}


@router.get("/predictions", summary="Pronósticos destacados con filtros (Pro)")
def list_predictions(sport: Optional[str] = None, league_id: Optional[int] = None, team_id: Optional[int] = None,
                     day: Optional[date] = Query(None, alias="date"), market: Optional[str] = Query(None, description="Mercado exacto (p. ej. OU:2.5) o grupo (basic, goals…)"),
                     min_probability: Optional[float] = Query(None, ge=0, le=1), max_probability: Optional[float] = Query(None, ge=0, le=1),
                     min_matches: Optional[int] = Query(None, ge=0), uncertainty: Optional[str] = Query(None, pattern="^(low|medium|high)$"),
                     category: Optional[str] = None, quality: Optional[str] = Query(None, pattern="^(low|medium|high)$"),
                     include_past: bool = False, page: int = 1, page_size: int = 30,
                     user: User = Depends(get_current_user), db: Session = Depends(get_db), plan: Plan = Depends(current_plan)):
    require_feature(plan, "probability_filter", "El listado completo de pronósticos y sus filtros son una función Pro.")
    lo, hi = _bounds(db)
    for v in (min_probability, max_probability):
        if v is not None and not (lo <= v <= hi):
            raise AppError(f"El filtro de probabilidad debe estar entre {lo:.0%} y {hi:.0%}", code="invalid_probability_filter", status_code=422)
    pg = Page(page, page_size)
    latest = select(func.max(FixtureAnalysis.id)).where(FixtureAnalysis.is_pre_match.is_(True), FixtureAnalysis.status == "ok").group_by(FixtureAnalysis.fixture_id)
    stmt = (select(Prediction, Fixture).join(Fixture, Fixture.id == Prediction.fixture_id)
            .join(FixtureAnalysis, FixtureAnalysis.id == Prediction.analysis_id)
            .where(Prediction.analysis_id.in_(latest), Prediction.is_pre_match.is_(True),
                   Prediction.market_group.in_(plan.allowed_market_groups or [])))
    if not include_past:
        stmt = stmt.where(Fixture.kickoff >= datetime.now(timezone.utc), Fixture.status == "scheduled")
    if sport:
        stmt = stmt.join(Sport, Sport.id == Prediction.sport_id).where(Sport.code == sport)
    if league_id:
        stmt = stmt.where(Prediction.league_id == league_id)
    if team_id:
        stmt = stmt.where((Fixture.home_team_id == team_id) | (Fixture.away_team_id == team_id))
    if day:
        z = ZoneInfo(user.profile.timezone); a = datetime.combine(day, time.min, tzinfo=z)
        stmt = stmt.where(Fixture.kickoff >= a.astimezone(timezone.utc), Fixture.kickoff < (a + timedelta(days=1)).astimezone(timezone.utc))
    if market:
        stmt = stmt.where((Prediction.market == market) | (Prediction.market_group == market))
    if min_probability is not None:
        stmt = stmt.where(Prediction.probability >= min_probability)
    if max_probability is not None:
        stmt = stmt.where(Prediction.probability <= max_probability)
    if min_matches is not None:
        stmt = stmt.where(Prediction.data_points >= min_matches)
    if uncertainty:
        stmt = stmt.where(Prediction.uncertainty == uncertainty)
    if category:
        stmt = stmt.where(Prediction.category == category)
    if quality:
        stmt = stmt.where(FixtureAnalysis.quality == quality)
    total = db.scalar(select(func.count()).select_from(stmt.subquery()))
    rows = db.execute(stmt.order_by(Prediction.probability.desc(), Prediction.id).offset(pg.offset).limit(pg.page_size)).unique().all()
    items = [{**prediction_out(p), "fixture": fixture_out(f, user.profile.timezone)} for p, f in rows]
    return {**paginated(items, total, pg), "disclaimer": DISCLAIMER}


@router.get("/models/performance", summary="Rendimiento histórico del modelo (Pro)")
def model_performance(sport: Optional[str] = None, user: User = Depends(get_current_user), db: Session = Depends(get_db),
                      plan: Plan = Depends(current_plan)):
    require_feature(plan, "model_performance", "Las estadísticas de rendimiento del modelo son una función Pro.")
    stmt = (select(PredictionEvaluation, ModelVersion, Sport).join(ModelVersion, ModelVersion.id == PredictionEvaluation.model_version_id)
            .join(Sport, Sport.id == PredictionEvaluation.sport_id))
    if sport:
        stmt = stmt.where(Sport.code == sport)
    rows = db.execute(stmt.order_by(Sport.code, PredictionEvaluation.market)).all()
    from ...db.models import League
    lg = {l.id: l.name for l in db.scalars(select(League))}
    return {"items": [{"sport": s.code, "model": mv.model.code, "version": mv.version, "league": lg.get(e.league_id) if e.league_id else "Todas",
                       "market": e.market, "n": e.n, "hit_rate": None if e.hit_rate is None else round(float(e.hit_rate), 4),
                       "brier": None if e.brier is None else round(float(e.brier), 4),
                       "log_loss": None if e.log_loss is None else round(float(e.log_loss), 4),
                       "mean_probability": None if e.mean_probability is None else round(float(e.mean_probability), 4)}
                      for e, mv, s in rows],
            "note": "Tasa de ocurrencia del evento pronosticado sobre pronósticos previos ya resueltos. Rendimiento pasado no garantiza resultados futuros."}
