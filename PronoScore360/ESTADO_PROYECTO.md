# Estado del proyecto PronoScore360

Fecha de este informe: 2026-10-12 · Versión 1.0.0 (base comercial, **no lista para publicar**).
Entorno de generación: Linux, Python 3.13, Node 22, JDK 21, PostgreSQL 16 local. **Sin** daemon Docker, **sin** Android SDK/AGP, **sin** acceso a documentación/APIs de API-SPORTS, Google ni Firebase, **sin** claves.

## Resumen en una línea
Backend, motor estadístico, web y la capa de red/lógica de Android funcionan y están probados contra PostgreSQL real; **la app Android (UI) y Docker Compose no se pudieron compilar/ejecutar aquí**, y todo lo que depende de credenciales externas está implementado pero sin verificar contra los servicios reales.

---
## 1. FUNCIONAL Y PROBADO (ejecutado en este entorno)
| Módulo | Evidencia |
|---|---|
| Motor de analytics (Poisson/Dixon-Coles, binomial negativa, NBA Monte Carlo, métricas, calibración isotónica, backtest walk-forward, probabilidad conjunta, categorías) | 13 pruebas pytest |
| Backend: registro, login, bloqueo, refresh con rotación/reutilización, logout, reset y verificación de correo, cambio de contraseña, perfil, borrado real de cuenta, roles, rate limit | `backend/tests` (89 pruebas, PostgreSQL real) |
| Cuota gratuita (5 partidos/día, sin doble cobro, renovación por zona horaria del servidor, anti-evasión), planes editables desde Admin | pruebas + humo E2E |
| Pronósticos DEMO calculados por el motor sobre datos ficticios (fútbol: 1X2, DC, 0.5-4.5, BTTS, córners, tarjetas, top-5 marcadores; NBA: ganador, totales, por equipo, hándicap, rangos, líneas personalizadas) | pruebas de coherencia (suma 1, monotonía de líneas, sin fuga temporal) |
| Datos insuficientes, predicciones previas vs. posteriores, liquidación y métricas por modelo/mercado | pruebas |
| Filtros (deporte, liga, fecha, equipo, mercado, probabilidad 60-95 %, partidos, incertidumbre, categoría, calidad), paginación, zonas horarias | pruebas |
| Selecciones/combinadas: conjunta del mismo partido ≠ producto; «no calculable» cuando no hay modelo conjunto; CRUD, compartir texto | pruebas |
| Favoritos (límite por plan), historial por ventana de plan, comparación guardados vs. resultados, notificaciones | pruebas |
| Suscripciones: simulador local (renovar/cancelar/gracia/retención/vencer/reembolsar), expiración por reloj, bloqueo en producción | pruebas |
| Lógica de Google Play: verificación (subscriptionsv2 **simulada**), rechazo de cuenta distinta/token ajeno/estado no activo/producto desconocido, acknowledge, RTDN autenticado/idempotente/ciclo de vida | pruebas con cliente falso — **no contra Google** |
| Capa de proveedores (parseo, endpoints/cabecera, presupuesto+reserva, caché Redis/memoria, reintentos, 429, errores 200, red) y sincronización idempotente | pruebas con HTTP simulado — **no contra API-SPORTS** |
| Migraciones Alembic: subir/bajar/subir y `alembic check` sin diferencias | pruebas + manual |
| Panel admin (backend): dashboard, usuarios, planes, ajustes, proveedores, tareas, modelos/backtest, logs, avisos; permisos 401/403 en cada ruta | pruebas |
| Web (React): build de producción, typecheck, 29 pruebas, 5 de integración con backend real (incl. flujo UI login→partido→análisis), capturas reales, sin errores de consola, sin scroll horizontal en móvil | Vitest + Playwright manual (`docs/screenshots`) |
| Android `:core` (modelos, API Retrofit, autenticador de renovación de token, errores, etiquetas, cesta) | 14 pruebas JUnit, incluida una contra el backend real |
| Worker de tareas programadas (arranca y registra jobs) | ejecutado manualmente |
| Scripts de humo E2E (`tests/e2e/smoke.sh`) | OK |

## 2. IMPLEMENTADO, PENDIENTE DE CONFIGURACIÓN
* **Modo REAL de proveedores** (`PROVIDER_MODE=real` + ligas en `tracked_leagues`): código listo; requiere claves y ajustar presupuesto (CONFIGURACION_API.md).
* **Inicio de sesión con Google** (`GOOGLE_CLIENT_ID`; verificación vía `tokeninfo` de Google; probado con simulación). Falta el botón en las apps.
* **SMTP** para correos de reset/verificación (sin SMTP se registran en el log, sólo local).
* **Redis** (obligatorio en producción: caché, límites, presupuesto).
* **Firebase FCM** en Android (necesita `google-services.json`); el servidor guarda tokens pero **no envía push** todavía.
* **Producción:** `docker-compose.prod.yml` + Caddy (HTTPS) listos, **sin probar** (no hay Docker aquí).

## 3. PENDIENTE DE CREDENCIALES EXTERNAS (no verificable aún)
* Datos reales API-Football/API-NBA (los payloads de las pruebas son reconstruidos; confirmar endpoints/campos con la documentación y tu plan).
* Google Play Developer API (`subscriptionsv2`, `acknowledge`), RTDN con Pub/Sub, productos y precios regionales (CL 9.990 CLP, US 9,99 USD).
* Pasarela de pago web (no elegida; interfaz y 501 en producción).
* Firebase, Google Sign-In con cliente OAuth real.

## 4. PENDIENTE DE VALIDACIÓN O PUBLICACIÓN
* **App Android `:app`: código completo (Compose, Hilt, Room, DataStore, Billing, FCM, 20+ pantallas) escrito SIN compilador Android disponible → NO está compilada ni ejecutada. Espera errores de compilación al primer *sync*.** El job de CI `android-app` y `scripts/build-android.*` la compilan en cuanto haya SDK.
* Docker Compose (dev y prod): no ejecutado.
* Revisión legal (privacidad/términos son borradores), derechos de datos/logos, políticas de Google Play/IARC (docs/FICHA_GOOGLE_PLAY.md), capturas e ícono PNG finales.
* Calidad estadística real: con datos DEMO sólo se demuestra que el motor funciona y no tiene fuga temporal (ver MODELOS_ESTADISTICOS.md); **no hay evidencia de precisión con datos reales**.
* Pruebas de seguridad independientes, carga, accesibilidad auditada. Lista completa: `docs/PENDIENTES_PRODUCCION.md`.

---
## Criterios de entrega del encargo (1-18)
| # | Criterio | Estado |
|---|---|---|
| 1 | Backend inicia | ✅ probado (uvicorn + worker) |
| 2 | PostgreSQL + migraciones | ✅ probado (PG 16 local) |
| 3 | Web se abre | ✅ build + capturas + integración |
| 4 | **Android compila** | ❌ `:core` sí; **`:app` NO verificado** |
| 5 | Registro y login | ✅ |
| 6 | Gratis = 5 análisis diarios | ✅ |
| 7 | Pro demo con acceso premium | ✅ |
| 8 | Partidos en modo DEMO | ✅ (≈54 próximos y ≈620 resultados ficticios; cambian con cada regeneración) |
| 9 | Pronósticos con cálculos reales sobre datos DEMO | ✅ |
| 10 | Marcadores en ambas apps | ✅ web probada · ⚠ Android sin compilar |
| 11 | Historial se guarda | ✅ |
| 12 | Panel administrativo | ✅ |
| 13 | Filtros | ✅ |
| 14 | Tests principales pasan | ✅ 150 automáticas ejecutadas (13+89+29+5+14) · ⚠ Android `:app` y Docker sin ejecutar |
| 15 | APIs reales configurables por claves | ✅ implementado · ⏳ sin verificar |
| 16 | Pagos reales preparados | ✅ preparado · ⏳ sin verificar (web: pendiente de elegir pasarela) |
| 17 | Documentación de instalación | ✅ |
| 18 | Lista de pendientes de producción | ✅ `docs/PENDIENTES_PRODUCCION.md` |

## Cobertura de pantallas (28 solicitadas)
Web: las 28 existen; Marcadores, Estadísticas y Comparación son pestañas/secciones (en detalle de partido e Historial) y «Plan gratuito/Pro» comparten la pantalla Planes. Android: todas salvo **Panel administrativo (sólo web, por decisión)**; sin compilar.

## Cómo ejecutar, entrar como admin, activar APIs y compilar
* **Ejecutar:** INSTALL_WINDOWS.md / INSTALL_LINUX.md (`docker compose up -d --build`, o `scripts/dev-local.sh` sin Docker).
* **Admin (sólo local):** `admin@pronoscore-demo.example.com` / `Demo#Admin2026` (botón «Admin» en el login). En producción: registra una cuenta y promuévela con SQL (PUBLICACION_WEB.md).
* **Activar APIs:** CONFIGURACION_API.md (`PROVIDER_MODE=real`, claves, `tracked_leagues`).
* **APK:** `bash scripts/build-android.sh debug` (o Android Studio ▶). **AAB:** keystore + `bash scripts/build-android.sh release` (CONFIGURACION_GOOGLE_PLAY.md §4).

## Cómo reproducir las verificaciones
Comandos exactos en TESTING.md. Reproducible con `bash scripts/test-all.sh` (necesita PostgreSQL local y backend en :8000 para la parte de integración).
