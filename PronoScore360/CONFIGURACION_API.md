# Configuración de proveedores deportivos (API-SPORTS)

Proveedores preparados: **API-Football** (`https://v3.football.api-sports.io`) y **API-NBA** (`https://v2.nba.api-sports.io`). Panel: https://dashboard.api-sports.io/

## ⚠ Qué está verificado y qué no
* **No se pudo consultar la documentación en vivo** de API-SPORTS desde el entorno de desarrollo (red restringida) y **no hay claves**. Los clientes están escritos con los endpoints/parámetros de la v3 (football) y v2 (NBA) que se conocían, **sin invención de endpoints adicionales**, y probados con respuestas HTTP simuladas (`backend/tests/test_providers_cache_sync.py`) cuyos *payloads fueron reconstruidos de memoria*, no capturados.
* **Antes de producción**, con tu clave: (1) contrasta cada endpoint de la tabla con la documentación oficial, (2) ejecuta una sincronización con una liga y revisa `Admin → Proveedores API`, (3) ajusta `parse_*` en `backend/app/services/providers/apifootball.py` / `apinba.py` si algún campo difiere.
* **Lesiones NBA:** no implementadas (no se confirmó un endpoint). Fútbol: `injuries?fixture=` implementado pero no se sincroniza automáticamente en esta versión.
* **Alineaciones:** interfaz definida (`lineups`), sin implementación ni pantalla.

## Endpoints usados
| Uso | API-Football v3 | API-NBA v2 |
|---|---|---|
| Calendario/resultados por liga y temporada | `GET /fixtures?league=&season=&timezone=UTC` | `GET /games?league=standard&season=` |
| Partidos de un día | `GET /fixtures?date=` | `GET /games?date=` |
| Clasificación | `GET /standings?league=&season=` | `GET /standings?league=standard&season=` |
| Estadísticas por partido (córners, tarjetas, posesión / tiros, rebotes, pérdidas) | `GET /fixtures/statistics?fixture=` | `GET /games/statistics?id=` |
| Ligas | `GET /leagues?current=true` | (fija: NBA = `standard`) |
| Lesiones | `GET /injuries?fixture=` | no implementado |
| H2H | `GET /fixtures/headtohead?h2h=a-b` | — |
Autenticación: cabecera `x-apisports-key` (sólo desde el backend).

## Activar el modo REAL
1. `.env`: `PROVIDER_MODE=real`, `APIFOOTBALL_KEY=...`, `APINBA_KEY=...`, `PROVIDER_DAILY_BUDGET=<tu límite diario>` (plan gratuito típico: 100/día; confírmalo en tu panel).
2. Elige ligas: `Admin → Ajustes → tracked_leagues` (o SQL):
   ```json
   [{"sport":"football","external_id":"39","season":2025},{"sport":"basketball","external_id":"standard","season":2025}]
   ```
   (`external_id` = id de liga en API-SPORTS; el id de la Premier League en API-Football es 39 según su documentación — verifícalo.)
3. Reinicia la API y el *worker* (`SCHEDULER_ENABLED=true` en un solo proceso). Ejecuta manualmente: `Admin → Tareas → sync_fixtures:football`, `sync_standings:football`, `sync_fixture_stats:football`.
4. Los datos DEMO (`is_demo=true`) conviven con los reales; en producción no se crean (`seed_demo` está bloqueado fuera de `local/test`).
5. **Los modelos necesitan historial:** al menos 5 partidos previos por equipo (idealmente ≥ 20). Con 1 petición por liga/temporada se descarga la temporada completa; las estadísticas por partido (córners/tarjetas/posesiones) cuestan **1 petición por partido** → `sync_fixture_stats` procesa 20 partidos por ejecución para respetar el presupuesto.

## Control de consumo
* `ProviderHttpClient` cuenta cada petición real en caché (Redis) por proveedor y día UTC; supera `PROVIDER_DAILY_BUDGET − PROVIDER_RESERVE` → las sincronizaciones se pausan (`BudgetExceeded`); la reserva queda para peticiones interactivas.
* Caché por endpoint+parámetros (TTL 10-60 min según recurso; 1 día ligas). Un acierto de caché no consume presupuesto.
* Reintentos con *backoff* exponencial (429/5xx/errores de red), errores de negocio (HTTP 200 con `errors`) tratados como fallo.
* Cada llamada queda en `provider_requests` (visible en Admin). El contador real del proveedor (`x-ratelimit-requests-remaining`) se registra.
* Frecuencias configurables: `app_settings.update_frequency_minutes` y `FREQ` del scheduler (`backend/app/jobs/scheduler.py`).

## Planes de pago / datos que pueden faltar
Depende de tu plan de API-SPORTS (no verificado): temporadas históricas disponibles, límite diario, `fixtures/statistics` (puede no existir para ligas menores → los mercados de córners/tarjetas devuelven *sin datos* y **no se publican**), lesiones/alineaciones, cuotas de actualización. El motor degrada con honestidad: sin datos de córners → no hay mercado de córners; sin goles al descanso → no hay `HT_1X2`.

## Derechos de uso comercial (obligatorio verificar)
Antes de mostrar datos, **escudos/logos** y nombres de ligas en producción, revisa los *Términos de API-SPORTS* y la licencia de imágenes de equipos/ligas: pueden exigir plan específico, atribución o prohibir redistribución. La app **no incluye logos** (usa iniciales); si usas `logo_url` del proveedor, valida su licencia. Pendiente (docs/PENDIENTES_PRODUCCION.md).

## Añadir otro proveedor
Implementa `SportsProvider` (`backend/app/services/providers/base.py`) y regístralo en `factory.py`. Las apps Android/web no cambian: sólo consumen `/api/v1`.
