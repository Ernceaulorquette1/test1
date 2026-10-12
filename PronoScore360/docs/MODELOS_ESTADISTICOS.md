# Modelos estadísticos (PronoScore360 Analytics)

Paquete `analytics/pronoscore_analytics` (sin dependencias de web/BD; sólo numpy/scipy). Versiones en `version.py` (se guardan en `model_versions`; cualquier cambio de fórmula/parámetros debe subir la versión).

## Fútbol — `football.py` v1.0.0
* **Goles:** Poisson bivariado. `λ_local = μ·h·ataque_local·defensa_visitante`, `λ_visit = μ·ataque_visit·defensa_local`. Parámetros por máxima verosimilitud multiplicativa iterativa (60 iteraciones), **ponderación exponencial por recencia** (semivida 180 días), **contracción** hacia 1 (3 goles-pseudo) y ventaja de localía `h` estimada.
* **Dixon-Coles:** corrección `τ` de marcadores bajos (0-0, 1-0, 0-1, 1-1) con `ρ = −0.08` (valor típico fijo; **no se re-estima** todavía — pendiente).
* **Matriz de marcadores** 0-10 × 0-10 renormalizada → 1X2, doble oportunidad, más/menos 0.5…4.5, ambos marcan, top-5 de marcadores exactos, goles esperados.
* **Córners y tarjetas:** media por equipo = promedio (lo que genera él, lo que concede el rival); **binomial negativa** (sobredispersión estimada de los datos de liga), total por convolución. Sólo si hay ≥ 8 partidos con ese dato por equipo; si no, **no se publica el mercado**.
* **Descanso (HT_1X2):** sólo si existen ≥ 30 partidos con goles al descanso (fracción de goles del primer tiempo); en otro caso no se publica.
* **Sin fuga temporal:** `fit(matches, as_of)` ignora cualquier partido con fecha ≥ `as_of`.

## NBA — `basketball.py` v1.0.0
* Posesiones `FGA + 0.44·FTA − OREB + TOV` (si hay box score; si no se usa un ritmo por defecto y se indica «estimado»). ORtg/DRtg por 100 posesiones con decaimiento (semivida 60 días) y contracción.
* Puntos esperados = `ritmo·(ORtg_a + DRtg_b − media_liga)/100 ± localía/2 + ajuste de descanso`. **Localía** estimada con prior (2.6 pts, peso 40 partidos). **Descanso** (back-to-back −2.0, ≥3 días +0.5) son **heurísticas fijas documentadas, no estimadas**.
* **Monte Carlo** (20 000 simulaciones, semilla fija → reproducible): ritmo compartido (correlación positiva entre equipos) + ruido normal por equipo (σ estimada de residuos); empates resueltos como prórroga. De ahí: probabilidad de victoria, margen, total, líneas de totales/por equipo/hándicap, rangos 80 %, distribución. Las líneas personalizadas se recalculan de los parámetros guardados (determinista).
* **Marcador central:** se informa con su probabilidad real de acierto exacto (≈1-2 %), nunca como "marcador probable".
* **Cuartos y jugadores:** no implementados (sin modelo validado ni datos). La arquitectura admite añadirlos como nuevos mercados.

## Calidad estadística
* `metrics.py`: Brier, log-loss, ECE, tasa de acierto por umbral, **calibración isotónica (PAV)**. La calibración está **disponible pero no se aplica automáticamente** a las predicciones publicadas (pendiente: aplicarla cuando haya ≥ N predicciones resueltas por mercado).
* `backtest.py`: **walk-forward** (reentrena cada 3-7 días sólo con datos anteriores) → por mercado: Brier, log-loss, ECE, acierto con p≥60 %, tasa base; Brier multiclase 1X2; MAE del total (NBA). Disponible desde Admin → Modelos.
* `PredictionEvaluation` (por job): sobre predicciones **previas ya resueltas**, por modelo/deporte/liga/mercado y corte p≥0.60.
* **Importante:** con los datos DEMO (sintéticos, ≈100 partidos de test) el backtest muestra Brier multiclase 1X2 ≈ 0.641 (azar uniforme = 0.667) y NBA ganador Brier ≈ 0.245 (azar = 0.25): *una ventaja pequeña, como corresponde a datos aleatorios con poca señal*. **Esto no demuestra precisión real**; la calidad real sólo se conoce con datos reales y cientos de partidos por liga.
* Las **categorías** y la regla «<5 partidos ⇒ Datos insuficientes» están documentadas en `ARQUITECTURA.md`.

## Probabilidad conjunta — `joint.py`
Mismo partido: máscara sobre la matriz de marcador (fútbol) o muestras Monte Carlo (NBA) ⇒ conjunta exacta del modelo. Distintos partidos: producto (independencia, declarada). Mercados sin modelo conjunto (córners/tarjetas + goles) ⇒ `computable=false`.

## Límites conocidos
No hay: bajas/lesiones en el modelo, valor de mercado, xG, clima, viajes, plantillas rotadas, modelos de jugadores, ajuste por calendario europeo. Ligas con pocos partidos o recién creadas darán «Datos insuficientes» o incertidumbre alta. Parámetros fijos (ρ, semividas, contracción, home-advantage prior) deberían optimizarse por liga con backtesting sobre datos reales.
