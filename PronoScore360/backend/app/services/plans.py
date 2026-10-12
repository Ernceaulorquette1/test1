"""Plan efectivo del usuario, cuotas diarias y control de consumo (todo en servidor)."""
from __future__ import annotations

from datetime import date, datetime, timedelta, timezone
from zoneinfo import ZoneInfo

from sqlalchemy import func, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from ..core.errors import AppError, PaymentRequired, QuotaExceeded
from ..db.models import DailyUsage, Fixture, Plan, Subscription, User

ENTITLED_STATUSES = ("active", "canceled", "grace_period")


def utcnow() -> datetime:
    return datetime.now(timezone.utc)


def get_plan(db: Session, code: str) -> Plan:
    p = db.scalar(select(Plan).where(Plan.code == code))
    if not p:
        raise AppError(f"Plan {code} no configurado", code="plan_missing", status_code=500)
    return p


def active_subscription(db: Session, user_id: int, now: datetime | None = None) -> Subscription | None:
    """Suscripción que otorga acceso Pro AHORA según el servidor (nunca según el dispositivo)."""
    now = now or utcnow()
    subs = db.scalars(select(Subscription).where(Subscription.user_id == user_id,
                                                 Subscription.status.in_(ENTITLED_STATUSES))
                      .order_by(Subscription.id.desc())).all()
    for s in subs:
        if s.status == "grace_period":
            if s.grace_until and s.grace_until > now:
                return s
        elif s.current_period_end is None or s.current_period_end > now:   # active / canceled hasta fin de periodo
            return s
    return None


def effective_plan(db: Session, user: User, now: datetime | None = None) -> Plan:
    if user.role.code == "ADMIN":
        return get_plan(db, "PRO")
    sub = active_subscription(db, user.id, now)
    return sub.plan if sub else get_plan(db, "FREE")


def account_today(user: User, now: datetime | None = None) -> date:
    """Fecha 'de cuenta' calculada con el reloj del SERVIDOR y la zona horaria de la cuenta."""
    now = now or utcnow()
    return now.astimezone(ZoneInfo(user.profile.timezone)).date()


def next_reset(user: User, now: datetime | None = None) -> datetime:
    now = now or utcnow()
    tz = ZoneInfo(user.profile.timezone)
    local = now.astimezone(tz)
    nxt = datetime.combine(local.date() + timedelta(days=1), datetime.min.time(), tzinfo=tz)
    return nxt.astimezone(timezone.utc)


def usage_summary(db: Session, user: User, plan: Plan | None = None, now: datetime | None = None) -> dict:
    plan = plan or effective_plan(db, user, now)
    today = account_today(user, now)
    used = db.scalar(select(func.count()).select_from(DailyUsage).where(DailyUsage.user_id == user.id,
                                                                      DailyUsage.usage_date == today)) or 0
    limit = plan.daily_analysis_limit
    return {"plan": plan.code, "date": today.isoformat(), "timezone": user.profile.timezone, "limit": limit,
            "used": used, "remaining": None if limit is None else max(limit - used, 0),
            "resets_at": next_reset(user, now).isoformat()}


def consume_analysis(db: Session, user: User, fixture_id: int, analysis_id: int | None, plan: Plan,
                     now: datetime | None = None) -> dict:
    """Cobra UNA consulta por partido y día. Repetir el mismo partido el mismo día no vuelve a cobrar."""
    today = account_today(user, now)
    exists = db.scalar(select(DailyUsage.id).where(DailyUsage.user_id == user.id, DailyUsage.usage_date == today,
                                                   DailyUsage.fixture_id == fixture_id))
    if exists is None:
        limit = plan.daily_analysis_limit
        if limit is not None:
            used = db.scalar(select(func.count()).select_from(DailyUsage).where(
                DailyUsage.user_id == user.id, DailyUsage.usage_date == today)) or 0
            if used >= limit:
                raise QuotaExceeded(
                    f"Alcanzaste el límite de {limit} partidos analizados hoy en el plan {plan.name}. "
                    f"Se renueva a las 00:00 (zona {user.profile.timezone}). Pasa a Pro para análisis ilimitados.",
                    extra={"usage": usage_summary(db, user, plan, now)})
        try:
            with db.begin_nested():
                db.add(DailyUsage(user_id=user.id, usage_date=today, fixture_id=fixture_id, analysis_id=analysis_id,
                                  plan_code=plan.code))
        except IntegrityError:    # carrera: otra petición ya lo registró
            pass
    return usage_summary(db, user, plan, now)


def require_feature(plan: Plan, feature: str, message: str) -> None:
    if not (plan.features or {}).get(feature):
        raise PaymentRequired(message)
