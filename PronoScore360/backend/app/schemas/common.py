from __future__ import annotations

from typing import Any, Optional

from pydantic import BaseModel
from sqlalchemy.orm import Session

from ..db.models import User
from ..services.plans import effective_plan, active_subscription, usage_summary


class TokenPair(BaseModel):
    access_token: str
    refresh_token: str
    token_type: str = "bearer"
    expires_in: int


class PlanOut(BaseModel):
    code: str
    name: str
    daily_analysis_limit: Optional[int]
    favorites_limit: Optional[int]
    history_days: Optional[int]
    selections_enabled: bool
    allowed_market_groups: list[str]
    features: dict[str, Any]
    prices: dict[str, Any]
    google_product_id: Optional[str] = None


class UserOut(BaseModel):
    id: int
    email: str
    display_name: str
    role: str
    email_verified: bool
    country: str
    language: str
    timezone: str
    theme: str
    plan: PlanOut
    subscription_status: Optional[str]
    subscription_ends_at: Optional[str]
    usage: dict[str, Any]
    is_demo: bool


def plan_out(p) -> dict:
    return {"code": p.code, "name": p.name, "daily_analysis_limit": p.daily_analysis_limit,
            "favorites_limit": p.favorites_limit, "history_days": p.history_days,
            "selections_enabled": p.selections_enabled, "allowed_market_groups": p.allowed_market_groups or [],
            "features": p.features or {}, "prices": p.prices or {}, "google_product_id": p.google_product_id}


def serialize_user(db: Session, u: User) -> dict:
    """Estado del plan siempre calculado por el servidor: Android y web ven lo mismo."""
    plan = effective_plan(db, u)
    sub = active_subscription(db, u.id)
    return {"id": u.id, "email": u.email, "display_name": u.profile.display_name, "role": u.role.code,
            "email_verified": u.email_verified, "country": u.profile.country, "language": u.profile.language,
            "timezone": u.profile.timezone, "theme": u.profile.theme, "plan": plan_out(plan),
            "subscription_status": sub.status if sub else None,
            "subscription_ends_at": (sub.current_period_end.isoformat() if sub and sub.current_period_end else None),
            "usage": usage_summary(db, u, plan), "is_demo": u.is_demo}
