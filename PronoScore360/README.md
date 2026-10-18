# PronoScore360

Plataforma de **análisis deportivo estadístico** (fútbol internacional y NBA) para **Android y web**.
Calcula probabilidades con modelos reales (Poisson/Dixon-Coles, eficiencia por posesión + Monte Carlo), estima marcadores y las muestra con su incertidumbre.
**Es informativa: no permite apuestas, depósitos ni enlaza con casas de apuestas. Las probabilidades no garantizan resultados.**

> Estado honesto y detallado de cada módulo (qué está probado, qué no): **[ESTADO_PROYECTO.md](ESTADO_PROYECTO.md)**.

## Contenido

| Carpeta | Qué contiene |
|---|---|
| `backend/` | API FastAPI + SQLAlchemy + Alembic (83 operaciones en 76 rutas bajo `/api/v1`), auth JWT/Argon2, cuotas, planes, pagos, admin, tareas programadas |
| `analytics/` | **PronoScore360 Analytics**: modelos de fútbol y NBA, calibración, métricas, backtesting, probabilidad conjunta (paquete independiente) |
| `web/` | React + TypeScript + Vite + Bootstrap 5 (28 pantallas incl. panel admin) |
| `android/` | Kotlin + Compose + Hilt + Room + Billing. `:core` (red/lógica, probado) y `:app` (UI, **sin compilar en este entorno**) |
| `database/` | Esquema SQL generado, diagrama ER, notas de diseño |
| `infrastructure/` | Compose de producción, Caddy (HTTPS), plantilla de variables |
| `scripts/` | Instalación Windows/Linux, desarrollo sin Docker, pruebas, build Android, ZIP |
| `tests/` | Prueba de humo end-to-end (`tests/e2e/smoke.sh`) |
| `docs/` | Documentación ampliada: modelos, pendientes, ficha de Google Play, ejemplos de API, capturas |

## Inicio rápido (modo DEMO, sin claves)

```bash
cp .env.example .env              # Windows: scripts\setup-windows.ps1 lo hace por ti
docker compose up -d --build      # PostgreSQL + Redis + API + Web
```
* Web: http://localhost:5173 · API/Swagger: http://localhost:8000/docs
* Cuentas **sólo locales** (se muestran como botones en la pantalla de login):

| Rol | Correo | Contraseña |
|---|---|---|
| Administrador | `admin@pronoscore-demo.example.com` | `Demo#Admin2026` |
| Pro (suscripción simulada) | `pro@pronoscore-demo.example.com` | `Demo#Pro2026` |
| Gratis | `free@pronoscore-demo.example.com` | `Demo#Free2026` |

Los pasos completos (incluido Android en emulador y teléfono físico) están en **[INSTALL_WINDOWS.md](INSTALL_WINDOWS.md)** / **[INSTALL_LINUX.md](INSTALL_LINUX.md)**.
No es "un solo comando": hay pasos manuales (Docker Desktop abierto, Android Studio, claves de API si las quieres).

## Documentación
[ARQUITECTURA](ARQUITECTURA.md) · [SEGURIDAD](SEGURIDAD.md) · [TESTING](TESTING.md) · [CONFIGURACION_API](CONFIGURACION_API.md) · [CONFIGURACION_PAGOS](CONFIGURACION_PAGOS.md) · [CONFIGURACION_GOOGLE_PLAY](CONFIGURACION_GOOGLE_PLAY.md) · [PUBLICACION_WEB](PUBLICACION_WEB.md) · [docs/MODELOS_ESTADISTICOS](docs/MODELOS_ESTADISTICOS.md) · [docs/PENDIENTES_PRODUCCION](docs/PENDIENTES_PRODUCCION.md) · [docs/FICHA_GOOGLE_PLAY](docs/FICHA_GOOGLE_PLAY.md) · [docs/API_EJEMPLOS](docs/API_EJEMPLOS.md)
