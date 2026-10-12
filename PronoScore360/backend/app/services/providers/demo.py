"""Proveedor DEMO: no llama a Internet. Los datos reales los genera app/seed/demo.py y viven en BD.

Este adaptador existe para que el sincronizador funcione igual en modo DEMO (no-op identificado)."""
from __future__ import annotations

from .base import SportsProvider


class DemoProvider(SportsProvider):
    is_demo = True

    def __init__(self, sport: str):
        self.sport, self.name = sport, f"demo-{sport}"

    def leagues(self): return []
    def fixtures_by_date(self, day, **kw): return []
    def fixtures_by_league_season(self, league_external_id, season, **kw): return []
    def standings(self, league_external_id, season): return []
    def fixture_statistics(self, fixture_external_id): return []
