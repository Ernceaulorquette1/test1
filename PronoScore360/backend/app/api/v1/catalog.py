from __future__ import annotations

from datetime import date, datetime, time, timedelta, timezone
from typing import Optional
from zoneinfo import ZoneInfo

from fastapi import APIRouter, Depends, Query
from sqlalchemy import func, or_, select
from sqlalchemy.orm import Session

from ...core.cache import get_cache
from ...core.errors import AppError, NotFound
from ...db.models import (Country, Fixture, HistoricalResult, Injury, League, Sport, Standing, Team, User)
from ...db.session import get_db
from ..deps import Page, get_current_user, paginated
from .serializers import fixture_out, team_out

router = APIRouter(tags=["Catálogo deportivo"], dependencies=[Depends(get_current_user)])


@router.get("/sports", summary="Deportes disponibles")
def sports(db: Session = Depends(get_db)):
    return [{"code": s.code, "name": s.name} for s in db.scalars(select(Sport).where(Sport.enabled.is_(True)))]


@router.get("/leagues", summary="Ligas (búsqueda por nombre/país)")
def leagues(sport: Optional[str] = None, q: Optional[str] = None, country: Optional[str] = None,
            page: int = 1, page_size: int = 50, db: Session = Depends(get_db)):
    pg = Page(page, page_size)
    stmt = select(League).join(Sport).where(League.is_active.is_(True))
    if sport:
        stmt = stmt.where(Sport.code == sport)
    if q:
        stmt = stmt.where(League.name.ilike(f"%{q}%"))
    if country:
        stmt = stmt.join(Country, Country.id == League.country_id).where(Country.name.ilike(f"%{country}%"))
    total = db.scalar(select(func.count()).select_from(stmt.subquery()))
    rows = db.scalars(stmt.order_by(League.name).offset(pg.offset).limit(pg.page_size)).unique().all()
    return paginated([{"id": l.id, "name": l.name, "sport": l.sport.code, "country": l.country.name if l.country else None,
                       "logo_url": l.logo_url, "is_demo": l.is_demo} for l in rows], total, pg)


@router.get("/teams", summary="Búsqueda de equipos")
def teams(q: Optional[str] = None, sport: Optional[str] = None, league_id: Optional[int] = None,
          page: int = 1, page_size: int = 30, db: Session = Depends(get_db)):
    pg = Page(page, page_size)
    stmt = select(Team).join(Sport, Sport.id == Team.sport_id)
    if q:
        stmt = stmt.where(Team.name.ilike(f"%{q}%"))
    if sport:
        stmt = stmt.where(Sport.code == sport)
    if league_id:
        in_league = select(Fixture.home_team_id).where(Fixture.league_id == league_id).union(
            select(Fixture.away_team_id).where(Fixture.league_id == league_id))
        stmt = stmt.where(Team.id.in_(in_league))
    total = db.scalar(select(func.count()).select_from(stmt.subquery()))
    rows = db.scalars(stmt.order_by(Team.name).offset(pg.offset).limit(pg.page_size)).all()
    return paginated([team_out(t) for t in rows], total, pg)


def _day_bounds(d: date, tz: str) -> tuple[datetime, datetime]:
    z = ZoneInfo(tz)
    start = datetime.combine(d, time.min, tzinfo=z)
    return start.astimezone(timezone.utc), (start + timedelta(days=1)).astimezone(timezone.utc)


@router.get("/fixtures", summary="Calendario de partidos con filtros")
def fixtures(sport: Optional[str] = None, league_id: Optional[int] = None, team_id: Optional[int] = None,
             q: Optional[str] = Query(None, description="Texto en nombre de equipo o liga"),
             day: Optional[date] = Query(None, alias="date", description="Fecha (YYYY-MM-DD) en la zona horaria indicada"),
             date_from: Optional[date] = None, date_to: Optional[date] = None, status: Optional[str] = None,
             tz: Optional[str] = Query(None, description="Zona horaria IANA; por defecto la del usuario"),
             order: str = "asc", page: int = 1, page_size: int = 30,
             user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    tz = tz or user.profile.timezone
    try:
        ZoneInfo(tz)
    except Exception:
        raise AppError("Zona horaria inválida", code="invalid_timezone", status_code=422)
    pg = Page(page, page_size)
    stmt = select(Fixture).join(League, League.id == Fixture.league_id).join(Sport, Sport.id == Fixture.sport_id)
    if sport:
        stmt = stmt.where(Sport.code == sport)
    if league_id:
        stmt = stmt.where(Fixture.league_id == league_id)
    if team_id:
        stmt = stmt.where(or_(Fixture.home_team_id == team_id, Fixture.away_team_id == team_id))
    if status:
        stmt = stmt.where(Fixture.status == status)
    if q:
        ht, at = Team.__table__.alias("ht"), Team.__table__.alias("at")
        stmt = stmt.join(ht, ht.c.id == Fixture.home_team_id).join(at, at.c.id == Fixture.away_team_id).where(
            or_(ht.c.name.ilike(f"%{q}%"), at.c.name.ilike(f"%{q}%"), League.name.ilike(f"%{q}%")))
    if day:
        a, b = _day_bounds(day, tz); stmt = stmt.where(Fixture.kickoff >= a, Fixture.kickoff < b)
    if date_from:
        stmt = stmt.where(Fixture.kickoff >= _day_bounds(date_from, tz)[0])
    if date_to:
        stmt = stmt.where(Fixture.kickoff < _day_bounds(date_to, tz)[1])
    total = db.scalar(select(func.count()).select_from(stmt.subquery()))
    stmt = stmt.order_by(Fixture.kickoff.desc() if order == "desc" else Fixture.kickoff).offset(pg.offset).limit(pg.page_size)
    rows = db.scalars(stmt).unique().all()
    return paginated([fixture_out(f, tz) for f in rows], total, pg)


@router.get("/fixtures/{fixture_id}", summary="Detalle de partido (sin consumir cuota)")
def fixture_detail(fixture_id: int, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    f = db.get(Fixture, fixture_id)
    if not f:
        raise NotFound("Partido no encontrado")
    inj = db.scalars(select(Injury).where(Injury.fixture_id == f.id)).all()
    return {**fixture_out(f, user.profile.timezone),
            "injuries": [{"team_id": i.team_id, "player": i.player_name, "reason": i.reason, "status": i.status} for i in inj],
            "lineups": None, "lineups_note": "Alineaciones disponibles sólo si el proveedor las entrega."}


def _form(db: Session, team_id: int, last: int, before: Optional[datetime] = None, sport: str = "football") -> dict:
    q = select(HistoricalResult).where(or_(HistoricalResult.home_team_id == team_id, HistoricalResult.away_team_id == team_id))
    if before:
        q = q.where(HistoricalResult.played_at < before)
    rows = db.scalars(q.order_by(HistoricalResult.played_at.desc()).limit(last)).all()
    n = len(rows)
    out = {"matches": n, "wins": 0, "draws": 0, "losses": 0, "for": 0, "against": 0, "form": [],
           "home": {"matches": 0, "points_for": 0, "points_against": 0, "wins": 0},
           "away": {"matches": 0, "points_for": 0, "points_against": 0, "wins": 0}}
    cor_f = cor_a = card_f = card_a = 0; ncor = ncard = 0
    for r in rows:
        home = r.home_team_id == team_id
        gf, ga = (r.home_score, r.away_score) if home else (r.away_score, r.home_score)
        res = "W" if gf > ga else "D" if gf == ga else "L"
        out["form"].append(res)
        out["wins" if res == "W" else "draws" if res == "D" else "losses"] += 1
        out["for"] += gf; out["against"] += ga
        side = out["home" if home else "away"]
        side["matches"] += 1; side["points_for"] += gf; side["points_against"] += ga; side["wins"] += int(res == "W")
        x = r.extra or {}
        k = "home" if home else "away"; o = "away" if home else "home"
        if x.get(f"{k}_corners") is not None and x.get(f"{o}_corners") is not None:
            cor_f += x[f"{k}_corners"]; cor_a += x[f"{o}_corners"]; ncor += 1
        if x.get(f"{k}_cards") is not None and x.get(f"{o}_cards") is not None:
            card_f += x[f"{k}_cards"]; card_a += x[f"{o}_cards"]; ncard += 1
    if n:
        out["avg_for"], out["avg_against"] = round(out["for"] / n, 2), round(out["against"] / n, 2)
    if ncor:
        out["corners"] = {"avg_for": round(cor_f / ncor, 2), "avg_against": round(cor_a / ncor, 2), "matches": ncor}
    if ncard:
        out["cards"] = {"avg_for": round(card_f / ncard, 2), "avg_against": round(card_a / ncard, 2), "matches": ncard}
    return out


@router.get("/teams/{team_id}/stats", summary="Estadísticas del equipo: últimos 5/10/20, local y visitante")
def team_stats(team_id: int, last: int = Query(10, description="5, 10 o 20"), db: Session = Depends(get_db)):
    if last not in (5, 10, 20):
        raise AppError("last debe ser 5, 10 o 20", code="invalid_param", status_code=422)
    t = db.get(Team, team_id)
    if not t:
        raise NotFound("Equipo no encontrado")
    return {"team": team_out(t), "last": last, **_form(db, team_id, last)}


@router.get("/fixtures/{fixture_id}/stats", summary="Forma y enfrentamientos directos del partido")
def fixture_stats(fixture_id: int, db: Session = Depends(get_db)):
    f = db.get(Fixture, fixture_id)
    if not f:
        raise NotFound("Partido no encontrado")
    h2h = db.scalars(select(HistoricalResult).where(
        or_((HistoricalResult.home_team_id == f.home_team_id) & (HistoricalResult.away_team_id == f.away_team_id),
            (HistoricalResult.home_team_id == f.away_team_id) & (HistoricalResult.away_team_id == f.home_team_id)),
        HistoricalResult.played_at < f.kickoff).order_by(HistoricalResult.played_at.desc()).limit(10)).all()
    names = {f.home_team_id: f.home_team.name, f.away_team_id: f.away_team.name}
    return {"fixture_id": f.id,
            "form": {str(n): {"home_team": _form(db, f.home_team_id, n, f.kickoff), "away_team": _form(db, f.away_team_id, n, f.kickoff)}
                     for n in (5, 10, 20)},
            "head_to_head": [{"date": r.played_at.isoformat(), "home": names[r.home_team_id], "away": names[r.away_team_id],
                              "score": f"{r.home_score}-{r.away_score}"} for r in h2h],
            "note": "Sólo partidos anteriores al inicio del encuentro."}


@router.get("/standings", summary="Clasificación de una liga")
def standings(league_id: int, db: Session = Depends(get_db)):
    key = f"api:standings:{league_id}"
    cache = get_cache()
    hit = cache.get_json(key)
    if hit:
        return hit
    lg = db.get(League, league_id)
    if not lg:
        raise NotFound("Liga no encontrada")
    year = db.scalar(select(func.max(Standing.season_year)).where(Standing.league_id == league_id))
    rows = db.execute(select(Standing, Team).join(Team, Team.id == Standing.team_id).where(
        Standing.league_id == league_id, Standing.season_year == year).order_by(Standing.position)).all()
    out = {"league": {"id": lg.id, "name": lg.name, "sport": lg.sport.code, "is_demo": lg.is_demo}, "season": year,
           "rows": [{"position": s.position, "team": team_out(t), "played": s.played, "won": s.won, "drawn": s.drawn,
                     "lost": s.lost, "points_for": s.points_for, "points_against": s.points_against, "points": s.points,
                     "form": s.form} for s, t in rows]}
    cache.set_json(key, out, 300)
    return out
