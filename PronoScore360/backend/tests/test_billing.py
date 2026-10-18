import base64
import json
from datetime import datetime, timedelta, timezone

import pytest
from sqlalchemy import select

from app.api.v1.billing import get_play_client
from app.db.models import BillingEvent, PaymentTransaction, Subscription
from app.services.billing import service as billing
from app.services.billing.google_play import GooglePlayClient, VerifiedPurchase, parse_subscription_v2

B = "/api/v1"


def plan_of(client, h):
    return client.get(f"{B}/me", headers=h).json()["plan"]["code"]


class FakePlay(GooglePlayClient):
    def __init__(self):
        self.purchases: dict[str, VerifiedPurchase] = {}
        self.acked: list[tuple] = []

    def get_subscription(self, token):
        from app.core.errors import AppError
        if token not in self.purchases:
            raise AppError("Compra no encontrada", code="play_invalid_token", status_code=400)
        return self.purchases[token]

    def acknowledge(self, product_id, token):
        self.acked.append((product_id, token))


def purchase(status="active", user_id=None, **kw):
    d = dict(status=status, product_id="pronoscore360_pro_monthly", expiry=datetime.now(timezone.utc) + timedelta(days=30),
             order_id=f"GPA.{__import__('uuid').uuid4().hex[:12]}", acknowledged=False, auto_renew=True,
             obfuscated_account_id=billing.account_hash(user_id) if user_id else None, is_test=True, raw_state="SUBSCRIPTION_STATE_ACTIVE")
    d.update(kw)
    return VerifiedPurchase(**d)


@pytest.fixture()
def play(app):
    fake = FakePlay()
    app.dependency_overrides[get_play_client] = lambda: fake
    yield fake
    app.dependency_overrides.pop(get_play_client, None)


# ---------- simulador de desarrollo ----------
def test_demo_checkout_activates_pro_then_lifecycle(client, user):
    h = user["headers"]
    assert plan_of(client, h) == "FREE"
    r = client.post(f"{B}/subscriptions/demo/activate", headers=h).json()
    assert r["subscription"]["is_test"] and r["effective"]["plan"]["code"] == "PRO"
    assert plan_of(client, h) == "PRO"
    ev = lambda a: client.post(f"{B}/subscriptions/demo/event", headers=h, json={"action": a}).json()
    assert ev("cancel")["subscription"]["status"] == "canceled" and plan_of(client, h) == "PRO"   # acceso hasta fin de periodo
    assert ev("renew")["subscription"]["status"] == "active"
    ev("grace"); assert plan_of(client, h) == "PRO"
    ev("hold"); assert plan_of(client, h) == "FREE"
    ev("renew"); assert plan_of(client, h) == "PRO"
    ev("expire"); assert plan_of(client, h) == "FREE"
    ev("renew"); ev("refund"); assert plan_of(client, h) == "FREE"
    assert client.post(f"{B}/subscriptions/demo/event", headers=h, json={"action": "boom"}).status_code == 422
    sub = client.get(f"{B}/subscriptions/me", headers=h).json()
    assert sub["subscriptions"][0]["provider"] == "demo"
    assert all(t["is_test"] for t in client.get(f"{B}/subscriptions/transactions", headers=h).json())


def test_demo_simulator_disabled_outside_dev(client, user):
    from app.core.config import get_settings
    s = get_settings(); old = s.env
    s.env = "production"
    try:
        assert client.post(f"{B}/subscriptions/demo/activate", headers=user["headers"]).status_code == 403
        assert client.post(f"{B}/subscriptions/checkout", headers=user["headers"], json={"plan_code": "PRO"}).status_code == 501
    finally:
        s.env = old
    assert plan_of(client, user["headers"]) == "FREE"


def test_pro_access_unlocks_and_expiry_relocks(client, user, fixtures_info):
    h = user["headers"]
    assert client.get(f"{B}/predictions", headers=h).status_code == 402
    client.post(f"{B}/subscriptions/demo/activate", headers=h)
    assert client.get(f"{B}/predictions", headers=h).status_code == 200
    client.post(f"{B}/subscriptions/demo/event", headers=h, json={"action": "expire"})
    assert client.get(f"{B}/predictions", headers=h).status_code == 402


def test_expired_by_clock_is_not_pro(client, user, db):
    """Un periodo vencido según el reloj del servidor no da acceso aunque el estado siga 'active'."""
    client.post(f"{B}/subscriptions/demo/activate", headers=user["headers"])
    s = db.scalar(select(Subscription).where(Subscription.user_id == user["id"]))
    s.current_period_end = datetime.now(timezone.utc) - timedelta(minutes=1); db.commit()
    assert plan_of(client, user["headers"]) == "FREE"
    assert billing.expire_overdue(db) >= 1


# ---------- Google Play ----------
def test_google_verify_activates_only_after_server_check(client, user, play):
    h = user["headers"]
    tok = "tok-" + "a" * 30
    # el cliente "dice" que pagó pero Google no conoce el token
    bad = client.post(f"{B}/subscriptions/google/verify", headers=h, json={"purchase_token": tok})
    assert bad.status_code == 400 and plan_of(client, h) == "FREE"
    play.purchases[tok] = purchase(user_id=user["id"])
    ok = client.post(f"{B}/subscriptions/google/verify", headers=h, json={"purchase_token": tok}).json()
    assert ok["subscription"]["provider"] == "google_play" and ok["effective"]["plan"]["code"] == "PRO"
    assert play.acked == [("pronoscore360_pro_monthly", tok)]                    # acknowledge ejecutado
    assert plan_of(client, h) == "PRO"


def test_google_verify_rejections(client, make_user, play):
    a, b = make_user(), make_user()
    tok = "tok-" + "b" * 30
    play.purchases[tok] = purchase(user_id=a["id"])
    assert client.post(f"{B}/subscriptions/google/verify", headers=b["headers"], json={"purchase_token": tok}).status_code == 403  # cuenta distinta
    play.purchases[tok] = purchase(user_id=a["id"], acknowledged=True)
    assert client.post(f"{B}/subscriptions/google/verify", headers=a["headers"], json={"purchase_token": tok}).status_code == 200
    play.purchases[tok] = purchase(user_id=None, acknowledged=True)
    assert client.post(f"{B}/subscriptions/google/verify", headers=b["headers"], json={"purchase_token": tok}).status_code == 409   # ya vinculada
    t2 = "tok-" + "c" * 30
    play.purchases[t2] = purchase(status="expired", user_id=b["id"], raw_state="SUBSCRIPTION_STATE_EXPIRED")
    assert client.post(f"{B}/subscriptions/google/verify", headers=b["headers"], json={"purchase_token": t2}).status_code == 402
    t3 = "tok-" + "d" * 30
    play.purchases[t3] = purchase(user_id=b["id"], product_id="otro_producto")
    assert client.post(f"{B}/subscriptions/google/verify", headers=b["headers"], json={"purchase_token": t3}).status_code == 400
    assert plan_of(client, b["headers"]) == "FREE"


def test_google_restore(client, user, play):
    t1, t2 = "tok-" + "e" * 30, "tok-" + "f" * 30
    play.purchases[t1] = purchase(user_id=user["id"], acknowledged=True)
    r = client.post(f"{B}/subscriptions/google/restore", headers=user["headers"], json={"purchase_tokens": [t1, t2]}).json()
    assert len(r["restored"]) == 1 and len(r["failed"]) == 1 and r["effective"]["plan"]["code"] == "PRO"


def _rtdn(ntype, token, mid):
    data = {"version": "1.0", "packageName": "com.pronoscore360.app", "eventTimeMillis": "1",
            "subscriptionNotification": {"version": "1.0", "notificationType": ntype, "purchaseToken": token, "subscriptionId": "pronoscore360_pro_monthly"}}
    return {"message": {"data": base64.b64encode(json.dumps(data).encode()).decode(), "messageId": mid}, "subscription": "x"}


def test_rtdn_requires_authentication(client):
    assert client.post(f"{B}/webhooks/google-play", json=_rtdn(2, "t" * 20, "m1")).status_code == 403


def test_rtdn_lifecycle_refund_cancel_expire(client, user, play, monkeypatch, db):
    monkeypatch.setattr("app.api.v1.billing.verify_pubsub_jwt", lambda auth: True)
    h, tok = user["headers"], "tok-" + "g" * 30
    play.purchases[tok] = purchase(user_id=user["id"], acknowledged=True)
    client.post(f"{B}/subscriptions/google/verify", headers=h, json={"purchase_token": tok})
    assert plan_of(client, h) == "PRO"
    # cancelación: sigue con acceso hasta expirar
    play.purchases[tok] = purchase(status="canceled", user_id=user["id"], acknowledged=True, auto_renew=False)
    assert client.post(f"{B}/webhooks/google-play", json=_rtdn(3, tok, "m-cancel")).json()["status"] == "processed"
    assert plan_of(client, h) == "PRO"
    # duplicado idempotente
    assert client.post(f"{B}/webhooks/google-play", json=_rtdn(3, tok, "m-cancel")).json()["status"] == "duplicate"
    # periodo de gracia / retención
    play.purchases[tok] = purchase(status="on_hold", user_id=user["id"], acknowledged=True)
    client.post(f"{B}/webhooks/google-play", json=_rtdn(5, tok, "m-hold"))
    assert plan_of(client, h) == "FREE"
    play.purchases[tok] = purchase(user_id=user["id"], acknowledged=True)
    client.post(f"{B}/webhooks/google-play", json=_rtdn(1, tok, "m-recovered"))
    assert plan_of(client, h) == "PRO"
    # reembolso (REVOKED) => se revoca el acceso y se marca la transacción
    client.post(f"{B}/webhooks/google-play", json=_rtdn(12, tok, "m-revoked"))
    assert plan_of(client, h) == "FREE"
    db.expire_all()
    assert db.scalar(select(PaymentTransaction.status).where(PaymentTransaction.user_id == user["id"])) == "refunded"
    assert db.scalar(select(BillingEvent.processed).where(BillingEvent.event_id == "m-revoked")) is True
    # token desconocido => se registra, no falla
    assert client.post(f"{B}/webhooks/google-play", json=_rtdn(2, "desconocido-" + "x" * 20, "m-unknown")).json()["status"] == "unknown_subscription"


def test_parse_google_v2_response():
    v = parse_subscription_v2({"subscriptionState": "SUBSCRIPTION_STATE_IN_GRACE_PERIOD", "latestOrderId": "GPA.1",
                               "acknowledgementState": "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED", "testPurchase": {},
                               "externalAccountIdentifiers": {"obfuscatedExternalAccountId": "abc"},
                               "lineItems": [{"productId": "pronoscore360_pro_monthly", "expiryTime": "2030-01-01T00:00:00Z",
                                              "autoRenewingPlan": {"autoRenewEnabled": True}}]})
    assert (v.status, v.acknowledged, v.auto_renew, v.is_test, v.obfuscated_account_id) == ("grace_period", True, True, True, "abc")
    assert v.expiry.year == 2030


def test_account_id_endpoint_is_stable(client, user):
    a = client.get(f"{B}/billing/account-id", headers=user["headers"]).json()["obfuscated_account_id"]
    assert a == billing.account_hash(user["id"]) and len(a) == 32 and a != str(user["id"])
