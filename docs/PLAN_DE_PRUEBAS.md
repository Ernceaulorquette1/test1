# Plan y Casos de Prueba — NutriScan AI

## 1. Estrategia

| Nivel | Alcance | Herramienta | Ejecución |
|---|---|---|---|
| Unitarias backend | Servicios, JWT, parser de visión, calculadora nutricional | JUnit 5 + AssertJ | `mvn test` (CI en cada push) |
| Unitarias mobile | Modelos, mapeos de API | flutter_test | `flutter test` (CI) |
| Integración | Controladores + BD (H2/Testcontainers) | Spring Boot Test | CI |
| E2E manual | Flujos críticos en dispositivo real | Checklist QA | Antes de cada release |
| Rendimiento | p95 < 500 ms, 1000 req/s en API | k6 / Gatling | Pre-producción |
| Seguridad | OWASP Top 10, revisión de dependencias | Análisis estático + revisión | Cada release |

Criterio de salida: 100% de casos críticos en verde, sin defectos de severidad alta abiertos.

## 2. Casos de prueba funcionales

| ID | Caso | Pasos | Resultado esperado |
|---|---|---|---|
| TC-01 | Login con Google | Abrir app → Continuar con Google → cuenta válida | Home visible; JWT almacenado |
| TC-02 | Registro email + perfil | Registrarse → completar perfil | Calorías objetivo calculadas (Mifflin-St Jeor) |
| TC-03 | Recuperar contraseña | Login → "¿Olvidaste tu contraseña?" | Correo de recuperación recibido |
| TC-04 | Análisis de foto válida | Escanear plato de comida | Plato, ingredientes, kcal y macros mostrados |
| TC-05 | Foto sin comida | Escanear objeto no comestible | Mensaje "No se detectó comida"; no se guarda |
| TC-06 | Cuota gratuita agotada | 4º análisis del día sin Premium | HTTP 402; se ofrece Premium |
| TC-07 | Guardar comida | Analizar → elegir "Almuerzo" → guardar | Aparece en historial y dashboard actualiza restantes |
| TC-08 | Eliminar comida | Deslizar comida en historial | Se elimina; totales del día recalculados |
| TC-09 | Historial de otro día | Navegar con flechas | Comidas del día correcto agrupadas por tipo |
| TC-10 | Estadísticas semana/mes | Cambiar rango | Gráficos y promedio coherentes con lo registrado |
| TC-11 | Registrar peso | Estadísticas → Registrar → 72.5 | Punto agregado; perfil actualiza peso e IMC |
| TC-12 | Código de barras válido | Escanear EAN de producto | Nombre, marca y nutrición por 100 g |
| TC-13 | Código inexistente | Escanear código inventado | "Producto no encontrado", sin crash |
| TC-14 | Chat IA | Preguntar "¿Qué puedo desayunar?" | Respuesta en español, personalizada al perfil |
| TC-15 | Agua | Agregar 3 vasos | Total 750 ml; progreso y vasos pintados |
| TC-16 | Compra Premium | Comprar plan anual (sandbox) | `premium=true`; análisis ilimitados |
| TC-17 | Token expirado | Esperar expiración y usar app | Refresh automático transparente; sin logout |
| TC-18 | Sin conexión | Modo avión → abrir historial | Mensaje de error amable; reintento al volver |

## 3. Casos de prueba de seguridad

| ID | Caso | Resultado esperado |
|---|---|---|
| TS-01 | Petición sin JWT a `/api/v1/profile` | 401 |
| TS-02 | JWT manipulado (payload alterado) | 401 (firma inválida) — cubierto por test unitario |
| TS-03 | Refresh token usado como access | 401 — cubierto por test unitario |
| TS-04 | SQLi en parámetros (`' OR 1=1--`) | Sin efecto: JPA parametrizado + validación |
| TS-05 | Acceso a comida de otro usuario por ID | 404 (scoping por user_id) |
| TS-06 | Respuesta de error | Sin stacktrace ni detalles internos |
