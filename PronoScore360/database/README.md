# Base de datos (PostgreSQL 16)

* **Fuente de verdad:** modelos SQLAlchemy en `backend/app/db/models.py` + migraciones Alembic en `backend/alembic/versions/`.
* `schema.sql`: SQL de la migración `0001` generado con `alembic upgrade head --sql` (referencia / revisión por DBAs; no es necesario ejecutarlo a mano).
* `ER.mmd`: diagrama entidad-relación (Mermaid) generado desde los modelos (`docs/` lo incrusta).

## Entidades (40 tablas)
Solicitadas (34): `users, roles, user_profiles, plans, subscriptions, payment_transactions, billing_events, daily_usage, sports, countries, leagues, seasons, teams, players, fixtures, fixture_statistics, team_statistics, standings, historical_results, injuries, prediction_models, model_versions, predictions, prediction_outcomes, prediction_evaluations, saved_predictions, favorites, selection_groups, selection_items, notifications, provider_requests, provider_sync_jobs, app_settings, audit_logs`.

Auxiliares añadidas (6; 40 tablas en total): `refresh_tokens` (sesiones con rotación), `one_time_tokens` (reset de contraseña / verificación de correo, sólo hash), `fixture_analyses` (análisis almacenado, base de la regla «mismo resultado = misma consulta» y de la separación pre-partido/post-partido), `device_tokens` (FCM), `announcements` (avisos admin), `error_logs`.

## Decisiones de diseño
* **Cuotas:** `daily_usage` tiene `UNIQUE(user_id, usage_date, fixture_id)`; `usage_date` se calcula en el servidor con la zona horaria de la cuenta.
* **Sin fuga de información:** `historical_results.played_at` + `predictions.is_pre_match`; sólo las predicciones previas entran en `prediction_outcomes/evaluations`.
* **Restricciones:** `CHECK probability BETWEEN 0 AND 1`, `CHECK home_team_id <> away_team_id`, claves únicas por proveedor (`provider, external_id`), idempotencia de eventos de pago (`billing_events UNIQUE(provider,event_id)`).
* **Planes configurables:** `plans` (límites, precios por país en JSON, grupos de mercados permitidos, funciones) editable desde el panel admin sin redeploy.
* **Borrado de cuenta:** `ON DELETE CASCADE` desde `users` hacia perfil, sesiones, favoritos, selecciones, uso y notificaciones; `audit_logs` y `payment_transactions` conservan filas con `user_id NULL` (obligaciones contables/seguridad).

## Migraciones
```bash
cd backend
alembic upgrade head          # aplicar
alembic downgrade base        # revertir todo (probado)
alembic revision --autogenerate -m "cambio"   # nueva migración tras editar models.py
alembic check                 # verifica que modelos y migraciones coinciden
```
## Datos de demostración
`backend/app/seed/demo.py` (sólo `ENV=local|test`): liga y equipos **ficticios** marcados `[DEMO]`, historial simulado con semilla fija, predicciones «previas» generadas con datos anteriores a cada partido y usuarios de prueba. Se regeneran con fechas actuales desde Admin → Tareas → «Regenerar datos DEMO».
