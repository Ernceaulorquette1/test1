"""Sincronización con proveedores deportivos (sólo modo REAL) y utilidades de upsert."""
from __future__ import annotations

import logging
from datetime import date, datetime, timedelta, timezone
from typing import Callable, Optional

from sqlalchemy import select
from sqlalchemy.orm import Session

from ..core.cache import get_cache
from ..db.models import (AppSetting, Country, Fixture, FixtureStatistic, HistoricalResult, League, ProviderRequest,
                         ProviderSyncJob, Season, Sport, Standing, Team)
from .providers.base import PFixture, PLeague, PTeam, SportsProvider
from .providers.factory import get_provider

log = logging.getLogger("pronoscore.sync")


def now() -> datetime:
    return datetime.now(timezone.utc)


def db_recorder(db_factory) -> Callable[[dict], None]:
    """Registra cada petición al proveedor en provider_requests (sesión propia para no mezclar transacciones)."""
    def rec(d: dict) -> None:
        db = db_factory()
        try:
            db.add(ProviderRequest(provider=d["provider"], endpoint=d.get("endpoint", ""), params=d.get("params") or {},
                                   status_code=d.get("status_code"), ok=d.get("ok", False), from_cache=d.get("from_cache", False),
                                   duration_ms=d.get("duration_ms"), remaining_daily=d.get("remaining_daily"), error=d.get("error")))
            db.commit()
        finally:
            db.close()
    return rec


def sport_row(db: Session, code: str) -> Sport:
    return db.scalar(select(Sport).where(Sport.code == code))


def upsert_league(db: Session, sport: Sport, provider: str, pl: PLeague) -> League:
    lg = db.scalar(select(League).where(League.provider == provider, League.external_id == pl.external_id, League.sport_id == sport.id))
    country = None
    if pl.country:
        country = db.scalar(select(Country).where(Country.name == pl.country))
        if not country:
            country = Country(name=pl.country, code=pl.country_code); db.add(country); db.flush()
    if not lg:
        lg = League(sport_id=sport.id, provider=provider, external_id=pl.external_id, name=pl.name, logo_url=pl.logo,
                    country_id=country.id if country else None)
        db.add(lg); db.flush()
    else:
        lg.name, lg.logo_url = pl.name, pl.logo or lg.logo_url
    return lg


def upsert_team(db: Session, sport: Sport, provider: str, pt: PTeam) -> Team:
    t = db.scalar(select(Team).where(Team.provider == provider, Team.external_id == pt.external_id, Team.sport_id == sport.id))
    if not t:
        t = Team(sport_id=sport.id, provider=provider, external_id=pt.external_id, name=pt.name, logo_url=pt.logo,
                 short_name=pt.short_name)
        db.add(t); db.flush()
    else:
        t.name, t.logo_url = pt.name, pt.logo or t.logo_url
    return t


def upsert_fixture(db: Session, sport: Sport, provider: str, pf: PFixture) -> Fixture:
    lg = upsert_league(db, sport, provider, pf.league)
    season = db.scalar(select(Season).where(Season.league_id == lg.id, Season.year == pf.season))
    if not season:
        season = Season(league_id=lg.id, year=pf.season, is_current=True); db.add(season); db.flush()
    h, a = upsert_team(db, sport, provider, pf.home), upsert_team(db, sport, provider, pf.away)
    fx = db.scalar(select(Fixture).where(Fixture.provider == provider, Fixture.external_id == pf.external_id, Fixture.sport_id == sport.id))
    if not fx:
        fx = Fixture(sport_id=sport.id, league_id=lg.id, season_id=season.id, provider=provider, external_id=pf.external_id,
                     home_team_id=h.id, away_team_id=a.id, kickoff=pf.kickoff, status=pf.status)
        db.add(fx)
    fx.kickoff, fx.status, fx.home_score, fx.away_score = pf.kickoff, pf.status, pf.home_score, pf.away_score
    db.flush()
    if pf.status == "finished" and pf.home_score is not None and pf.away_score is not None:
        hr = db.scalar(select(HistoricalResult).where(HistoricalResult.fixture_id == fx.id))
        extra = {"ht_home": pf.ht_home, "ht_away": pf.ht_away}
        if not hr:
            db.add(HistoricalResult(fixture_id=fx.id, sport_id=sport.id, league_id=lg.id, home_team_id=h.id, away_team_id=a.id,
                                    played_at=pf.kickoff, home_score=pf.home_score, away_score=pf.away_score, extra=extra))
        else:
            hr.home_score, hr.away_score = pf.home_score, pf.away_score
            hr.extra = {**(hr.extra or {}), **{k: v for k, v in extra.items() if v is not None}}
    return fx


def tracked_leagues(db: Session) -> list[dict]:
    row = db.get(AppSetting, "tracked_leagues")
    return (row.value if row else []) or []


class JobRun:
    def __init__(self, db: Session, job: str, provider: str, by: str = "scheduler"):
        self.db, self.rec = db, ProviderSyncJob(job=job, provider=provider, status="running", triggered_by=by)
        db.add(self.rec); db.commit()

    def finish(self, status: str, items: int = 0, message: str = "") -> None:
        self.rec.status, self.rec.items, self.rec.message, self.rec.finished_at = status, items, message[:2000], now()
        self.db.commit()


def sync_fixtures(db: Session, sport_code: str, provider: Optional[SportsProvider] = None, by: str = "scheduler") -> dict:
    """Descarga calendario/resultados de las ligas rastreadas (1 petición por liga+temporada)."""
    provider = provider or get_provider(sport_code)
    run = JobRun(db, f"sync_fixtures:{sport_code}", provider.name, by)
    if provider.is_demo:
        run.finish("skipped", 0, "Modo DEMO: no se consulta ningún proveedor externo")
        return {"status": "skipped", "reason": "demo_mode"}
    sport = sport_row(db, sport_code)
    items = 0
    try:
        for lg in [x for x in tracked_leagues(db) if x["sport"] == sport_code]:
            for pf in provider.fixtures_by_league_season(str(lg["external_id"]), int(lg["season"])):
                upsert_fixture(db, sport, provider.name, pf); items += 1
            db.commit()
        get_cache().invalidate_prefix("api:")
        run.finish("success", items)
        return {"status": "success", "items": items}
    except Exception as e:   # el job registra el fallo y no tumba el scheduler
        db.rollback()
        run.finish("failed", items, str(e))
        log.exception("sync_fixtures falló")
        return {"status": "failed", "error": str(e)}


def sync_standings(db: Session, sport_code: str, provider: Optional[SportsProvider] = None, by: str = "scheduler") -> dict:
    provider = provider or get_provider(sport_code)
    run = JobRun(db, f"sync_standings:{sport_code}", provider.name, by)
    if provider.is_demo:
        run.finish("skipped", 0, "Modo DEMO"); return {"status": "skipped", "reason": "demo_mode"}
    sport, items = sport_row(db, sport_code), 0
    try:
        for lg in [x for x in tracked_leagues(db) if x["sport"] == sport_code]:
            league = db.scalar(select(League).where(League.provider == provider.name, League.external_id == str(lg["external_id"])))
            if not league:
                continue
            for r in provider.standings(str(lg["external_id"]), int(lg["season"])):
                t = upsert_team(db, sport, provider.name, r.team)
                st = db.scalar(select(Standing).where(Standing.league_id == league.id, Standing.season_year == int(lg["season"]), Standing.team_id == t.id))
                if not st:
                    st = Standing(league_id=league.id, season_year=int(lg["season"]), team_id=t.id, position=r.position); db.add(st)
                st.position, st.played, st.won, st.drawn, st.lost = r.position, r.played, r.won, r.drawn, r.lost
                st.points_for, st.points_against, st.points, st.form, st.group_name = r.points_for, r.points_against, r.points, r.form, r.group
                items += 1
            db.commit()
        get_cache().invalidate_prefix("api:")
        run.finish("success", items); return {"status": "success", "items": items}
    except Exception as e:
        db.rollback(); run.finish("failed", items, str(e)); return {"status": "failed", "error": str(e)}


def sync_fixture_stats(db: Session, sport_code: str, limit: int = 20, provider: Optional[SportsProvider] = None, by: str = "scheduler") -> dict:
    """Estadísticas por partido (córners, tarjetas, posesiones) de partidos terminados que aún no las tienen.
    Consume 1 petición por partido: se limita para respetar el presupuesto diario."""
    provider = provider or get_provider(sport_code)
    run = JobRun(db, f"sync_fixture_stats:{sport_code}", provider.name, by)
    if provider.is_demo:
        run.finish("skipped", 0, "Modo DEMO"); return {"status": "skipped", "reason": "demo_mode"}
    sport_id = sport_row(db, sport_code).id
    have = select(FixtureStatistic.fixture_id)
    fxs = db.scalars(select(Fixture).where(Fixture.sport_id == sport_id, Fixture.status == "finished", Fixture.provider == provider.name,
                                           Fixture.id.not_in(have)).order_by(Fixture.kickoff.desc()).limit(limit)).all()
    items = 0
    try:
        for fx in fxs:
            stats = provider.fixture_statistics(fx.external_id)
            by_team = {s.team_external_id: s.stats for s in stats}
            hs, as_ = by_team.get(fx.home_team.external_id), by_team.get(fx.away_team.external_id)
            if hs is None or as_ is None:
                continue
            for team_id, st in ((fx.home_team_id, hs), (fx.away_team_id, as_)):
                db.add(FixtureStatistic(fixture_id=fx.id, team_id=team_id, stats=st))
            hr = db.scalar(select(HistoricalResult).where(HistoricalResult.fixture_id == fx.id))
            if hr:
                ex = dict(hr.extra or {})
                if sport_code == "football":
                    ex.update(home_corners=hs.get("corners"), away_corners=as_.get("corners"),
                              home_cards=(hs.get("yellow_cards") or 0) + (hs.get("red_cards") or 0),
                              away_cards=(as_.get("yellow_cards") or 0) + (as_.get("red_cards") or 0))
                else:
                    for side, st in (("home", hs), ("away", as_)):
                        if all(st.get(k) is not None for k in ("fga", "fta", "oreb", "tov")):
                            ex[f"{side}_poss"] = st["fga"] + 0.44 * st["fta"] - st["oreb"] + st["tov"]
                hr.extra = ex
            items += 1
            db.commit()
        run.finish("success", items); return {"status": "success", "items": items}
    except Exception as e:
        db.rollback(); run.finish("failed", items, str(e)); return {"status": "failed", "error": str(e)}
