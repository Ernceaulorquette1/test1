from __future__ import annotations

from typing import Optional

from sqlalchemy.orm import Session

from ..db.models import AuditLog, ErrorLog


def audit(db: Session, action: str, *, actor_id: Optional[int] = None, entity: str = "", entity_id: Optional[str | int] = None,
          detail: Optional[dict] = None, ip: Optional[str] = None) -> None:
    db.add(AuditLog(actor_user_id=actor_id, action=action, entity=entity,
                    entity_id=str(entity_id) if entity_id is not None else None, detail=detail or {}, ip=ip))


def log_error(db: Session, source: str, message: str, context: Optional[dict] = None) -> None:
    db.add(ErrorLog(source=source, message=message[:4000], context=context or {}))
