from __future__ import annotations

from functools import lru_cache
from typing import Literal

from pydantic import Field, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

DEFAULT_DEV_SECRET = "dev-only-insecure-secret-change-me-0123456789abcdef"


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=(".env", "../.env"), extra="ignore")

    app_name: str = "PronoScore360"
    env: Literal["local", "test", "staging", "production"] = "local"
    database_url: str = "postgresql+psycopg://pronoscore:pronoscore@localhost:5432/pronoscore"
    redis_url: str = ""                      # vacío => caché/limitador en memoria (sólo local/test)
    cors_origins: str = "http://localhost:5173,http://127.0.0.1:5173,http://localhost:3000"

    # --- seguridad ---
    jwt_secret: str = DEFAULT_DEV_SECRET
    jwt_algorithm: str = "HS256"
    access_token_minutes: int = 15
    refresh_token_days: int = 30
    password_reset_minutes: int = 30
    max_sessions_per_user: int = 10
    rate_limit_per_minute: int = 120         # por IP/usuario, general
    auth_rate_limit_per_minute: int = 10     # login/registro/recuperación por IP
    login_max_failures: int = 8              # bloqueo temporal por cuenta
    login_lock_minutes: int = 15

    # --- proveedores deportivos ---
    provider_mode: Literal["demo", "real"] = "demo"
    apifootball_key: str = ""
    apifootball_base_url: str = "https://v3.football.api-sports.io"
    apinba_key: str = ""
    apinba_base_url: str = "https://v2.nba.api-sports.io"
    provider_daily_budget: int = 100         # tope propio de peticiones/día por proveedor (plan gratuito API-SPORTS: 100)
    provider_reserve: int = 10               # reserva que no se gasta en sincronizaciones automáticas
    provider_timeout_seconds: float = 15.0
    provider_max_retries: int = 2
    provider_cache_ttl_seconds: int = 900

    # --- planes / cuotas ---
    default_timezone: str = "UTC"
    free_daily_analyses_default: int = 5

    # --- pagos ---
    payments_provider: Literal["demo", "none"] = "demo"   # 'demo' SOLO local/test
    google_play_package_name: str = "com.pronoscore360.app"
    google_play_product_id: str = "pronoscore360_pro_monthly"
    google_play_service_account_json: str = ""            # ruta o JSON del service account
    google_play_rtdn_audience: str = ""                   # audiencia esperada del JWT de Pub/Sub push
    google_play_rtdn_service_account: str = ""            # email de la cuenta que firma el push
    google_client_id: str = ""

    # --- correo ---
    smtp_host: str = ""
    smtp_port: int = 587
    smtp_user: str = ""
    smtp_password: str = ""
    smtp_from: str = "no-reply@pronoscore360.example"
    frontend_url: str = "http://localhost:5173"

    # --- tareas ---
    scheduler_enabled: bool = False
    seed_demo_on_start: bool = False
    demo_accounts_enabled: bool = True       # forzado a False fuera de local/test

    @property
    def is_dev(self) -> bool:
        return self.env in ("local", "test")

    @property
    def cors_list(self) -> list[str]:
        return [o.strip() for o in self.cors_origins.split(",") if o.strip()]

    @model_validator(mode="after")
    def _production_guards(self):
        if not self.is_dev:
            problems = []
            if self.jwt_secret == DEFAULT_DEV_SECRET or len(self.jwt_secret) < 32:
                problems.append("JWT_SECRET debe definirse (>=32 caracteres) en staging/producción")
            if self.payments_provider == "demo":
                problems.append("PAYMENTS_PROVIDER=demo no está permitido fuera de local/test")
            if not self.redis_url:
                problems.append("REDIS_URL es obligatorio fuera de local/test")
            if problems:
                raise ValueError("; ".join(problems))
            self.demo_accounts_enabled = False
        return self


@lru_cache
def get_settings() -> Settings:
    return Settings()
