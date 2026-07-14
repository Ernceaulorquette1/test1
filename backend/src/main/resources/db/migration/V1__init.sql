-- NutriScan AI — esquema inicial
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE users (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    firebase_uid     VARCHAR(128) NOT NULL UNIQUE,
    email            VARCHAR(255) NOT NULL UNIQUE,
    name             VARCHAR(120),
    photo_url        TEXT,
    age              INT           CHECK (age BETWEEN 10 AND 120),
    sex              VARCHAR(10)   CHECK (sex IN ('MALE','FEMALE','OTHER')),
    weight_kg        NUMERIC(5,2)  CHECK (weight_kg BETWEEN 20 AND 400),
    height_cm        NUMERIC(5,2)  CHECK (height_cm BETWEEN 90 AND 250),
    activity_level   VARCHAR(20)   CHECK (activity_level IN ('SEDENTARY','LIGHT','MODERATE','ACTIVE','VERY_ACTIVE')),
    goal             VARCHAR(20)   CHECK (goal IN ('LOSE_WEIGHT','MAINTAIN','GAIN_MUSCLE')),
    target_weight_kg NUMERIC(5,2),
    target_calories  INT,
    premium_until    TIMESTAMPTZ,
    fcm_token        TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE meals (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    meal_type   VARCHAR(12) NOT NULL CHECK (meal_type IN ('BREAKFAST','LUNCH','DINNER','SNACK')),
    name        VARCHAR(200) NOT NULL,
    photo_url   TEXT,
    portion     VARCHAR(100),
    eaten_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    calories    NUMERIC(7,1) NOT NULL DEFAULT 0,
    protein_g   NUMERIC(6,1) NOT NULL DEFAULT 0,
    carbs_g     NUMERIC(6,1) NOT NULL DEFAULT 0,
    fat_g       NUMERIC(6,1) NOT NULL DEFAULT 0,
    fiber_g     NUMERIC(6,1) NOT NULL DEFAULT 0,
    sodium_mg   NUMERIC(7,1) NOT NULL DEFAULT 0,
    sugar_g     NUMERIC(6,1) NOT NULL DEFAULT 0
);
CREATE INDEX idx_meals_user_eaten ON meals(user_id, eaten_at DESC);

CREATE TABLE meal_items (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    meal_id    UUID NOT NULL REFERENCES meals(id) ON DELETE CASCADE,
    name       VARCHAR(200) NOT NULL,
    quantity   VARCHAR(100),
    calories   NUMERIC(7,1) NOT NULL DEFAULT 0,
    protein_g  NUMERIC(6,1) NOT NULL DEFAULT 0,
    carbs_g    NUMERIC(6,1) NOT NULL DEFAULT 0,
    fat_g      NUMERIC(6,1) NOT NULL DEFAULT 0,
    fiber_g    NUMERIC(6,1) NOT NULL DEFAULT 0,
    sodium_mg  NUMERIC(7,1) NOT NULL DEFAULT 0,
    sugar_g    NUMERIC(6,1) NOT NULL DEFAULT 0
);

CREATE TABLE water_logs (
    id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id  UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    log_date DATE NOT NULL,
    ml       INT  NOT NULL CHECK (ml > 0),
    logged_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_water_user_date ON water_logs(user_id, log_date);

CREATE TABLE weight_logs (
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id   UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    log_date  DATE NOT NULL,
    weight_kg NUMERIC(5,2) NOT NULL,
    UNIQUE (user_id, log_date)
);

CREATE TABLE chat_messages (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role       VARCHAR(10) NOT NULL CHECK (role IN ('USER','ASSISTANT')),
    content    TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_chat_user_created ON chat_messages(user_id, created_at);

CREATE TABLE subscriptions (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    plan           VARCHAR(10) NOT NULL CHECK (plan IN ('MONTHLY','ANNUAL')),
    purchase_token TEXT NOT NULL,           -- token de Google Play Billing
    status         VARCHAR(12) NOT NULL CHECK (status IN ('ACTIVE','EXPIRED','CANCELED')),
    started_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at     TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_subs_user ON subscriptions(user_id);
