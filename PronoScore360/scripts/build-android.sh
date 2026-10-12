#!/usr/bin/env bash
# Compila Android (requiere Android SDK: ANDROID_HOME o local.properties con sdk.dir).
#   bash scripts/build-android.sh debug                 -> APK debug  (apuntando a http://10.0.2.2:8000/api/v1/)
#   API_BASE_URL=http://192.168.1.50:8000/api/v1/ bash scripts/build-android.sh debug   -> teléfono físico en la misma Wi-Fi
#   API_BASE_URL=https://api.tudominio.com/api/v1/ bash scripts/build-android.sh release -> AAB firmado (requiere keystore.properties)
set -euo pipefail
cd "$(dirname "$0")/../android"
variant=${1:-debug}
args=()
[ -n "${API_BASE_URL:-}" ] && args+=("-PapiBaseUrl=${API_BASE_URL}")
[ -n "${VERSION_CODE:-}" ] && args+=("-PversionCode=${VERSION_CODE}")
[ -n "${VERSION_NAME:-}" ] && args+=("-PversionName=${VERSION_NAME}")
case "$variant" in
  debug)   ./gradlew assembleDebug "${args[@]}"; echo "APK: android/app/build/outputs/apk/debug/app-debug.apk";;
  release) [ -f keystore.properties ] || { echo "Falta android/keystore.properties (ver CONFIGURACION_GOOGLE_PLAY.md)" >&2; exit 1; }
           ./gradlew bundleRelease "${args[@]}"; echo "AAB: android/app/build/outputs/bundle/release/app-release.aab";;
  *) echo "uso: build-android.sh debug|release" >&2; exit 1;;
esac
