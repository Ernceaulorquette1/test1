"""Datos de DEMOSTRACIÓN (ficticios, identificados con [DEMO]). NUNCA son datos deportivos reales.

Se generan con una semilla fija: equipos con fortalezas latentes y resultados simulados con Poisson/Normal.
Sólo para probar la aplicación sin claves de API."""
from __future__ import annotations

import random
from datetime import datetime, timedelta, timezone
from typing import Optional

import numpy as np
from sqlalchemy import delete, select
from sqlalchemy.orm import Session

from ..core.config import get_settings
from ..db.models import (Announcement, Country, Fixture, HistoricalResult, Injury, League, Notification, Season, Sport,
                         Standing, Subscription, Team, User, Plan, FixtureAnalysis, Prediction, PredictionOutcome,
                         PredictionEvaluation, DailyUsage)
from ..services import accounts
from ..services.analysis import create_analysis
from ..services.billing.service import demo_activate
from ..services.settlement import recompute_evaluations, settle_fixture
from .base import seed_base

DEMO_PASSWORDS = {"admin": "Demo#Admin2026", "pro": "Demo#Pro2026", "free": "Demo#Free2026"}
DEMO_EMAILS = {"admin": "admin@pronoscore-demo.example.com", "pro": "pro@pronoscore-demo.example.com",
               "free": "free@pronoscore-demo.example.com"}

FOOTBALL_TEAMS = ["Demo FC Aurora", "Demo United Boreal", "Demo Atlético Cóndor", "Demo Real Pampa", "Demo Sporting Delta",
                  "Demo Racing Austral", "Demo Deportivo Ceibo", "Demo City Nova", "Demo Athletic Brisa", "Demo Olimpia Sur"]
BASKET_TEAMS = ["Demo Hoopers Norte", "Demo Dunkers Costa", "Demo Rockets Andes", "Demo Wolves Valle", "Demo Kings Delta",
                "Demo Flames Sur", "Demo Giants Bahía", "Demo Phoenix Pampa", "Demo Comets Lago", "Demo Titans Cumbre",
                "Demo Pilots Alba", "Demo Sharks Puerto"]


def _slug(n: str) -> str:
    return "".join(c for c in n.lower() if c.isalnum())[:8].upper()


def has_demo_data(db: Session) -> bool:
    return db.scalar(select(League.id).where(League.is_demo.is_(True)).limit(1)) is not None


def wipe_demo_data(db: Session) -> None:
    fx_ids = select(Fixture.id).where(Fixture.is_demo.is_(True))
    db.execute(delete(PredictionEvaluation))
    db.execute(delete(DailyUsage).where(DailyUsage.fixture_id.in_(fx_ids)))
    db.execute(delete(FixtureAnalysis).where(FixtureAnalysis.fixture_id.in_(fx_ids)))   # cascada a predicciones/resultados
    db.execute(delete(Injury).where(Injury.team_id.in_(select(Team.id).where(Team.is_demo.is_(True)))))
    db.execute(delete(Standing).where(Standing.league_id.in_(select(League.id).where(League.is_demo.is_(True)))))
    db.execute(delete(HistoricalResult).where(HistoricalResult.is_demo.is_(True)))
    db.execute(delete(Fixture).where(Fixture.is_demo.is_(True)))
    db.execute(delete(Team).where(Team.is_demo.is_(True)))
    db.execute(delete(Season).where(Season.league_id.in_(select(League.id).where(League.is_demo.is_(True)))))
    db.execute(delete(League).where(League.is_demo.is_(True)))
    db.flush()


def _mk_league(db, sport: Sport, name: str, ext: str, country: Country) -> tuple[League, Season]:
    lg = League(sport_id=sport.id, country_id=country.id, provider="demo", external_id=ext, name=name, is_demo=True)
    db.add(lg); db.flush()
    se = Season(league_id=lg.id, year=datetime.now(timezone.utc).year, is_current=True)
    db.add(se); db.flush()
    return lg, se


def _mk_teams(db, sport, names, prefix) -> list[Team]:
    out = []
    for i, n in enumerate(names):
        t = Team(sport_id=sport.id, provider="demo", external_id=f"{prefix}{i}", name=n, short_name=_slug(n[5:]), is_demo=True)
        db.add(t); out.append(t)
    db.flush()
    return out




def build_standings(db: Session, league: League, season: Season, teams: list[Team], finished: list[Fixture], sport_code: str):
    rows = {t.id: dict(p=0, w=0, d=0, l=0, gf=0, ga=0, pts=0, form=[]) for t in teams}
    for f in sorted(finished, key=lambda x: x.kickoff):
        for tid, gf, ga in ((f.home_team_id, f.home_score, f.away_score), (f.away_team_id, f.away_score, f.home_score)):
            r = rows[tid]; r["p"] += 1; r["gf"] += gf; r["ga"] += ga
            res = "W" if gf > ga else "D" if gf == ga else "L"
            r["form"].append(res)
            if res == "W":
                r["w"] += 1; r["pts"] += 3 if sport_code == "football" else 1
            elif res == "D":
                r["d"] += 1; r["pts"] += 1
            else:
                r["l"] += 1
    key = (lambda kv: (-kv[1]["pts"], -(kv[1]["gf"] - kv[1]["ga"]))) if sport_code == "football" else \
          (lambda kv: (-kv[1]["w"], -(kv[1]["gf"] - kv[1]["ga"])))
    for pos, (tid, r) in enumerate(sorted(rows.items(), key=key), start=1):
        db.add(Standing(league_id=league.id, season_year=season.year, team_id=tid, position=pos, played=r["p"], won=r["w"],
                        drawn=r["d"], lost=r["l"], points_for=r["gf"], points_against=r["ga"], points=r["pts"],
                        form="".join(r["form"][-5:])))


def seed_football(db: Session, sport: Sport, country: Country, now: datetime, rng: np.random.Generator):
    lg, season = _mk_league(db, sport, "Liga Demo Atlántica [DEMO]", "DEMO-FB-1", country)
    teams = _mk_teams(db, sport, FOOTBALL_TEAMS, "FB")
    att = {t.id: float(np.exp(rng.normal(0, 0.28))) for t in teams}
    dfn = {t.id: float(np.exp(rng.normal(0, 0.28))) for t in teams}
    # calendario: jornadas semanales, 5 partidos por jornada; ~28 jornadas pasadas (3 vueltas parciales) + próximos 7 días
    pairs = [(h, a) for h in teams for a in teams if h.id != a.id]
    rng.shuffle(pairs)
    day0 = now.replace(hour=15, minute=0, second=0, microsecond=0)
    fixtures, finished, idx = [], [], 0
    slots = []                      # (kickoff)
    for d in range(-150, 8):
        k = day0 + timedelta(days=d)
        if k.weekday() in (2, 5, 6):    # miércoles, sábado, domingo
            for hh in (13, 16):
                slots.append(k.replace(hour=hh) + timedelta(minutes=0))
    # asegurar partidos HOY futuros
    slots += [now + timedelta(hours=3), now + timedelta(hours=5)]
    slots = sorted(set(slots))
    for kickoff in slots:
        for _ in range(2 if kickoff.hour % 2 else 3):
            h, a = pairs[idx % len(pairs)]; idx += 1
            fx = Fixture(sport_id=sport.id, league_id=lg.id, season_id=season.id, provider="demo", external_id=f"DFB{idx}",
                         home_team_id=h.id, away_team_id=a.id, kickoff=kickoff, is_demo=True,
                         status="finished" if kickoff < now - timedelta(hours=2) else "scheduled")
            db.add(fx); fixtures.append(fx)
    db.flush()
    for fx in fixtures:
        if fx.status != "finished":
            continue
        lam_h = 1.35 * 1.25 * att[fx.home_team_id] * dfn[fx.away_team_id]
        lam_a = 1.35 * att[fx.away_team_id] * dfn[fx.home_team_id]
        hg, ag = int(rng.poisson(lam_h)), int(rng.poisson(lam_a))
        hth, hta = int(rng.binomial(hg, 0.45)), int(rng.binomial(ag, 0.45))
        fx.home_score, fx.away_score = hg, ag
        db.add(HistoricalResult(
            fixture_id=fx.id, sport_id=sport.id, league_id=lg.id, home_team_id=fx.home_team_id, away_team_id=fx.away_team_id,
            played_at=fx.kickoff, home_score=hg, away_score=ag, is_demo=True,
            extra={"ht_home": hth, "ht_away": hta, "home_corners": int(rng.poisson(5.6 * att[fx.home_team_id])),
                   "away_corners": int(rng.poisson(4.4 * att[fx.away_team_id])),
                   "home_cards": int(rng.poisson(2.0)), "away_cards": int(rng.poisson(2.3))}))
        finished.append(fx)
    build_standings(db, lg, season, teams, finished, "football")
    # lesión de ejemplo (dato ficticio)
    nxt = next((f for f in fixtures if f.status == "scheduled"), None)
    if nxt:
        db.add(Injury(team_id=nxt.home_team_id, fixture_id=nxt.id, player_name="Jugador Demo 9", reason="Lesión muscular (demo)"))
    return fixtures


def seed_basketball(db: Session, sport: Sport, country: Country, now: datetime, rng: np.random.Generator):
    lg, season = _mk_league(db, sport, "Liga Basket Demo [DEMO]", "DEMO-BB-1", country)
    teams = _mk_teams(db, sport, BASKET_TEAMS, "BB")
    off = {t.id: 112 + float(rng.normal(0, 3)) for t in teams}
    dfn = {t.id: 112 + float(rng.normal(0, 3)) for t in teams}
    fixtures = []
    n = 0
    for d in range(-75, 8):
        day = (now + timedelta(days=d)).replace(hour=0, minute=0, second=0, microsecond=0)
        order = list(teams); rng.shuffle(order)
        for g in range(int(rng.integers(3, 6))):
            h, a = order[2 * g], order[2 * g + 1]
            kickoff = day + timedelta(hours=int(rng.choice([1, 2, 3, 23])))
            # hoy: hacer que los partidos queden en el futuro
            if d == 0:
                kickoff = now + timedelta(hours=2 + g)
            n += 1
            fx = Fixture(sport_id=sport.id, league_id=lg.id, season_id=season.id, provider="demo", external_id=f"DBB{n}",
                         home_team_id=h.id, away_team_id=a.id, kickoff=kickoff, is_demo=True,
                         status="finished" if kickoff < now - timedelta(hours=3) else "scheduled")
            db.add(fx); fixtures.append(fx)
    db.flush()
    finished = []
    for fx in fixtures:
        if fx.status != "finished":
            continue
        pace = float(rng.normal(99, 3))
        eh = pace * (off[fx.home_team_id] + dfn[fx.away_team_id] - 112) / 100 + 1.3
        ea = pace * (off[fx.away_team_id] + dfn[fx.home_team_id] - 112) / 100 - 1.3
        hp, ap = int(rng.normal(eh, 11)), int(rng.normal(ea, 11))
        if hp == ap:
            hp += 1
        fx.home_score, fx.away_score = hp, ap
        db.add(HistoricalResult(fixture_id=fx.id, sport_id=sport.id, league_id=lg.id, home_team_id=fx.home_team_id,
                                away_team_id=fx.away_team_id, played_at=fx.kickoff, home_score=hp, away_score=ap, is_demo=True,
                                extra={"home_poss": pace, "away_poss": pace}))
        finished.append(fx)
    build_standings(db, lg, season, teams, finished, "basketball")
    return fixtures


def backfill_predictions(db: Session, fixtures: list[Fixture], last_n: int) -> int:
    """DEMO: genera predicciones 'previas' sobre partidos ya jugados usando sólo datos anteriores a cada partido,
    para poblar historial y métricas. Quedan marcadas como demo_backfilled."""
    fin = sorted([f for f in fixtures if f.status == "finished"], key=lambda f: f.kickoff)[-last_n:]
    n = 0
    for fx in fin:
        t = fx.kickoff - timedelta(hours=2)
        an = create_analysis(db, fx, created_at=t, as_of=t, is_demo=True, force_pre_match=True)
        if an.status == "ok":
            n += settle_fixture(db, fx)
    return n


def seed_demo(db: Session, now: Optional[datetime] = None, force: bool = False) -> dict:
    s = get_settings()
    if not s.is_dev:
        raise RuntimeError("El seed DEMO sólo puede ejecutarse en entornos local/test")
    seed_base(db)
    if has_demo_data(db):
        if not force:
            return {"status": "exists"}
        wipe_demo_data(db)
    now = now or datetime.now(timezone.utc)
    rng = np.random.default_rng(360)
    country = db.scalar(select(Country).where(Country.name == "Demolandia [DEMO]"))
    if not country:
        country = Country(name="Demolandia [DEMO]", code="DM"); db.add(country); db.flush()
    fb_sport, bb_sport = (db.scalar(select(Sport).where(Sport.code == c)) for c in ("football", "basketball"))
    fb_fx = seed_football(db, fb_sport, country, now, rng)
    bb_fx = seed_basketball(db, bb_sport, country, now, rng)
    db.flush()
    n1 = backfill_predictions(db, fb_fx, 60)
    n2 = backfill_predictions(db, bb_fx, 80)
    recompute_evaluations(db)
    # análisis PRE-PARTIDO reales (generados ahora, antes del inicio) para los próximos partidos
    up = 0
    for fx in [f for f in fb_fx + bb_fx if f.status == "scheduled"]:
        if fx.kickoff - now < timedelta(days=3):
            create_analysis(db, fx, now, is_demo=True); up += 1
    seed_demo_users(db)
    if not db.scalar(select(Announcement.id)):
        db.add(Announcement(title="Modo DEMO activo", body="Los datos mostrados son ficticios y sirven sólo para pruebas.", level="warning"))
    return {"status": "seeded", "settled_outcomes": n1 + n2, "upcoming_analyses": up}


def seed_demo_users(db: Session) -> None:
    if not get_settings().demo_accounts_enabled:
        return
    specs = {"admin": ("ADMIN", "Admin Demo"), "pro": ("USER", "Usuario Pro Demo"), "free": ("USER", "Usuario Gratis Demo")}
    for key, (role, name) in specs.items():
        u = db.scalar(select(User).where(User.email == DEMO_EMAILS[key]))
        if not u:
            u = accounts.create_user(db, DEMO_EMAILS[key], DEMO_PASSWORDS[key], role=role, display_name=name,
                                     is_demo=True, email_verified=True)
            db.add(Notification(user_id=u.id, kind="info", title="Bienvenido a PronoScore360 (DEMO)",
                                body="Cuenta de demostración. Los datos son ficticios."))
        if key == "pro":
            demo_activate_if_needed(db, u)
    db.flush()


def demo_activate_if_needed(db: Session, u: User) -> None:
    from ..services.plans import active_subscription
    if not active_subscription(db, u.id):
        demo_activate(db, u, days=365)
