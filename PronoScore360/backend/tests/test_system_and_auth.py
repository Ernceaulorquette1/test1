import pytest
from app.services.mailer import OUTBOX
from conftest import PASSWORD, new_email

A = "/api/v1/auth"


def test_health_status_ready(client):
    assert client.get("/api/v1/health").json() == {"status": "ok"}
    s = client.get("/api/v1/status").json()
    assert s["is_demo_mode"] is True and s["provider_mode"] == "demo"
    assert client.get("/api/v1/ready").json()["database"] == "ok"


def test_register_login_me_flow(client, make_user):
    u = make_user(country="CL", timezone="America/Santiago", display_name="Ana")
    me = client.get("/api/v1/me", headers=u["headers"]).json()
    assert me["email"] == u["email"] and me["plan"]["code"] == "FREE" and me["role"] == "USER"
    assert me["usage"]["limit"] == 5 and me["usage"]["remaining"] == 5
    r = client.post(f"{A}/login", json={"email": u["email"].upper(), "password": u["password"]})
    assert r.status_code == 200 and r.json()["user"]["id"] == u["id"]


def test_register_validation(client, make_user):
    u = make_user()
    dup = client.post(f"{A}/register", json={"email": u["email"], "password": PASSWORD, "accept_terms": True})
    assert dup.status_code == 409 and dup.json()["error"]["code"] == "email_taken"
    assert client.post(f"{A}/register", json={"email": new_email(), "password": "corta1A", "accept_terms": True}).status_code == 422
    weak = client.post(f"{A}/register", json={"email": new_email(), "password": "todominusculas123", "accept_terms": True})
    assert weak.status_code == 422 and weak.json()["error"]["code"] == "weak_password"
    assert client.post(f"{A}/register", json={"email": new_email(), "password": PASSWORD, "accept_terms": False}).status_code == 422
    assert client.post(f"{A}/register", json={"email": "no-es-correo", "password": PASSWORD, "accept_terms": True}).status_code == 422
    badtz = client.post(f"{A}/register", json={"email": new_email(), "password": PASSWORD, "accept_terms": True, "timezone": "Marte/Olimpo"})
    assert badtz.status_code == 422


def test_password_is_argon2_hashed(client, make_user, db):
    from sqlalchemy import select
    from app.db.models import User
    u = make_user()
    h = db.scalar(select(User.password_hash).where(User.id == u["id"]))
    assert h.startswith("$argon2id$") and PASSWORD not in h


def test_login_wrong_password_and_lockout(client, make_user):
    u = make_user()
    for _ in range(8):
        r = client.post(f"{A}/login", json={"email": u["email"], "password": "Incorrecta#999"})
        assert r.status_code == 401 and r.json()["error"]["code"] == "invalid_credentials"
    locked = client.post(f"{A}/login", json={"email": u["email"], "password": u["password"]})
    assert locked.status_code == 423 and locked.json()["error"]["code"] == "account_locked"
    unknown = client.post(f"{A}/login", json={"email": new_email(), "password": "Incorrecta#999"})
    assert unknown.status_code == 401        # misma respuesta que contraseña incorrecta


def test_protected_requires_valid_token(client):
    assert client.get("/api/v1/me").status_code == 401
    assert client.get("/api/v1/me", headers={"Authorization": "Bearer basura"}).json()["error"]["code"] == "invalid_token"


def test_refresh_rotation_and_reuse_detection(client, make_user):
    u = make_user()
    old = u["tokens"]["refresh_token"]
    r1 = client.post(f"{A}/refresh", json={"refresh_token": old})
    assert r1.status_code == 200
    new = r1.json()["refresh_token"]
    assert new != old
    reuse = client.post(f"{A}/refresh", json={"refresh_token": old})       # token ya rotado => robo
    assert reuse.status_code == 401 and reuse.json()["error"]["code"] == "refresh_reuse"
    assert client.post(f"{A}/refresh", json={"refresh_token": new}).status_code == 401   # familia revocada
    assert client.post(f"{A}/refresh", json={"refresh_token": "x" * 40}).status_code == 401


def test_logout_revokes_refresh(client, make_user):
    u = make_user()
    rt = u["tokens"]["refresh_token"]
    assert client.post(f"{A}/logout", json={"refresh_token": rt}).status_code == 204
    assert client.post(f"{A}/refresh", json={"refresh_token": rt}).status_code == 401


def test_password_reset_flow(client, make_user):
    u = make_user()
    OUTBOX.clear()
    r = client.post(f"{A}/forgot-password", json={"email": u["email"]})
    assert r.status_code == 202
    assert client.post(f"{A}/forgot-password", json={"email": new_email()}).status_code == 202   # no revela cuentas
    token = OUTBOX[-1]["body"].split("token=")[1].split()[0].rstrip(")")
    new_pw = "NuevaClave#2026a"
    assert client.post(f"{A}/reset-password", json={"token": token, "new_password": new_pw}).status_code == 204
    assert client.post(f"{A}/reset-password", json={"token": token, "new_password": new_pw}).status_code == 400  # un solo uso
    assert client.post(f"{A}/login", json={"email": u["email"], "password": u["password"]}).status_code == 401
    assert client.post(f"{A}/login", json={"email": u["email"], "password": new_pw}).status_code == 200
    assert client.post(f"{A}/refresh", json={"refresh_token": u["tokens"]["refresh_token"]}).status_code == 401  # sesiones revocadas


def test_email_verification(client, make_user):
    OUTBOX.clear()
    u = make_user()
    token = [m for m in OUTBOX if m["to"] == u["email"] and "Verifica" in m["subject"]][-1]["body"].split("token=")[1].strip()
    assert client.get("/api/v1/me", headers=u["headers"]).json()["email_verified"] is False
    assert client.post(f"{A}/verify-email", json={"token": token}).status_code == 204
    assert client.get("/api/v1/me", headers=u["headers"]).json()["email_verified"] is True


def test_change_password_and_profile_update(client, make_user):
    u = make_user()
    h = u["headers"]
    bad = client.post("/api/v1/me/change-password", headers=h, json={"current_password": "mala", "new_password": "OtraClave#2026a"})
    assert bad.status_code == 401
    ok = client.post("/api/v1/me/change-password", headers=h, json={"current_password": u["password"], "new_password": "OtraClave#2026a"})
    assert ok.status_code == 204
    p = client.patch("/api/v1/me", headers=h, json={"language": "fr", "theme": "dark", "display_name": "Zoé", "country": "us"}).json()
    assert (p["language"], p["theme"], p["display_name"], p["country"]) == ("fr", "dark", "Zoé", "US")
    assert client.patch("/api/v1/me", headers=h, json={"language": "xx"}).status_code == 422


def test_timezone_change_cooldown_prevents_quota_evasion(client, make_user):
    u = make_user()
    r1 = client.patch("/api/v1/me", headers=u["headers"], json={"timezone": "Pacific/Auckland"})
    assert r1.status_code == 200 and r1.json()["timezone"] == "Pacific/Auckland"
    r2 = client.patch("/api/v1/me", headers=u["headers"], json={"timezone": "America/Los_Angeles"})
    assert r2.status_code == 429 and r2.json()["error"]["code"] == "timezone_cooldown"


def test_delete_account_removes_data(client, make_user, db):
    from sqlalchemy import select, func
    from app.db.models import User, UserProfile, RefreshToken, Favorite
    u = make_user()
    h = u["headers"]
    assert client.post("/api/v1/favorites", headers=h, json={"kind": "league", "ref_id": 1}).status_code in (201, 404)
    r = client.request("DELETE", "/api/v1/me", headers=h, json={"password": "mala", "confirm": True})
    assert r.status_code == 401
    assert client.request("DELETE", "/api/v1/me", headers=h, json={"password": u["password"], "confirm": False}).status_code == 422
    assert client.request("DELETE", "/api/v1/me", headers=h, json={"password": u["password"], "confirm": True}).status_code == 204
    db.expire_all()
    assert db.scalar(select(func.count()).select_from(User).where(User.id == u["id"])) == 0
    for m in (UserProfile, RefreshToken, Favorite):
        assert db.scalar(select(func.count()).select_from(m).where(m.user_id == u["id"])) == 0
    assert client.get("/api/v1/me", headers=h).status_code == 401


def test_rate_limiting_on_auth(client):
    from app.core.config import get_settings
    s = get_settings(); old = s.auth_rate_limit_per_minute
    s.auth_rate_limit_per_minute = 3
    try:
        codes = [client.post(f"{A}/login", json={"email": new_email(), "password": "Xx#123456789"}).status_code for _ in range(5)]
        assert codes[:3] == [401, 401, 401] and codes[3:] == [429, 429]
    finally:
        s.auth_rate_limit_per_minute = old


def test_demo_accounts_only_in_dev(client):
    from app.core.config import get_settings
    assert client.get(f"{A}/demo-accounts").status_code == 200
    s = get_settings(); old = s.env
    s.env = "production"
    try:
        assert client.get(f"{A}/demo-accounts").status_code == 403
    finally:
        s.env = old


def test_production_config_guards():
    from pydantic import ValidationError
    from app.core.config import Settings
    with pytest.raises(ValidationError):
        Settings(env="production", _env_file=None)
    with pytest.raises(ValidationError):
        Settings(env="production", jwt_secret="x" * 40, payments_provider="demo", redis_url="redis://r", _env_file=None)
    ok = Settings(env="production", jwt_secret="x" * 40, payments_provider="none", redis_url="redis://r", _env_file=None)
    assert ok.demo_accounts_enabled is False


def test_validation_error_format(client):
    r = client.post(f"{A}/login", json={"email": "x"})
    body = r.json()["error"]
    assert r.status_code == 422 and body["code"] == "validation_error" and body["details"]["errors"]
    assert r.headers["x-request-id"]


def test_error_envelope_404(client, user):
    r = client.get("/api/v1/fixtures/99999999", headers=user["headers"])
    assert r.status_code == 404 and r.json()["error"]["code"] == "not_found"
