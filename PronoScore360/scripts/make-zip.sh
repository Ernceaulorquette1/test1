#!/usr/bin/env bash
# Genera PronoScore360_Proyecto_Completo.zip (sin secretos, node_modules, builds ni cachés).
set -euo pipefail
cd "$(dirname "$0")/../.."
out="${1:-PronoScore360/PronoScore360_Proyecto_Completo.zip}"
rm -f "$out"
zip -rq "$out" PronoScore360 \
  -x 'PronoScore360/PronoScore360_Proyecto_Completo.zip' '*/node_modules/*' '*/__pycache__/*' '*/.pytest_cache/*' '*/dist/*' '*/build/*' \
     '*/.gradle/*' '*.egg-info/*' 'PronoScore360/.env' '*/local.properties' '*/keystore.properties' '*.jks' '*.keystore' 'PronoScore360/.venv/*'
echo "Creado $out ($(du -h "$out" | cut -f1))"
unzip -l "$out" | tail -1
