"""Modelo de datos de PronoScore360 (PostgreSQL). 35 entidades solicitadas + auxiliares."""
from __future__ import annotations

from datetime import date, datetime
from decimal import Decimal
from typing import Any, Optional

from sqlalchemy import (JSON, BigInteger, Boolean, CheckConstraint, Date, DateTime, ForeignKey, Index, Integer,
                        Numeric, String, Text, UniqueConstraint)
from sqlalchemy.orm import Mapped, mapped_column, relationship

from .base import Base, TimestampMixin, utcnow

TZ = DateTime(timezone=True)
J = JSON  # JSONB en PostgreSQL vía variant
from sqlalchemy.dialects.postgresql import JSONB  # noqa: E402

J = JSON().with_variant(JSONB(), "postgresql")


# ============================ Cuentas ============================
class Role(Base):
    __tablename__ = "roles"
    id: Mapped[int] = mapped_column(primary_key=True)
    code: Mapped[str] = mapped_column(String(20), unique=True)      # USER | ADMIN
    description: Mapped[str] = mapped_column(String(200), default="")


class User(Base, TimestampMixin):
    __tablename__ = "users"
    id: Mapped[int] = mapped_column(primary_key=True)
    email: Mapped[str] = mapped_column(String(254), unique=True, index=True)
    password_hash: Mapped[Optional[str]] = mapped_column(String(255))
    role_id: Mapped[int] = mapped_column(ForeignKey("roles.id"))
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)
    email_verified: Mapped[bool] = mapped_column(Boolean, default=False)
    google_sub: Mapped[Optional[str]] = mapped_column(String(64), unique=True)
    is_demo: Mapped[bool] = mapped_column(Boolean, default=False)
    failed_logins: Mapped[int] = mapped_column(Integer, default=0)
    locked_until: Mapped[Optional[datetime]] = mapped_column(TZ)
    last_login_at: Mapped[Optional[datetime]] = mapped_column(TZ)
    deleted_at: Mapped[Optional[datetime]] = mapped_column(TZ)
    role: Mapped[Role] = relationship(lazy="joined")
    profile: Mapped["UserProfile"] = relationship(back_populates="user", uselist=False, cascade="all, delete-orphan",
                                                  lazy="joined")


class UserProfile(Base, TimestampMixin):
    __tablename__ = "user_profiles"
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), primary_key=True)
    display_name: Mapped[str] = mapped_column(String(80), default="")
    country: Mapped[str] = mapped_column(String(2), default="CL")
    language: Mapped[str] = mapped_column(String(5), default="es")
    timezone: Mapped[str] = mapped_column(String(64), default="America/Santiago")
    theme: Mapped[str] = mapped_column(String(10), default="system")
    preferences: Mapped[dict] = mapped_column(J, default=dict)
    user: Mapped[User] = relationship(back_populates="profile")


class RefreshToken(Base):
    __tablename__ = "refresh_tokens"
    id: Mapped[int] = mapped_column(primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    family_id: Mapped[str] = mapped_column(String(36), index=True)
    token_hash: Mapped[str] = mapped_column(String(64), unique=True)
    expires_at: Mapped[datetime] = mapped_column(TZ)
    revoked_at: Mapped[Optional[datetime]] = mapped_column(TZ)
    replaced_by_id: Mapped[Optional[int]] = mapped_column(Integer)
    user_agent: Mapped[str] = mapped_column(String(200), default="")
    created_at: Mapped[datetime] = mapped_column(TZ, default=utcnow)


class OneTimeToken(Base):
    """Recuperación de contraseña y verificación de correo (sólo se guarda el hash)."""
    __tablename__ = "one_time_tokens"
    id: Mapped[int] = mapped_column(primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    purpose: Mapped[str] = mapped_column(String(20))   # password_reset | email_verify
    token_hash: Mapped[str] = mapped_column(String(64), unique=True)
    expires_at: Mapped[datetime] = mapped_column(TZ)
    used_at: Mapped[Optional[datetime]] = mapped_column(TZ)


# ============================ Planes y pagos ============================
class Plan(Base, TimestampMixin):
    """Configuración de planes editable sin reescribir la aplicación."""
    __tablename__ = "plans"
    id: Mapped[int] = mapped_column(primary_key=True)
    code: Mapped[str] = mapped_column(String(20), unique=True)          # FREE | PRO
    name: Mapped[str] = mapped_column(String(60))
    daily_analysis_limit: Mapped[Optional[int]] = mapped_column(Integer)  # NULL = ilimitado
    favorites_limit: Mapped[Optional[int]] = mapped_column(Integer)
    history_days: Mapped[Optional[int]] = mapped_column(Integer)          # NULL = completo
    selections_enabled: Mapped[bool] = mapped_column(Boolean, default=False)
    max_selection_items: Mapped[int] = mapped_column(Integer, default=0)
    heavy_calcs_per_hour: Mapped[int] = mapped_column(Integer, default=30)  # límite técnico de cálculos costosos
    allowed_market_groups: Mapped[list] = mapped_column(J, default=list)
    prices: Mapped[dict] = mapped_column(J, default=dict)                   # {"CL": {"amount": 9990, "currency": "CLP"}, ...}
    google_product_id: Mapped[Optional[str]] = mapped_column(String(100))
    features: Mapped[dict] = mapped_column(J, default=dict)
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)
    sort_order: Mapped[int] = mapped_column(Integer, default=0)


class Subscription(Base, TimestampMixin):
    __tablename__ = "subscriptions"
    id: Mapped[int] = mapped_column(primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    plan_id: Mapped[int] = mapped_column(ForeignKey("plans.id"))
    provider: Mapped[str] = mapped_column(String(20))        # google_play | demo | web_gateway
    status: Mapped[str] = mapped_column(String(20), index=True)
    # active | grace_period | on_hold | canceled (activa hasta fin de periodo) | expired | refunded | pending
    external_id: Mapped[Optional[str]] = mapped_column(String(200))   # hash del purchase token / id de pasarela
    external_order_id: Mapped[Optional[str]] = mapped_column(String(100))
    started_at: Mapped[datetime] = mapped_column(TZ, default=utcnow)
    current_period_end: Mapped[Optional[datetime]] = mapped_column(TZ)
    grace_until: Mapped[Optional[datetime]] = mapped_column(TZ)
    auto_renew: Mapped[bool] = mapped_column(Boolean, default=True)
    canceled_at: Mapped[Optional[datetime]] = mapped_column(TZ)
    is_test: Mapped[bool] = mapped_column(Boolean, default=False)
    plan: Mapped[Plan] = relationship(lazy="joined")
    __table_args__ = (UniqueConstraint("provider", "external_id", name="uq_subscriptions_provider_external"),
                      Index("ix_subscriptions_user_status", "user_id", "status"))


class PaymentTransaction(Base):
    __tablename__ = "payment_transactions"
    id: Mapped[int] = mapped_column(primary_key=True)
    user_id: Mapped[Optional[int]] = mapped_column(ForeignKey("users.id", ondelete="SET NULL"), index=True)
    subscription_id: Mapped[Optional[int]] = mapped_column(ForeignKey("subscriptions.id", ondelete="SET NULL"))
    provider: Mapped[str] = mapped_column(String(20))
    external_id: Mapped[Optional[str]] = mapped_column(String(200))
    amount: Mapped[Decimal] = mapped_column(Numeric(12, 2), default=0)
    currency: Mapped[str] = mapped_column(String(3), default="USD")
    status: Mapped[str] = mapped_column(String(20))        # succeeded | refunded | failed | pending
    is_test: Mapped[bool] = mapped_column(Boolean, default=False)
    created_at: Mapped[datetime] = mapped_column(TZ, default=utcnow, index=True)
    __table_args__ = (UniqueConstraint("provider", "external_id", name="uq_payment_provider_external"),)


class BillingEvent(Base):
    """Eventos verificados del servidor (RTDN, webhooks). Idempotencia por (provider, event_id)."""
    __tablename__ = "billing_events"
    id: Mapped[int] = mapped_column(primary_key=True)
    provider: Mapped[str] = mapped_column(String(20))
    event_id: Mapped[str] = mapped_column(String(200))
    event_type: Mapped[str] = mapped_column(String(60))
    user_id: Mapped[Optional[int]] = mapped_column(ForeignKey("users.id", ondelete="SET NULL"))
    payload: Mapped[dict] = mapped_column(J, default=dict)
    processed: Mapped[bool] = mapped_column(Boolean, default=False)
    error: Mapped[Optional[str]] = mapped_column(Text)
    created_at: Mapped[datetime] = mapped_column(TZ, default=utcnow)
    __table_args__ = (UniqueConstraint("provider", "event_id", name="uq_billing_event"),)


class DailyUsage(Base):
    """Un registro por (usuario, día-de-cuenta, partido) = una consulta consumida."""
    __tablename__ = "daily_usage"
    id: Mapped[int] = mapped_column(primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    usage_date: Mapped[date] = mapped_column(Date)
    fixture_id: Mapped[int] = mapped_column(ForeignKey("fixtures.id", ondelete="CASCADE"))
    analysis_id: Mapped[Optional[int]] = mapped_column(Integer)
    plan_code: Mapped[str] = mapped_column(String(20))
    created_at: Mapped[datetime] = mapped_column(TZ, default=utcnow)
    __table_args__ = (UniqueConstraint("user_id", "usage_date", "fixture_id", name="uq_daily_usage_unique"),
                      Index("ix_daily_usage_user_date", "user_id", "usage_date"))


# ============================ Catálogo deportivo ============================
class Sport(Base):
    __tablename__ = "sports"
    id: Mapped[int] = mapped_column(primary_key=True)
    code: Mapped[str] = mapped_column(String(20), unique=True)   # football | basketball
    name: Mapped[str] = mapped_column(String(60))
    enabled: Mapped[bool] = mapped_column(Boolean, default=True)


class Country(Base):
    __tablename__ = "countries"
    id: Mapped[int] = mapped_column(primary_key=True)
    code: Mapped[Optional[str]] = mapped_column(String(10), unique=True)
    name: Mapped[str] = mapped_column(String(100))
    flag_url: Mapped[Optional[str]] = mapped_column(String(300))


class League(Base, TimestampMixin):
    __tablename__ = "leagues"
    id: Mapped[int] = mapped_column(primary_key=True)
    sport_id: Mapped[int] = mapped_column(ForeignKey("sports.id"))
    country_id: Mapped[Optional[int]] = mapped_column(ForeignKey("countries.id"))
    provider: Mapped[str] = mapped_column(String(20), default="demo")
    external_id: Mapped[str] = mapped_column(String(50))
    name: Mapped[str] = mapped_column(String(150), index=True)
    logo_url: Mapped[Optional[str]] = mapped_column(String(300))
    is_demo: Mapped[bool] = mapped_column(Boolean, default=False)
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)
    country: Mapped[Optional[Country]] = relationship(lazy="joined")
    sport: Mapped[Sport] = relationship(lazy="joined")
    __table_args__ = (UniqueConstraint("provider", "external_id", "sport_id", name="uq_league_provider_ext"),)


class Season(Base):
    __tablename__ = "seasons"
    id: Mapped[int] = mapped_column(primary_key=True)
    league_id: Mapped[int] = mapped_column(ForeignKey("leagues.id", ondelete="CASCADE"))
    year: Mapped[int] = mapped_column(Integer)
    start_date: Mapped[Optional[date]] = mapped_column(Date)
    end_date: Mapped[Optional[date]] = mapped_column(Date)
    is_current: Mapped[bool] = mapped_column(Boolean, default=False)
    __table_args__ = (UniqueConstraint("league_id", "year", name="uq_season_league_year"),)


class Team(Base, TimestampMixin):
    __tablename__ = "teams"
    id: Mapped[int] = mapped_column(primary_key=True)
    sport_id: Mapped[int] = mapped_column(ForeignKey("sports.id"))
    country_id: Mapped[Optional[int]] = mapped_column(ForeignKey("countries.id"))
    provider: Mapped[str] = mapped_column(String(20), default="demo")
    external_id: Mapped[str] = mapped_column(String(50))
    name: Mapped[str] = mapped_column(String(150), index=True)
    short_name: Mapped[Optional[str]] = mapped_column(String(20))
    logo_url: Mapped[Optional[str]] = mapped_column(String(300))
    is_demo: Mapped[bool] = mapped_column(Boolean, default=False)
    __table_args__ = (UniqueConstraint("provider", "external_id", "sport_id", name="uq_team_provider_ext"),)


class Player(Base):
    __tablename__ = "players"
    id: Mapped[int] = mapped_column(primary_key=True)
    team_id: Mapped[Optional[int]] = mapped_column(ForeignKey("teams.id", ondelete="SET NULL"), index=True)
    provider: Mapped[str] = mapped_column(String(20), default="demo")
    external_id: Mapped[str] = mapped_column(String(50))
    name: Mapped[str] = mapped_column(String(150))
    position: Mapped[Optional[str]] = mapped_column(String(30))
    __table_args__ = (UniqueConstraint("provider", "external_id", name="uq_player_provider_ext"),)


class Fixture(Base, TimestampMixin):
    __tablename__ = "fixtures"
    id: Mapped[int] = mapped_column(primary_key=True)
    sport_id: Mapped[int] = mapped_column(ForeignKey("sports.id"))
    league_id: Mapped[int] = mapped_column(ForeignKey("leagues.id"), index=True)
    season_id: Mapped[Optional[int]] = mapped_column(ForeignKey("seasons.id"))
    provider: Mapped[str] = mapped_column(String(20), default="demo")
    external_id: Mapped[str] = mapped_column(String(50))
    home_team_id: Mapped[int] = mapped_column(ForeignKey("teams.id"), index=True)
    away_team_id: Mapped[int] = mapped_column(ForeignKey("teams.id"), index=True)
    kickoff: Mapped[datetime] = mapped_column(TZ, index=True)
    status: Mapped[str] = mapped_column(String(20), default="scheduled")  # scheduled | live | finished | postponed | canceled
    home_score: Mapped[Optional[int]] = mapped_column(Integer)
    away_score: Mapped[Optional[int]] = mapped_column(Integer)
    is_demo: Mapped[bool] = mapped_column(Boolean, default=False)
    league: Mapped[League] = relationship(lazy="joined")
    home_team: Mapped[Team] = relationship(foreign_keys=[home_team_id], lazy="joined")
    away_team: Mapped[Team] = relationship(foreign_keys=[away_team_id], lazy="joined")
    __table_args__ = (UniqueConstraint("provider", "external_id", "sport_id", name="uq_fixture_provider_ext"),
                      Index("ix_fixtures_sport_kickoff", "sport_id", "kickoff"),
                      CheckConstraint("home_team_id <> away_team_id", name="teams_differ"))


class FixtureStatistic(Base):
    __tablename__ = "fixture_statistics"
    id: Mapped[int] = mapped_column(primary_key=True)
    fixture_id: Mapped[int] = mapped_column(ForeignKey("fixtures.id", ondelete="CASCADE"), index=True)
    team_id: Mapped[int] = mapped_column(ForeignKey("teams.id"))
    stats: Mapped[dict] = mapped_column(J, default=dict)   # corners, yellow, red, shots, fga, fta, oreb, tov...
    __table_args__ = (UniqueConstraint("fixture_id", "team_id", name="uq_fixture_stat_team"),)


class TeamStatistic(Base):
    __tablename__ = "team_statistics"
    id: Mapped[int] = mapped_column(primary_key=True)
    team_id: Mapped[int] = mapped_column(ForeignKey("teams.id", ondelete="CASCADE"), index=True)
    league_id: Mapped[int] = mapped_column(ForeignKey("leagues.id", ondelete="CASCADE"))
    season_year: Mapped[int] = mapped_column(Integer)
    stats: Mapped[dict] = mapped_column(J, default=dict)
    updated_at: Mapped[datetime] = mapped_column(TZ, default=utcnow, onupdate=utcnow)
    __table_args__ = (UniqueConstraint("team_id", "league_id", "season_year", name="uq_team_stat"),)


class Standing(Base):
    __tablename__ = "standings"
    id: Mapped[int] = mapped_column(primary_key=True)
    league_id: Mapped[int] = mapped_column(ForeignKey("leagues.id", ondelete="CASCADE"), index=True)
    season_year: Mapped[int] = mapped_column(Integer)
    team_id: Mapped[int] = mapped_column(ForeignKey("teams.id"))
    position: Mapped[int] = mapped_column(Integer)
    played: Mapped[int] = mapped_column(Integer, default=0)
    won: Mapped[int] = mapped_column(Integer, default=0)
    drawn: Mapped[int] = mapped_column(Integer, default=0)
    lost: Mapped[int] = mapped_column(Integer, default=0)
    points_for: Mapped[int] = mapped_column(Integer, default=0)      # goles/puntos a favor
    points_against: Mapped[int] = mapped_column(Integer, default=0)
    points: Mapped[int] = mapped_column(Integer, default=0)
    form: Mapped[str] = mapped_column(String(20), default="")
    group_name: Mapped[Optional[str]] = mapped_column(String(50))
    updated_at: Mapped[datetime] = mapped_column(TZ, default=utcnow, onupdate=utcnow)
    __table_args__ = (UniqueConstraint("league_id", "season_year", "team_id", name="uq_standing"),)


class HistoricalResult(Base):
    """Resultados cerrados usados para entrenar los modelos (sin datos posteriores al evento)."""
    __tablename__ = "historical_results"
    id: Mapped[int] = mapped_column(primary_key=True)
    fixture_id: Mapped[int] = mapped_column(ForeignKey("fixtures.id", ondelete="CASCADE"), unique=True)
    sport_id: Mapped[int] = mapped_column(ForeignKey("sports.id"))
    league_id: Mapped[int] = mapped_column(ForeignKey("leagues.id"))
    home_team_id: Mapped[int] = mapped_column(ForeignKey("teams.id"))
    away_team_id: Mapped[int] = mapped_column(ForeignKey("teams.id"))
    played_at: Mapped[datetime] = mapped_column(TZ)
    home_score: Mapped[int] = mapped_column(Integer)
    away_score: Mapped[int] = mapped_column(Integer)
    extra: Mapped[dict] = mapped_column(J, default=dict)   # ht goals, corners, cards, possessions…
    is_demo: Mapped[bool] = mapped_column(Boolean, default=False)
    __table_args__ = (Index("ix_hist_sport_league_played", "sport_id", "league_id", "played_at"),
                      Index("ix_hist_home", "home_team_id", "played_at"), Index("ix_hist_away", "away_team_id", "played_at"))


class Injury(Base):
    __tablename__ = "injuries"
    id: Mapped[int] = mapped_column(primary_key=True)
    team_id: Mapped[int] = mapped_column(ForeignKey("teams.id", ondelete="CASCADE"), index=True)
    player_id: Mapped[Optional[int]] = mapped_column(ForeignKey("players.id", ondelete="SET NULL"))
    fixture_id: Mapped[Optional[int]] = mapped_column(ForeignKey("fixtures.id", ondelete="CASCADE"))
    player_name: Mapped[str] = mapped_column(String(150))
    reason: Mapped[str] = mapped_column(String(100), default="")
    status: Mapped[str] = mapped_column(String(30), default="out")
    reported_at: Mapped[datetime] = mapped_column(TZ, default=utcnow)


# ============================ Modelos y predicciones ============================
class PredictionModel(Base):
    __tablename__ = "prediction_models"
    id: Mapped[int] = mapped_column(primary_key=True)
    code: Mapped[str] = mapped_column(String(50), unique=True)
    sport_id: Mapped[int] = mapped_column(ForeignKey("sports.id"))
    name: Mapped[str] = mapped_column(String(100))
    description: Mapped[str] = mapped_column(Text, default="")


class ModelVersion(Base):
    __tablename__ = "model_versions"
    id: Mapped[int] = mapped_column(primary_key=True)
    model_id: Mapped[int] = mapped_column(ForeignKey("prediction_models.id", ondelete="CASCADE"))
    version: Mapped[str] = mapped_column(String(20))
    params: Mapped[dict] = mapped_column(J, default=dict)
    metrics: Mapped[dict] = mapped_column(J, default=dict)     # resultado de backtesting
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)
    created_at: Mapped[datetime] = mapped_column(TZ, default=utcnow)
    model: Mapped[PredictionModel] = relationship(lazy="joined")
    __table_args__ = (UniqueConstraint("model_id", "version", name="uq_model_version"),)


class FixtureAnalysis(Base):
    """Análisis completo almacenado de un partido (una fila = un resultado estable)."""
    __tablename__ = "fixture_analyses"
    id: Mapped[int] = mapped_column(primary_key=True)
    fixture_id: Mapped[int] = mapped_column(ForeignKey("fixtures.id", ondelete="CASCADE"), index=True)
    model_version_id: Mapped[int] = mapped_column(ForeignKey("model_versions.id"))
    status: Mapped[str] = mapped_column(String(20))            # ok | insufficient_data
    quality: Mapped[str] = mapped_column(String(15))
    n_home: Mapped[int] = mapped_column(Integer, default=0)
    n_away: Mapped[int] = mapped_column(Integer, default=0)
    payload: Mapped[dict] = mapped_column(J, default=dict)     # parámetros del modelo, top scores, drivers…
    is_pre_match: Mapped[bool] = mapped_column(Boolean, default=True)   # False => generado tras el inicio (excluido de evaluación)
    generated_at: Mapped[datetime] = mapped_column(TZ, default=utcnow, index=True)
    is_demo: Mapped[bool] = mapped_column(Boolean, default=False)
    __table_args__ = (Index("ix_analysis_fixture_prematch", "fixture_id", "is_pre_match", "generated_at"),)


class Prediction(Base):
    __tablename__ = "predictions"
    id: Mapped[int] = mapped_column(primary_key=True)
    analysis_id: Mapped[int] = mapped_column(ForeignKey("fixture_analyses.id", ondelete="CASCADE"), index=True)
    fixture_id: Mapped[int] = mapped_column(ForeignKey("fixtures.id", ondelete="CASCADE"), index=True)
    sport_id: Mapped[int] = mapped_column(ForeignKey("sports.id"))
    league_id: Mapped[int] = mapped_column(ForeignKey("leagues.id"))
    market: Mapped[str] = mapped_column(String(60))            # '1X2' | 'OU:2.5' | 'ML' | ...
    selection: Mapped[str] = mapped_column(String(30))         # 'home' | 'over' ...
    market_group: Mapped[str] = mapped_column(String(30))      # basic | goals | corners | cards | nba_lines ...
    probability: Mapped[float] = mapped_column(Numeric(7, 6))
    category: Mapped[str] = mapped_column(String(25))
    uncertainty: Mapped[str] = mapped_column(String(10))
    data_points: Mapped[int] = mapped_column(Integer)
    explanation: Mapped[str] = mapped_column(Text, default="")
    is_pre_match: Mapped[bool] = mapped_column(Boolean, default=True)
    created_at: Mapped[datetime] = mapped_column(TZ, default=utcnow, index=True)
    __table_args__ = (UniqueConstraint("analysis_id", "market", "selection", name="uq_prediction_unique"),
                      Index("ix_pred_filter", "sport_id", "league_id", "market", "probability"),
                      CheckConstraint("probability >= 0 AND probability <= 1", name="prob_range"))


class PredictionOutcome(Base):
    __tablename__ = "prediction_outcomes"
    id: Mapped[int] = mapped_column(primary_key=True)
    prediction_id: Mapped[int] = mapped_column(ForeignKey("predictions.id", ondelete="CASCADE"), unique=True)
    hit: Mapped[bool] = mapped_column(Boolean)
    actual: Mapped[dict] = mapped_column(J, default=dict)
    settled_at: Mapped[datetime] = mapped_column(TZ, default=utcnow)


class PredictionEvaluation(Base):
    """Métricas agregadas por modelo/deporte/liga/mercado (recalculadas por job)."""
    __tablename__ = "prediction_evaluations"
    id: Mapped[int] = mapped_column(primary_key=True)
    model_version_id: Mapped[int] = mapped_column(ForeignKey("model_versions.id", ondelete="CASCADE"))
    sport_id: Mapped[int] = mapped_column(ForeignKey("sports.id"))
    league_id: Mapped[Optional[int]] = mapped_column(ForeignKey("leagues.id"))
    market: Mapped[str] = mapped_column(String(60))
    n: Mapped[int] = mapped_column(Integer)
    hit_rate: Mapped[Optional[float]] = mapped_column(Numeric(7, 6))
    brier: Mapped[Optional[float]] = mapped_column(Numeric(8, 6))
    log_loss: Mapped[Optional[float]] = mapped_column(Numeric(8, 6))
    mean_probability: Mapped[Optional[float]] = mapped_column(Numeric(7, 6))
    computed_at: Mapped[datetime] = mapped_column(TZ, default=utcnow)
    __table_args__ = (Index("ix_eval_lookup", "model_version_id", "sport_id", "league_id", "market"),)


# ============================ Uso del usuario ============================
class SavedPrediction(Base):
    __tablename__ = "saved_predictions"
    id: Mapped[int] = mapped_column(primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    prediction_id: Mapped[int] = mapped_column(ForeignKey("predictions.id", ondelete="CASCADE"))
    note: Mapped[str] = mapped_column(String(300), default="")
    created_at: Mapped[datetime] = mapped_column(TZ, default=utcnow)
    __table_args__ = (UniqueConstraint("user_id", "prediction_id", name="uq_saved_prediction"),)


class Favorite(Base):
    __tablename__ = "favorites"
    id: Mapped[int] = mapped_column(primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    kind: Mapped[str] = mapped_column(String(10))          # team | league | fixture
    ref_id: Mapped[int] = mapped_column(Integer)
    created_at: Mapped[datetime] = mapped_column(TZ, default=utcnow)
    __table_args__ = (UniqueConstraint("user_id", "kind", "ref_id", name="uq_favorite"),)


class SelectionGroup(Base):
    __tablename__ = "selection_groups"
    id: Mapped[int] = mapped_column(primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    name: Mapped[str] = mapped_column(String(100))
    created_at: Mapped[datetime] = mapped_column(TZ, default=utcnow)
    joint_probability: Mapped[Optional[float]] = mapped_column(Numeric(7, 6))
    joint_computable: Mapped[bool] = mapped_column(Boolean, default=False)
    items: Mapped[list["SelectionItem"]] = relationship(back_populates="group", cascade="all, delete-orphan",
                                                        lazy="selectin")


class SelectionItem(Base):
    __tablename__ = "selection_items"
    id: Mapped[int] = mapped_column(primary_key=True)
    group_id: Mapped[int] = mapped_column(ForeignKey("selection_groups.id", ondelete="CASCADE"), index=True)
    fixture_id: Mapped[int] = mapped_column(ForeignKey("fixtures.id", ondelete="CASCADE"))
    market: Mapped[str] = mapped_column(String(60))        # formato 'OU:2.5:over'
    probability_at_save: Mapped[Optional[float]] = mapped_column(Numeric(7, 6))
    group: Mapped[SelectionGroup] = relationship(back_populates="items")
    __table_args__ = (UniqueConstraint("group_id", "fixture_id", "market", name="uq_selection_item"),)


class Notification(Base):
    __tablename__ = "notifications"
    id: Mapped[int] = mapped_column(primary_key=True)
    user_id: Mapped[Optional[int]] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)  # NULL = global
    kind: Mapped[str] = mapped_column(String(30), default="info")
    title: Mapped[str] = mapped_column(String(150))
    body: Mapped[str] = mapped_column(Text, default="")
    data: Mapped[dict] = mapped_column(J, default=dict)
    read_at: Mapped[Optional[datetime]] = mapped_column(TZ)
    created_at: Mapped[datetime] = mapped_column(TZ, default=utcnow, index=True)


class DeviceToken(Base):
    """Tokens FCM por dispositivo (notificaciones push cuando Firebase esté configurado)."""
    __tablename__ = "device_tokens"
    id: Mapped[int] = mapped_column(primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    token: Mapped[str] = mapped_column(String(300), unique=True)
    platform: Mapped[str] = mapped_column(String(10), default="android")
    created_at: Mapped[datetime] = mapped_column(TZ, default=utcnow)


class Announcement(Base):
    __tablename__ = "announcements"
    id: Mapped[int] = mapped_column(primary_key=True)
    title: Mapped[str] = mapped_column(String(150))
    body: Mapped[str] = mapped_column(Text, default="")
    level: Mapped[str] = mapped_column(String(10), default="info")   # info | warning | critical
    active: Mapped[bool] = mapped_column(Boolean, default=True)
    created_at: Mapped[datetime] = mapped_column(TZ, default=utcnow)


# ============================ Operación ============================
class ProviderRequest(Base):
    __tablename__ = "provider_requests"
    id: Mapped[int] = mapped_column(primary_key=True)
    provider: Mapped[str] = mapped_column(String(30), index=True)
    endpoint: Mapped[str] = mapped_column(String(120))
    params: Mapped[dict] = mapped_column(J, default=dict)
    status_code: Mapped[Optional[int]] = mapped_column(Integer)
    ok: Mapped[bool] = mapped_column(Boolean, default=False)
    from_cache: Mapped[bool] = mapped_column(Boolean, default=False)
    duration_ms: Mapped[Optional[int]] = mapped_column(Integer)
    remaining_daily: Mapped[Optional[int]] = mapped_column(Integer)
    error: Mapped[Optional[str]] = mapped_column(Text)
    created_at: Mapped[datetime] = mapped_column(TZ, default=utcnow, index=True)


class ProviderSyncJob(Base):
    __tablename__ = "provider_sync_jobs"
    id: Mapped[int] = mapped_column(primary_key=True)
    job: Mapped[str] = mapped_column(String(50), index=True)
    provider: Mapped[str] = mapped_column(String(30), default="")
    status: Mapped[str] = mapped_column(String(15))     # running | success | failed | skipped
    started_at: Mapped[datetime] = mapped_column(TZ, default=utcnow)
    finished_at: Mapped[Optional[datetime]] = mapped_column(TZ)
    items: Mapped[int] = mapped_column(Integer, default=0)
    message: Mapped[Optional[str]] = mapped_column(Text)
    triggered_by: Mapped[str] = mapped_column(String(30), default="scheduler")


class AppSetting(Base):
    __tablename__ = "app_settings"
    key: Mapped[str] = mapped_column(String(80), primary_key=True)
    value: Mapped[Any] = mapped_column(J)
    description: Mapped[str] = mapped_column(String(250), default="")
    updated_at: Mapped[datetime] = mapped_column(TZ, default=utcnow, onupdate=utcnow)


class AuditLog(Base):
    __tablename__ = "audit_logs"
    id: Mapped[int] = mapped_column(BigInteger().with_variant(Integer, "sqlite"), primary_key=True)
    actor_user_id: Mapped[Optional[int]] = mapped_column(ForeignKey("users.id", ondelete="SET NULL"), index=True)
    action: Mapped[str] = mapped_column(String(80), index=True)
    entity: Mapped[str] = mapped_column(String(50), default="")
    entity_id: Mapped[Optional[str]] = mapped_column(String(50))
    detail: Mapped[dict] = mapped_column(J, default=dict)
    ip: Mapped[Optional[str]] = mapped_column(String(64))
    created_at: Mapped[datetime] = mapped_column(TZ, default=utcnow, index=True)


class ErrorLog(Base):
    __tablename__ = "error_logs"
    id: Mapped[int] = mapped_column(primary_key=True)
    source: Mapped[str] = mapped_column(String(50))
    message: Mapped[str] = mapped_column(Text)
    context: Mapped[dict] = mapped_column(J, default=dict)
    created_at: Mapped[datetime] = mapped_column(TZ, default=utcnow, index=True)
