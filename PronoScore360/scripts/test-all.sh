#!/usr/bin/env bash
# Ejecuta TODAS las pruebas. Requiere: venv con backend/requirements-dev.txt, PostgreSQL local (BD 'pronoscore_test'), Node, JDK 17+.
# La integración web/Android contra el backend real necesita la API en http://localhost:8000 (se omite con aviso si no está).
set -uo pipefail
cd "$(dirname "$0")/.."
rc=0
echo "== analytics ==";  (cd analytics && python -m pytest -q) || rc=1
echo "== backend ==";    (cd backend && python -m pytest -q) || rc=1
echo "== web (unitarias) =="; (cd web && npm test --silent) || rc=1
echo "== web (integración con backend real) =="; (cd web && npm run test:integration --silent) || rc=1
echo "== android :core =="; (cd android && ./gradlew :core:test -PskipApp=true --no-daemon -q) || rc=1
[ $rc -eq 0 ] && echo "TODO OK" || echo "HAY FALLOS"
exit $rc
