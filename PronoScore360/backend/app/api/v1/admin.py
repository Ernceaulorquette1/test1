from __future__ import annotations

from datetime import datetime, timedelta, timezone
from typing import Any, Optional

from fastapi import APIRouter, Depends, Request
from pronoscore_analytics import backtest as bt
from pronoscore_analytics import basketball as bbm
from pronoscore_analytics import football as fbm
from pydantic import BaseModel, Field
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from ...core.cache import get_cache
from ...core.config import get_settings
from ...core.errors import AppError, Conflict, Forbidden, NotFound
from ...db.models import (Announcement, AppSetting, AuditLog, DailyUsage, ErrorLog, Fixture, FixtureAnalysis, HistoricalResult,
                          League, ModelVersion, Notification, PaymentTransaction, Plan, Prediction, PredictionEvaluation,
                          ProviderRequest, ProviderSyncJob, Role, Sport, Subscription, User)
from ...db.session import get_db
from ...jobs.tasks import JOBS, run_job
from ...services.audit import audit
from ...services.plans import active_subscription, effective_plan
from ..deps import Page, ip_of, paginated, require_admin

router = APIRouter(prefix="/admin", tags=["Administración"], dependencies=[Depends(require_admin)])


def _now() -> datetime:
    return datetime.now(timezone.utc)


@router.get("/dashboard", summary="Indicadores generales")
def dashboard(db: Session = Depends(get_db)):
    now = _now()
    total_users = db.scalar(select(func.count()).select_from(User).where(User.deleted_at.is_(None)))
    pro_users = len({s.user_id for s in db.scalars(select(Subscription).where(Subscription.status.in_(("active", "canceled", "grace_period")))) if active_subscription(db, s.user_id, now)})
    by_status = dict(db.execute(select(Subscription.status, func.count()).group_by(Subscription.status)).all())
    rev = db.execute(select(PaymentTransaction.currency, PaymentTransaction.is_test, func.sum(PaymentTransaction.amount)).where(
        PaymentTransaction.status == "succeeded").group_by(PaymentTransaction.currency, PaymentTransaction.is_test)).all()
    today = now.replace(hour=0, minute=0, second=0, microsecond=0)
    errors24 = db.scalar(select(func.count()).select_from(ErrorLog).where(ErrorLog.created_at > now - timedelta(hours=24)))
    jobs = db.execute(select(ProviderSyncJob.status, func.count()).where(ProviderSyncJob.started_at > now - timedelta(hours=24)).group_by(ProviderSyncJob.status)).all()
    s = get_settings()
    return {
        "users": {"total": total_users, "pro": pro_users, "free": total_users - pro_users,
                  "new_7d": db.scalar(select(func.count()).select_from(User).where(User.created_at > now - timedelta(days=7)))},
        "subscriptions": {"by_status": by_status, "active": by_status.get("active", 0) + by_status.get("canceled", 0) + by_status.get("grace_period", 0),
                          "canceled": by_status.get("canceled", 0) + by_status.get("expired", 0)},
        "revenue": [{"currency": c, "is_test": t, "amount": float(a or 0)} for c, t, a in rev],
        "usage": {"analyses_today": db.scalar(select(func.count()).select_from(DailyUsage).where(DailyUsage.created_at >= today)),
                  "analyses_7d": db.scalar(select(func.count()).select_from(DailyUsage).where(DailyUsage.created_at >= now - timedelta(days=7)))},
        "predictions": {"total": db.scalar(select(func.count()).select_from(Prediction)),
                        "analyses": db.scalar(select(func.count()).select_from(FixtureAnalysis)),
                        "fixtures": db.scalar(select(func.count()).select_from(Fixture))},
        "provider": {"mode": s.provider_mode, "requests_today": db.scalar(select(func.count()).select_from(ProviderRequest).where(
            ProviderRequest.created_at >= today, ProviderRequest.from_cache.is_(False)))},
        "jobs_24h": {k: v for k, v in jobs}, "errors_24h": errors24,
    }


# ---------------- usuarios ----------------
class UserPatch(BaseModel):
    is_active: Optional[bool] = None
    role: Optional[str] = Field(default=None, pattern="^(USER|ADMIN)$")


def _user_row(db: Session, u: User) -> dict:
    return {"id": u.id, "email": u.email, "role": u.role.code, "is_active": u.is_active, "email_verified": u.email_verified,
            "plan": effective_plan(db, u).code, "country": u.profile.country, "language": u.profile.language,
            "created_at": u.created_at.isoformat(), "last_login_at": u.last_login_at.isoformat() if u.last_login_at else None, "is_demo": u.is_demo}


@router.get("/users")
def users(q: Optional[str] = None, role: Optional[str] = None, page: int = 1, page_size: int = 25, db: Session = Depends(get_db)):
    pg = Page(page, page_size)
    stmt = select(User).where(User.deleted_at.is_(None))
    if q:
        stmt = stmt.where(User.email.ilike(f"%{q}%"))
    if role:
        stmt = stmt.join(Role, Role.id == User.role_id).where(Role.code == role)
    total = db.scalar(select(func.count()).select_from(stmt.subquery()))
    rows = db.scalars(stmt.order_by(User.id.desc()).offset(pg.offset).limit(pg.page_size)).unique().all()
    return paginated([_user_row(db, u) for u in rows], total, pg)


@router.patch("/users/{user_id}")
def patch_user(user_id: int, body: UserPatch, request: Request, admin: User = Depends(require_admin), db: Session = Depends(get_db)):
    u = db.get(User, user_id)
    if not u:
        raise NotFound("Usuario no encontrado")
    if u.id == admin.id and (body.is_active is False or (body.role and body.role != "ADMIN")):
        raise Forbidden("No puedes desactivarte ni quitarte el rol de administrador a ti mismo", code="self_lockout")
    detail = {}
    if body.is_active is not None:
        u.is_active = body.is_active; detail["is_active"] = body.is_active
    if body.role:
        u.role = db.scalar(select(Role).where(Role.code == body.role)); detail["role"] = body.role
    audit(db, "admin.user_updated", actor_id=admin.id, entity="user", entity_id=u.id, detail=detail, ip=ip_of(request))
    return _user_row(db, u)


# ---------------- planes / ajustes ----------------
class PlanPatch(BaseModel):
    name: Optional[str] = None
    daily_analysis_limit: Optional[int] = Field(default=None, ge=0)
    unlimited_daily: Optional[bool] = None
    favorites_limit: Optional[int] = Field(default=None, ge=0)
    history_days: Optional[int] = Field(default=None, ge=0)
    max_selection_items: Optional[int] = Field(default=None, ge=0, le=50)
    heavy_calcs_per_hour: Optional[int] = Field(default=None, ge=0)
    allowed_market_groups: Optional[list[str]] = None
    prices: Optional[dict[str, Any]] = None
    features: Optional[dict[str, Any]] = None
    is_active: Optional[bool] = None


@router.get("/plans")
def admin_plans(db: Session = Depends(get_db)):
    from ...schemas.common import plan_out
    return [{**plan_out(p), "is_active": p.is_active, "heavy_calcs_per_hour": p.heavy_calcs_per_hour, "max_selection_items": p.max_selection_items}
            for p in db.scalars(select(Plan).order_by(Plan.sort_order))]


@router.patch("/plans/{code}", summary="Editar precios y límites sin redeploy")
def patch_plan(code: str, body: PlanPatch, request: Request, admin: User = Depends(require_admin), db: Session = Depends(get_db)):
    p = db.scalar(select(Plan).where(Plan.code == code))
    if not p:
        raise NotFound("Plan no encontrado")
    data = body.model_dump(exclude_unset=True)
    if data.pop("unlimited_daily", False):
        p.daily_analysis_limit = None; data.pop("daily_analysis_limit", None)
    for k, v in data.items():
        setattr(p, k, v)
    audit(db, "admin.plan_updated", actor_id=admin.id, entity="plan", entity_id=code, detail=body.model_dump(exclude_unset=True), ip=ip_of(request))
    return {"code": p.code, "updated": list(data.keys())}


class SettingIn(BaseModel):
    value: Any


@router.get("/settings")
def settings_list(db: Session = Depends(get_db)):
    return [{"key": s.key, "value": s.value, "description": s.description} for s in db.scalars(select(AppSetting).order_by(AppSetting.key))]


@router.put("/settings/{key}")
def settings_put(key: str, body: SettingIn, request: Request, admin: User = Depends(require_admin), db: Session = Depends(get_db)):
    row = db.get(AppSetting, key)
    if not row:
        raise NotFound("Ajuste desconocido")
    row.value = body.value
    audit(db, "admin.setting_updated", actor_id=admin.id, entity="setting", entity_id=key, detail={"value": body.value}, ip=ip_of(request))
    return {"key": key, "value": row.value}


# ---------------- avisos ----------------
class AnnouncementIn(BaseModel):
    title: str = Field(min_length=1, max_length=150)
    body: str = ""
    level: str = Field(default="info", pattern="^(info|warning|critical)$")
    active: bool = True


@router.get("/announcements")
def ann_list(db: Session = Depends(get_db)):
    return [{"id": a.id, "title": a.title, "body": a.body, "level": a.level, "active": a.active} for a in db.scalars(select(Announcement).order_by(Announcement.id.desc()))]


@router.post("/announcements", status_code=201)
def ann_create(body: AnnouncementIn, admin: User = Depends(require_admin), db: Session = Depends(get_db)):
    a = Announcement(**body.model_dump()); db.add(a); db.flush()
    audit(db, "admin.announcement_created", actor_id=admin.id, entity="announcement", entity_id=a.id)
    return {"id": a.id}


@router.put("/announcements/{aid}")
def ann_update(aid: int, body: AnnouncementIn, db: Session = Depends(get_db)):
    a = db.get(Announcement, aid)
    if not a:
        raise NotFound("Aviso no encontrado")
    for k, v in body.model_dump().items():
        setattr(a, k, v)
    return {"id": a.id}


@router.delete("/announcements/{aid}", status_code=204)
def ann_delete(aid: int, db: Session = Depends(get_db)):
    a = db.get(Announcement, aid)
    if a:
        db.delete(a)


class BroadcastIn(BaseModel):
    title: str = Field(min_length=1, max_length=150)
    body: str = ""


@router.post("/notifications/broadcast", status_code=201, summary="Notificación global")
def broadcast(body: BroadcastIn, admin: User = Depends(require_admin), db: Session = Depends(get_db)):
    db.add(Notification(user_id=None, kind="announcement", title=body.title, body=body.body))
    audit(db, "admin.broadcast", actor_id=admin.id, detail={"title": body.title})
    return {"status": "created"}


# ---------------- proveedores / tareas ----------------
@router.get("/providers", summary="Estado y consumo de proveedores API")
def providers(db: Session = Depends(get_db)):
    s = get_settings(); cache = get_cache(); now = _now()
    day = now.strftime("%Y%m%d")
    out = []
    for name, key in (("api-football", s.apifootball_key), ("api-nba", s.apinba_key)):
        used = int(cache.get(f"provider:budget:{name}:{day}") or 0)
        daily = db.execute(select(func.date_trunc("day", ProviderRequest.created_at).label("d"), func.count(), func.sum(func.cast(ProviderRequest.ok, __import__("sqlalchemy").Integer)))
                           .where(ProviderRequest.provider == name, ProviderRequest.from_cache.is_(False), ProviderRequest.created_at > now - timedelta(days=7))
                           .group_by("d").order_by("d")).all()
        out.append({"provider": name, "configured": bool(key), "mode": s.provider_mode, "budget_per_day": s.provider_daily_budget,
                    "reserve": s.provider_reserve, "used_today_budget": used,
                    "daily": [{"day": d.date().isoformat(), "requests": n, "ok": int(ok or 0)} for d, n, ok in daily]})
    recent = db.scalars(select(ProviderRequest).order_by(ProviderRequest.id.desc()).limit(30)).all()
    return {"providers": out, "recent": [{"provider": r.provider, "endpoint": r.endpoint, "ok": r.ok, "from_cache": r.from_cache,
                                          "status": r.status_code, "error": r.error, "at": r.created_at.isoformat()} for r in recent],
            "notes": "Los datos reales requieren claves y plan de API-SPORTS; verificar derechos de uso comercial antes de producción."}


@router.get("/jobs")
def jobs(limit: int = 50, db: Session = Depends(get_db)):
    rows = db.scalars(select(ProviderSyncJob).order_by(ProviderSyncJob.id.desc()).limit(min(limit, 200))).all()
    return {"available": sorted(JOBS.keys()) + [f"{k}:{sp}" for k in ("sync_fixtures", "sync_standings", "sync_fixture_stats") for sp in ("football", "basketball")],
            "items": [{"id": j.id, "job": j.job, "provider": j.provider, "status": j.status, "items": j.items, "message": j.message,
                       "started_at": j.started_at.isoformat(), "finished_at": j.finished_at.isoformat() if j.finished_at else None,
                       "by": j.triggered_by} for j in rows]}


class RunJobIn(BaseModel):
    job: str


@router.post("/jobs/run", summary="Ejecutar una tarea de actualización/recálculo ahora")
def jobs_run(body: RunJobIn, request: Request, admin: User = Depends(require_admin), db: Session = Depends(get_db)):
    audit(db, "admin.job_run", actor_id=admin.id, detail={"job": body.job}, ip=ip_of(request))
    db.commit()
    return run_job(body.job, by=f"admin:{admin.id}")


@router.post("/demo/reseed", summary="[SÓLO LOCAL/TEST] Regenerar datos DEMO con fechas actuales")
def reseed(admin: User = Depends(require_admin), db: Session = Depends(get_db)):
    if not get_settings().is_dev:
        raise Forbidden("Sólo disponible en local/test", code="demo_disabled")
    from ...seed.demo import seed_demo
    get_cache().invalidate_prefix("api:")
    return seed_demo(db, force=True)


# ---------------- logs ----------------
@router.get("/logs/errors")
def error_logs(page: int = 1, page_size: int = 50, db: Session = Depends(get_db)):
    pg = Page(page, page_size)
    total = db.scalar(select(func.count()).select_from(ErrorLog))
    rows = db.scalars(select(ErrorLog).order_by(ErrorLog.id.desc()).offset(pg.offset).limit(pg.page_size)).all()
    return paginated([{"id": e.id, "source": e.source, "message": e.message, "context": e.context, "at": e.created_at.isoformat()} for e in rows], total, pg)


@router.get("/logs/audit")
def audit_logs(action: Optional[str] = None, page: int = 1, page_size: int = 50, db: Session = Depends(get_db)):
    pg = Page(page, page_size)
    stmt = select(AuditLog)
    if action:
        stmt = stmt.where(AuditLog.action.ilike(f"{action}%"))
    total = db.scalar(select(func.count()).select_from(stmt.subquery()))
    rows = db.scalars(stmt.order_by(AuditLog.id.desc()).offset(pg.offset).limit(pg.page_size)).all()
    return paginated([{"id": a.id, "actor": a.actor_user_id, "action": a.action, "entity": a.entity, "entity_id": a.entity_id,
                       "detail": a.detail, "ip": a.ip, "at": a.created_at.isoformat()} for a in rows], total, pg)


@router.get("/subscriptions")
def admin_subs(status: Optional[str] = None, page: int = 1, page_size: int = 50, db: Session = Depends(get_db)):
    pg = Page(page, page_size)
    stmt = select(Subscription, User).join(User, User.id == Subscription.user_id)
    if status:
        stmt = stmt.where(Subscription.status == status)
    total = db.scalar(select(func.count()).select_from(stmt.subquery()))
    rows = db.execute(stmt.order_by(Subscription.id.desc()).offset(pg.offset).limit(pg.page_size)).unique().all()
    return paginated([{"id": s.id, "user": u.email, "plan": s.plan.code, "provider": s.provider, "status": s.status, "is_test": s.is_test,
                       "period_end": s.current_period_end.isoformat() if s.current_period_end else None} for s, u in rows], total, pg)


# ---------------- calidad de modelos ----------------
@router.get("/models")
def models(db: Session = Depends(get_db)):
    mvs = db.scalars(select(ModelVersion)).unique().all()
    ev = db.execute(select(PredictionEvaluation, Sport.code).join(Sport, Sport.id == PredictionEvaluation.sport_id)
                    .where(PredictionEvaluation.market.in_(("ALL", "ALL@>=0.60")))).all()
    lg = {l.id: l.name for l in db.scalars(select(League))}
    return {"versions": [{"id": m.id, "model": m.model.code, "version": m.version, "active": m.is_active, "params": m.params,
                          "backtest": m.metrics} for m in mvs],
            "evaluations": [{"sport": sc, "model_version_id": e.model_version_id, "league": lg.get(e.league_id, "Todas"), "slice": e.market,
                             "n": e.n, "hit_rate": float(e.hit_rate), "brier": float(e.brier), "log_loss": float(e.log_loss),
                             "mean_probability": float(e.mean_probability)} for e, sc in ev]}


class BacktestIn(BaseModel):
    sport: str = Field(pattern="^(football|basketball)$")
    league_id: Optional[int] = None
    test_fraction: float = Field(default=0.3, gt=0.05, lt=0.8)


@router.post("/models/backtest", summary="Backtesting walk-forward con datos de la BD")
def run_backtest(body: BacktestIn, admin: User = Depends(require_admin), db: Session = Depends(get_db)):
    from ...services.analysis import ensure_model_version
    stmt = select(HistoricalResult).join(Sport, Sport.id == HistoricalResult.sport_id).where(Sport.code == body.sport)
    if body.league_id:
        stmt = stmt.where(HistoricalResult.league_id == body.league_id)
    rows = db.scalars(stmt.order_by(HistoricalResult.played_at)).all()
    if len(rows) < 60:
        raise AppError("Se necesitan al menos 60 resultados históricos para un backtest", code="insufficient_data", status_code=422)
    cut = rows[int(len(rows) * (1 - body.test_fraction))].played_at
    if body.sport == "football":
        recs = [fbm.MatchRecord(r.played_at, f"t{r.home_team_id}", f"t{r.away_team_id}", r.home_score, r.away_score,
                                (r.extra or {}).get("home_corners"), (r.extra or {}).get("away_corners"), (r.extra or {}).get("home_cards"),
                                (r.extra or {}).get("away_cards"), (r.extra or {}).get("ht_home"), (r.extra or {}).get("ht_away")) for r in rows]
        res = bt.backtest_football(recs, cut)
    else:
        recs = [bbm.GameRecord(r.played_at, f"t{r.home_team_id}", f"t{r.away_team_id}", r.home_score, r.away_score,
                               (r.extra or {}).get("home_poss"), (r.extra or {}).get("away_poss")) for r in rows]
        res = bt.backtest_basketball(recs, cut)
    res.pop("predictions", None)
    mv = ensure_model_version(db, body.sport)
    mv.metrics = {"computed_at": _now().isoformat(), "train_until": cut.isoformat(), "league_id": body.league_id, **res}
    audit(db, "admin.backtest", actor_id=admin.id, entity="model_version", entity_id=mv.id)
    return mv.metrics
