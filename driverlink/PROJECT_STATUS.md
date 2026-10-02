# PROJECT_STATUS — Estado por módulo

Leyenda:
- **FUNCIONA** — implementado y operativo (en demo y en prod con Firebase configurado).
- **REQUIERE CONFIGURACIÓN** — el código existe; necesita credenciales, consola o aprobación externa.
- **USA MOCK** — en el flavor `demo` se usa una implementación en memoria / simulada.
- **PENDIENTE** — no implementado aún (arquitectura e interfaces preparadas).

## Verificación realizada

| Verificación | Resultado |
|---|---|
| Dominio (validaciones, políticas, casos de uso): 36 pruebas JVM | ✅ en verde |
| ViewModels (Login, Registro) | incluidos en `testDemoDebugUnitTest` (CI) |
| Reglas Firestore + Storage: 12 pruebas en emulador | ✅ en verde |
| Cloud Functions: `tsc --strict` + pruebas | ✅ en verde |
| Compilación Android (demo, prod, androidTest) y lint | CI `.github/workflows/driverlink.yml` |
| Prueba manual en dispositivo | No realizada en este entorno: hacer el recorrido de SETUP.md §4 |

## Módulos

| Módulo | Estado | Notas |
|---|---|---|
| Splash / Welcome / navegación por sesión | FUNCIONA | Grafos Auth / Main / Company; back stack limpio al cambiar de sesión. |
| Registro manual (nombre, apellido, correo, teléfono, contraseña, ciudad, comuna, plataformas, foto opcional) | FUNCIONA | Validaciones en dominio; perfil creado como DRIVER/FREE/UNVERIFIED (exigido por reglas). |
| Login / logout / recuperar contraseña | FUNCIONA · USA MOCK en demo | Demo: cuentas fijas en memoria. |
| "Continuar con plataforma compatible" (OAuth) | PENDIENTE | Interfaz `ExternalAuthProvider` lista; no existe integración oficial autorizada, la app lo informa sin simularla. |
| Verificación de conductor (estados UNVERIFIED…SUSPENDED) | FUNCIONA · REQUIERE CONFIGURACIÓN | Envío de evidencia privada a Storage; revisión por moderador con la Function `reviewVerification` (panel web pendiente). |
| Perfil, edición, foto, plan, verificación, fecha de ingreso | FUNCIONA | |
| Reputación | FUNCIONA (básica) | Contadores `alertsCreated`/`alertsConfirmed` solo en servidor; puntaje avanzado PENDIENTE. |
| Eliminar cuenta | FUNCIONA · REQUIERE CONFIGURACIÓN | Function `deleteAccount` (prod). |
| Home (saludo, ciudad, alertas activas, accesos rápidos, SOS, alertas recientes, canales) | FUNCIONA | |
| Canales comunitarios por región/ciudad/zona | FUNCIONA | Son datos (`channels`); `seedBaseData` / `upsertChannel` para crearlos. |
| Chat (texto, hora, nombre, foto, estado de envío, responder, eliminar propio, denunciar, bloquear, paginación) | FUNCIONA | 30 mensajes por página; offline con caché de Firestore. Imágenes: tipo `IMAGE_FUTURE` reservado (PENDIENTE). |
| Radio walkie-talkie (mantener para hablar, tiempo, cancelar, enviar, reproducir/pausar) | FUNCIONA · USA MOCK en demo | Prod sube a Storage (AAC comprimido). Demo guarda local y trae audios de tono generados. Límite configurable (30 s). |
| Canales de radio PUBLIC/COMMUNITY/PRIVATE_COMPANY/EMERGENCY | FUNCIONA | Gestión por admin vía Function. |
| Mapa (ubicación propia con permiso, marcadores por categoría, tarjeta con votos) | FUNCIONA · REQUIERE CONFIGURACIÓN | Necesita `MAPS_API_KEY`; sin clave muestra lista. Proveedor aislado en `ui/map/AlertMap.kt`. |
| Crear alerta (categoría → descripción → ubicación → confirmar → publicar) | FUNCIONA | En demo, emuladores sin GPS usan el centro de Santiago. |
| Confirmaciones (Confirmo / Ya terminó / Incorrecta), un voto por usuario | FUNCIONA | Contadores y auto-resolución en servidor (`onAlertVoteCreated`). |
| Expiración configurable de alertas | FUNCIONA | `alertExpirationMinutes` + `expireAlerts` cada 15 min. |
| Notificación de alertas por zona | FUNCIONA · REQUIERE CONFIGURACIÓN | Tema FCM `alerts_<ciudad>`. Demo: notificación local simulada cada 3 min. |
| Plan Comunidad gratis / DriverLink Pro / Trial / Premium gate | FUNCIONA | Estado emitido por servidor; trial una vez por cuenta (`startTrial`). Precio desde configuración. |
| Cobro de DriverLink Pro (Google Play Billing) | PENDIENTE · REQUIERE CONFIGURACIÓN | Servidor listo (`verifyPlayPurchase`); falta integrar la librería Play Billing en `BillingGateway` y crear el producto `driverlink_pro_monthly` en Play Console. Hoy el botón informa "aún no habilitada". |
| Expiración de suscripciones | FUNCIONA | Cliente trata vencidos como EXPIRED; job diario `expireSubscriptions`. |
| SOS (mantener 3 s, tipo, confirmación, ubicación, estados, cancelar, servicios oficiales) | FUNCIONA · USA MOCK en demo | Prod: Functions `createSosEvent`/`cancelSosEvent`. Demo: un "operador" simulado avanza el estado (5 s, 15 s, 105 s). |
| Ubicación durante SOS | FUNCIONA | Servicio en primer plano solo mientras el SOS está activo. |
| Contactos de emergencia (Premium) | FUNCIONA | Reglas exigen Premium vigente. Acción: marcar al contacto. |
| Panel SOS para operadores | PENDIENTE (backend listo) | `SosOperatorRepository` + Function `updateSosEvent` (aceptar, estado, notas). Falta el panel web. |
| Notificaciones FCM y preferencias por categoría | FUNCIONA · REQUIERE CONFIGURACIÓN | Prod requiere Firebase; demo usa notificaciones locales. |
| Privacidad y usuarios bloqueados | FUNCIONA | |
| Denuncias (usuario, mensaje, audio, alerta) y auto-ocultamiento | FUNCIONA | UI de denuncia para mensajes y alertas; ocultamiento por umbral en servidor. Revisión de denuncias: panel web PENDIENTE. |
| Administración (roles, suspender/reactivar, verificaciones, canales, configuración) | FUNCIONA (backend) · PENDIENTE (UI web) | Functions `setUserRole`, `setAccountSuspended`, `reviewVerification`, `upsertChannel`, `seedBaseData`. Métricas: PENDIENTE. |
| Remote Config | FUNCIONA · REQUIERE CONFIGURACIÓN | Valores por defecto empaquetados. |
| Analytics / Crashlytics | FUNCIONA · REQUIERE CONFIGURACIÓN | Demo: Logcat. Eventos sin datos sensibles. |
| Sin conexión | FUNCIONA | Banner, caché Firestore, mensajes pendientes, fallos explícitos en acciones críticas. |
| Crear empresa (RUT validado) | FUNCIONA | Prod: Function `createCompany` (empresa queda `PENDING_REVIEW` para revisión comercial). |
| Invitación por código DL-XXX-0000 y solicitud de ingreso | FUNCIONA | Conocer el código no da acceso: requiere aprobación. Enlace de invitación: PENDIENTE. |
| Aprobar/rechazar conductores, ver miembros | FUNCIONA | Por rol (OWNER/ADMIN aprueban). |
| Vehículos y asignación a conductor (con historial) | FUNCIONA | |
| Canales privados de empresa (chat y radio) | FUNCIONA | Canales por defecto al crear la empresa. Crear canales extra desde la app: PENDIENTE (repositorio listo). |
| Jornada (iniciar/pausa/emergencia/finalizar) + ubicación operacional | FUNCIONA | Historial `locationUpdates` con retención configurable. |
| Mapa de flota / dashboard / geocercas / turnos / despacho | PENDIENTE | Datos y permisos (`VIEW_FLEET_MAP`) preparados. |
| Cobro empresarial (base + por conductor) | PENDIENTE | Campo `plan` en la empresa; sin valores definidos. |
| Idiomas (inglés, francés, kreyòl) | PENDIENTE | Todos los textos en `strings.xml`; agregar `values-en`, `values-fr`, `values-ht`. |
| iOS / panel web | PENDIENTE | Backend y reglas independientes del cliente. |

## Qué falta configurar externamente

1. Proyecto Firebase (plan Blaze) + `app/google-services.json` → FIREBASE_SETUP.md.
2. Despliegue de reglas, índices y Cloud Functions.
3. Primer ADMIN y `seedBaseData`.
4. API key de Google Maps en `local.properties`.
5. Play Console: app publicada en pista de prueba, producto `driverlink_pro_monthly`, acceso de la cuenta de servicio, `playPackageName`.
6. App Check (Play Integrity) antes del lanzamiento.
7. Keystore de release y `signingConfig`.
8. Textos legales (términos y política de privacidad) referenciados en el registro.
