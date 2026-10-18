# Pruebas

## Resumen de la última ejecución (en el entorno de generación)
| Suite | Herramienta | Resultado |
|---|---|---|
| Motor `analytics` | pytest | **13 / 13** |
| Backend (PostgreSQL real + migraciones Alembic) | pytest | **89 / 89** |
| Web unitarias/componentes | Vitest + React Testing Library | **29 / 29** |
| Web ↔ backend real (login, calendario, análisis, refresh, 403 admin, flujo UI completo) | Vitest | **5 / 5** |
| Android `:core` (MockWebServer + backend real) | JUnit 4 | **14 / 14** (incluye `LiveBackendTest` contra la API) |
| Prueba de humo E2E | `tests/e2e/smoke.sh` | **OK** |
| Android `:app` (compilación y `UiStateTest`) | Gradle/AGP | **NO EJECUTADO** (sin Android SDK; lo ejecuta el job `android-app` de CI) |
| Docker Compose | — | **NO EJECUTADO** (sin daemon Docker en el entorno) |

## Comandos
```bash
python -m venv .venv && . .venv/bin/activate
pip install -r backend/requirements-dev.txt -e analytics
(cd analytics && python -m pytest -q)
(cd backend  && python -m pytest -q)          # requiere PostgreSQL con BD 'pronoscore_test' (TEST_DATABASE_URL)
(cd web && npm ci && npm run typecheck && npm test)
# integración (backend en marcha en :8000, ENV=local, SEED_DEMO_ON_START=true):
(cd web && npm run test:integration)
(cd android && ./gradlew :core:test -PskipApp=true)   # sólo :core (sin SDK). Completo: ./gradlew :app:assembleDebug :app:testDebugUnitTest :core:test
bash tests/e2e/smoke.sh
bash scripts/test-all.sh                       # todo lo anterior
```
## Cobertura por requisito
* **Registro/login/autorización**: `test_system_and_auth.py`, `test_selections_authz_admin.py` (todas las rutas admin ×3 estados).
* **Límite de 5 análisis, renovación, acceso Pro, suscripciones**: `test_quota_and_predictions.py` (renovación a medianoche de Tokio con reloj controlado), `test_billing.py` (simulador, Google verify/restore, RTDN: cancelación, retención, recuperación, reembolso, duplicados, token desconocido).
* **Cálculos fútbol/NBA/marcadores/conjuntas**: `analytics/tests/test_analytics.py` (coherencia de mercados, sin fuga temporal, honestidad del marcador NBA, backtest ≪ azar, conjunta ≠ producto), más coherencia vía API.
* **Caché, errores del proveedor**: `test_providers_cache_sync.py` (presupuesto/reserva, reintentos, 429, errores 200, red, caché Redis vía fakeredis).
* **Persistencia PostgreSQL**: migraciones up/down/check, restricciones `CHECK/UNIQUE`, sesiones independientes.
* **Pantallas principales**: `web/src/test/pages.test.tsx` (login, registro, guardas, detalle con bloqueos, «datos insuficientes», 429, planes CLP/USD, selecciones no calculables).
* **Integración frontend/backend**: `web/src/test/integration.test.tsx`, `android/.../LiveBackendTest.kt`.

## No cubierto todavía
Pruebas instrumentadas/UI de Android, E2E de navegador (Playwright) automatizado en CI (sólo capturas manuales en `docs/screenshots`), pruebas de carga, pruebas contra API-SPORTS/Google/FCM reales, accesibilidad auditada (sólo controles básicos: etiquetas, roles, contraste del tema).
