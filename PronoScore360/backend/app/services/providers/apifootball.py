"""API-Football (API-SPORTS v3). Cabecera de autenticación: x-apisports-key.

NOTA: los endpoints/parámetros usados son los de la v3 conocida (fixtures, standings,
fixtures/statistics, injuries, leagues). No se pudo contrastar con la documentación en vivo
desde el entorno de desarrollo; revisar CONFIGURACION_API.md antes de producción.
"""
from __future__ import annotations

from datetime import date, datetime, timezone
from typing import Optional

from .base import (PFixture, PInjury, PLeague, PStandingRow, PTeam, PTeamFixtureStats, SportsProvider)
from .http import ProviderHttpClient

STATUS_MAP = {"TBD": "scheduled", "NS": "scheduled", "1H": "live", "HT": "live", "2H": "live", "ET": "live",
              "BT": "live", "P": "live", "SUSP": "live", "INT": "live", "LIVE": "live",
              "FT": "finished", "AET": "finished", "PEN": "finished", "AWD": "finished", "WO": "finished",
              "PST": "postponed", "CANC": "canceled", "ABD": "canceled"}

STAT_KEYS = {"Corner Kicks": "corners", "Yellow Cards": "yellow_cards", "Red Cards": "red_cards",
             "Total Shots": "shots", "Shots on Goal": "shots_on_target", "Ball Possession": "possession",
             "Fouls": "fouls", "Offsides": "offsides"}


def _dt(s: str) -> datetime:
    return datetime.fromisoformat(s.replace("Z", "+00:00")).astimezone(timezone.utc)


def parse_fixture(item: dict) -> PFixture:
    f, lg, t, g = item["fixture"], item["league"], item["teams"], item.get("goals") or {}
    ht = (item.get("score") or {}).get("halftime") or {}
    return PFixture(
        external_id=str(f["id"]),
        league=PLeague(str(lg["id"]), lg["name"], lg.get("country"), None, lg.get("logo"), lg.get("season")),
        season=int(lg["season"]),
        home=PTeam(str(t["home"]["id"]), t["home"]["name"], t["home"].get("logo")),
        away=PTeam(str(t["away"]["id"]), t["away"]["name"], t["away"].get("logo")),
        kickoff=_dt(f["date"]), status=STATUS_MAP.get((f.get("status") or {}).get("short", "NS"), "scheduled"),
        home_score=g.get("home"), away_score=g.get("away"), ht_home=ht.get("home"), ht_away=ht.get("away"))


def parse_stat_value(v) -> Optional[float]:
    if v is None:
        return None
    if isinstance(v, str):
        v = v.strip().rstrip("%")
        try:
            return float(v)
        except ValueError:
            return None
    return float(v)


class ApiFootballProvider(SportsProvider):
    name, sport = "api-football", "football"

    def __init__(self, http: ProviderHttpClient):
        self.http = http

    def leagues(self) -> list[PLeague]:
        data = self.http.get("leagues", {"current": "true"}, ttl=86400)
        out = []
        for it in data.get("response", []):
            lg, c = it["league"], it.get("country") or {}
            out.append(PLeague(str(lg["id"]), lg["name"], c.get("name"), c.get("code"), lg.get("logo")))
        return out

    def fixtures_by_date(self, day, *, league_external_id=None, season=None, priority="normal"):
        params = {"date": day.isoformat(), "timezone": "UTC"}
        if league_external_id:
            params.update(league=league_external_id, season=season)
        data = self.http.get("fixtures", params, ttl=600, priority=priority)
        return [parse_fixture(i) for i in data.get("response", [])]

    def fixtures_by_league_season(self, league_external_id, season, *, priority="normal"):
        data = self.http.get("fixtures", {"league": league_external_id, "season": season, "timezone": "UTC"},
                             ttl=3600, priority=priority)
        return [parse_fixture(i) for i in data.get("response", [])]

    def standings(self, league_external_id, season):
        data = self.http.get("standings", {"league": league_external_id, "season": season}, ttl=3600)
        rows: list[PStandingRow] = []
        for it in data.get("response", []):
            for group in it["league"].get("standings", []):
                for r in group:
                    a = r["all"]
                    rows.append(PStandingRow(PTeam(str(r["team"]["id"]), r["team"]["name"], r["team"].get("logo")),
                                             r["rank"], a["played"], a["win"], a["draw"], a["lose"],
                                             a["goals"]["for"], a["goals"]["against"], r["points"],
                                             r.get("form") or "", r.get("group")))
        return rows

    def fixture_statistics(self, fixture_external_id):
        data = self.http.get("fixtures/statistics", {"fixture": fixture_external_id}, ttl=3600)
        out = []
        for it in data.get("response", []):
            stats = {}
            for s in it.get("statistics", []):
                key = STAT_KEYS.get(s["type"])
                if key:
                    stats[key] = parse_stat_value(s["value"])
            out.append(PTeamFixtureStats(str(it["team"]["id"]), stats))
        return out

    def injuries(self, fixture_external_id):
        data = self.http.get("injuries", {"fixture": fixture_external_id}, ttl=3600)
        return [PInjury(str(i["team"]["id"]), i["player"]["name"], i["player"].get("reason") or "",
                        i["player"].get("type") or "out") for i in data.get("response", [])]

    def head_to_head(self, home_id: str, away_id: str) -> list[PFixture]:
        data = self.http.get("fixtures/headtohead", {"h2h": f"{home_id}-{away_id}"}, ttl=86400)
        return [parse_fixture(i) for i in data.get("response", [])]
