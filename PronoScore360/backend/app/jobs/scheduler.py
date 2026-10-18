from __future__ import annotations

import logging

from apscheduler.schedulers.background import BackgroundScheduler

from ..core.config import get_settings
from .tasks import run_job

log = logging.getLogger("pronoscore.scheduler")
_sched: BackgroundScheduler | None = None


def start_scheduler(freq: dict | None = None) -> BackgroundScheduler | None:
    """Frecuencias (minutos) configurables en app_settings.update_frequency_minutes. Con varias réplicas de la API,
    ejecutar el scheduler en UN solo proceso (SCHEDULER_ENABLED=true sólo en el worker)."""
    global _sched
    if not get_settings().scheduler_enabled or _sched:
        return _sched
    freq = freq or {"fixtures": 360, "standings": 720, "stats": 180, "analysis": 60}
    s = BackgroundScheduler(timezone="UTC")
    for sport in ("football", "basketball"):
        s.add_job(run_job, "interval", minutes=freq["fixtures"], args=[f"sync_fixtures:{sport}"], id=f"sf-{sport}", max_instances=1, coalesce=True)
        s.add_job(run_job, "interval", minutes=freq["standings"], args=[f"sync_standings:{sport}"], id=f"ss-{sport}", max_instances=1, coalesce=True)
        s.add_job(run_job, "interval", minutes=freq["stats"], args=[f"sync_fixture_stats:{sport}"], id=f"sfs-{sport}", max_instances=1, coalesce=True)
    s.add_job(run_job, "interval", minutes=freq["analysis"], args=["generate_analyses"], id="gen", max_instances=1, coalesce=True)
    s.add_job(run_job, "interval", minutes=30, args=["settle_finished"], id="settle", max_instances=1, coalesce=True)
    s.add_job(run_job, "interval", minutes=30, args=["send_alerts"], id="alerts", max_instances=1, coalesce=True)
    s.add_job(run_job, "interval", minutes=15, args=["expire_subscriptions"], id="expire", max_instances=1, coalesce=True)
    s.add_job(run_job, "cron", hour=3, minute=17, args=["cleanup"], id="cleanup")
    s.start()
    _sched = s
    log.info("scheduler iniciado")
    return s


def stop_scheduler() -> None:
    global _sched
    if _sched:
        _sched.shutdown(wait=False)
        _sched = None
