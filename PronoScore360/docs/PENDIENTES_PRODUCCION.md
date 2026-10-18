# Pendientes antes de producción (lista de verificación)

## Bloqueantes
- [ ] **Compilar `:app` Android** en Android Studio / CI y corregir errores (el código se escribió sin compilador Android disponible).
- [ ] **Credenciales API-SPORTS**, contrastar endpoints con la documentación oficial y **derechos de uso comercial** de datos y logos (CONFIGURACION_API.md).
- [ ] **Pasarela de pago web** (elegir proveedor, implementar `PaymentGateway` + webhook firmado) o decidir lanzar sólo Android.
- [ ] **Google Play:** cuenta de desarrollador, producto `pronoscore360_pro_monthly`, cuenta de servicio, RTDN (Pub/Sub), pruebas de compra reales (alta, renovación, cancelación, gracia, retención, reembolso).
- [ ] **Revisión legal** de política de privacidad y términos (son borradores), aviso de juego responsable, cumplimiento local (Chile Ley 19.628 / 21.719, GDPR si hay usuarios UE) y reglas de contenido relacionado con apuestas/pronósticos de cada tienda.
- [ ] Probar `docker compose` (dev y prod) en un equipo con Docker (no se pudo en el entorno de generación).
- [ ] Reemplazar correos/dominios de ejemplo (`*.example`, `soporte@pronoscore360.example`).
- [ ] Definir `JWT_SECRET`, contraseñas de BD y Redis; desactivar cualquier cuenta demo (se desactivan solas fuera de local/test).

## Importantes
- [ ] Envío **push FCM** desde el servidor (hoy sólo se guardan tokens; las alertas son notificaciones in-app).
- [ ] Aplicar **calibración isotónica** a predicciones publicadas cuando haya historial real; re-estimar ρ de Dixon-Coles; optimizar hiperparámetros por liga.
- [ ] Sincronizar lesiones/alineaciones (fútbol) y mostrar en detalle del partido; lesiones NBA si el proveedor/plan las ofrece.
- [ ] Verificación de correo obligatoria opcional, 2FA para administradores, CAPTCHA en registro, política de contraseñas filtrada (HIBP).
- [ ] Tokens web: evaluar cookies HttpOnly + CSRF y CSP estricta.
- [ ] Observabilidad: Sentry/OpenTelemetry, métricas Prometheus, alertas (errores, presupuesto de API, jobs fallidos), `error_logs` hoy sólo se llena manualmente.
- [ ] Copias de seguridad automáticas de PostgreSQL + prueba de restauración; política de retención de `provider_requests`/`audit_logs` (hay limpieza de `provider_requests` a 30 días).
- [ ] Pruebas: instrumentadas Android, E2E de navegador en CI, carga/estrés, accesibilidad auditada (TalkBack, contraste WCAG AA), penetración.
- [ ] Imágenes para compartir selecciones (hoy: texto); ícono y capturas finales de la ficha.
- [ ] Traducciones completas en/fr/ht (hoy parciales) y localización de fechas/monedas.
- [ ] Panel admin: edición de planes avanzada (grupos/funciones), gestión de ligas rastreadas con UI, exportaciones.
- [ ] Rendimiento: paginar/virtualizar listas largas del detalle NBA; índice/consulta de `predictions` para listados con filtros a gran escala.
- [ ] Mitigación de abuso de cuotas por múltiples cuentas (límite por dispositivo/IP, verificación de correo, detección).

## Recomendado
- [ ] Revisión periódica de `pip-audit`/`npm audit`/Dependabot; fijar versiones con *lock* de Python (pip-tools/uv).
- [ ] Plan de soporte (correo, tiempos), página de estado, términos de reembolso.
- [ ] Modelos de jugadores (puntos/rebotes/asistencias/triples) y cuartos NBA sólo tras validar con backtesting.
