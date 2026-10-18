from __future__ import annotations

from fastapi import APIRouter, Depends
from sqlalchemy import select, text
from sqlalchemy.orm import Session

from ...core.cache import get_cache
from ...core.config import get_settings
from ...db.models import Announcement
from ...db.session import get_db

router = APIRouter(tags=["Sistema"])
VERSION = "1.0.0"


@router.get("/health", summary="Liveness")
def health():
    return {"status": "ok"}


@router.get("/ready", summary="Readiness: BD y caché")
def ready(db: Session = Depends(get_db)):
    db.execute(text("SELECT 1"))
    c = get_cache(); c.set("ready:probe", "1", 5)
    return {"status": "ready", "database": "ok", "cache": "ok" if c.get("ready:probe") == "1" else "error"}


@router.get("/status", summary="Estado público del sistema")
def status(db: Session = Depends(get_db)):
    s = get_settings()
    ann = db.scalars(select(Announcement).where(Announcement.active.is_(True)).order_by(Announcement.id.desc())).all()
    return {"app": s.app_name, "version": VERSION, "environment": s.env, "provider_mode": s.provider_mode,
            "is_demo_mode": s.provider_mode == "demo", "payments": s.payments_provider if s.is_dev else "production",
            "announcements": [{"id": a.id, "title": a.title, "body": a.body, "level": a.level} for a in ann]}
