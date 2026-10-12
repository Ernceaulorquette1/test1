from __future__ import annotations

from datetime import datetime
from zoneinfo import ZoneInfo

from ...db.models import Fixture, Team


def team_out(t: Team) -> dict:
    return {"id": t.id, "name": t.name, "short_name": t.short_name, "logo_url": t.logo_url, "is_demo": t.is_demo}


def fixture_out(f: Fixture, tz: str | None = None) -> dict:
    d = {"id": f.id, "sport": f.league.sport.code, "league": {"id": f.league.id, "name": f.league.name,
                                                               "country": f.league.country.name if f.league.country else None,
                                                               "logo_url": f.league.logo_url},
         "home_team": team_out(f.home_team), "away_team": team_out(f.away_team),
         "kickoff": f.kickoff.isoformat(), "status": f.status, "home_score": f.home_score, "away_score": f.away_score,
         "is_demo": f.is_demo, "data_source": "DEMO (datos ficticios)" if f.is_demo else f.provider}
    if tz:
        d["kickoff_local"] = f.kickoff.astimezone(ZoneInfo(tz)).isoformat()
    return d
