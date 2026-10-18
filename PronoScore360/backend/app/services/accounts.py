from __future__ import annotations

import uuid
from datetime import datetime, timedelta, timezone
from typing import Optional
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

import httpx
from sqlalchemy import delete, select, update
from sqlalchemy.orm import Session

from ..core.config import get_settings
from ..core.errors import AppError, Conflict, Forbidden, Unauthorized
from ..core.security import (create_access_token, hash_password, hash_token, needs_rehash, new_opaque_token,
                             verify_password)
from ..db.models import OneTimeToken, RefreshToken, Role, User, UserProfile
from .audit import audit
from .mailer import send_mail

# Hash ficticio para igualar tiempos cuando el usuario no existe (mitiga enumeración por tiempo)
_DUMMY_HASH = hash_password("dummy-password-for-timing")


def now() -> datetime:
    return datetime.now(timezone.utc)


def validate_timezone(tz: str) -> str:
    try:
        ZoneInfo(tz)
    except (ZoneInfoNotFoundError, ValueError, KeyError):
        raise AppError("Zona horaria inválida", code="invalid_timezone", status_code=422)
    return tz


def get_role(db: Session, code: str) -> Role:
    r = db.scalar(select(Role).where(Role.code == code))
    if not r:
        r = Role(code=code, description=code)
        db.add(r)
        db.flush()
    return r


def create_user(db: Session, email: str, password: Optional[str], *, role: str = "USER", display_name: str = "",
                country: str = "CL", language: str = "es", timezone_: str = "America/Santiago",
                is_demo: bool = False, email_verified: bool = False, google_sub: Optional[str] = None) -> User:
    email = email.strip().lower()
    if db.scalar(select(User.id).where(User.email == email)):
        raise Conflict("Ya existe una cuenta con ese correo", code="email_taken")
    validate_timezone(timezone_)
    u = User(email=email, password_hash=hash_password(password) if password else None, role=get_role(db, role),
             is_demo=is_demo, email_verified=email_verified, google_sub=google_sub)
    u.profile = UserProfile(display_name=display_name or email.split("@")[0], country=country.upper(),
                            language=language, timezone=timezone_)
    db.add(u)
    db.flush()
    return u


def _issue_refresh(db: Session, user: User, family: Optional[str] = None, user_agent: str = "") -> str:
    s = get_settings()
    # control de sesiones: se revocan las más antiguas si se supera el máximo
    active = db.scalars(select(RefreshToken).where(RefreshToken.user_id == user.id, RefreshToken.revoked_at.is_(None),
                                                    RefreshToken.expires_at > now()).order_by(RefreshToken.id)).all()
    for old in active[: max(0, len(active) - s.max_sessions_per_user + 1)]:
        old.revoked_at = now()
    raw = new_opaque_token()
    db.add(RefreshToken(user_id=user.id, family_id=family or str(uuid.uuid4()), token_hash=hash_token(raw),
                        expires_at=now() + timedelta(days=s.refresh_token_days), user_agent=user_agent[:200]))
    db.flush()
    return raw


def issue_tokens(db: Session, user: User, family: Optional[str] = None, user_agent: str = "") -> dict:
    refresh = _issue_refresh(db, user, family, user_agent)
    return {"access_token": create_access_token(user.id, user.role.code), "refresh_token": refresh,
            "token_type": "bearer", "expires_in": get_settings().access_token_minutes * 60}


def register(db: Session, email: str, password: str, *, display_name: str = "", country: str = "CL", language: str = "es",
             timezone_: str = "America/Santiago", ip: Optional[str] = None, user_agent: str = "") -> tuple[User, dict]:
    u = create_user(db, email, password, display_name=display_name, country=country, language=language, timezone_=timezone_)
    audit(db, "auth.register", actor_id=u.id, entity="user", entity_id=u.id, ip=ip)
    request_email_verification(db, u)
    return u, issue_tokens(db, u, user_agent=user_agent)


def login(db: Session, email: str, password: str, *, ip: Optional[str] = None, user_agent: str = "") -> tuple[User, dict]:
    s = get_settings()
    u = db.scalar(select(User).where(User.email == email.strip().lower(), User.deleted_at.is_(None)))
    if u is None or u.password_hash is None:
        verify_password(password, _DUMMY_HASH)
        raise Unauthorized("Correo o contraseña incorrectos", code="invalid_credentials")
    if u.locked_until and u.locked_until > now():
        raise AppError("Cuenta bloqueada temporalmente por intentos fallidos. Inténtalo más tarde.",
                       code="account_locked", status_code=423)
    if not verify_password(password, u.password_hash):
        u.failed_logins += 1
        if u.failed_logins >= s.login_max_failures:
            u.locked_until = now() + timedelta(minutes=s.login_lock_minutes)
            u.failed_logins = 0
            audit(db, "auth.locked", actor_id=u.id, entity="user", entity_id=u.id, ip=ip)
        db.commit()   # persistir el contador aunque la petición falle
        raise Unauthorized("Correo o contraseña incorrectos", code="invalid_credentials")
    if not u.is_active:
        raise Forbidden("Cuenta desactivada", code="account_disabled")
    if u.is_demo and not s.demo_accounts_enabled:
        raise Forbidden("Las cuentas de demostración sólo funcionan en entornos locales o de prueba", code="demo_disabled")
    u.failed_logins, u.locked_until, u.last_login_at = 0, None, now()
    if needs_rehash(u.password_hash):
        u.password_hash = hash_password(password)
    audit(db, "auth.login", actor_id=u.id, entity="user", entity_id=u.id, ip=ip)
    return u, issue_tokens(db, u, user_agent=user_agent)


def refresh(db: Session, raw: str, user_agent: str = "") -> tuple[User, dict]:
    rt = db.scalar(select(RefreshToken).where(RefreshToken.token_hash == hash_token(raw)))
    if rt is None:
        raise Unauthorized("Sesión inválida", code="invalid_refresh")
    if rt.revoked_at is not None:
        # reutilización de un token ya rotado => posible robo: se revoca toda la familia
        db.execute(update(RefreshToken).where(RefreshToken.family_id == rt.family_id, RefreshToken.revoked_at.is_(None))
                   .values(revoked_at=now()))
        db.commit()
        raise Unauthorized("Sesión revocada por seguridad. Inicia sesión de nuevo.", code="refresh_reuse")
    if rt.expires_at <= now():
        raise Unauthorized("Sesión expirada", code="refresh_expired")
    user = db.get(User, rt.user_id)
    if not user or not user.is_active or user.deleted_at:
        raise Unauthorized("Cuenta no disponible", code="invalid_refresh")
    rt.revoked_at = now()
    tokens = issue_tokens(db, user, family=rt.family_id, user_agent=user_agent)
    return user, tokens


def logout(db: Session, raw: str) -> None:
    rt = db.scalar(select(RefreshToken).where(RefreshToken.token_hash == hash_token(raw)))
    if rt and rt.revoked_at is None:
        db.execute(update(RefreshToken).where(RefreshToken.family_id == rt.family_id, RefreshToken.revoked_at.is_(None))
                   .values(revoked_at=now()))


def revoke_all_sessions(db: Session, user_id: int) -> None:
    db.execute(update(RefreshToken).where(RefreshToken.user_id == user_id, RefreshToken.revoked_at.is_(None))
               .values(revoked_at=now()))


def _make_one_time(db: Session, user: User, purpose: str, minutes: int) -> str:
    db.execute(delete(OneTimeToken).where(OneTimeToken.user_id == user.id, OneTimeToken.purpose == purpose,
                                          OneTimeToken.used_at.is_(None)))
    raw = new_opaque_token()
    db.add(OneTimeToken(user_id=user.id, purpose=purpose, token_hash=hash_token(raw),
                        expires_at=now() + timedelta(minutes=minutes)))
    db.flush()
    return raw


def request_password_reset(db: Session, email: str) -> None:
    """Respuesta idéntica exista o no la cuenta (no revela cuentas)."""
    u = db.scalar(select(User).where(User.email == email.strip().lower(), User.deleted_at.is_(None)))
    if not u:
        return
    s = get_settings()
    token = _make_one_time(db, u, "password_reset", s.password_reset_minutes)
    send_mail(u.email, "Recupera tu contraseña de PronoScore360",
              f"Usa este enlace (válido {s.password_reset_minutes} min): {s.frontend_url}/reset-password?token={token}")


def reset_password(db: Session, token: str, new_password: str) -> None:
    ot = db.scalar(select(OneTimeToken).where(OneTimeToken.token_hash == hash_token(token),
                                              OneTimeToken.purpose == "password_reset"))
    if not ot or ot.used_at or ot.expires_at <= now():
        raise AppError("El enlace es inválido o expiró", code="invalid_token", status_code=400)
    u = db.get(User, ot.user_id)
    u.password_hash = hash_password(new_password)
    u.failed_logins, u.locked_until = 0, None
    ot.used_at = now()
    revoke_all_sessions(db, u.id)
    audit(db, "auth.password_reset", actor_id=u.id, entity="user", entity_id=u.id)


def request_email_verification(db: Session, u: User) -> None:
    token = _make_one_time(db, u, "email_verify", 60 * 24)
    send_mail(u.email, "Verifica tu correo en PronoScore360",
              f"Verifica tu correo: {get_settings().frontend_url}/verify-email?token={token}")


def verify_email(db: Session, token: str) -> None:
    ot = db.scalar(select(OneTimeToken).where(OneTimeToken.token_hash == hash_token(token),
                                              OneTimeToken.purpose == "email_verify"))
    if not ot or ot.used_at or ot.expires_at <= now():
        raise AppError("El enlace es inválido o expiró", code="invalid_token", status_code=400)
    db.get(User, ot.user_id).email_verified = True
    ot.used_at = now()


def change_password(db: Session, u: User, current: str, new: str) -> None:
    if not u.password_hash or not verify_password(current, u.password_hash):
        raise Unauthorized("La contraseña actual es incorrecta", code="invalid_credentials")
    u.password_hash = hash_password(new)
    revoke_all_sessions(db, u.id)
    audit(db, "auth.password_changed", actor_id=u.id, entity="user", entity_id=u.id)


def delete_account(db: Session, u: User, password: Optional[str]) -> None:
    """Eliminación real: borra el usuario y sus datos asociados (CASCADE). Se conserva sólo un
    registro de auditoría anónimo."""
    if u.password_hash and (not password or not verify_password(password, u.password_hash)):
        raise Unauthorized("Contraseña incorrecta", code="invalid_credentials")
    uid = u.id
    audit(db, "account.deleted", actor_id=None, entity="user", entity_id=uid)
    db.delete(u)
    db.flush()


def google_login(db: Session, id_token: str, *, client: Optional[httpx.Client] = None) -> tuple[User, dict]:
    """Verifica el ID token con el endpoint tokeninfo de Google y comprueba la audiencia.
    (En producción alto tráfico se recomienda la librería google-auth con verificación local de firma.)"""
    s = get_settings()
    if not s.google_client_id:
        raise AppError("Inicio de sesión con Google no configurado (GOOGLE_CLIENT_ID)", code="google_not_configured", status_code=501)
    c = client or httpx.Client(timeout=10)
    r = c.get("https://oauth2.googleapis.com/tokeninfo", params={"id_token": id_token})
    if r.status_code != 200:
        raise Unauthorized("Token de Google inválido", code="invalid_google_token")
    info = r.json()
    if info.get("aud") != s.google_client_id or info.get("email_verified") not in (True, "true"):
        raise Unauthorized("Token de Google no válido para esta aplicación", code="invalid_google_token")
    email = info["email"].lower()
    u = db.scalar(select(User).where((User.google_sub == info["sub"]) | (User.email == email)))
    if u is None:
        u = create_user(db, email, None, display_name=info.get("name", ""), google_sub=info["sub"], email_verified=True)
    elif not u.google_sub:
        u.google_sub, u.email_verified = info["sub"], True
    u.last_login_at = now()
    return u, issue_tokens(db, u)
