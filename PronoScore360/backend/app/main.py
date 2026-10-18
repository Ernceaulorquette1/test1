from __future__ import annotations

import logging
import time
import uuid
from contextlib import asynccontextmanager

from fastapi import Depends, FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware

from .api.v1 import api_router
from .core.config import get_settings
from .core.errors import install_error_handlers
from .core.logging import setup_logging
from .core.ratelimit import general_limit

log = logging.getLogger("pronoscore")


@asynccontextmanager
async def lifespan(app: FastAPI):
    s = get_settings()
    setup_logging()
    from .db.session import SessionLocal
    from .seed.base import seed_base
    db = SessionLocal()
    try:
        seed_base(db)
        if s.seed_demo_on_start and s.is_dev:
            from .seed.demo import seed_demo
            log.info("seed demo: %s", seed_demo(db))
        db.commit()
    except Exception:
        db.rollback()
        log.exception("fallo en el arranque (¿migraciones aplicadas? ejecutar 'alembic upgrade head')")
        raise
    finally:
        db.close()
    from .jobs.scheduler import start_scheduler, stop_scheduler
    start_scheduler()
    yield
    stop_scheduler()


def create_app() -> FastAPI:
    s = get_settings()
    app = FastAPI(
        title="PronoScore360 API", version="1.0.0", lifespan=lifespan,
        description=("API de análisis deportivo (fútbol y NBA). **Plataforma informativa: no permite apuestas.** "
                     "Las probabilidades son estimaciones estadísticas y no garantizan resultados. "
                     "En modo DEMO todos los datos deportivos son ficticios."),
        docs_url="/docs", redoc_url="/redoc", openapi_url="/openapi.json",
        dependencies=[Depends(general_limit)])
    app.add_middleware(CORSMiddleware, allow_origins=s.cors_list, allow_credentials=False,
                       allow_methods=["GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"], allow_headers=["Authorization", "Content-Type"])

    @app.middleware("http")
    async def request_context(request: Request, call_next):
        request.state.request_id = request.headers.get("x-request-id") or uuid.uuid4().hex
        t0 = time.time()
        response = await call_next(request)
        response.headers["x-request-id"] = request.state.request_id
        response.headers["x-content-type-options"] = "nosniff"
        response.headers["x-frame-options"] = "DENY"
        response.headers["referrer-policy"] = "no-referrer"
        if not s.is_dev:
            response.headers["strict-transport-security"] = "max-age=31536000; includeSubDomains"
        log.info("%s %s %s %dms", request.method, request.url.path, response.status_code, (time.time() - t0) * 1000)
        return response

    install_error_handlers(app)
    app.include_router(api_router, prefix="/api/v1")

    @app.get("/", include_in_schema=False)
    def root():
        return {"app": s.app_name, "docs": "/docs", "api": "/api/v1"}

    return app


app = create_app()
