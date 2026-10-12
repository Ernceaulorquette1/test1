"""Datos iniciales NO deportivos: roles, deportes, planes, ajustes. Idempotente."""
from __future__ import annotations

from sqlalchemy import select
from sqlalchemy.orm import Session

from ..db.models import AppSetting, Plan, Role, Sport

ALL_GROUPS = ["basic", "goals", "corners", "cards", "halftime", "nba_lines"]

PLANS = [
    dict(code="FREE", name="Gratis", daily_analysis_limit=5, favorites_limit=10, history_days=7, selections_enabled=False,
         max_selection_items=0, heavy_calcs_per_hour=0, allowed_market_groups=["basic"], sort_order=0, prices={},
         features={"exact_score_top_n": 1, "advanced_stats": False, "model_performance": False, "alerts": False,
                   "probability_filter": False, "custom_lines": False, "compare_results": False}),
    dict(code="PRO", name="Pro", daily_analysis_limit=None, favorites_limit=200, history_days=None, selections_enabled=True,
         max_selection_items=12, heavy_calcs_per_hour=60, allowed_market_groups=ALL_GROUPS, sort_order=1,
         prices={"CL": {"amount": 9990, "currency": "CLP"}, "US": {"amount": 9.99, "currency": "USD"},
                 "DEFAULT": {"amount": 9.99, "currency": "USD", "note": "Precios regionales se configuran en Google Play Console"}},
         google_product_id="pronoscore360_pro_monthly",
         features={"exact_score_top_n": 5, "advanced_stats": True, "model_performance": True, "alerts": True,
                   "probability_filter": True, "custom_lines": True, "compare_results": True}),
]

SETTINGS = {
    "tracked_leagues": ([], "Ligas a sincronizar en modo REAL: [{sport, external_id, season}]"),
    "update_frequency_minutes": ({"fixtures": 360, "standings": 720, "stats": 180, "analysis": 60}, "Frecuencia de tareas"),
    "probability_filter_range": ({"min": 0.60, "max": 0.95}, "Rango permitido del filtro de probabilidad"),
    "maintenance_mode": (False, "Bloquea escrituras de usuarios"),
}


def seed_base(db: Session) -> None:
    for code, desc in (("USER", "Usuario"), ("ADMIN", "Administrador")):
        if not db.scalar(select(Role).where(Role.code == code)):
            db.add(Role(code=code, description=desc))
    for code, name in (("football", "Fútbol"), ("basketball", "Baloncesto / NBA")):
        if not db.scalar(select(Sport).where(Sport.code == code)):
            db.add(Sport(code=code, name=name))
    for p in PLANS:
        row = db.scalar(select(Plan).where(Plan.code == p["code"]))
        if row is None:
            db.add(Plan(**p))     # los planes existentes NO se sobrescriben: son configuración editable
    for k, (v, d) in SETTINGS.items():
        if db.get(AppSetting, k) is None:
            db.add(AppSetting(key=k, value=v, description=d))
    db.flush()
