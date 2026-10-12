"""Interfaz de pasarela de pago web (a conectar con una pasarela compatible con Chile/internacional)
y adaptador de DEMOSTRACIÓN que nunca mueve dinero ni funciona en producción."""
from __future__ import annotations

from abc import ABC, abstractmethod
from dataclasses import dataclass

from ...core.config import get_settings
from ...core.errors import AppError


@dataclass
class CheckoutSession:
    provider: str
    session_id: str
    url: str | None
    is_test: bool


class PaymentGateway(ABC):
    name = "base"

    @abstractmethod
    def create_checkout(self, user_id: int, plan_code: str, country: str) -> CheckoutSession: ...

    @abstractmethod
    def parse_webhook(self, payload: bytes, signature: str | None) -> dict:
        """Debe VERIFICAR la firma del proveedor y devolver {'event_id','type','user_id','external_id',...}."""


class NotConfiguredGateway(PaymentGateway):
    name = "none"

    def create_checkout(self, user_id, plan_code, country):
        raise AppError("No hay pasarela de pago web configurada. Ver CONFIGURACION_PAGOS.md", code="gateway_not_configured",
                       status_code=501)

    def parse_webhook(self, payload, signature):
        raise AppError("No hay pasarela de pago web configurada", code="gateway_not_configured", status_code=501)


class DemoGateway(PaymentGateway):
    name = "demo"

    def create_checkout(self, user_id, plan_code, country):
        s = get_settings()
        if not s.is_dev or s.payments_provider != "demo":
            raise AppError("El simulador de pagos sólo existe en entornos locales/de prueba", code="demo_disabled", status_code=403)
        return CheckoutSession("demo", f"demo_{user_id}_{plan_code}", None, True)

    def parse_webhook(self, payload, signature):
        raise AppError("El adaptador demo no usa webhooks", code="not_supported", status_code=400)


def get_gateway() -> PaymentGateway:
    s = get_settings()
    return DemoGateway() if (s.is_dev and s.payments_provider == "demo") else NotConfiguredGateway()
