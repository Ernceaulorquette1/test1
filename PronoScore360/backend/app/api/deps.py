from __future__ import annotations

import jwt
from fastapi import Depends, Header, Request
from sqlalchemy.orm import Session

from ..core.errors import Forbidden, Unauthorized
from ..core.ratelimit import client_ip
from ..core.security import decode_access_token
from ..db.models import Plan, User
from ..db.session import get_db
from ..services.plans import effective_plan


def get_current_user(request: Request, authorization: str | None = Header(default=None),
                     db: Session = Depends(get_db)) -> User:
    if not authorization or not authorization.lower().startswith("bearer "):
        raise Unauthorized("Falta el token de acceso", code="missing_token")
    try:
        payload = decode_access_token(authorization[7:])
    except jwt.ExpiredSignatureError:
        raise Unauthorized("El token expiró", code="token_expired")
    except jwt.PyJWTError:
        raise Unauthorized("Token inválido", code="invalid_token")
    user = db.get(User, int(payload["sub"]))
    if not user or not user.is_active or user.deleted_at:
        raise Unauthorized("Cuenta no disponible", code="invalid_token")
    request.state.user_id = user.id
    return user


def get_optional_user(request: Request, authorization: str | None = Header(default=None),
                      db: Session = Depends(get_db)) -> User | None:
    if not authorization:
        return None
    return get_current_user(request, authorization, db)


def require_admin(user: User = Depends(get_current_user)) -> User:
    if user.role.code != "ADMIN":      # control en servidor: ocultar botones en React no basta
        raise Forbidden("Se requieren permisos de administrador", code="admin_required")
    return user


def current_plan(user: User = Depends(get_current_user), db: Session = Depends(get_db)) -> Plan:
    return effective_plan(db, user)


def ip_of(request: Request) -> str:
    return client_ip(request)


class Page:
    def __init__(self, page: int = 1, page_size: int = 20):
        self.page = max(page, 1)
        self.page_size = min(max(page_size, 1), 100)

    @property
    def offset(self) -> int:
        return (self.page - 1) * self.page_size


def paginated(items: list, total: int, p: Page) -> dict:
    return {"items": items, "total": total, "page": p.page, "page_size": p.page_size}
