BEGIN;

CREATE TABLE alembic_version (
    version_num VARCHAR(32) NOT NULL, 
    CONSTRAINT alembic_version_pkc PRIMARY KEY (version_num)
);

-- Running upgrade  -> 0001

CREATE TABLE announcements (
    id SERIAL NOT NULL, 
    title VARCHAR(150) NOT NULL, 
    body TEXT NOT NULL, 
    level VARCHAR(10) NOT NULL, 
    active BOOLEAN NOT NULL, 
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, 
    CONSTRAINT pk_announcements PRIMARY KEY (id)
);

CREATE TABLE app_settings (
    key VARCHAR(80) NOT NULL, 
    value JSONB NOT NULL, 
    description VARCHAR(250) NOT NULL, 
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL, 
    CONSTRAINT pk_app_settings PRIMARY KEY (key)
);

CREATE TABLE countries (
    id SERIAL NOT NULL, 
    code VARCHAR(10), 
    name VARCHAR(100) NOT NULL, 
    flag_url VARCHAR(300), 
    CONSTRAINT pk_countries PRIMARY KEY (id), 
    CONSTRAINT uq_countries_code UNIQUE (code)
);

CREATE TABLE error_logs (
    id SERIAL NOT NULL, 
    source VARCHAR(50) NOT NULL, 
    message TEXT NOT NULL, 
    context JSONB NOT NULL, 
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, 
    CONSTRAINT pk_error_logs PRIMARY KEY (id)
);

CREATE INDEX ix_error_logs_created_at ON error_logs (created_at);

CREATE TABLE plans (
    id SERIAL NOT NULL, 
    code VARCHAR(20) NOT NULL, 
    name VARCHAR(60) NOT NULL, 
    daily_analysis_limit INTEGER, 
    favorites_limit INTEGER, 
    history_days INTEGER, 
    selections_enabled BOOLEAN NOT NULL, 
    max_selection_items INTEGER NOT NULL, 
    heavy_calcs_per_hour INTEGER NOT NULL, 
    allowed_market_groups JSONB NOT NULL, 
    prices JSONB NOT NULL, 
    google_product_id VARCHAR(100), 
    features JSONB NOT NULL, 
    is_active BOOLEAN NOT NULL, 
    sort_order INTEGER NOT NULL, 
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, 
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL, 
    CONSTRAINT pk_plans PRIMARY KEY (id), 
    CONSTRAINT uq_plans_code UNIQUE (code)
);

CREATE TABLE provider_requests (
    id SERIAL NOT NULL, 
    provider VARCHAR(30) NOT NULL, 
    endpoint VARCHAR(120) NOT NULL, 
    params JSONB NOT NULL, 
    status_code INTEGER, 
    ok BOOLEAN NOT NULL, 
    from_cache BOOLEAN NOT NULL, 
    duration_ms INTEGER, 
    remaining_daily INTEGER, 
    error TEXT, 
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, 
    CONSTRAINT pk_provider_requests PRIMARY KEY (id)
);

CREATE INDEX ix_provider_requests_created_at ON provider_requests (created_at);

CREATE INDEX ix_provider_requests_provider ON provider_requests (provider);

CREATE TABLE provider_sync_jobs (
    id SERIAL NOT NULL, 
    job VARCHAR(50) NOT NULL, 
    provider VARCHAR(30) NOT NULL, 
    status VARCHAR(15) NOT NULL, 
    started_at TIMESTAMP WITH TIME ZONE NOT NULL, 
    finished_at TIMESTAMP WITH TIME ZONE, 
    items INTEGER NOT NULL, 
    message TEXT, 
    triggered_by VARCHAR(30) NOT NULL, 
    CONSTRAINT pk_provider_sync_jobs PRIMARY KEY (id)
);

CREATE INDEX ix_provider_sync_jobs_job ON provider_sync_jobs (job);

CREATE TABLE roles (
    id SERIAL NOT NULL, 
    code VARCHAR(20) NOT NULL, 
    description VARCHAR(200) NOT NULL, 
    CONSTRAINT pk_roles PRIMARY KEY (id), 
    CONSTRAINT uq_roles_code UNIQUE (code)
);

CREATE TABLE sports (
    id SERIAL NOT NULL, 
    code VARCHAR(20) NOT NULL, 
    name VARCHAR(60) NOT NULL, 
    enabled BOOLEAN NOT NULL, 
    CONSTRAINT pk_sports PRIMARY KEY (id), 
    CONSTRAINT uq_sports_code UNIQUE (code)
);

CREATE TABLE leagues (
    id SERIAL NOT NULL, 
    sport_id INTEGER NOT NULL, 
    country_id INTEGER, 
    provider VARCHAR(20) NOT NULL, 
    external_id VARCHAR(50) NOT NULL, 
    name VARCHAR(150) NOT NULL, 
    logo_url VARCHAR(300), 
    is_demo BOOLEAN NOT NULL, 
    is_active BOOLEAN NOT NULL, 
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, 
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL, 
    CONSTRAINT pk_leagues PRIMARY KEY (id), 
    CONSTRAINT fk_leagues_country_id_countries FOREIGN KEY(country_id) REFERENCES countries (id), 
    CONSTRAINT fk_leagues_sport_id_sports FOREIGN KEY(sport_id) REFERENCES sports (id), 
    CONSTRAINT uq_league_provider_ext UNIQUE (provider, external_id, sport_id)
);

CREATE INDEX ix_leagues_name ON leagues (name);

CREATE TABLE prediction_models (
    id SERIAL NOT NULL, 
    code VARCHAR(50) NOT NULL, 
    sport_id INTEGER NOT NULL, 
    name VARCHAR(100) NOT NULL, 
    description TEXT NOT NULL, 
    CONSTRAINT pk_prediction_models PRIMARY KEY (id), 
    CONSTRAINT fk_prediction_models_sport_id_sports FOREIGN KEY(sport_id) REFERENCES sports (id), 
    CONSTRAINT uq_prediction_models_code UNIQUE (code)
);

CREATE TABLE teams (
    id SERIAL NOT NULL, 
    sport_id INTEGER NOT NULL, 
    country_id INTEGER, 
    provider VARCHAR(20) NOT NULL, 
    external_id VARCHAR(50) NOT NULL, 
    name VARCHAR(150) NOT NULL, 
    short_name VARCHAR(20), 
    logo_url VARCHAR(300), 
    is_demo BOOLEAN NOT NULL, 
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, 
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL, 
    CONSTRAINT pk_teams PRIMARY KEY (id), 
    CONSTRAINT fk_teams_country_id_countries FOREIGN KEY(country_id) REFERENCES countries (id), 
    CONSTRAINT fk_teams_sport_id_sports FOREIGN KEY(sport_id) REFERENCES sports (id), 
    CONSTRAINT uq_team_provider_ext UNIQUE (provider, external_id, sport_id)
);

CREATE INDEX ix_teams_name ON teams (name);

CREATE TABLE users (
    id SERIAL NOT NULL, 
    email VARCHAR(254) NOT NULL, 
    password_hash VARCHAR(255), 
    role_id INTEGER NOT NULL, 
    is_active BOOLEAN NOT NULL, 
    email_verified BOOLEAN NOT NULL, 
    google_sub VARCHAR(64), 
    is_demo BOOLEAN NOT NULL, 
    failed_logins INTEGER NOT NULL, 
    locked_until TIMESTAMP WITH TIME ZONE, 
    last_login_at TIMESTAMP WITH TIME ZONE, 
    deleted_at TIMESTAMP WITH TIME ZONE, 
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, 
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL, 
    CONSTRAINT pk_users PRIMARY KEY (id), 
    CONSTRAINT fk_users_role_id_roles FOREIGN KEY(role_id) REFERENCES roles (id), 
    CONSTRAINT uq_users_google_sub UNIQUE (google_sub)
);

CREATE UNIQUE INDEX ix_users_email ON users (email);

CREATE TABLE audit_logs (
    id BIGSERIAL NOT NULL, 
    actor_user_id INTEGER, 
    action VARCHAR(80) NOT NULL, 
    entity VARCHAR(50) NOT NULL, 
    entity_id VARCHAR(50), 
    detail JSONB NOT NULL, 
    ip VARCHAR(64), 
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, 
    CONSTRAINT pk_audit_logs PRIMARY KEY (id), 
    CONSTRAINT fk_audit_logs_actor_user_id_users FOREIGN KEY(actor_user_id) REFERENCES users (id) ON DELETE SET NULL
);

CREATE INDEX ix_audit_logs_action ON audit_logs (action);

CREATE INDEX ix_audit_logs_actor_user_id ON audit_logs (actor_user_id);

CREATE INDEX ix_audit_logs_created_at ON audit_logs (created_at);

CREATE TABLE billing_events (
    id SERIAL NOT NULL, 
    provider VARCHAR(20) NOT NULL, 
    event_id VARCHAR(200) NOT NULL, 
    event_type VARCHAR(60) NOT NULL, 
    user_id INTEGER, 
    payload JSONB NOT NULL, 
    processed BOOLEAN NOT NULL, 
    error TEXT, 
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, 
    CONSTRAINT pk_billing_events PRIMARY KEY (id), 
    CONSTRAINT fk_billing_events_user_id_users FOREIGN KEY(user_id) REFERENCES users (id) ON DELETE SET NULL, 
    CONSTRAINT uq_billing_event UNIQUE (provider, event_id)
);

CREATE TABLE device_tokens (
    id SERIAL NOT NULL, 
    user_id INTEGER NOT NULL, 
    token VARCHAR(300) NOT NULL, 
    platform VARCHAR(10) NOT NULL, 
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, 
    CONSTRAINT pk_device_tokens PRIMARY KEY (id), 
    CONSTRAINT fk_device_tokens_user_id_users FOREIGN KEY(user_id) REFERENCES users (id) ON DELETE CASCADE, 
    CONSTRAINT uq_device_tokens_token UNIQUE (token)
);

CREATE INDEX ix_device_tokens_user_id ON device_tokens (user_id);

CREATE TABLE favorites (
    id SERIAL NOT NULL, 
    user_id INTEGER NOT NULL, 
    kind VARCHAR(10) NOT NULL, 
    ref_id INTEGER NOT NULL, 
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, 
    CONSTRAINT pk_favorites PRIMARY KEY (id), 
    CONSTRAINT fk_favorites_user_id_users FOREIGN KEY(user_id) REFERENCES users (id) ON DELETE CASCADE, 
    CONSTRAINT uq_favorite UNIQUE (user_id, kind, ref_id)
);

CREATE INDEX ix_favorites_user_id ON favorites (user_id);

CREATE TABLE model_versions (
    id SERIAL NOT NULL, 
    model_id INTEGER NOT NULL, 
    version VARCHAR(20) NOT NULL, 
    params JSONB NOT NULL, 
    metrics JSONB NOT NULL, 
    is_active BOOLEAN NOT NULL, 
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, 
    CONSTRAINT pk_model_versions PRIMARY KEY (id), 
    CONSTRAINT fk_model_versions_model_id_prediction_models FOREIGN KEY(model_id) REFERENCES prediction_models (id) ON DELETE CASCADE, 
    CONSTRAINT uq_model_version UNIQUE (model_id, version)
);

CREATE TABLE notifications (
    id SERIAL NOT NULL, 
    user_id INTEGER, 
    kind VARCHAR(30) NOT NULL, 
    title VARCHAR(150) NOT NULL, 
    body TEXT NOT NULL, 
    data JSONB NOT NULL, 
    read_at TIMESTAMP WITH TIME ZONE, 
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, 
    CONSTRAINT pk_notifications PRIMARY KEY (id), 
    CONSTRAINT fk_notifications_user_id_users FOREIGN KEY(user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX ix_notifications_created_at ON notifications (created_at);

CREATE INDEX ix_notifications_user_id ON notifications (user_id);

CREATE TABLE one_time_tokens (
    id SERIAL NOT NULL, 
    user_id INTEGER NOT NULL, 
    purpose VARCHAR(20) NOT NULL, 
    token_hash VARCHAR(64) NOT NULL, 
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL, 
    used_at TIMESTAMP WITH TIME ZONE, 
    CONSTRAINT pk_one_time_tokens PRIMARY KEY (id), 
    CONSTRAINT fk_one_time_tokens_user_id_users FOREIGN KEY(user_id) REFERENCES users (id) ON DELETE CASCADE, 
    CONSTRAINT uq_one_time_tokens_token_hash UNIQUE (token_hash)
);

CREATE INDEX ix_one_time_tokens_user_id ON one_time_tokens (user_id);

CREATE TABLE players (
    id SERIAL NOT NULL, 
    team_id INTEGER, 
    provider VARCHAR(20) NOT NULL, 
    external_id VARCHAR(50) NOT NULL, 
    name VARCHAR(150) NOT NULL, 
    position VARCHAR(30), 
    CONSTRAINT pk_players PRIMARY KEY (id), 
    CONSTRAINT fk_players_team_id_teams FOREIGN KEY(team_id) REFERENCES teams (id) ON DELETE SET NULL, 
    CONSTRAINT uq_player_provider_ext UNIQUE (provider, external_id)
);

CREATE INDEX ix_players_team_id ON players (team_id);

CREATE TABLE refresh_tokens (
    id SERIAL NOT NULL, 
    user_id INTEGER NOT NULL, 
    family_id VARCHAR(36) NOT NULL, 
    token_hash VARCHAR(64) NOT NULL, 
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL, 
    revoked_at TIMESTAMP WITH TIME ZONE, 
    replaced_by_id INTEGER, 
    user_agent VARCHAR(200) NOT NULL, 
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, 
    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id), 
    CONSTRAINT fk_refresh_tokens_user_id_users FOREIGN KEY(user_id) REFERENCES users (id) ON DELETE CASCADE, 
    CONSTRAINT uq_refresh_tokens_token_hash UNIQUE (token_hash)
);

CREATE INDEX ix_refresh_tokens_family_id ON refresh_tokens (family_id);

CREATE INDEX ix_refresh_tokens_user_id ON refresh_tokens (user_id);

CREATE TABLE seasons (
    id SERIAL NOT NULL, 
    league_id INTEGER NOT NULL, 
    year INTEGER NOT NULL, 
    start_date DATE, 
    end_date DATE, 
    is_current BOOLEAN NOT NULL, 
    CONSTRAINT pk_seasons PRIMARY KEY (id), 
    CONSTRAINT fk_seasons_league_id_leagues FOREIGN KEY(league_id) REFERENCES leagues (id) ON DELETE CASCADE, 
    CONSTRAINT uq_season_league_year UNIQUE (league_id, year)
);

CREATE TABLE selection_groups (
    id SERIAL NOT NULL, 
    user_id INTEGER NOT NULL, 
    name VARCHAR(100) NOT NULL, 
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, 
    joint_probability NUMERIC(7, 6), 
    joint_computable BOOLEAN NOT NULL, 
    CONSTRAINT pk_selection_groups PRIMARY KEY (id), 
    CONSTRAINT fk_selection_groups_user_id_users FOREIGN KEY(user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX ix_selection_groups_user_id ON selection_groups (user_id);

CREATE TABLE standings (
    id SERIAL NOT NULL, 
    league_id INTEGER NOT NULL, 
    season_year INTEGER NOT NULL, 
    team_id INTEGER NOT NULL, 
    position INTEGER NOT NULL, 
    played INTEGER NOT NULL, 
    won INTEGER NOT NULL, 
    drawn INTEGER NOT NULL, 
    lost INTEGER NOT NULL, 
    points_for INTEGER NOT NULL, 
    points_against INTEGER NOT NULL, 
    points INTEGER NOT NULL, 
    form VARCHAR(20) NOT NULL, 
    group_name VARCHAR(50), 
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL, 
    CONSTRAINT pk_standings PRIMARY KEY (id), 
    CONSTRAINT fk_standings_league_id_leagues FOREIGN KEY(league_id) REFERENCES leagues (id) ON DELETE CASCADE, 
    CONSTRAINT fk_standings_team_id_teams FOREIGN KEY(team_id) REFERENCES teams (id), 
    CONSTRAINT uq_standing UNIQUE (league_id, season_year, team_id)
);

CREATE INDEX ix_standings_league_id ON standings (league_id);

CREATE TABLE subscriptions (
    id SERIAL NOT NULL, 
    user_id INTEGER NOT NULL, 
    plan_id INTEGER NOT NULL, 
    provider VARCHAR(20) NOT NULL, 
    status VARCHAR(20) NOT NULL, 
    external_id VARCHAR(200), 
    external_order_id VARCHAR(100), 
    started_at TIMESTAMP WITH TIME ZONE NOT NULL, 
    current_period_end TIMESTAMP WITH TIME ZONE, 
    grace_until TIMESTAMP WITH TIME ZONE, 
    auto_renew BOOLEAN NOT NULL, 
    canceled_at TIMESTAMP WITH TIME ZONE, 
    is_test BOOLEAN NOT NULL, 
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, 
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL, 
    CONSTRAINT pk_subscriptions PRIMARY KEY (id), 
    CONSTRAINT fk_subscriptions_plan_id_plans FOREIGN KEY(plan_id) REFERENCES plans (id), 
    CONSTRAINT fk_subscriptions_user_id_users FOREIGN KEY(user_id) REFERENCES users (id) ON DELETE CASCADE, 
    CONSTRAINT uq_subscriptions_provider_external UNIQUE (provider, external_id)
);

CREATE INDEX ix_subscriptions_status ON subscriptions (status);

CREATE INDEX ix_subscriptions_user_id ON subscriptions (user_id);

CREATE INDEX ix_subscriptions_user_status ON subscriptions (user_id, status);

CREATE TABLE team_statistics (
    id SERIAL NOT NULL, 
    team_id INTEGER NOT NULL, 
    league_id INTEGER NOT NULL, 
    season_year INTEGER NOT NULL, 
    stats JSONB NOT NULL, 
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL, 
    CONSTRAINT pk_team_statistics PRIMARY KEY (id), 
    CONSTRAINT fk_team_statistics_league_id_leagues FOREIGN KEY(league_id) REFERENCES leagues (id) ON DELETE CASCADE, 
    CONSTRAINT fk_team_statistics_team_id_teams FOREIGN KEY(team_id) REFERENCES teams (id) ON DELETE CASCADE, 
    CONSTRAINT uq_team_stat UNIQUE (team_id, league_id, season_year)
);

CREATE INDEX ix_team_statistics_team_id ON team_statistics (team_id);

CREATE TABLE user_profiles (
    user_id INTEGER NOT NULL, 
    display_name VARCHAR(80) NOT NULL, 
    country VARCHAR(2) NOT NULL, 
    language VARCHAR(5) NOT NULL, 
    timezone VARCHAR(64) NOT NULL, 
    theme VARCHAR(10) NOT NULL, 
    preferences JSONB NOT NULL, 
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, 
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL, 
    CONSTRAINT pk_user_profiles PRIMARY KEY (user_id), 
    CONSTRAINT fk_user_profiles_user_id_users FOREIGN KEY(user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE fixtures (
    id SERIAL NOT NULL, 
    sport_id INTEGER NOT NULL, 
    league_id INTEGER NOT NULL, 
    season_id INTEGER, 
    provider VARCHAR(20) NOT NULL, 
    external_id VARCHAR(50) NOT NULL, 
    home_team_id INTEGER NOT NULL, 
    away_team_id INTEGER NOT NULL, 
    kickoff TIMESTAMP WITH TIME ZONE NOT NULL, 
    status VARCHAR(20) NOT NULL, 
    home_score INTEGER, 
    away_score INTEGER, 
    is_demo BOOLEAN NOT NULL, 
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, 
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL, 
    CONSTRAINT pk_fixtures PRIMARY KEY (id), 
    CONSTRAINT ck_fixtures_teams_differ CHECK (home_team_id <> away_team_id), 
    CONSTRAINT fk_fixtures_away_team_id_teams FOREIGN KEY(away_team_id) REFERENCES teams (id), 
    CONSTRAINT fk_fixtures_home_team_id_teams FOREIGN KEY(home_team_id) REFERENCES teams (id), 
    CONSTRAINT fk_fixtures_league_id_leagues FOREIGN KEY(league_id) REFERENCES leagues (id), 
    CONSTRAINT fk_fixtures_season_id_seasons FOREIGN KEY(season_id) REFERENCES seasons (id), 
    CONSTRAINT fk_fixtures_sport_id_sports FOREIGN KEY(sport_id) REFERENCES sports (id), 
    CONSTRAINT uq_fixture_provider_ext UNIQUE (provider, external_id, sport_id)
);

CREATE INDEX ix_fixtures_away_team_id ON fixtures (away_team_id);

CREATE INDEX ix_fixtures_home_team_id ON fixtures (home_team_id);

CREATE INDEX ix_fixtures_kickoff ON fixtures (kickoff);

CREATE INDEX ix_fixtures_league_id ON fixtures (league_id);

CREATE INDEX ix_fixtures_sport_kickoff ON fixtures (sport_id, kickoff);

CREATE TABLE payment_transactions (
    id SERIAL NOT NULL, 
    user_id INTEGER, 
    subscription_id INTEGER, 
    provider VARCHAR(20) NOT NULL, 
    external_id VARCHAR(200), 
    amount NUMERIC(12, 2) NOT NULL, 
    currency VARCHAR(3) NOT NULL, 
    status VARCHAR(20) NOT NULL, 
    is_test BOOLEAN NOT NULL, 
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, 
    CONSTRAINT pk_payment_transactions PRIMARY KEY (id), 
    CONSTRAINT fk_payment_transactions_subscription_id_subscriptions FOREIGN KEY(subscription_id) REFERENCES subscriptions (id) ON DELETE SET NULL, 
    CONSTRAINT fk_payment_transactions_user_id_users FOREIGN KEY(user_id) REFERENCES users (id) ON DELETE SET NULL, 
    CONSTRAINT uq_payment_provider_external UNIQUE (provider, external_id)
);

CREATE INDEX ix_payment_transactions_created_at ON payment_transactions (created_at);

CREATE INDEX ix_payment_transactions_user_id ON payment_transactions (user_id);

CREATE TABLE prediction_evaluations (
    id SERIAL NOT NULL, 
    model_version_id INTEGER NOT NULL, 
    sport_id INTEGER NOT NULL, 
    league_id INTEGER, 
    market VARCHAR(60) NOT NULL, 
    n INTEGER NOT NULL, 
    hit_rate NUMERIC(7, 6), 
    brier NUMERIC(8, 6), 
    log_loss NUMERIC(8, 6), 
    mean_probability NUMERIC(7, 6), 
    computed_at TIMESTAMP WITH TIME ZONE NOT NULL, 
    CONSTRAINT pk_prediction_evaluations PRIMARY KEY (id), 
    CONSTRAINT fk_prediction_evaluations_league_id_leagues FOREIGN KEY(league_id) REFERENCES leagues (id), 
    CONSTRAINT fk_prediction_evaluations_model_version_id_model_versions FOREIGN KEY(model_version_id) REFERENCES model_versions (id) ON DELETE CASCADE, 
    CONSTRAINT fk_prediction_evaluations_sport_id_sports FOREIGN KEY(sport_id) REFERENCES sports (id)
);

CREATE INDEX ix_eval_lookup ON prediction_evaluations (model_version_id, sport_id, league_id, market);

CREATE TABLE daily_usage (
    id SERIAL NOT NULL, 
    user_id INTEGER NOT NULL, 
    usage_date DATE NOT NULL, 
    fixture_id INTEGER NOT NULL, 
    analysis_id INTEGER, 
    plan_code VARCHAR(20) NOT NULL, 
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, 
    CONSTRAINT pk_daily_usage PRIMARY KEY (id), 
    CONSTRAINT fk_daily_usage_fixture_id_fixtures FOREIGN KEY(fixture_id) REFERENCES fixtures (id) ON DELETE CASCADE, 
    CONSTRAINT fk_daily_usage_user_id_users FOREIGN KEY(user_id) REFERENCES users (id) ON DELETE CASCADE, 
    CONSTRAINT uq_daily_usage_unique UNIQUE (user_id, usage_date, fixture_id)
);

CREATE INDEX ix_daily_usage_user_date ON daily_usage (user_id, usage_date);

CREATE INDEX ix_daily_usage_user_id ON daily_usage (user_id);

CREATE TABLE fixture_analyses (
    id SERIAL NOT NULL, 
    fixture_id INTEGER NOT NULL, 
    model_version_id INTEGER NOT NULL, 
    status VARCHAR(20) NOT NULL, 
    quality VARCHAR(15) NOT NULL, 
    n_home INTEGER NOT NULL, 
    n_away INTEGER NOT NULL, 
    payload JSONB NOT NULL, 
    is_pre_match BOOLEAN NOT NULL, 
    generated_at TIMESTAMP WITH TIME ZONE NOT NULL, 
    is_demo BOOLEAN NOT NULL, 
    CONSTRAINT pk_fixture_analyses PRIMARY KEY (id), 
    CONSTRAINT fk_fixture_analyses_fixture_id_fixtures FOREIGN KEY(fixture_id) REFERENCES fixtures (id) ON DELETE CASCADE, 
    CONSTRAINT fk_fixture_analyses_model_version_id_model_versions FOREIGN KEY(model_version_id) REFERENCES model_versions (id)
);

CREATE INDEX ix_analysis_fixture_prematch ON fixture_analyses (fixture_id, is_pre_match, generated_at);

CREATE INDEX ix_fixture_analyses_fixture_id ON fixture_analyses (fixture_id);

CREATE INDEX ix_fixture_analyses_generated_at ON fixture_analyses (generated_at);

CREATE TABLE fixture_statistics (
    id SERIAL NOT NULL, 
    fixture_id INTEGER NOT NULL, 
    team_id INTEGER NOT NULL, 
    stats JSONB NOT NULL, 
    CONSTRAINT pk_fixture_statistics PRIMARY KEY (id), 
    CONSTRAINT fk_fixture_statistics_fixture_id_fixtures FOREIGN KEY(fixture_id) REFERENCES fixtures (id) ON DELETE CASCADE, 
    CONSTRAINT fk_fixture_statistics_team_id_teams FOREIGN KEY(team_id) REFERENCES teams (id), 
    CONSTRAINT uq_fixture_stat_team UNIQUE (fixture_id, team_id)
);

CREATE INDEX ix_fixture_statistics_fixture_id ON fixture_statistics (fixture_id);

CREATE TABLE historical_results (
    id SERIAL NOT NULL, 
    fixture_id INTEGER NOT NULL, 
    sport_id INTEGER NOT NULL, 
    league_id INTEGER NOT NULL, 
    home_team_id INTEGER NOT NULL, 
    away_team_id INTEGER NOT NULL, 
    played_at TIMESTAMP WITH TIME ZONE NOT NULL, 
    home_score INTEGER NOT NULL, 
    away_score INTEGER NOT NULL, 
    extra JSONB NOT NULL, 
    is_demo BOOLEAN NOT NULL, 
    CONSTRAINT pk_historical_results PRIMARY KEY (id), 
    CONSTRAINT fk_historical_results_away_team_id_teams FOREIGN KEY(away_team_id) REFERENCES teams (id), 
    CONSTRAINT fk_historical_results_fixture_id_fixtures FOREIGN KEY(fixture_id) REFERENCES fixtures (id) ON DELETE CASCADE, 
    CONSTRAINT fk_historical_results_home_team_id_teams FOREIGN KEY(home_team_id) REFERENCES teams (id), 
    CONSTRAINT fk_historical_results_league_id_leagues FOREIGN KEY(league_id) REFERENCES leagues (id), 
    CONSTRAINT fk_historical_results_sport_id_sports FOREIGN KEY(sport_id) REFERENCES sports (id), 
    CONSTRAINT uq_historical_results_fixture_id UNIQUE (fixture_id)
);

CREATE INDEX ix_hist_away ON historical_results (away_team_id, played_at);

CREATE INDEX ix_hist_home ON historical_results (home_team_id, played_at);

CREATE INDEX ix_hist_sport_league_played ON historical_results (sport_id, league_id, played_at);

CREATE TABLE injuries (
    id SERIAL NOT NULL, 
    team_id INTEGER NOT NULL, 
    player_id INTEGER, 
    fixture_id INTEGER, 
    player_name VARCHAR(150) NOT NULL, 
    reason VARCHAR(100) NOT NULL, 
    status VARCHAR(30) NOT NULL, 
    reported_at TIMESTAMP WITH TIME ZONE NOT NULL, 
    CONSTRAINT pk_injuries PRIMARY KEY (id), 
    CONSTRAINT fk_injuries_fixture_id_fixtures FOREIGN KEY(fixture_id) REFERENCES fixtures (id) ON DELETE CASCADE, 
    CONSTRAINT fk_injuries_player_id_players FOREIGN KEY(player_id) REFERENCES players (id) ON DELETE SET NULL, 
    CONSTRAINT fk_injuries_team_id_teams FOREIGN KEY(team_id) REFERENCES teams (id) ON DELETE CASCADE
);

CREATE INDEX ix_injuries_team_id ON injuries (team_id);

CREATE TABLE selection_items (
    id SERIAL NOT NULL, 
    group_id INTEGER NOT NULL, 
    fixture_id INTEGER NOT NULL, 
    market VARCHAR(60) NOT NULL, 
    probability_at_save NUMERIC(7, 6), 
    CONSTRAINT pk_selection_items PRIMARY KEY (id), 
    CONSTRAINT fk_selection_items_fixture_id_fixtures FOREIGN KEY(fixture_id) REFERENCES fixtures (id) ON DELETE CASCADE, 
    CONSTRAINT fk_selection_items_group_id_selection_groups FOREIGN KEY(group_id) REFERENCES selection_groups (id) ON DELETE CASCADE, 
    CONSTRAINT uq_selection_item UNIQUE (group_id, fixture_id, market)
);

CREATE INDEX ix_selection_items_group_id ON selection_items (group_id);

CREATE TABLE predictions (
    id SERIAL NOT NULL, 
    analysis_id INTEGER NOT NULL, 
    fixture_id INTEGER NOT NULL, 
    sport_id INTEGER NOT NULL, 
    league_id INTEGER NOT NULL, 
    market VARCHAR(60) NOT NULL, 
    selection VARCHAR(30) NOT NULL, 
    market_group VARCHAR(30) NOT NULL, 
    probability NUMERIC(7, 6) NOT NULL, 
    category VARCHAR(25) NOT NULL, 
    uncertainty VARCHAR(10) NOT NULL, 
    data_points INTEGER NOT NULL, 
    explanation TEXT NOT NULL, 
    is_pre_match BOOLEAN NOT NULL, 
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, 
    CONSTRAINT pk_predictions PRIMARY KEY (id), 
    CONSTRAINT ck_predictions_prob_range CHECK (probability >= 0 AND probability <= 1), 
    CONSTRAINT fk_predictions_analysis_id_fixture_analyses FOREIGN KEY(analysis_id) REFERENCES fixture_analyses (id) ON DELETE CASCADE, 
    CONSTRAINT fk_predictions_fixture_id_fixtures FOREIGN KEY(fixture_id) REFERENCES fixtures (id) ON DELETE CASCADE, 
    CONSTRAINT fk_predictions_league_id_leagues FOREIGN KEY(league_id) REFERENCES leagues (id), 
    CONSTRAINT fk_predictions_sport_id_sports FOREIGN KEY(sport_id) REFERENCES sports (id), 
    CONSTRAINT uq_prediction_unique UNIQUE (analysis_id, market, selection)
);

CREATE INDEX ix_pred_filter ON predictions (sport_id, league_id, market, probability);

CREATE INDEX ix_predictions_analysis_id ON predictions (analysis_id);

CREATE INDEX ix_predictions_created_at ON predictions (created_at);

CREATE INDEX ix_predictions_fixture_id ON predictions (fixture_id);

CREATE TABLE prediction_outcomes (
    id SERIAL NOT NULL, 
    prediction_id INTEGER NOT NULL, 
    hit BOOLEAN NOT NULL, 
    actual JSONB NOT NULL, 
    settled_at TIMESTAMP WITH TIME ZONE NOT NULL, 
    CONSTRAINT pk_prediction_outcomes PRIMARY KEY (id), 
    CONSTRAINT fk_prediction_outcomes_prediction_id_predictions FOREIGN KEY(prediction_id) REFERENCES predictions (id) ON DELETE CASCADE, 
    CONSTRAINT uq_prediction_outcomes_prediction_id UNIQUE (prediction_id)
);

CREATE TABLE saved_predictions (
    id SERIAL NOT NULL, 
    user_id INTEGER NOT NULL, 
    prediction_id INTEGER NOT NULL, 
    note VARCHAR(300) NOT NULL, 
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, 
    CONSTRAINT pk_saved_predictions PRIMARY KEY (id), 
    CONSTRAINT fk_saved_predictions_prediction_id_predictions FOREIGN KEY(prediction_id) REFERENCES predictions (id) ON DELETE CASCADE, 
    CONSTRAINT fk_saved_predictions_user_id_users FOREIGN KEY(user_id) REFERENCES users (id) ON DELETE CASCADE, 
    CONSTRAINT uq_saved_prediction UNIQUE (user_id, prediction_id)
);

CREATE INDEX ix_saved_predictions_user_id ON saved_predictions (user_id);

INSERT INTO alembic_version (version_num) VALUES ('0001') RETURNING alembic_version.version_num;

COMMIT;

