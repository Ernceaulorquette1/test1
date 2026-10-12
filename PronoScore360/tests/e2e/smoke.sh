#!/usr/bin/env bash
# Prueba de humo end-to-end contra una instancia en marcha (Docker Compose o dev-local). Uso: bash tests/e2e/smoke.sh [http://localhost:8000]
set -euo pipefail
API=${1:-http://localhost:8000}/api/v1
j() { python3 -c "import sys,json;d=json.load(sys.stdin);print($1)"; }
step() { printf '%-60s' "$1"; }
ok() { echo OK; }
step "health";            curl -fs $API/health | j "d['status']" | grep -q ok && ok
step "status (modo)";     curl -fs $API/status | j "d['provider_mode']"
T=$(curl -fs -X POST $API/auth/login -H 'content-type: application/json' -d '{"email":"free@pronoscore-demo.example.com","password":"Demo#Free2026"}' | j "d['tokens']['access_token']")
H="Authorization: Bearer $T"
step "login demo gratis"; echo OK
FID=$(curl -fs "$API/fixtures?sport=football&status=scheduled&page_size=1" -H "$H" | j "d['items'][0]['id']")
step "calendario"; echo "fixture $FID"
step "análisis (1 consulta)"; curl -fs $API/fixtures/$FID/analysis -H "$H" | j "str(len(d['predictions']))+' pronósticos, usadas '+str(d['usage']['used'])+'/'+str(d['usage']['limit'])"
step "mismo partido no descuenta"; curl -fs $API/fixtures/$FID/analysis -H "$H" | j "'usadas '+str(d['usage']['used'])"
step "Pro-only bloqueado (402)"; test "$(curl -s -o /dev/null -w '%{http_code}' $API/predictions -H "$H")" = 402 && ok
step "admin bloqueado (403)"; test "$(curl -s -o /dev/null -w '%{http_code}' $API/admin/dashboard -H "$H")" = 403 && ok
echo "SMOKE OK"
