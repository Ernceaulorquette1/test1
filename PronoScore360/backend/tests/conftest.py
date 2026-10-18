import os
import uuid

TEST_DB = os.environ.get("TEST_DATABASE_URL", "postgresql+psycopg://pronoscore:pronoscore@localhost:5432/pronoscore_test")
os.environ.update(ENV="test", DATABASE_URL=TEST_DB, REDIS_URL="", PROVIDER_MODE="demo", PAYMENTS_PROVIDER="demo",
                  AUTH_RATE_LIMIT_PER_MINUTE="100000", RATE_LIMIT_PER_MINUTE="100000", SCHEDULER_ENABLED="false",
                  JWT_SECRET="test-secret-test-secret-test-secret-123456")

import pytest
from alembic import command
from alembic.config import Config
from fastapi.testclient import TestClient
from sqlalchemy import text

from app.core.cache import Cache, MemoryBackend, set_cache
from app.core.config import get_settings
from app.db.session import SessionLocal, get_engine, reset_engine


@pytest.fixture(scope="session", autouse=True)
def _database():
    """Esquema real de PostgreSQL creado con las migraciones de Alembic (las valida) + datos DEMO."""
    eng = get_engine()
    with eng.begin() as c:
        c.execute(text("DROP SCHEMA public CASCADE")); c.execute(text("CREATE SCHEMA public"))
    cfg = Config(os.path.join(os.path.dirname(__file__), "..", "alembic.ini"))
    cfg.set_main_option("script_location", os.path.join(os.path.dirname(__file__), "..", "alembic"))
    command.upgrade(cfg, "head")
    from app.seed.demo import seed_demo
    db = SessionLocal()
    try:
        seed_demo(db); db.commit()
    finally:
        db.close()
    yield
    reset_engine()


@pytest.fixture(autouse=True)
def _fresh_cache():
    set_cache(Cache(MemoryBackend()))
    yield


@pytest.fixture(scope="session")
def app():
    from app.main import create_app
    return create_app()


@pytest.fixture()
def client(app):
    with TestClient(app) as c:      # ejecuta lifespan (seed_base)
        yield c


@pytest.fixture()
def db():
    s = SessionLocal()
    try:
        yield s
    finally:
        s.rollback(); s.close()


PASSWORD = "Prueba#12345x"


def new_email() -> str:
    return f"u{uuid.uuid4().hex[:10]}@pronoscore-test.example.com"


@pytest.fixture()
def make_user(client):
    def _make(email=None, password=PASSWORD, **extra):
        email = email or new_email()
        r = client.post("/api/v1/auth/register", json={"email": email, "password": password, "accept_terms": True, **extra})
        assert r.status_code == 201, r.text
        body = r.json()
        return {"email": email, "password": password, "id": body["user"]["id"], "tokens": body["tokens"],
                "headers": {"Authorization": f"Bearer {body['tokens']['access_token']}"}}
    return _make


@pytest.fixture()
def user(make_user):
    return make_user()


def _login(client, email, password):
    r = client.post("/api/v1/auth/login", json={"email": email, "password": password})
    assert r.status_code == 200, r.text
    return {"Authorization": f"Bearer {r.json()['tokens']['access_token']}"}


@pytest.fixture()
def admin_h(client):
    from app.seed.demo import DEMO_EMAILS, DEMO_PASSWORDS
    return _login(client, DEMO_EMAILS["admin"], DEMO_PASSWORDS["admin"])


@pytest.fixture()
def pro_h(client):
    from app.seed.demo import DEMO_EMAILS, DEMO_PASSWORDS
    return _login(client, DEMO_EMAILS["pro"], DEMO_PASSWORDS["pro"])


@pytest.fixture(scope="session")
def fixtures_info():
    """IDs de partidos DEMO próximos (fútbol/baloncesto) ya con análisis previo."""
    from sqlalchemy import select
    from datetime import datetime, timezone
    from app.db.models import Fixture, Sport
    db = SessionLocal()
    try:
        now = datetime.now(timezone.utc)
        out = {}
        for code in ("football", "basketball"):
            sid = db.scalar(select(Sport.id).where(Sport.code == code))
            out[code] = [f.id for f in db.scalars(select(Fixture).where(Fixture.sport_id == sid, Fixture.status == "scheduled",
                                                                          Fixture.kickoff > now).order_by(Fixture.kickoff).limit(12))]
        out["finished_football"] = db.scalar(select(Fixture.id).join(Sport).where(Sport.code == "football", Fixture.status == "finished").order_by(Fixture.kickoff.desc()).limit(1))
        return out
    finally:
        db.close()
