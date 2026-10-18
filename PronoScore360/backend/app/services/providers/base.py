"""Interfaz abstracta de proveedor deportivo + DTOs normalizados.

Las aplicaciones Android/web NUNCA hablan con el proveedor: sólo con nuestra API.
Para agregar otro proveedor basta implementar `SportsProvider` y registrarlo en factory.py.
"""
from __future__ import annotations

from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from datetime import date, datetime
from typing import Optional


@dataclass
class PLeague:
    external_id: str
    name: str
    country: Optional[str] = None
    country_code: Optional[str] = None
    logo: Optional[str] = None
    season: Optional[int] = None


@dataclass
class PTeam:
    external_id: str
    name: str
    logo: Optional[str] = None
    short_name: Optional[str] = None


@dataclass
class PFixture:
    external_id: str
    league: PLeague
    season: int
    home: PTeam
    away: PTeam
    kickoff: datetime                      # tz-aware UTC
    status: str                            # scheduled | live | finished | postponed | canceled
    home_score: Optional[int] = None
    away_score: Optional[int] = None
    ht_home: Optional[int] = None
    ht_away: Optional[int] = None


@dataclass
class PStandingRow:
    team: PTeam
    position: int
    played: int
    won: int
    drawn: int
    lost: int
    points_for: int
    points_against: int
    points: int
    form: str = ""
    group: Optional[str] = None


@dataclass
class PTeamFixtureStats:
    team_external_id: str
    stats: dict = field(default_factory=dict)   # claves normalizadas: corners, yellow_cards, red_cards, fga, fta, oreb, tov, points...


@dataclass
class PInjury:
    team_external_id: str
    player_name: str
    reason: str = ""
    status: str = "out"


class SportsProvider(ABC):
    name: str = "base"
    sport: str = "football"
    is_demo: bool = False

    @abstractmethod
    def leagues(self) -> list[PLeague]: ...

    @abstractmethod
    def fixtures_by_date(self, day: date, *, league_external_id: Optional[str] = None,
                         season: Optional[int] = None, priority: str = "normal") -> list[PFixture]: ...

    @abstractmethod
    def fixtures_by_league_season(self, league_external_id: str, season: int, *, priority: str = "normal") -> list[PFixture]: ...

    @abstractmethod
    def standings(self, league_external_id: str, season: int) -> list[PStandingRow]: ...

    @abstractmethod
    def fixture_statistics(self, fixture_external_id: str) -> list[PTeamFixtureStats]: ...

    def injuries(self, fixture_external_id: str) -> list[PInjury]:
        """Opcional: no todos los proveedores/planes ofrecen lesiones."""
        raise NotImplementedError(f"{self.name} no ofrece lesiones")

    def lineups(self, fixture_external_id: str) -> list[dict]:
        raise NotImplementedError(f"{self.name} no ofrece alineaciones")
