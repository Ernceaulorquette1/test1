# Arquitectura de DriverLink

## 1. Principios

1. **Separación de áreas.** COMUNIDAD, EMPRESA, SOS y ADMINISTRACIÓN tienen datos, rutas de Firestore y permisos distintos. Un permiso de un área nunca habilita otra.
2. **El servidor decide lo sensible.** Suscripción, verificación, roles, reputación, contadores, SOS y membresías de empresa se escriben solo desde Cloud Functions. El cliente nunca guarda `premium=true`.
3. **Costos acotados.** Ningún listener global: consultas filtradas por canal, ciudad, estado y fecha, con `limit` y paginación.
4. **Reemplazable.** El proveedor de mapas, el backend (demo/Firebase), la facturación y el OAuth externo están detrás de interfaces.

## 2. Capas y paquetes (`cl.driverlink.app`)

```
core/            Utilidades transversales sin lógica de negocio
  result/        AppResult, AppError, ErrorMapper, safeCall
  ui/            UiState (Loading/Empty/Success/Error), UiText, mensajes de error
  network/       ConnectivityObserver (estado sin conexión)
  analytics/     AnalyticsTracker + eventos (sin datos sensibles)
  logging/       CrashReporter
  time/          Clock, RelativeTime
domain/          Kotlin puro (sin Android ni Firebase)
  model/         User, Channel, Message, Alert, SosEvent, Subscription, Company, Vehicle, WorkSession, AppConfig...
  repository/    Interfaces de repositorio
  usecase/       Register, Login, CreateAlert, VoteAlert, StartSos, SendMessage, SendAudio, CreateCompany...
  policy/        FeatureAccessPolicy (Premium), RolePolicy (permisos), AlertExpirationPolicy
  validation/    Email, teléfono chileno, contraseña, RUT, patente, código de invitación
  auth/          ExternalAuthProvider (OAuth futuro)
data/            Implementaciones por flavor
  demo/          (src/demo) backend en memoria fiel a las reglas del servidor
  firebase/      (src/prod) Firestore, Auth, Storage, Functions, FCM, Remote Config
di/              DataModule (contrato), AppContainer, FlavorModule (uno por flavor)
presentation/    Pantalla + ViewModel por funcionalidad
  auth, home, map, alerts, chat, radio, sos, subscription, profile, settings, notifications, company, splash, common
navigation/      Routes, DriverLinkNavHost (grafos Auth / Main / Company), SessionViewModel
services/        location (FusedLocationProvider, OperationalLocationService), audio (AudioRecorder, AudioPlayer), notifications
ui/              theme (colores, tipografía, estilos de alerta), components (estados, SOS, tarjetas), map (AlertMap)
```

Dependencias: `presentation → domain ← data`. El dominio no depende de nada externo, por eso sus 36 pruebas corren en la JVM sin Android.

### Decisiones técnicas

| Decisión | Motivo |
|---|---|
| **Flavors `demo` y `prod`** | Los datos de demostración nunca se mezclan con producción: el código demo solo existe en `src/demo`; Firebase solo en `src/prod` (`prodImplementation`). El demo compila sin credenciales. |
| **DI manual (`AppContainer`) en vez de Hilt** | Menos configuración (sin kapt/ksp), build más rápido y simple para el MVP. Todo entra por constructor, migrar a Hilt es mecánico. |
| **Rutas String en Navigation Compose** | Centralizadas en `Routes`; evita depender del plugin de serialización. |
| **Mapeo manual Firestore ↔ dominio** | Sin reflexión: compatible con R8 sin reglas `-keep` y tolerante a campos faltantes. |
| **Listeners que no lanzan excepciones** | `Query.observe` emite un valor de respaldo ante `PERMISSION_DENIED` y lo registra en Crashlytics; la app no se cierra. |
| **Mensajes de datos FCM** | El servidor envía `category/title/body`; la app elige el canal de notificación. Las alertas por zona usan temas (`alerts_<ciudad>`) para no consultar usuarios uno a uno. |
| **Callable Functions para operaciones sensibles** | SOS, trial, compras, empresas, roles y suspensiones validan en servidor. |
| **Región `southamerica-west1` (Santiago)** | Latencia baja para Chile. Configurable en `FUNCTIONS_REGION` y `functions/src/config.ts`. |
| **Audio AAC 16 kHz mono 32 kbps** | ≈4 KB/s: un audio de 30 s pesa ~120 KB (control de costos de Storage y datos). |

## 3. Navegación

```
splash
├── auth (sin sesión)          welcome → login / register
├── main (con sesión)          bottom bar: home · map · radio · channels · profile
│     verification, alerts/create, alerts/{id}, chat/{channelId}?companyId=,
│     sos, sos/active/{id}, subscription, profile/edit, emergency-contacts,
│     settings/notifications, settings/privacy
└── company                    company/entry, create, join, {id}/home, {id}/members, {id}/vehicles
```

- `SessionViewModel` observa el estado de autenticación y reemplaza el back stack al cambiar (no se puede volver con "atrás" a una sesión anterior).
- **Premium por ruta no basta:** `SosScreen` y `EmergencyContactsScreen` se envuelven en `PremiumGate`, que consulta la suscripción emitida por el servidor. Si alguien navega directo a la ruta, solo ve la pantalla de bloqueo; además Firestore Rules y la Function `createSosEvent` vuelven a validar.
- Las pantallas de empresa verifican membresía `ACTIVE` y permisos por rol (`RolePolicy`); las reglas lo aplican en servidor.
- Accesos rápidos: botón **Reportar** y **SOS** flotantes en Inicio, Mapa y Chat; botón SOS grande en Home.

## 4. Modelo de Firestore

| Colección | Documento | Escribe | Lee |
|---|---|---|---|
| `users/{uid}` | perfil privado | dueño (campos permitidos) + Functions | dueño, moderación, operador SOS |
| `users/{uid}/emergencyContacts/{id}` | contacto | dueño con Premium vigente | dueño |
| `users/{uid}/blockedUsers/{uid2}` | bloqueo | dueño | dueño |
| `users/{uid}/devices/{token}` | token FCM | dueño | dueño |
| `publicProfiles/{uid}` | nombre, foto, ciudad, plataformas, verificación | Functions (`onUserWritten`) | autenticados |
| `verificationRequests/{uid}` | solicitud + rutas privadas de evidencia | dueño (PENDING), moderación vía Functions | dueño, moderación |
| `platforms/{id}` | catálogo Uber/DiDi/Cabify/Otra | admin | autenticados |
| `userPlatforms/{id}` | vínculo verificado con plataforma (futuro OAuth) | Functions | dueño |
| `channels/{id}` | canal comunitario (`type`, `mode` TEXT/RADIO, región, ciudad, zona, orden) | admin (`upsertChannel`) | autenticados |
| `channels/{id}/messages/{id}` | mensaje (texto o audio) | autor; borrado lógico | autenticados |
| `channelMembers/{channelId}_{uid}` | preferencias por canal (silenciar, favorito) | dueño | dueño |
| `alerts/{id}` | alerta (categoría, lat/lng, ciudad, comuna, contadores, `expiresAt`) | autor (crear), Functions (contadores, estado) | autenticados |
| `alerts/{id}/confirmations/{uid}` | **alertConfirmations**: un voto por usuario (ID = uid) | votante, una vez | votante, moderación |
| `sosEvents/{id}` | SOS (usuario, tipo, ubicación, estado, operador, fechas) | Functions; dueño solo ubicación | dueño, operadores |
| `sosEvents/{id}/operatorNotes/{id}` | notas del operador | Functions | operadores |
| `reports/{id}` | denuncia (USER/MESSAGE/AUDIO/ALERT + motivo) | denunciante | moderación |
| `moderationCounters/{tipo_id}` / `moderationActions/{id}` | contadores / auditoría | Functions | admin (vía backend) |
| `subscriptions/{uid}` | FREE/TRIAL/PRO/EXPIRED/COMPANY, `expiresAt`, `trialUsed` | **solo Functions** | dueño |
| `notificationPreferences/{uid}` | mapa de categorías | dueño | dueño |
| `companies/{id}` | RUT, razón social, nombre comercial, contacto, estado, plan | Functions (`createCompany`), dueño (datos de contacto) | miembros |
| `companies/{id}/private/invite` | código de invitación vigente | Functions | OWNER/ADMIN |
| `companies/{id}/members/{uid}` | **companyMembers**: rol + estado | Functions | el propio miembro, OWNER/ADMIN/SUPERVISOR/DISPATCHER |
| `companies/{id}/channels/{id}/messages/{id}` | chat y radio privados | miembros ACTIVE | miembros ACTIVE |
| `companies/{id}/vehicles/{patente}` | vehículo | OWNER/ADMIN (crear), SUPERVISOR (asignar) | miembros ACTIVE |
| `companies/{id}/vehicleAssignments/{id}` | historial de asignaciones | OWNER/ADMIN/SUPERVISOR | roles de gestión |
| `companies/{id}/workSessions/{id}` | jornada (estado, inicio/fin, última ubicación) | el conductor (su jornada abierta) | conductor, roles de gestión |
| `companies/{id}/workSessions/{id}/locationUpdates/{id}` | **locationUpdates**: recorrido autorizado | conductor con jornada abierta | roles de gestión |
| `companyInvites/{code}` | código → empresa, activo | Functions | nadie (cliente) |
| `adminConfig/app` | trialDays, umbrales, expiración, retención, playPackageName | admin | autenticados |

**Consultas reales y sus índices** (`firebase/firestore.indexes.json`):

- Canales: `active == true && mode == X order by order`.
- Mensajes: `order by createdAt desc limit 30` + página siguiente con `createdAt < cursor`.
- Alertas: `status == ACTIVE && city == X && expiresAt > now order by expiresAt limit 100`.
- SOS activo propio: `userId == uid && status in [...] order by createdAt desc limit 1`.
- Mis empresas: collection group `members where userId == uid` (override de índice).
- Jornada activa: `userId == uid && endedAt == null`.

Se evitan "joins": los mensajes guardan `senderName` y `senderPhotoUrl`; las alertas `creatorName`; las membresías `displayName`.

## 5. Seguridad

`firebase/firestore.rules` y `firebase/storage.rules`, verificadas con `firebase/rules-tests` (12 pruebas en el emulador):

- Un usuario solo modifica campos de perfil autorizados; no puede verificarse, darse Premium ni otro rol.
- Roles de plataforma (`MODERATOR`, `ADMIN`, `SOS_OPERATOR`) son *custom claims* asignados por `setUserRole` (solo ADMIN). Las suspensiones agregan el claim `suspended` y revocan tokens.
- Canales y audios privados de empresa: solo miembros `ACTIVE` (Storage consulta Firestore con `firestore.get`).
- Evidencia de verificación: escritura solo del dueño; lectura solo de moderación/admin. Nunca pública.
- Votos únicos por diseño (ID = uid, sin `update`). Contadores solo en servidor.
- SOS: creación vía Function con validación Premium; el dueño solo actualiza su ubicación mientras el caso está activo.

## 6. Suscripciones (SubscriptionManager)

`SubscriptionRepository` (estado del servidor) + `BillingGateway` (tienda) + `FeatureAccessPolicy` (decisión de UX):

- Estados: `FREE`, `TRIAL`, `PRO`, `EXPIRED`, `COMPANY`. Un TRIAL/PRO vencido se trata como `EXPIRED` aunque el job diario aún no haya corrido.
- Trial: Function `startTrial` (una vez por cuenta, duración desde `adminConfig/app.trialDays`: 30 en lanzamiento, luego 7).
- Compra: `verifyPlayPurchase` valida el token con la Google Play Developer API y otorga PRO.
- Al terminar: solo se bloquean SOS, contactos de emergencia y asistencia; chat, radio, mapa y alertas siguen gratis. Mensaje: *"Tu período de DriverLink Pro terminó. Puedes continuar utilizando gratuitamente las funciones de comunidad."*

## 7. Ubicación y privacidad

- `ACCESS_FINE/COARSE_LOCATION` solo al abrir el mapa, crear una alerta, enviar un SOS o iniciar jornada, siempre con explicación previa.
- **No** se declara `ACCESS_BACKGROUND_LOCATION`. El seguimiento usa `OperationalLocationService` (servicio en primer plano tipo `location`, con notificación visible) **solo** durante un SOS activo (cada 20 s, se detiene al resolverse/cancelarse) o una jornada empresarial (cada 60 s / 50 m, se detiene al finalizar).
- El mapa comunitario nunca muestra la ubicación de otros conductores; las ubicaciones de empresa viven en `companies/...` y no se mezclan con la comunidad.
- Retención configurable: `purgeOldLocations` borra recorridos con más de `locationRetentionDays`.

## 8. Configuración remota

Remote Config (cliente) y `adminConfig/app` (servidor), mismos valores por defecto que `AppConfig`:
`trial_days`, `max_audio_duration_seconds`, `alert_expiration_minutes` (JSON por categoría), `premium_price_display`, `sos_enabled`, `company_features_enabled`, `maintenance_mode`, `sos_hold_millis`, `chat_page_size`, `alert_resolve_threshold`, intervalos de ubicación y `official_services` (133, 131, 132, 134).

## 9. Errores, estados y sin conexión

- Repositorios devuelven `AppResult`; las excepciones se traducen con `FirebaseErrorMapper` a `AppError` y luego a textos de `strings.xml`. Nunca se muestra un error técnico.
- Pantallas de carga usan `UiState` (Loading / Empty / Success / Error) y `StateContent`.
- `ConnectivityObserver` muestra un banner sin conexión. Firestore usa caché persistente (50 MB): el chat funciona offline y los mensajes quedan "Enviando" (`hasPendingWrites`). Las acciones críticas (SOS, trial, empresa) son llamadas a Functions: si fallan se informa explícitamente que **no** se completaron (ej. "El SOS NO se envió").

## 10. Escalabilidad y futuro

- **iOS / web:** el modelo de datos, reglas y Functions son independientes del cliente; el panel SOS usará `SosOperatorRepository` y la Function `updateSosEvent` que ya existen.
- **Nuevos países:** canales, servicios oficiales, precios y textos son datos/configuración.
- **Flotas:** `RolePolicy` ya define `VIEW_FLEET_MAP`; las jornadas guardan última ubicación y recorrido para el mapa de flota, geocercas e historial.
- **OAuth con plataformas:** implementar `ExternalAuthProvider` (Custom Tabs + PKCE + token personalizado de Firebase) y registrarlo en `ExternalAuthRegistry`.
- **Branding:** colores en `ui/theme/Color.kt`, textos en `strings.xml`, nombre por flavor en `build.gradle.kts`, íconos en `res/drawable` y `res/mipmap-anydpi-v26`.
