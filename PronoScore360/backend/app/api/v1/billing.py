from __future__ import annotations

from typing import Optional

from fastapi import APIRouter, Depends, Header, Request
from pydantic import BaseModel, Field
from sqlalchemy import select
from sqlalchemy.orm import Session

from ...core.config import get_settings
from ...core.errors import AppError, Forbidden
from ...db.models import PaymentTransaction, Plan, Subscription, User
from ...db.session import get_db
from ...schemas.common import plan_out, serialize_user
from ...services.billing import service as billing
from ...services.billing.gateway import get_gateway
from ...services.billing.google_play import GooglePlayClient, verify_pubsub_jwt
from ..deps import get_current_user

router = APIRouter(tags=["Planes y suscripciones"])


def get_play_client() -> GooglePlayClient:      # sobreescribible en pruebas
    return GooglePlayClient()


class PurchaseIn(BaseModel):
    purchase_token: str = Field(min_length=10, max_length=4096)


class RestoreIn(BaseModel):
    purchase_tokens: list[str] = Field(min_length=1, max_length=10)


class CheckoutIn(BaseModel):
    plan_code: str = "PRO"


class DemoEventIn(BaseModel):
    action: str = Field(pattern="^(renew|cancel|expire|refund|grace|hold)$")


def sub_out(s: Subscription) -> dict:
    return {"id": s.id, "plan": s.plan.code, "provider": s.provider, "status": s.status, "auto_renew": s.auto_renew,
            "started_at": s.started_at.isoformat(), "current_period_end": s.current_period_end.isoformat() if s.current_period_end else None,
            "grace_until": s.grace_until.isoformat() if s.grace_until else None, "is_test": s.is_test}


@router.get("/plans", summary="Planes y precios (configurables en BD)")
def plans(db: Session = Depends(get_db)):
    return [plan_out(p) for p in db.scalars(select(Plan).where(Plan.is_active.is_(True)).order_by(Plan.sort_order))]


@router.get("/subscriptions/me", summary="Suscripciones del usuario y plan efectivo")
def my_subscription(user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    subs = db.scalars(select(Subscription).where(Subscription.user_id == user.id).order_by(Subscription.id.desc())).all()
    return {"effective": serialize_user(db, user), "subscriptions": [sub_out(s) for s in subs]}


@router.get("/billing/account-id", summary="Identificador ofuscado para setObfuscatedAccountId (Google Play)")
def account_id(user: User = Depends(get_current_user)):
    return {"obfuscated_account_id": billing.account_hash(user.id)}


@router.post("/subscriptions/google/verify", summary="Verificar compra de Google Play en el servidor y activar Pro")
def google_verify(body: PurchaseIn, user: User = Depends(get_current_user), db: Session = Depends(get_db),
                  client: GooglePlayClient = Depends(get_play_client)):
    sub = billing.verify_and_activate(db, user, body.purchase_token, client)
    return {"subscription": sub_out(sub), "effective": serialize_user(db, user)}


@router.post("/subscriptions/google/restore", summary="Restaurar compras (re-verifica tokens en el servidor)")
def google_restore(body: RestoreIn, user: User = Depends(get_current_user), db: Session = Depends(get_db),
                   client: GooglePlayClient = Depends(get_play_client)):
    restored, failed = [], []
    for t in body.purchase_tokens:
        try:
            restored.append(sub_out(billing.verify_and_activate(db, user, t, client)))
        except AppError as e:
            failed.append({"error": e.code, "message": e.message})
    return {"restored": restored, "failed": failed, "effective": serialize_user(db, user)}


@router.post("/webhooks/google-play", summary="RTDN de Google Play (Pub/Sub push)", include_in_schema=True)
def rtdn(body: dict, authorization: Optional[str] = Header(default=None), db: Session = Depends(get_db),
         client: GooglePlayClient = Depends(get_play_client)):
    s = get_settings()
    if not verify_pubsub_jwt(authorization):
        raise Forbidden("Notificación no autenticada", code="rtdn_unauthenticated")
    return billing.handle_rtdn(db, body, client)


@router.post("/subscriptions/checkout", summary="Iniciar pago web (pasarela configurable)")
def checkout(body: CheckoutIn, user: User = Depends(get_current_user)):
    cs = get_gateway().create_checkout(user.id, body.plan_code, user.profile.country)
    return {"provider": cs.provider, "session_id": cs.session_id, "url": cs.url, "is_test": cs.is_test}


@router.post("/subscriptions/demo/activate", summary="[SÓLO LOCAL/TEST] Simula pago y activa Pro")
def demo_activate(user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    sub = billing.demo_activate(db, user)
    return {"subscription": sub_out(sub), "effective": serialize_user(db, user)}


@router.post("/subscriptions/demo/event", summary="[SÓLO LOCAL/TEST] Simula renovación/cancelación/vencimiento/reembolso")
def demo_event(body: DemoEventIn, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    sub = billing.demo_event(db, user, body.action)
    return {"subscription": sub_out(sub), "effective": serialize_user(db, user)}


@router.get("/subscriptions/transactions", summary="Transacciones del usuario")
def transactions(user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    rows = db.scalars(select(PaymentTransaction).where(PaymentTransaction.user_id == user.id).order_by(PaymentTransaction.id.desc())).all()
    return [{"id": t.id, "provider": t.provider, "amount": float(t.amount), "currency": t.currency, "status": t.status,
             "is_test": t.is_test, "created_at": t.created_at.isoformat()} for t in rows]
