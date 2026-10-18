from __future__ import annotations

import hashlib
from datetime import datetime, timedelta, timezone
from decimal import Decimal
from typing import Optional

from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from ...core.config import get_settings
from ...core.errors import AppError, Conflict, Forbidden
from ...db.models import BillingEvent, PaymentTransaction, Plan, Subscription, User
from ..audit import audit
from ..plans import get_plan
from .google_play import GooglePlayClient, VerifiedPurchase, decode_pubsub, RTDN_TYPES


def now() -> datetime:
    return datetime.now(timezone.utc)


def token_id(purchase_token: str) -> str:
    return hashlib.sha256(purchase_token.encode()).hexdigest()


def account_hash(user_id: int) -> str:
    """Valor para setObfuscatedAccountId en Android: vincula la compra con la cuenta sin exponer el id real."""
    return hashlib.sha256(f"{get_settings().jwt_secret}:acct:{user_id}".encode()).hexdigest()[:32]


def _record_payment(db: Session, sub: Subscription, v: VerifiedPurchase) -> None:
    if not v.order_id:
        return
    exists = db.scalar(select(PaymentTransaction.id).where(PaymentTransaction.provider == "google_play",
                                                            PaymentTransaction.external_id == v.order_id))
    if exists is None and v.status in ("active", "canceled", "grace_period"):
        price = (sub.plan.prices or {}).get("CL") or {}
        db.add(PaymentTransaction(user_id=sub.user_id, subscription_id=sub.id, provider="google_play",
                                  external_id=v.order_id, amount=Decimal(str(price.get("amount", 0))),
                                  currency=price.get("currency", "USD"), status="succeeded", is_test=v.is_test))


def apply_verified_purchase(db: Session, user: User, purchase_token: str, v: VerifiedPurchase,
                            client: Optional[GooglePlayClient] = None) -> Subscription:
    s = get_settings()
    plan = db.scalar(select(Plan).where(Plan.google_product_id == v.product_id, Plan.is_active.is_(True)))
    if plan is None:
        raise AppError(f"Producto desconocido: {v.product_id}", code="unknown_product", status_code=400)
    if v.obfuscated_account_id and v.obfuscated_account_id != account_hash(user.id):
        raise Forbidden("La compra pertenece a otra cuenta", code="purchase_account_mismatch")
    tid = token_id(purchase_token)
    sub = db.scalar(select(Subscription).where(Subscription.provider == "google_play", Subscription.external_id == tid))
    if sub and sub.user_id != user.id:
        raise Conflict("Esta compra ya está vinculada a otra cuenta", code="purchase_already_linked")
    if sub is None:
        sub = Subscription(user_id=user.id, plan_id=plan.id, provider="google_play", status=v.status, external_id=tid)
        db.add(sub)
    sub.plan_id, sub.status, sub.current_period_end = plan.id, v.status, v.expiry
    sub.auto_renew, sub.external_order_id, sub.is_test = v.auto_renew, v.order_id, v.is_test
    if v.status == "grace_period":
        sub.grace_until = v.expiry + timedelta(days=7) if v.expiry else None   # el fin real lo marca Google (expiryTime ampliado)
    if v.status == "canceled" and not sub.canceled_at:
        sub.canceled_at = now()
    db.flush()
    _record_payment(db, sub, v)
    if not v.acknowledged and v.status in ("active", "canceled", "grace_period") and client is not None:
        client.acknowledge(v.product_id, purchase_token)
    audit(db, "billing.subscription_synced", actor_id=user.id, entity="subscription", entity_id=sub.id,
          detail={"status": v.status, "provider": "google_play"})
    return sub


def verify_and_activate(db: Session, user: User, purchase_token: str, client: GooglePlayClient) -> Subscription:
    """El plan Pro SOLO se activa tras consultar a Google Play desde el servidor."""
    v = client.get_subscription(purchase_token)
    if v.status not in ("active", "canceled", "grace_period"):
        raise AppError(f"La compra no está activa (estado: {v.raw_state or v.status})", code="purchase_not_active", status_code=402)
    return apply_verified_purchase(db, user, purchase_token, v, client)


def handle_rtdn(db: Session, body: dict, client: GooglePlayClient) -> dict:
    """Procesa una notificación de Pub/Sub. Se vuelve a consultar el estado en Google (no se confía en el payload)."""
    message_id, data = decode_pubsub(body)
    event_id = message_id or hashlib.sha256(str(data).encode()).hexdigest()
    sn = data.get("subscriptionNotification")
    ev = BillingEvent(provider="google_play", event_id=event_id,
                      event_type=RTDN_TYPES.get((sn or {}).get("notificationType"), "TEST" if "testNotification" in data else "OTHER"),
                      payload=data)
    try:
        with db.begin_nested():
            db.add(ev); db.flush()
    except IntegrityError:
        return {"status": "duplicate"}
    if not sn:
        ev.processed = True
        return {"status": "ignored"}
    token = sn["purchaseToken"]
    sub = db.scalar(select(Subscription).where(Subscription.provider == "google_play", Subscription.external_id == token_id(token)))
    if sub is None:
        ev.error = "suscripción desconocida (compra aún no enlazada por la app)"
        return {"status": "unknown_subscription"}
    ev.user_id = sub.user_id
    try:
        v = client.get_subscription(token)
        v_status = "refunded" if sn.get("notificationType") == 12 else v.status
        v.status = v_status
        apply_verified_purchase(db, db.get(User, sub.user_id), token, v, client)
        if v_status == "refunded":
            for t in db.scalars(select(PaymentTransaction).where(PaymentTransaction.subscription_id == sub.id)):
                t.status = "refunded"
        ev.processed = True
    except AppError as e:
        ev.error = e.message
        raise
    return {"status": "processed", "type": ev.event_type}


# ---------------- simulador de desarrollo (nunca en producción) ----------------
def demo_activate(db: Session, user: User, days: int = 30) -> Subscription:
    s = get_settings()
    if not (s.is_dev and s.payments_provider == "demo"):
        raise Forbidden("El simulador de pagos sólo existe en entornos locales/de prueba", code="demo_disabled")
    plan = get_plan(db, "PRO")
    ext = f"demo-{user.id}"
    sub = db.scalar(select(Subscription).where(Subscription.provider == "demo", Subscription.external_id == ext))
    end = now() + timedelta(days=days)
    if sub is None:
        sub = Subscription(user_id=user.id, plan_id=plan.id, provider="demo", external_id=ext, status="active",
                           current_period_end=end, is_test=True)
        db.add(sub)
    else:
        sub.status, sub.current_period_end, sub.canceled_at, sub.auto_renew = "active", end, None, True
    db.flush()
    db.add(PaymentTransaction(user_id=user.id, subscription_id=sub.id, provider="demo", external_id=f"demo-{user.id}-{now().timestamp()}",
                              amount=Decimal("0"), currency="USD", status="succeeded", is_test=True))
    audit(db, "billing.demo_activate", actor_id=user.id, entity="subscription", entity_id=sub.id)
    return sub


def demo_event(db: Session, user: User, action: str) -> Subscription:
    s = get_settings()
    if not (s.is_dev and s.payments_provider == "demo"):
        raise Forbidden("El simulador de pagos sólo existe en entornos locales/de prueba", code="demo_disabled")
    sub = db.scalar(select(Subscription).where(Subscription.user_id == user.id, Subscription.provider == "demo")
                    .order_by(Subscription.id.desc()))
    if sub is None:
        raise AppError("No hay suscripción demo", code="no_subscription", status_code=404)
    t = now()
    if action == "renew":
        sub.status, sub.current_period_end = "active", max(sub.current_period_end or t, t) + timedelta(days=30)
    elif action == "cancel":
        sub.status, sub.auto_renew, sub.canceled_at = "canceled", False, t
    elif action == "expire":
        sub.status, sub.current_period_end = "expired", t - timedelta(seconds=1)
    elif action == "refund":
        sub.status = "refunded"
    elif action == "grace":
        sub.status, sub.grace_until = "grace_period", t + timedelta(days=3)
    elif action == "hold":
        sub.status = "on_hold"
    else:
        raise AppError("Acción demo inválida", code="invalid_action", status_code=422)
    audit(db, f"billing.demo_{action}", actor_id=user.id, entity="subscription", entity_id=sub.id)
    return sub


def expire_overdue(db: Session) -> int:
    """Job: pasa a 'expired' las suscripciones cuyo periodo/gracia terminó."""
    t = now(); n = 0
    for sub in db.scalars(select(Subscription).where(Subscription.status.in_(("active", "canceled", "grace_period")))):
        end = sub.grace_until if sub.status == "grace_period" else sub.current_period_end
        if end is not None and end <= t:
            sub.status = "expired"; n += 1
    return n
