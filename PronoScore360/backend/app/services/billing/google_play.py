"""Verificación de compras con Google Play Developer API (subscriptionsv2) y RTDN.

Referencia (documentación oficial de Google Play Billing):
  GET  https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{pkg}/purchases/subscriptionsv2/tokens/{token}
  POST https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{pkg}/purchases/subscriptions/{productId}/tokens/{token}:acknowledge
NO verificado contra Google real en este entorno (faltan credenciales) -> ver CONFIGURACION_GOOGLE_PLAY.md.
"""
from __future__ import annotations

import base64
import json
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Optional

import httpx

from ...core.config import get_settings
from ...core.errors import AppError, ProviderError

API = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications"

STATE_MAP = {
    "SUBSCRIPTION_STATE_ACTIVE": "active",
    "SUBSCRIPTION_STATE_CANCELED": "canceled",          # cancelada pero con acceso hasta expiryTime
    "SUBSCRIPTION_STATE_IN_GRACE_PERIOD": "grace_period",
    "SUBSCRIPTION_STATE_ON_HOLD": "on_hold",
    "SUBSCRIPTION_STATE_PAUSED": "on_hold",
    "SUBSCRIPTION_STATE_EXPIRED": "expired",
    "SUBSCRIPTION_STATE_PENDING": "pending",
    "SUBSCRIPTION_STATE_PENDING_PURCHASE_CANCELED": "expired",
}

# notificationType de RTDN (documentación de Google Play)
RTDN_TYPES = {1: "RECOVERED", 2: "RENEWED", 3: "CANCELED", 4: "PURCHASED", 5: "ON_HOLD", 6: "IN_GRACE_PERIOD",
              7: "RESTARTED", 8: "PRICE_CHANGE_CONFIRMED", 9: "DEFERRED", 10: "PAUSED", 11: "PAUSE_SCHEDULE_CHANGED",
              12: "REVOKED", 13: "EXPIRED", 20: "PENDING_PURCHASE_CANCELED"}


@dataclass
class VerifiedPurchase:
    status: str
    product_id: str
    expiry: Optional[datetime]
    order_id: Optional[str]
    acknowledged: bool
    auto_renew: bool
    obfuscated_account_id: Optional[str]
    is_test: bool
    revoked: bool = False
    raw_state: str = ""


def _ts(s: Optional[str]) -> Optional[datetime]:
    return datetime.fromisoformat(s.replace("Z", "+00:00")).astimezone(timezone.utc) if s else None


def parse_subscription_v2(d: dict) -> VerifiedPurchase:
    items = d.get("lineItems") or []
    if not items:
        raise ProviderError("Respuesta de Google Play sin lineItems", code="play_invalid_response")
    li = items[0]
    state = d.get("subscriptionState", "")
    ext = d.get("externalAccountIdentifiers") or {}
    return VerifiedPurchase(
        status=STATE_MAP.get(state, "pending"), product_id=li.get("productId", ""), expiry=_ts(li.get("expiryTime")),
        order_id=d.get("latestOrderId"), acknowledged=d.get("acknowledgementState") == "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED",
        auto_renew=bool((li.get("autoRenewingPlan") or {}).get("autoRenewEnabled", False)),
        obfuscated_account_id=ext.get("obfuscatedExternalAccountId"), is_test="testPurchase" in d, raw_state=state)


class GooglePlayClient:
    def __init__(self, http: Optional[httpx.Client] = None, token_provider=None):
        self.s = get_settings()
        self.http = http or httpx.Client(timeout=15)
        self._token_provider = token_provider

    def _token(self) -> str:
        if self._token_provider:
            return self._token_provider()
        if not self.s.google_play_service_account_json:
            raise AppError("Google Play no está configurado (GOOGLE_PLAY_SERVICE_ACCOUNT_JSON)",
                           code="play_not_configured", status_code=501)
        from google.auth.transport.requests import Request
        from google.oauth2 import service_account
        raw = self.s.google_play_service_account_json
        info = json.loads(raw) if raw.strip().startswith("{") else json.load(open(raw))
        creds = service_account.Credentials.from_service_account_info(
            info, scopes=["https://www.googleapis.com/auth/androidpublisher"])
        creds.refresh(Request())
        return creds.token

    def _headers(self) -> dict:
        return {"Authorization": f"Bearer {self._token()}"}

    def get_subscription(self, purchase_token: str) -> VerifiedPurchase:
        url = f"{API}/{self.s.google_play_package_name}/purchases/subscriptionsv2/tokens/{purchase_token}"
        r = self.http.get(url, headers=self._headers())
        if r.status_code in (400, 404, 410):
            raise AppError("Compra no encontrada o token inválido en Google Play", code="play_invalid_token", status_code=400)
        if r.status_code >= 400:
            raise ProviderError(f"Google Play respondió HTTP {r.status_code}", code="play_error")
        return parse_subscription_v2(r.json())

    def acknowledge(self, product_id: str, purchase_token: str) -> None:
        url = (f"{API}/{self.s.google_play_package_name}/purchases/subscriptions/{product_id}"
               f"/tokens/{purchase_token}:acknowledge")
        r = self.http.post(url, headers=self._headers(), json={})
        if r.status_code >= 400:
            raise ProviderError(f"No se pudo confirmar (acknowledge) la compra: HTTP {r.status_code}", code="play_ack_failed")


def decode_pubsub(body: dict) -> tuple[str, dict]:
    msg = body.get("message") or {}
    if "data" not in msg:
        raise AppError("Mensaje Pub/Sub inválido", code="bad_rtdn", status_code=400)
    data = json.loads(base64.b64decode(msg["data"]).decode())
    return msg.get("messageId", ""), data


def verify_pubsub_jwt(authorization: Optional[str]) -> bool:
    """Valida el JWT OIDC que Pub/Sub adjunta a los push (audiencia y cuenta de servicio esperadas)."""
    s = get_settings()
    if not s.google_play_rtdn_audience:
        return False
    if not authorization or not authorization.lower().startswith("bearer "):
        return False
    try:
        from google.auth.transport import requests as greq
        from google.oauth2 import id_token
        claims = id_token.verify_oauth2_token(authorization[7:], greq.Request(), audience=s.google_play_rtdn_audience)
        return (not s.google_play_rtdn_service_account) or claims.get("email") == s.google_play_rtdn_service_account
    except Exception:
        return False
