#!/usr/bin/env bash
# Alternativa SIN Docker (probada en este proyecto): PostgreSQL local + venv Python + Vite.
# Requisitos: PostgreSQL 14+ en localhost:5432 con usuario/BD 'pronoscore', Python >= 3.11, Node >= 20.
#   createuser -P pronoscore ; createdb -O pronoscore pronoscore       (contraseña local: pronoscore)
set -euo pipefail
cd "$(dirname "$0")/.."
python3 -m venv .venv && . .venv/bin/activate
pip install -q -r backend/requirements.txt -e analytics
export DATABASE_URL=${DATABASE_URL:-postgresql+psycopg://pronoscore:pronoscore@localhost:5432/pronoscore}
export ENV=local SEED_DEMO_ON_START=true PROVIDER_MODE=demo PYTHONPATH=backend:analytics
(cd backend && alembic upgrade head)
(cd web && npm install --no-audit --no-fund && npm run dev -- --host &)
cd backend && exec uvicorn app.main:app --host 0.0.0.0 --port 8000
