from __future__ import annotations

from datetime import datetime, timedelta, timezone
from typing import Optional

from fastapi import APIRouter, Depends, Request
from pydantic import BaseModel, Field
from sqlalchemy import select
from sqlalchemy.orm import Session

from ...core.errors import AppError
from ...db.models import DailyUsage, DeviceToken, Fixture, Plan, PredictionOutcome, Prediction, SavedPrediction, User
from ...db.session import get_db
from ...schemas.common import serialize_user
from ...services import accounts
from ...services.plans import effective_plan, usage_summary
from ..deps import Page, current_plan, get_current_user, ip_of, paginated
from .serializers import fixture_out

router = APIRouter(prefix="/me", tags=["Perfil y uso"])

TZ_CHANGE_COOLDOWN_DAYS = 7     # evita evadir la renovación de cuota cambiando la zona horaria


class ProfilePatch(BaseModel):
    display_name: Optional[str] = Field(default=None, max_length=80)
    country: Optional[str] = Field(default=None, min_length=2, max_length=2)
    language: Optional[str] = Field(default=None, pattern="^(es|en|fr|ht)$")
    timezone: Optional[str] = Field(default=None, max_length=64)
    theme: Optional[str] = Field(default=None, pattern="^(light|dark|system)$")
    preferences: Optional[dict] = None


class PasswordChange(BaseModel):
    current_password: str
    new_password: str = Field(min_length=10, max_length=128)


class DeleteAccount(BaseModel):
    password: Optional[str] = None
    confirm: bool


class DeviceIn(BaseModel):
    token: str = Field(min_length=10, max_length=300)
    platform: str = Field(default="android", pattern="^(android|web)$")


@router.get("", summary="Perfil, plan y consumo del usuario actual")
def me(user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    return serialize_user(db, user)


@router.patch("", summary="Actualizar perfil/preferencias")
def patch_me(body: ProfilePatch, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    p = user.profile
    if body.timezone and body.timezone != p.timezone:
        accounts.validate_timezone(body.timezone)
        last = (p.preferences or {}).get("tz_changed_at")
        if last and datetime.fromisoformat(last) > datetime.now(timezone.utc) - timedelta(days=TZ_CHANGE_COOLDOWN_DAYS):
            raise AppError(f"La zona horaria de la cuenta sólo puede cambiarse cada {TZ_CHANGE_COOLDOWN_DAYS} días "
                           "(define la renovación de tu límite diario).", code="timezone_cooldown", status_code=429)
        p.timezone = body.timezone
        p.preferences = {**(p.preferences or {}), "tz_changed_at": datetime.now(timezone.utc).isoformat()}
    for f in ("display_name", "country", "language", "theme"):
        v = getattr(body, f)
        if v is not None:
            setattr(p, f, v.upper() if f == "country" else v)
    if body.preferences is not None:
        keep = {k: v for k, v in (p.preferences or {}).items() if k == "tz_changed_at"}
        p.preferences = {**body.preferences, **keep}
    return serialize_user(db, user)


@router.post("/change-password", status_code=204)
def change_password(body: PasswordChange, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    accounts.change_password(db, user, body.current_password, body.new_password)


@router.delete("", status_code=204, summary="Eliminar cuenta y datos asociados")
def delete_me(body: DeleteAccount, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    if not body.confirm:
        raise AppError("Debes confirmar la eliminación", code="confirm_required", status_code=422)
    accounts.delete_account(db, user, body.password)


@router.get("/usage", summary="Consultas usadas y restantes del día (zona horaria de la cuenta)")
def usage(user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    return usage_summary(db, user)


@router.get("/history", summary="Historial de partidos analizados (ventana según plan)")
def history(page: int = 1, page_size: int = 20, user: User = Depends(get_current_user), db: Session = Depends(get_db),
            plan: Plan = Depends(current_plan)):
    pg = Page(page, page_size)
    q = select(DailyUsage, Fixture).join(Fixture, Fixture.id == DailyUsage.fixture_id).where(DailyUsage.user_id == user.id)
    if plan.history_days is not None:
        q = q.where(DailyUsage.created_at >= datetime.now(timezone.utc) - timedelta(days=plan.history_days))
    rows = db.execute(q.order_by(DailyUsage.created_at.desc())).all()
    items = [{"date": u.usage_date.isoformat(), "viewed_at": u.created_at.isoformat(), "fixture": fixture_out(f)}
             for u, f in rows[pg.offset: pg.offset + pg.page_size]]
    return {**paginated(items, len(rows), pg), "history_days": plan.history_days}


@router.get("/comparison", summary="Pronósticos guardados vs resultados reales (Pro)")
def comparison(user: User = Depends(get_current_user), db: Session = Depends(get_db), plan: Plan = Depends(current_plan)):
    from ...services.plans import require_feature
    require_feature(plan, "compare_results", "La comparación de pronósticos y resultados es una función Pro.")
    rows = db.execute(select(SavedPrediction, Prediction, PredictionOutcome, Fixture)
                      .join(Prediction, Prediction.id == SavedPrediction.prediction_id)
                      .join(Fixture, Fixture.id == Prediction.fixture_id)
                      .outerjoin(PredictionOutcome, PredictionOutcome.prediction_id == Prediction.id)
                      .where(SavedPrediction.user_id == user.id).order_by(SavedPrediction.created_at.desc())).all()
    items, settled, hits = [], 0, 0
    for sp, pr, oc, fx in rows:
        if oc is not None:
            settled += 1; hits += int(oc.hit)
        items.append({"saved_id": sp.id, "fixture": fixture_out(fx), "market": pr.market, "selection": pr.selection,
                      "probability": float(pr.probability), "is_pre_match": pr.is_pre_match,
                      "result": None if oc is None else ("hit" if oc.hit else "miss"),
                      "final_score": None if fx.home_score is None else f"{fx.home_score}-{fx.away_score}"})
    return {"items": items, "summary": {"saved": len(rows), "settled": settled, "hits": hits,
                                        "hit_rate": (hits / settled) if settled else None,
                                        "note": "Resultados pasados; no garantizan resultados futuros."}}


@router.post("/devices", status_code=204, summary="Registrar token FCM")
def register_device(body: DeviceIn, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    row = db.scalar(select(DeviceToken).where(DeviceToken.token == body.token))
    if row:
        row.user_id = user.id
    else:
        db.add(DeviceToken(user_id=user.id, token=body.token, platform=body.platform))
