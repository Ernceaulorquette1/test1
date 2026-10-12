# Ejemplos de uso de la API

Documentación interactiva: `http://localhost:8000/docs` (Swagger) · `/redoc` · `/openapi.json` (83 operaciones en 76 rutas). Todas las rutas bajo `/api/v1`. Errores uniformes: `{"error": {"code", "message", "request_id", "details"?}}`.
(Las respuestas siguientes provienen de una ejecución real en modo DEMO; abreviadas.)

```bash
API=http://localhost:8000/api/v1
# 1) Login
curl -s -X POST $API/auth/login -H 'content-type: application/json' \
  -d '{"email":"free@pronoscore-demo.example.com","password":"Demo#Free2026"}'
# -> {"user":{...,"plan":{"code":"FREE","daily_analysis_limit":5,...},"usage":{"limit":5,"used":0,"remaining":5,"resets_at":"..."}},
#     "tokens":{"access_token":"...","refresh_token":"...","expires_in":900}}
TOKEN=...   # access_token

# 2) Calendario filtrado, con hora local
curl -s "$API/fixtures?sport=football&status=scheduled&date=2026-10-12&tz=America/Santiago" -H "Authorization: Bearer $TOKEN"

# 3) Análisis (descuenta UNA consulta por partido y día)
curl -s $API/fixtures/326/analysis -H "Authorization: Bearer $TOKEN"
```
```json
{ "fixture": {"id": 326, "sport": "football", "status": "scheduled", "is_demo": true},
  "analysis": {"status": "ok", "quality": "high", "n_home": 68, "n_away": 62, "is_pre_match": true, "model": "football_poisson_dc", "model_version": "1.0.0"},
  "predictions": [
    {"market": "1X2", "selection": "home", "key": "1X2:home", "probability": 0.5768, "category": "medium_probability", "uncertainty": "medium", "data_points": 62},
    {"market": "1X2", "selection": "draw", "probability": 0.2487, "category": "high_uncertainty"},
    {"market": "BTTS", "selection": "yes", "probability": 0.4944, "category": "high_uncertainty"}],
  "locked_groups": ["goals","corners","cards"],           // plan Gratis
  "exact_score": {"expected_goals": {"home": 1.7, "away": 1.0}, "most_likely": {"home": 1, "away": 1, "probability": 0.1}, "top_scores": [ /* 1 en Gratis, 5 en Pro */ ]},
  "usage": {"limit": 5, "used": 1, "remaining": 4} }
```
```bash
# 4) Límite alcanzado -> 429
{"error":{"code":"quota_exceeded","message":"Alcanzaste el límite de 5 partidos analizados hoy ... Se renueva a las 00:00 (zona America/Santiago)...","details":{"usage":{...}}}}

# 5) Función Pro con cuenta Gratis -> 402
curl -s $API/predictions -H "Authorization: Bearer $TOKEN"     # {"error":{"code":"plan_required",...}}

# 6) Pro: ranking con filtros (prob. entre 60 % y 95 %; no se fuerzan resultados)
curl -s "$API/predictions?sport=basketball&market=ML&min_probability=0.65&uncertainty=low&min_matches=15" -H "Authorization: Bearer $PRO"

# 7) Pro: líneas NBA personalizadas
curl -s "$API/nba/games/575/lines?total=215.5&home_total=108.5&spread=-4.5" -H "Authorization: Bearer $PRO"

# 8) Probabilidad conjunta (mismo partido: correlacionados, NO se multiplican)
curl -s -X POST $API/selections/evaluate -H "Authorization: Bearer $PRO" -H 'content-type: application/json' \
  -d '{"items":[{"fixture_id":326,"key":"1X2:home"},{"fixture_id":326,"key":"OU:2.5:over"}]}'
# córners + goles del mismo partido -> {"joint":{"computable":false,"reason":"No se puede calcular con precisión la probabilidad total..."}}

# 9) Renovar sesión / salir
curl -s -X POST $API/auth/refresh -H 'content-type: application/json' -d '{"refresh_token":"..."}'
curl -s -X POST $API/auth/logout  -H 'content-type: application/json' -d '{"refresh_token":"..."}'

# 10) Android: verificación de compra (el servidor consulta a Google Play)
curl -s -X POST $API/subscriptions/google/verify -H "Authorization: Bearer $TOKEN" -H 'content-type: application/json' -d '{"purchase_token":"..."}'
```
**Claves de selección:** `1X2:home|draw|away`, `DC:1X|12|X2`, `OU:2.5:over|under`, `BTTS:yes|no`, `CORNERS_TOTAL:9.5:over`, `CARDS_TOTAL:3.5:under`, `ML:home|away`, `TOTAL:221.5:over`, `TEAM_TOTAL:home:110.5:over`, `SPREAD:home:-3.5`.
**Paginación:** `?page=1&page_size=30` → `{items,total,page,page_size}`.
