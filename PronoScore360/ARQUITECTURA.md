# Arquitectura

```
 Android (Compose, Hilt, Room, Billing) ─┐
                                         ├─ HTTPS /api/v1 ─► FastAPI ──► PostgreSQL (SQLAlchemy/Alembic)
 Web React (Vite, TanStack Query) ───────┘                    │  ├────► Redis (caché, límites, presupuesto del proveedor)
                                                              │  ├────► PronoScore360 Analytics (paquete Python puro)
                                                              │  └────► Proveedores (API-SPORTS) · Google Play API
 Worker (APScheduler) ── sincroniza, genera análisis previos, liquida, evalúa, expira suscripciones
```
**Regla central:** las apps sólo hablan con nuestra API. Claves de proveedores y de Google Play **sólo existen en el backend**.

## Backend (`backend/app`)
| Módulo | Responsabilidad |
|---|---|
| `core/` | configuración (validaciones de producción), seguridad (Argon2id, JWT), caché Redis/memoria, *rate limit*, errores uniformes, logging JSON |
| `db/models.py` | 40 tablas; `db/session.py` |
| `services/accounts.py` | registro, login (bloqueo por intentos), refresh con rotación y detección de reutilización, reset/verificación, borrado de cuenta, Google Sign-In |
| `services/plans.py` | plan efectivo, **cuota diaria** (por partido y día de cuenta), renovación |
| `services/analysis.py` | entrena con datos **estrictamente anteriores** al partido, guarda análisis y predicciones, marca `is_pre_match` |
| `services/settlement.py` | resuelve predicciones previas contra resultados; agrega Brier/log-loss/acierto por modelo-liga-mercado |
| `services/providers/` | interfaz `SportsProvider`, clientes API-Football/API-NBA, `ProviderHttpClient` (presupuesto, caché, reintentos), proveedor DEMO |
| `services/billing/` | Google Play (subscriptionsv2, RTDN), pasarela web (interfaz), simulador local |
| `jobs/` | tareas programadas y su ejecutor registrado en `provider_sync_jobs` |
| `api/v1/` | routers: auth, me, catálogo, pronósticos, favoritos/selecciones/notificaciones, billing, admin, sistema |
| `seed/` | planes/ajustes (idempotente) y datos DEMO (sólo `local/test`) |

## Reglas de negocio importantes
* **Cuota gratuita:** `daily_usage` (único por usuario-día-partido). Repetir un partido el mismo día no descuenta; los análisis con «datos insuficientes» tampoco. El día se calcula con el reloj del servidor y la zona de la cuenta (`profile.timezone`, cambiable cada 7 días). Los límites viven en `plans` (editable en Admin).
* **Honestidad estadística:** sin ≥5 partidos previos por equipo no hay pronóstico («Datos insuficientes»). Las probabilidades se muestran tal cual las calcula el modelo; los filtros (60-95 %) sólo seleccionan, nunca elevan.
* **Predicciones previas vs. posteriores:** un análisis creado cuando el partido ya empezó/terminó se guarda con `is_pre_match=false`; no entra en liquidación ni métricas.
* **Combinadas:** `analytics.joint` calcula la probabilidad conjunta con la distribución común (matriz de marcador / muestras Monte Carlo) para mercados del mismo partido; entre partidos distintos asume independencia y lo declara; si un mercado no tiene modelo conjunto (córners/tarjetas con goles) responde «no se puede calcular con precisión».
* **Categorías** (`analytics/quality.py`): `insufficient_data` (<5 partidos), `high_uncertainty` (p<55 % o calidad baja), `medium_probability` (55-70 %), `high_probability` (≥70 % con calidad media/alta). Describen respaldo estadístico, no certeza.

## Analytics (`analytics/pronoscore_analytics`)
`football.py` (Poisson + Dixon-Coles, decaimiento temporal, contracción, binomial negativa para córners/tarjetas, 1X2/DC/OU/BTTS/marcadores/HT), `basketball.py` (ORtg/DRtg, ritmo, localía, descanso, Monte Carlo), `metrics.py` (Brier, log-loss, ECE, calibración isotónica), `backtest.py` (walk-forward sin fuga), `joint.py`, `quality.py`. Detalle: [docs/MODELOS_ESTADISTICOS.md](docs/MODELOS_ESTADISTICOS.md).

## Web (`web/src`)
`api/` (Axios con renovación de token *single-flight*), `auth/` (contexto y guardas), `pages/` (public, home/calendarios/equipos, match, lists, account, admin), `components/`, `theme/` (claro/oscuro), `i18n/` (es completo; en/fr/ht parcial).

## Android (`android/`)
* `:core` (JVM puro): modelos kotlinx.serialization, `PronoApi` (Retrofit), `ApiClient` (OkHttp + autenticador que renueva el token), etiquetas y cesta de selecciones. **Compilado y probado.**
* `:app` (Android): Hilt (DI), MVVM (`SessionViewModel`, `CatalogViewModel`, `ListsViewModel`, `PlansViewModel` con `StateFlow`), Navigation Compose, Room (caché sin conexión de calendarios/planes), DataStore (tema), EncryptedSharedPreferences (tokens), `BillingManager`, FCM. **No compilado en el entorno de generación.**

## Decisiones y límites conocidos
* Sincrónico (SQLAlchemy sync + psycopg): simple y suficiente para esta escala; el cálculo pesado se almacena (un análisis por partido).
* El scheduler corre en un proceso `worker` aparte para evitar duplicados con varias réplicas.
* Tokens web en `localStorage` (riesgo XSS, ver SEGURIDAD.md); alternativa: cookies HttpOnly + CSRF (no implementado).
