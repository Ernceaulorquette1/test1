"""Proveedores (con transporte HTTP simulado), caché, sincronización y persistencia en PostgreSQL.

NOTA: los payloads de API-SPORTS de estas pruebas fueron reconstruidos a partir de la documentación conocida de
la v3 / v2; NO provienen de llamadas reales (no hay claves). Ver CONFIGURACION_API.md."""
from datetime import date, datetime, timedelta, timezone

import fakeredis
import httpx
import pytest
from sqlalchemy import func, select
from sqlalchemy.exc import IntegrityError

from app.core.cache import Cache, MemoryBackend, RedisBackend
from app.core.errors import ProviderError
from app.db.models import (DailyUsage, Fixture, HistoricalResult, League, Notification, Prediction, ProviderRequest, Sport, Team, User)
from app.services.providers.apifootball import ApiFootballProvider, parse_fixture
from app.services.providers.apinba import ApiNbaProvider
from app.services.providers.http import BudgetExceeded, ProviderHttpClient

FB_FIXTURE = {"fixture": {"id": 1001, "date": "2026-10-20T19:00:00+00:00", "status": {"short": "FT"}},
              "league": {"id": 39, "name": "Premier Prueba", "country": "England", "season": 2026, "logo": "l.png"},
              "teams": {"home": {"id": 33, "name": "Club A"}, "away": {"id": 34, "name": "Club B"}},
              "goals": {"home": 2, "away": 1}, "score": {"halftime": {"home": 1, "away": 0}}}
FB_NS = {**FB_FIXTURE, "fixture": {"id": 1002, "date": "2026-10-25T19:00:00+00:00", "status": {"short": "NS"}}, "goals": {"home": None, "away": None}}


def make_client(handler, key="K", budget_env=None, caches=None, sleeps=None):
    transport = httpx.MockTransport(handler)
    cache = caches or Cache(MemoryBackend())
    return ProviderHttpClient("api-football", "https://v3.football.api-sports.io", key, client=httpx.Client(transport=transport),
                              cache=cache, sleep=(sleeps.append if sleeps is not None else (lambda s: None)), recorder=None)


def ok(payload, **headers):
    return httpx.Response(200, json={"errors": [], "response": payload}, headers=headers)


def test_parse_football_fixture_statuses():
    f = parse_fixture(FB_FIXTURE)
    assert (f.status, f.home_score, f.away_score, f.ht_home, f.home.external_id) == ("finished", 2, 1, 1, "33")
    assert f.kickoff.tzinfo is not None
    assert parse_fixture(FB_NS).status == "scheduled"


def test_football_provider_calls_correct_endpoints_and_header():
    seen = []

    def h(req: httpx.Request):
        seen.append(req)
        if req.url.path.endswith("/fixtures/statistics"):
            return ok([{"team": {"id": 33}, "statistics": [{"type": "Corner Kicks", "value": 7}, {"type": "Yellow Cards", "value": 2},
                                                           {"type": "Red Cards", "value": None}, {"type": "Ball Possession", "value": "55%"}]}])
        if req.url.path.endswith("/standings"):
            return ok([{"league": {"standings": [[{"rank": 1, "team": {"id": 33, "name": "Club A"}, "points": 10, "form": "WWD",
                                                   "all": {"played": 4, "win": 3, "draw": 1, "lose": 0, "goals": {"for": 8, "against": 2}}}]]}}])
        return ok([FB_FIXTURE])

    p = ApiFootballProvider(make_client(h))
    assert len(p.fixtures_by_date(date(2026, 10, 20))) == 1
    assert len(p.fixtures_by_league_season("39", 2026)) == 1
    st = p.fixture_statistics("1001")[0].stats
    assert st["corners"] == 7 and st["yellow_cards"] == 2 and st["red_cards"] is None and st["possession"] == 55
    row = p.standings("39", 2026)[0]
    assert (row.position, row.points, row.points_for, row.form) == (1, 10, 8, "WWD")
    assert all(r.headers["x-apisports-key"] == "K" for r in seen)
    assert seen[0].url.path == "/fixtures" and seen[0].url.params["date"] == "2026-10-20"
    assert seen[1].url.params["league"] == "39" and seen[1].url.params["season"] == "2026"


def test_missing_key_never_hits_network():
    called = []
    c = make_client(lambda r: called.append(r) or ok([]), key="")
    with pytest.raises(ProviderError) as e:
        c.get("fixtures", {})
    assert e.value.code == "provider_not_configured" and not called


def test_cache_hit_does_not_consume_budget_or_requests():
    calls = []
    c = make_client(lambda r: calls.append(1) or ok([FB_FIXTURE]))
    a = c.get("fixtures", {"date": "2026-10-20"}, ttl=60)
    b = c.get("fixtures", {"date": "2026-10-20"}, ttl=60)
    assert a == b and len(calls) == 1 and c.used_today() == 1
    c.get("fixtures", {"date": "2026-10-21"}, ttl=60)
    assert len(calls) == 2 and c.used_today() == 2


def test_daily_budget_and_reserve():
    from app.core.config import get_settings
    s = get_settings(); old = (s.provider_daily_budget, s.provider_reserve)
    s.provider_daily_budget, s.provider_reserve = 5, 2
    try:
        c = make_client(lambda r: ok([]))
        for i in range(3):
            c.get("fixtures", {"i": i}, ttl=0)                      # presupuesto normal = 5 - 2 reserva = 3
        with pytest.raises(BudgetExceeded):
            c.get("fixtures", {"i": 99}, ttl=0)                      # sincronización: no toca la reserva
        c.get("fixtures", {"i": 100}, ttl=0, priority="high")        # petición interactiva sí puede
    finally:
        s.provider_daily_budget, s.provider_reserve = old


def test_retries_on_5xx_then_succeeds_and_gives_up():
    sleeps, n = [], {"c": 0}

    def flaky(req):
        n["c"] += 1
        return httpx.Response(503) if n["c"] < 3 else ok([FB_FIXTURE])

    c = make_client(flaky, sleeps=sleeps)
    assert c.get("fixtures", {}, ttl=0)["response"]
    assert n["c"] == 3 and sleeps == [1, 2]                          # backoff exponencial
    n["c"] = -100
    with pytest.raises(ProviderError):
        make_client(lambda r: httpx.Response(500), sleeps=[]).get("fixtures", {}, ttl=0)


def test_rate_limit_429_and_business_errors_and_network_errors():
    with pytest.raises(ProviderError):
        make_client(lambda r: httpx.Response(429)).get("x", {}, ttl=0)
    err = make_client(lambda r: httpx.Response(200, json={"errors": {"requests": "You have reached the request limit for the day"}, "response": []}))
    with pytest.raises(ProviderError) as e:
        err.get("x", {}, ttl=0)
    assert "request limit" in e.value.message

    def boom(req):
        raise httpx.ConnectError("sin red")

    with pytest.raises(ProviderError):
        make_client(boom).get("x", {}, ttl=0)


def test_client_error_records_calls():
    rec = []
    t = httpx.MockTransport(lambda r: ok([], **{"x-ratelimit-requests-remaining": "42"}))
    c = ProviderHttpClient("api-football", "https://x", "K", client=httpx.Client(transport=t), cache=Cache(MemoryBackend()), recorder=rec.append)
    c.get("fixtures", {"a": 1}, ttl=0)
    assert rec and rec[0]["ok"] and rec[0]["remaining_daily"] == 42


def test_nba_provider_parsing():
    game = {"id": 9, "league": "standard", "season": 2025, "date": {"start": "2026-02-01T01:00:00.000Z"}, "status": {"short": 3},
            "teams": {"home": {"id": 1, "name": "Home Team", "code": "HOM"}, "visitors": {"id": 2, "name": "Away Team"}},
            "scores": {"home": {"points": 110}, "visitors": {"points": 101}}}
    other = {**game, "id": 10, "league": "africa"}
    c = make_client(lambda r: ok([game, other]))
    p = ApiNbaProvider(c)
    g = p.fixtures_by_date(date(2026, 2, 1))
    assert len(g) == 1 and (g[0].home_score, g[0].away_score, g[0].status, g[0].home.short_name) == (110, 101, "finished", "HOM")
    with pytest.raises(NotImplementedError):
        p.injuries("9")


def test_real_mode_factory_and_demo_mode_makes_no_calls():
    from app.core.config import get_settings
    from app.services.providers.factory import get_provider
    s = get_settings()
    assert get_provider("football").is_demo
    s.provider_mode = "real"
    try:
        assert isinstance(get_provider("football"), ApiFootballProvider) and isinstance(get_provider("basketball"), ApiNbaProvider)
    finally:
        s.provider_mode = "demo"


# ---------- sincronización con proveedor simulado ----------
class FakeFootball(ApiFootballProvider):
    name = "api-football"

    def __init__(self):
        pass

    def fixtures_by_league_season(self, league_external_id, season, *, priority="normal"):
        return [parse_fixture(FB_FIXTURE), parse_fixture(FB_NS)]

    def fixture_statistics(self, fid):
        from app.services.providers.base import PTeamFixtureStats
        return [PTeamFixtureStats("33", {"corners": 6, "yellow_cards": 2, "red_cards": 0}), PTeamFixtureStats("34", {"corners": 3, "yellow_cards": 1, "red_cards": 1})]

    def standings(self, l, s):
        from app.services.providers.base import PStandingRow, PTeam
        return [PStandingRow(PTeam("33", "Club A"), 1, 1, 1, 0, 0, 2, 1, 3, "W")]


def test_sync_upserts_and_is_idempotent(db):
    from app.db.models import AppSetting, Standing
    from app.services import sync
    row = db.get(AppSetting, "tracked_leagues")
    row.value = [{"sport": "football", "external_id": "39", "season": 2026}]; db.commit()
    try:
        r1 = sync.sync_fixtures(db, "football", FakeFootball())
        r2 = sync.sync_fixtures(db, "football", FakeFootball())
        assert r1["status"] == r2["status"] == "success"
        fxs = db.scalars(select(Fixture).where(Fixture.provider == "api-football")).all()
        assert len(fxs) == 2 and {f.status for f in fxs} == {"finished", "scheduled"}
        hist = db.scalars(select(HistoricalResult).where(HistoricalResult.fixture_id.in_([f.id for f in fxs]))).all()
        assert len(hist) == 1 and (hist[0].home_score, hist[0].extra["ht_home"]) == (2, 1)
        assert not any(f.is_demo for f in fxs)
        assert sync.sync_fixture_stats(db, "football", provider=FakeFootball())["items"] == 1
        db.expire_all()
        h = db.scalars(select(HistoricalResult).where(HistoricalResult.fixture_id == hist[0].fixture_id)).one()
        assert h.extra["home_corners"] == 6 and h.extra["away_cards"] == 2
        assert sync.sync_standings(db, "football", FakeFootball())["items"] == 1
        assert db.scalar(select(func.count()).select_from(Standing).join(League).where(League.provider == "api-football")) == 1
    finally:
        db.rollback()
        from app.db.models import ProviderSyncJob
        row = db.get(AppSetting, "tracked_leagues"); row.value = []; db.commit()


def test_sync_failure_is_logged_not_raised(db):
    from app.db.models import AppSetting, ProviderSyncJob
    from app.services import sync

    class Broken(FakeFootball):
        def fixtures_by_league_season(self, *a, **k):
            raise ProviderError("proveedor caído")

    db.get(AppSetting, "tracked_leagues").value = [{"sport": "football", "external_id": "39", "season": 2026}]; db.commit()
    try:
        r = sync.sync_fixtures(db, "football", Broken())
        assert r["status"] == "failed"
        j = db.scalars(select(ProviderSyncJob).where(ProviderSyncJob.status == "failed").order_by(ProviderSyncJob.id.desc())).first()
        assert "proveedor caído" in j.message
    finally:
        db.get(AppSetting, "tracked_leagues").value = []; db.commit()


# ---------- caché ----------
@pytest.mark.parametrize("backend", ["memory", "redis"])
def test_cache_backends(backend):
    b = MemoryBackend() if backend == "memory" else RedisBackend(fakeredis.FakeRedis())
    c = Cache(b)
    c.set_json("api:a", {"x": 1}, 100); c.set_json("api:b", [1, 2]); c.set_json("other", 1)
    assert c.get_json("api:a") == {"x": 1} and c.get_json("missing") is None
    assert [c.incr("n", 60) for _ in range(3)] == [1, 2, 3]
    c.invalidate_prefix("api:")
    assert c.get_json("api:a") is None and c.get_json("api:b") is None and c.get_json("other") == 1


def test_memory_cache_ttl_expiry():
    b = MemoryBackend(); b.set("k", "v", ttl=1)
    b._d["k"] = (b._d["k"][0] - 5, "v")
    assert b.get("k") is None


def test_standings_endpoint_is_cached_and_invalidated(client, user, db):
    from app.core.cache import get_cache
    lg = client.get("/api/v1/leagues?sport=football", headers=user["headers"]).json()["items"][0]["id"]
    client.get(f"/api/v1/standings?league_id={lg}", headers=user["headers"])
    assert get_cache().get_json(f"api:standings:{lg}") is not None
    from app.jobs.tasks import run_job
    assert run_job("invalidate_cache")["status"] == "success"
    assert get_cache().get_json(f"api:standings:{lg}") is None


# ---------- jobs ----------
def test_generate_analyses_and_alerts_job(client, make_user, db):
    from app.jobs.tasks import generate_analyses, send_alerts
    u = make_user()
    client.post("/api/v1/subscriptions/demo/activate", headers=u["headers"])
    fx = db.scalars(select(Fixture).where(Fixture.status == "scheduled", Fixture.kickoff > datetime.now(timezone.utc),
                                          Fixture.kickoff < datetime.now(timezone.utc) + timedelta(hours=20))).first()
    assert fx is not None
    client.post("/api/v1/favorites", headers=u["headers"], json={"kind": "team", "ref_id": fx.home_team_id})
    assert send_alerts(db) >= 1
    db.commit()
    assert send_alerts(db) == 0                       # sin duplicados
    n = client.get("/api/v1/notifications", headers=u["headers"]).json()
    assert any(i["kind"] == "match_alert" for i in n["items"])
    free = make_user()
    client.post("/api/v1/favorites", headers=free["headers"], json={"kind": "team", "ref_id": fx.home_team_id})
    assert db.scalar(select(func.count()).select_from(Notification).where(Notification.user_id == free["id"], Notification.kind == "match_alert")) == 0
    generate_analyses(db)                              # no revienta; sólo partidos futuros


# ---------- persistencia / restricciones PostgreSQL ----------
def test_postgres_constraints(db):
    fx = db.scalars(select(Fixture).limit(1)).first()
    sport = db.get(Sport, fx.sport_id)
    with pytest.raises(IntegrityError):               # equipos distintos
        db.add(Fixture(sport_id=fx.sport_id, league_id=fx.league_id, provider="demo", external_id="SELF", home_team_id=fx.home_team_id,
                       away_team_id=fx.home_team_id, kickoff=datetime.now(timezone.utc))); db.flush()
    db.rollback()
    pr = db.scalars(select(Prediction).limit(1)).first()
    with pytest.raises(IntegrityError):               # probabilidad en [0,1]
        db.add(Prediction(analysis_id=pr.analysis_id, fixture_id=pr.fixture_id, sport_id=pr.sport_id, league_id=pr.league_id, market="X", selection="y",
                          market_group="basic", probability=1.5, category="c", uncertainty="low", data_points=1)); db.flush()
    db.rollback()


def test_usage_unique_and_persistence_across_sessions(make_user, db):
    from app.db.session import SessionLocal
    u = make_user()
    fid = db.scalars(select(Fixture.id).limit(1)).first()
    s1 = SessionLocal()
    s1.add(DailyUsage(user_id=u["id"], usage_date=date(2026, 1, 1), fixture_id=fid, plan_code="FREE")); s1.commit(); s1.close()
    s2 = SessionLocal()
    assert s2.scalar(select(func.count()).select_from(DailyUsage).where(DailyUsage.user_id == u["id"])) == 1
    with pytest.raises(IntegrityError):
        s2.add(DailyUsage(user_id=u["id"], usage_date=date(2026, 1, 1), fixture_id=fid, plan_code="FREE")); s2.commit()
    s2.rollback(); s2.close()


def test_google_login_with_mocked_google(client, monkeypatch):
    from app.core.config import get_settings
    from app.services import accounts
    s = get_settings()
    assert client.post("/api/v1/auth/google", json={"id_token": "x"}).status_code == 501
    s.google_client_id = "cid"
    try:
        def fake_get(self, url, params=None, **kw):
            return httpx.Response(200, json={"aud": "cid", "sub": "g-123", "email": "gente@pronoscore-test.example.com", "email_verified": "true", "name": "G"})
        monkeypatch.setattr(httpx.Client, "get", fake_get)
        r = client.post("/api/v1/auth/google", json={"id_token": "tok"})
        assert r.status_code == 200 and r.json()["user"]["email_verified"] is True
        assert client.post("/api/v1/auth/google", json={"id_token": "tok"}).json()["user"]["id"] == r.json()["user"]["id"]
        monkeypatch.setattr(httpx.Client, "get", lambda self, url, params=None, **kw: httpx.Response(200, json={"aud": "otra-app", "email": "a@b.co", "email_verified": "true", "sub": "1"}))
        assert client.post("/api/v1/auth/google", json={"id_token": "tok"}).status_code == 401
    finally:
        s.google_client_id = ""
