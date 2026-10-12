from __future__ import annotations

import logging
import uuid

from fastapi import FastAPI, HTTPException, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

log = logging.getLogger("pronoscore")


class AppError(Exception):
    status_code = 400
    code = "bad_request"

    def __init__(self, message: str, *, code: str | None = None, status_code: int | None = None, extra: dict | None = None):
        super().__init__(message)
        self.message = message
        if code:
            self.code = code
        if status_code:
            self.status_code = status_code
        self.extra = extra or {}


class NotFound(AppError):
    status_code, code = 404, "not_found"


class Unauthorized(AppError):
    status_code, code = 401, "unauthorized"


class Forbidden(AppError):
    status_code, code = 403, "forbidden"


class Conflict(AppError):
    status_code, code = 409, "conflict"


class QuotaExceeded(AppError):
    status_code, code = 429, "quota_exceeded"


class PaymentRequired(AppError):
    status_code, code = 402, "plan_required"


class ProviderError(AppError):
    status_code, code = 502, "provider_error"


def _body(code: str, message: str, request: Request, extra: dict | None = None) -> dict:
    return {"error": {"code": code, "message": message, "request_id": getattr(request.state, "request_id", None),
                      **({"details": extra} if extra else {})}}


def install_error_handlers(app: FastAPI) -> None:
    @app.exception_handler(AppError)
    async def _app_error(request: Request, exc: AppError):
        return JSONResponse(_body(exc.code, exc.message, request, exc.extra), status_code=exc.status_code)

    @app.exception_handler(HTTPException)
    async def _http(request: Request, exc: HTTPException):
        return JSONResponse(_body("http_error", str(exc.detail), request), status_code=exc.status_code,
                            headers=getattr(exc, "headers", None))

    @app.exception_handler(RequestValidationError)
    async def _validation(request: Request, exc: RequestValidationError):
        details = [{"field": ".".join(str(p) for p in e["loc"] if p != "body"), "message": e["msg"]} for e in exc.errors()]
        return JSONResponse(_body("validation_error", "Datos de entrada inválidos", request, {"errors": details}),
                            status_code=422)

    @app.exception_handler(Exception)
    async def _unhandled(request: Request, exc: Exception):
        rid = getattr(request.state, "request_id", uuid.uuid4().hex)
        log.exception("unhandled error request_id=%s path=%s", rid, request.url.path)
        return JSONResponse(_body("internal_error", "Error interno del servidor", request), status_code=500)
