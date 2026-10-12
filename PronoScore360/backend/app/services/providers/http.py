"""Cliente HTTP común: control de consumo diario, caché, reintentos y registro de peticiones."""
from __future__ import annotations

import logging
import time
from datetime import datetime, timezone
from typing import Callable, Optional

import httpx

from ...core.cache import Cache, get_cache
from ...core.config import get_settings
from ...core.errors import ProviderError

log = logging.getLogger("pronoscore.provider")


class BudgetExceeded(ProviderError):
    code = "provider_budget_exceeded"
    status_code = 503


class ProviderHttpClient:
    """Todas las llamadas a API-SPORTS pasan por aquí.

    * Presupuesto diario propio (PROVIDER_DAILY_BUDGET) por proveedor, con reserva para
      peticiones interactivas ('high'); las sincronizaciones ('normal'/'low') no tocan la reserva.
    * Caché por (endpoint, parámetros) con TTL; un acierto de caché NO consume presupuesto.
    * Reintentos con backoff exponencial ante 429/5xx/errores de red.
    * Se respeta el contador real del proveedor (cabecera x-ratelimit-requests-remaining).
    """

    def __init__(self, provider: str, base_url: str, api_key: str, *, client: Optional[httpx.Client] = None,
                 cache: Optional[Cache] = None, sleep: Callable[[float], None] = time.sleep,
                 recorder: Optional[Callable[[dict], None]] = None):
        s = get_settings()
        self.provider, self.base_url, self.api_key = provider, base_url.rstrip("/"), api_key
        self.client = client or httpx.Client(timeout=s.provider_timeout_seconds)
        self.cache = cache or get_cache()
        self.sleep, self.recorder, self.settings = sleep, recorder, s

    # -- presupuesto --
    def _budget_key(self) -> str:
        return f"provider:budget:{self.provider}:{datetime.now(timezone.utc).strftime('%Y%m%d')}"

    def used_today(self) -> int:
        return int(self.cache.get(self._budget_key()) or 0)

    def _reserve(self, priority: str) -> None:
        limit = self.settings.provider_daily_budget
        if priority != "high":
            limit -= self.settings.provider_reserve
        n = self.cache.incr(self._budget_key(), 90000)
        if n > limit:
            raise BudgetExceeded(f"Presupuesto diario de {self.provider} agotado ({n - 1}/{limit}).")

    def _record(self, **kw) -> None:
        if self.recorder:
            try:
                self.recorder({"provider": self.provider, **kw})
            except Exception:  # el registro nunca debe romper la llamada
                log.exception("no se pudo registrar provider_request")

    def get(self, endpoint: str, params: dict, *, ttl: Optional[int] = None, priority: str = "normal") -> dict:
        if not self.api_key:
            raise ProviderError(f"Falta la clave de API de {self.provider} (variable de entorno).", code="provider_not_configured")
        ttl = self.settings.provider_cache_ttl_seconds if ttl is None else ttl
        ckey = f"provider:cache:{self.provider}:{endpoint}:{sorted(params.items())}"
        if ttl:
            hit = self.cache.get_json(ckey)
            if hit is not None:
                self._record(endpoint=endpoint, params=params, ok=True, from_cache=True, status_code=200)
                return hit
        self._reserve(priority)
        url = f"{self.base_url}/{endpoint.lstrip('/')}"
        last_err = ""
        for attempt in range(self.settings.provider_max_retries + 1):
            t0 = time.time()
            try:
                r = self.client.get(url, params=params, headers={"x-apisports-key": self.api_key})
                ms = int((time.time() - t0) * 1000)
                remaining = r.headers.get("x-ratelimit-requests-remaining")
                if r.status_code in (429,) or r.status_code >= 500:
                    last_err = f"HTTP {r.status_code}"
                    self._record(endpoint=endpoint, params=params, ok=False, status_code=r.status_code, duration_ms=ms, error=last_err)
                    if attempt < self.settings.provider_max_retries:
                        self.sleep(min(2 ** attempt, 8))
                        continue
                    break
                r.raise_for_status()
                data = r.json()
                errors = data.get("errors")
                if errors:  # API-SPORTS devuelve errores de negocio con HTTP 200
                    msg = str(errors)
                    self._record(endpoint=endpoint, params=params, ok=False, status_code=r.status_code, duration_ms=ms, error=msg)
                    raise ProviderError(f"{self.provider} respondió con errores: {msg}")
                self._record(endpoint=endpoint, params=params, ok=True, status_code=r.status_code, duration_ms=ms,
                             remaining_daily=int(remaining) if remaining and remaining.isdigit() else None)
                if ttl:
                    self.cache.set_json(ckey, data, ttl)
                return data
            except httpx.HTTPStatusError as e:
                last_err = f"HTTP {e.response.status_code}"
                self._record(endpoint=endpoint, params=params, ok=False, status_code=e.response.status_code, error=last_err)
                break
            except (httpx.TransportError, ValueError) as e:
                last_err = f"{type(e).__name__}: {e}"
                self._record(endpoint=endpoint, params=params, ok=False, error=last_err)
                if attempt < self.settings.provider_max_retries:
                    self.sleep(min(2 ** attempt, 8))
                    continue
        raise ProviderError(f"Error consultando {self.provider}/{endpoint}: {last_err}")
