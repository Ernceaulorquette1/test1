"""Proceso de tareas programadas (un solo proceso aunque la API tenga varias réplicas).
Uso: SCHEDULER_ENABLED=true python -m app.worker"""
from __future__ import annotations

import logging
import signal
import threading

from .core.config import get_settings
from .core.logging import setup_logging
from .db.session import SessionLocal
from .jobs.scheduler import start_scheduler, stop_scheduler
from .seed.base import seed_base


def main() -> None:
    setup_logging()
    log = logging.getLogger("pronoscore.worker")
    s = get_settings()
    if not s.scheduler_enabled:
        raise SystemExit("SCHEDULER_ENABLED debe ser true para el worker")
    db = SessionLocal()
    try:
        seed_base(db); db.commit()
    finally:
        db.close()
    start_scheduler()
    log.info("worker iniciado (modo proveedores: %s)", s.provider_mode)
    stop = threading.Event()
    for sig in (signal.SIGINT, signal.SIGTERM):
        signal.signal(sig, lambda *_: stop.set())
    stop.wait()
    stop_scheduler()


if __name__ == "__main__":
    main()
