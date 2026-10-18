"""Tareas programadas. Todas son idempotentes y registran su ejecución en provider_sync_jobs."""
from __future__ import annotations

import logging
from datetime import datetime, timedelta, timezone
from typing import Callable

from sqlalchemy import delete, or_, select
from sqlalchemy.orm import Session

from ..core.cache import get_cache
from ..db.models import (Favorite, Fixture, HistoricalResult, Notification, OneTimeToken, PredictionOutcome, Prediction,
                         ProviderRequest, RefreshToken, Subscription, User)
from ..db.session import SessionLocal
from ..services import analysis as analysis_svc
from ..services import settlement, sync
from ..services.billing import service as billing
from ..services.plans import effective_plan

log = logging.getLogger("pronoscore.jobs")


def _now() -> datetime:
    return datetime.now(timezone.utc)


def generate_analyses(db: Session, horizon_hours: int = 48) -> int:
    """Predicciones PREVIAS: sólo para partidos que aún no empezaron."""
    now = _now()
    n = 0
    for fx in db.scalars(select(Fixture).where(Fixture.status == "scheduled", Fixture.kickoff > now,
                                               Fixture.kickoff < now + timedelta(hours=horizon_hours))):
        if analysis_svc.regenerate_if_stale(db, fx, now=now):
            n += 1
    return n


def settle_finished(db: Session) -> int:
    """Compara predicciones previas con resultados y recalcula métricas."""
    n = 0
    pending = select(Prediction.fixture_id).outerjoin(PredictionOutcome, PredictionOutcome.prediction_id == Prediction.id).where(
        PredictionOutcome.id.is_(None), Prediction.is_pre_match.is_(True))
    for fx in db.scalars(select(Fixture).where(Fixture.status == "finished", Fixture.id.in_(pending))):
        n += settlement.settle_fixture(db, fx)
    if n:
        settlement.recompute_evaluations(db)
    return n


def send_alerts(db: Session) -> int:
    """Alertas (Pro): partidos de favoritos que empiezan en < 24 h. Una notificación por usuario/partido."""
    now = _now(); n = 0
    soon = db.scalars(select(Fixture).where(Fixture.status == "scheduled", Fixture.kickoff > now, Fixture.kickoff < now + timedelta(hours=24))).all()
    for fx in soon:
        favs = db.scalars(select(Favorite).where(or_(
            (Favorite.kind == "team") & (Favorite.ref_id.in_([fx.home_team_id, fx.away_team_id])),
            (Favorite.kind == "fixture") & (Favorite.ref_id == fx.id),
            (Favorite.kind == "league") & (Favorite.ref_id == fx.league_id)))).all()
        for uid in {f.user_id for f in favs}:
            user = db.get(User, uid)
            if not user or not (effective_plan(db, user).features or {}).get("alerts"):
                continue
            exists = db.scalar(select(Notification.id).where(Notification.user_id == uid, Notification.kind == "match_alert",
                                                             Notification.data["fixture_id"].as_integer() == fx.id))
            if exists:
                continue
            db.add(Notification(user_id=uid, kind="match_alert", title=f"{fx.home_team.name} vs {fx.away_team.name}",
                                body="Un partido de tus favoritos empieza en menos de 24 h. El análisis ya está disponible.",
                                data={"fixture_id": fx.id}))
            n += 1
    return n


def cleanup(db: Session) -> int:
    now = _now()
    n = 0
    for stmt in (delete(RefreshToken).where(RefreshToken.expires_at < now - timedelta(days=7)),
                 delete(OneTimeToken).where(OneTimeToken.expires_at < now - timedelta(days=2)),
                 delete(ProviderRequest).where(ProviderRequest.created_at < now - timedelta(days=30))):
        n += db.execute(stmt).rowcount or 0
    return n


def invalidate_cache(db: Session) -> int:
    get_cache().invalidate_prefix("api:")
    return 1


JOBS: dict[str, Callable[[Session], int]] = {
    "generate_analyses": generate_analyses,
    "settle_finished": settle_finished,
    "send_alerts": send_alerts,
    "expire_subscriptions": billing.expire_overdue,
    "cleanup": cleanup,
    "invalidate_cache": invalidate_cache,
}


def run_job(name: str, by: str = "scheduler") -> dict:
    """Ejecuta una tarea con su propia sesión y la registra."""
    db = SessionLocal()
    try:
        if name.startswith(("sync_fixtures:", "sync_standings:", "sync_fixture_stats:")):
            kind, sport = name.split(":")
            fn = {"sync_fixtures": sync.sync_fixtures, "sync_standings": sync.sync_standings,
                  "sync_fixture_stats": sync.sync_fixture_stats}[kind]
            return fn(db, sport, by=by)
        fn = JOBS.get(name)
        if fn is None:
            return {"status": "failed", "error": f"tarea desconocida: {name}"}
        run = sync.JobRun(db, name, "internal", by)
        try:
            items = fn(db)
            db.commit()
            run.finish("success", int(items))
            return {"status": "success", "items": items}
        except Exception as e:
            db.rollback()
            run.finish("failed", 0, str(e))
            log.exception("tarea %s falló", name)
            return {"status": "failed", "error": str(e)}
    finally:
        db.close()
