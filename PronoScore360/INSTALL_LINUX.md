# Instalación en Linux / macOS

**Requisitos:** Git, Docker Engine + plugin `docker compose`. Para Android: Android Studio (o SDK de línea de comandos + JDK 17).

```bash
unzip PronoScore360_Proyecto_Completo.zip && cd PronoScore360
bash scripts/setup-linux.sh        # crea .env (JWT_SECRET aleatorio), levanta Docker Compose y espera a la API
```
Manual: `cp .env.example .env && docker compose up -d --build && docker compose logs -f api`.

* Web http://localhost:5173 · Swagger http://localhost:8000/docs
* Cuentas DEMO: ver [README](README.md#inicio-rápido-modo-demo-sin-claves).
* Prueba de humo: `bash tests/e2e/smoke.sh`

## Sin Docker (verificado en el desarrollo)
```bash
sudo -u postgres createuser -P pronoscore     # contraseña: pronoscore
sudo -u postgres createdb -O pronoscore pronoscore
bash scripts/dev-local.sh                     # venv + migraciones + API (8000) + Vite (5173)
```
## Android
* Emulador: build debug apunta a `http://10.0.2.2:8000/api/v1/` (sin cambios).
* Teléfono físico (misma red): `API_BASE_URL=http://192.168.1.50:8000/api/v1/ bash scripts/build-android.sh debug` y `adb install -r android/app/build/outputs/apk/debug/app-debug.apk`. Abre el puerto 8000 en el firewall (`sudo ufw allow 8000/tcp`).
* Release/AAB: ver [CONFIGURACION_GOOGLE_PLAY.md](CONFIGURACION_GOOGLE_PLAY.md).
* ⚠ `:app` no pudo compilarse en el entorno de generación (sin SDK): ver [ESTADO_PROYECTO.md](ESTADO_PROYECTO.md).

## Pruebas
```bash
python -m venv .venv && . .venv/bin/activate && pip install -r backend/requirements-dev.txt -e analytics
bash scripts/test-all.sh
```
