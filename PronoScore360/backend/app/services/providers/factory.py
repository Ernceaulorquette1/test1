from __future__ import annotations

from ...core.config import get_settings
from .apifootball import ApiFootballProvider
from .apinba import ApiNbaProvider
from .base import SportsProvider
from .demo import DemoProvider
from .http import ProviderHttpClient


def get_provider(sport: str, recorder=None) -> SportsProvider:
    s = get_settings()
    if s.provider_mode == "demo":
        return DemoProvider(sport)
    if sport == "football":
        return ApiFootballProvider(ProviderHttpClient("api-football", s.apifootball_base_url, s.apifootball_key, recorder=recorder))
    if sport == "basketball":
        return ApiNbaProvider(ProviderHttpClient("api-nba", s.apinba_base_url, s.apinba_key, recorder=recorder))
    raise ValueError(f"Deporte no soportado: {sport}")
