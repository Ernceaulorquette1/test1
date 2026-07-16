# API REST — NutriScan AI
Base: `https://api.nutriscan.ai` · Documentación interactiva: `/swagger-ui.html` (OpenAPI 3 generada con springdoc)

Autenticación: `Authorization: Bearer <accessToken>` en todos los endpoints salvo `/auth/**`.

| Método | Endpoint | Descripción | Códigos |
|---|---|---|---|
| POST | `/api/v1/auth/login` | Canjea Firebase ID token por JWT propio | 200, 401 |
| POST | `/api/v1/auth/refresh` | Renueva el access token | 200, 401 |
| GET | `/api/v1/profile` | Perfil del usuario (incluye IMC y premium) | 200 |
| PUT | `/api/v1/profile` | Actualiza perfil; recalcula calorías objetivo | 200, 400 |
| POST | `/api/v1/meals/analyze` | Analiza foto (multipart `image`) con IA | 200, 402 cuota, 401 |
| POST | `/api/v1/meals` | Guarda comida con items | 200, 400 |
| GET | `/api/v1/meals/history?date=YYYY-MM-DD` | Historial del día con totales | 200 |
| DELETE | `/api/v1/meals/{id}` | Elimina una comida propia | 200, 404 |
| GET | `/api/v1/stats?range=week\|month\|year` | Series de calorías/macros y peso, IMC, promedio | 200 |
| POST | `/api/v1/stats/weight` | Registra el peso de hoy | 200 |
| GET | `/api/v1/water/today` | Consumo de agua del día y meta | 200 |
| POST | `/api/v1/water` | Agrega ml de agua | 200, 400 |
| POST | `/api/v1/chat` | Mensaje al asistente nutricional IA | 200 |
| GET | `/api/v1/chat/history` | Historial de conversación | 200 |
| GET | `/api/v1/barcode/{code}` | Nutrición por código de barras (por 100 g) | 200, 404 |
| POST | `/api/v1/subscriptions/verify` | Verifica compra Play Billing y activa Premium | 200, 400 |
| GET | `/api/v1/subscriptions/status` | Estado de la suscripción | 200 |

## Ejemplo — análisis de comida

```bash
curl -X POST https://api.nutriscan.ai/api/v1/meals/analyze \
  -H "Authorization: Bearer $TOKEN" \
  -F "image=@plato.jpg"
```

```json
{
  "dishName": "Pollo a la plancha con arroz y ensalada",
  "portion": "1 plato grande, ~380 g",
  "confidence": 0.92,
  "total": {"calories": 650, "proteinG": 45, "carbsG": 65, "fatG": 18,
            "fiberG": 6, "sodiumMg": 480, "sugarG": 4},
  "items": [
    {"name": "Pechuga de pollo", "quantity": "150 g",
     "nutrition": {"calories": 248, "proteinG": 46, "carbsG": 0, "fatG": 5,
                   "fiberG": 0, "sodiumMg": 110, "sugarG": 0}}
  ],
  "remainingFreeScans": 2
}
```

## Formato de error

```json
{"status": 402, "error": "Payment Required",
 "message": "Límite diario de análisis alcanzado. Hazte Premium para análisis ilimitados.",
 "timestamp": "2026-07-15T12:00:00Z"}
```
