#!/usr/bin/env bash
# PronoScore360 - preparación en Linux/macOS. Requisitos: Docker (con plugin compose) y Git.
# Uso: bash scripts/setup-linux.sh        (no instala Docker ni claves de API)
set -euo pipefail
cd "$(dirname "$0")/.."
need() { command -v "$1" >/dev/null || { echo "Falta '$1'. $2" >&2; exit 1; }; }
need git "Instálalo con tu gestor de paquetes."
need docker "Instala Docker Engine: https://docs.docker.com/engine/install/"
docker info >/dev/null 2>&1 || { echo "El daemon de Docker no está en ejecución (sudo systemctl start docker)." >&2; exit 1; }
docker compose version >/dev/null 2>&1 || { echo "Falta el plugin 'docker compose'." >&2; exit 1; }

if [ ! -f .env ]; then
  cp .env.example .env
  secret=$(head -c 48 /dev/urandom | base64 | tr -d '+/=\n')
  sed -i.bak "s|^JWT_SECRET=.*|JWT_SECRET=${secret}|" .env && rm -f .env.bak
  echo "Creado .env con JWT_SECRET aleatorio (PROVIDER_MODE=demo por defecto)."
else
  echo ".env ya existe: no se modifica."
fi

docker compose up -d --build
echo "Esperando a la API (primera vez: migraciones + datos DEMO)..."
for i in $(seq 1 90); do
  curl -fs http://localhost:8000/api/v1/health >/dev/null 2>&1 && { ok=1; break; }; sleep 2
done
[ "${ok:-0}" = 1 ] || { echo "La API no respondió: docker compose logs api" >&2; exit 1; }
cat <<MSG

Listo:
  Web:         http://localhost:5173
  API/Swagger: http://localhost:8000/docs
  Cuentas DEMO (sólo local): admin@pronoscore-demo.example.com / Demo#Admin2026 ; pro@... / Demo#Pro2026 ; free@... / Demo#Free2026
MSG
