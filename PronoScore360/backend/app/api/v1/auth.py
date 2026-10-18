from __future__ import annotations

from fastapi import APIRouter, Depends, Header, Request
from pydantic import BaseModel, EmailStr, Field
from sqlalchemy.orm import Session

from ...core.config import get_settings
from ...core.errors import Forbidden
from ...core.ratelimit import auth_limit
from ...db.session import get_db
from ...schemas.common import TokenPair, UserOut, serialize_user
from ...services import accounts
from ..deps import ip_of

router = APIRouter(prefix="/auth", tags=["Autenticación"], dependencies=[Depends(auth_limit)])

PASSWORD = Field(min_length=10, max_length=128, description="Mínimo 10 caracteres")


def _strong(pw: str) -> str:
    if pw.lower() == pw or not any(c.isdigit() for c in pw):
        from ...core.errors import AppError
        raise AppError("La contraseña debe incluir mayúsculas, minúsculas y números", code="weak_password", status_code=422)
    return pw


class RegisterIn(BaseModel):
    email: EmailStr
    password: str = PASSWORD
    display_name: str = Field(default="", max_length=80)
    country: str = Field(default="CL", min_length=2, max_length=2)
    language: str = Field(default="es", pattern="^(es|en|fr|ht)$")
    timezone: str = Field(default="America/Santiago", max_length=64)
    accept_terms: bool = Field(description="Debe ser true")


class LoginIn(BaseModel):
    email: EmailStr
    password: str = Field(min_length=1, max_length=128)


class AuthOut(BaseModel):
    user: UserOut
    tokens: TokenPair


class RefreshIn(BaseModel):
    refresh_token: str


class ForgotIn(BaseModel):
    email: EmailStr


class ResetIn(BaseModel):
    token: str
    new_password: str = PASSWORD


class VerifyIn(BaseModel):
    token: str


class GoogleIn(BaseModel):
    id_token: str


@router.post("/register", response_model=AuthOut, status_code=201, summary="Crear cuenta")
def register(body: RegisterIn, request: Request, db: Session = Depends(get_db)):
    if not body.accept_terms:
        from ...core.errors import AppError
        raise AppError("Debes aceptar los términos y la política de privacidad", code="terms_required", status_code=422)
    _strong(body.password)
    u, tokens = accounts.register(db, body.email, body.password, display_name=body.display_name, country=body.country,
                                  language=body.language, timezone_=body.timezone, ip=ip_of(request),
                                  user_agent=request.headers.get("user-agent", ""))
    return {"user": serialize_user(db, u), "tokens": tokens}


@router.post("/login", response_model=AuthOut, summary="Iniciar sesión")
def login(body: LoginIn, request: Request, db: Session = Depends(get_db)):
    u, tokens = accounts.login(db, body.email, body.password, ip=ip_of(request), user_agent=request.headers.get("user-agent", ""))
    return {"user": serialize_user(db, u), "tokens": tokens}


@router.post("/refresh", response_model=TokenPair, summary="Renovar tokens (rotación con detección de reutilización)")
def refresh(body: RefreshIn, request: Request, db: Session = Depends(get_db)):
    _, tokens = accounts.refresh(db, body.refresh_token, request.headers.get("user-agent", ""))
    return tokens


@router.post("/logout", status_code=204, summary="Cerrar sesión (revoca el refresh token)")
def logout(body: RefreshIn, db: Session = Depends(get_db)):
    accounts.logout(db, body.refresh_token)


@router.post("/forgot-password", status_code=202, summary="Solicitar recuperación de contraseña")
def forgot(body: ForgotIn, db: Session = Depends(get_db)):
    accounts.request_password_reset(db, body.email)
    return {"message": "Si el correo existe, enviaremos instrucciones."}


@router.post("/reset-password", status_code=204, summary="Establecer nueva contraseña con token")
def reset(body: ResetIn, db: Session = Depends(get_db)):
    _strong(body.new_password)
    accounts.reset_password(db, body.token, body.new_password)


@router.post("/verify-email", status_code=204, summary="Verificar correo")
def verify(body: VerifyIn, db: Session = Depends(get_db)):
    accounts.verify_email(db, body.token)


@router.post("/google", response_model=AuthOut, summary="Inicio de sesión con Google (requiere GOOGLE_CLIENT_ID)")
def google(body: GoogleIn, db: Session = Depends(get_db)):
    u, tokens = accounts.google_login(db, body.id_token)
    return {"user": serialize_user(db, u), "tokens": tokens}


@router.get("/demo-accounts", summary="Cuentas de demostración (sólo local/test)")
def demo_accounts():
    s = get_settings()
    if not (s.is_dev and s.demo_accounts_enabled):
        raise Forbidden("No disponible fuera de entornos locales/de prueba", code="demo_disabled")
    from ...seed.demo import DEMO_EMAILS, DEMO_PASSWORDS
    return [{"role": k, "email": DEMO_EMAILS[k], "password": DEMO_PASSWORDS[k]} for k in ("admin", "pro", "free")]
