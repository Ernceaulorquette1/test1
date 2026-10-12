from __future__ import annotations

import time

from fastapi import Request

from .cache import get_cache
from .config import get_settings
from .errors import AppError


class RateLimited(AppError):
    status_code, code = 429, "rate_limited"


def client_ip(request: Request) -> str:
    fwd = request.headers.get("x-forwarded-for")
    return fwd.split(",")[0].strip() if fwd else (request.client.host if request.client else "unknown")


def check_rate(key: str, limit: int, window: int = 60) -> None:
    bucket = int(time.time() // window)
    n = get_cache().incr(f"rl:{key}:{bucket}", window + 1)
    if n > limit:
        raise RateLimited("Demasiadas solicitudes. Inténtalo de nuevo en unos segundos.",
                          extra={"retry_after_seconds": window})


def general_limit(request: Request) -> None:
    check_rate(f"ip:{client_ip(request)}", get_settings().rate_limit_per_minute)


def auth_limit(request: Request) -> None:
    check_rate(f"auth:{client_ip(request)}", get_settings().auth_rate_limit_per_minute)
