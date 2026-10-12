"""API-NBA (API-SPORTS v2). Cabecera: x-apisports-key. Liga 'standard' = NBA.

Las lesiones NO están implementadas: no se pudo confirmar un endpoint de lesiones para API-NBA v2.
"""
from __future__ import annotations

from datetime import date, datetime, timezone

from .base import PFixture, PLeague, PStandingRow, PTeam, PTeamFixtureStats, SportsProvider
from .http import ProviderHttpClient

STATUS = {1: "scheduled", 2: "live", 3: "finished"}
NBA = PLeague("standard", "NBA", "USA", "US")


def _team(t: dict) -> PTeam:
    return PTeam(str(t["id"]), t["name"], t.get("logo"), t.get("code"))


def parse_game(item: dict) -> PFixture:
    sc = item.get("scores") or {}
    start = item["date"]["start"]
    return PFixture(
        external_id=str(item["id"]), league=NBA, season=int(item["season"]),
        home=_team(item["teams"]["home"]), away=_team(item["teams"]["visitors"]),
        kickoff=datetime.fromisoformat(start.replace("Z", "+00:00")).astimezone(timezone.utc),
        status=STATUS.get((item.get("status") or {}).get("short"), "scheduled"),
        home_score=(sc.get("home") or {}).get("points"), away_score=(sc.get("visitors") or {}).get("points"))


class ApiNbaProvider(SportsProvider):
    name, sport = "api-nba", "basketball"

    def __init__(self, http: ProviderHttpClient):
        self.http = http

    def leagues(self):
        return [NBA]

    def fixtures_by_date(self, day, *, league_external_id=None, season=None, priority="normal"):
        data = self.http.get("games", {"date": day.isoformat()}, ttl=600, priority=priority)
        return [parse_game(i) for i in data.get("response", []) if i.get("league") == "standard"]

    def fixtures_by_league_season(self, league_external_id, season, *, priority="normal"):
        data = self.http.get("games", {"league": "standard", "season": season}, ttl=3600, priority=priority)
        return [parse_game(i) for i in data.get("response", [])]

    def standings(self, league_external_id, season):
        data = self.http.get("standings", {"league": "standard", "season": season}, ttl=3600)
        rows = []
        for i in data.get("response", []):
            w, l = i["win"]["total"], i["loss"]["total"]
            rows.append(PStandingRow(_team(i["team"]), (i.get("conference") or {}).get("rank") or 0, w + l, w, 0, l,
                                     i.get("points", 0) or 0, 0, w, "", (i.get("conference") or {}).get("name")))
        return rows

    def fixture_statistics(self, fixture_external_id):
        data = self.http.get("games/statistics", {"id": fixture_external_id}, ttl=3600)
        out = []
        for it in data.get("response", []):
            st = (it.get("statistics") or [{}])[0]
            out.append(PTeamFixtureStats(str(it["team"]["id"]), {
                "points": st.get("points"), "fga": st.get("fga"), "fta": st.get("fta"),
                "oreb": st.get("offReb"), "tov": st.get("turnovers")}))
        return out
