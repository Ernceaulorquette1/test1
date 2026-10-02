# DriverLink Chile

**DriverLink** es una plataforma para conductores de aplicaciones de transporte en Chile: comunidad por canales, radio tipo walkie-talkie con mensajes cortos de audio, alertas viales en un mapa, SOS con asistencia (Premium) y una base preparada para **DriverLink Flotas** (empresas, conductores y vehículos).

> DriverLink es una aplicación independiente. **No es una aplicación oficial de Uber, DiDi ni Cabify** y no usa sus logos ni elementos visuales. Las integraciones con plataformas solo se implementarán mediante APIs y autorizaciones válidas.

## Contenido de esta carpeta

```
driverlink/
├── app/                      # App Android (Kotlin + Jetpack Compose)
│   └── src/
│       ├── main/             # Dominio, presentación, servicios, navegación, UI (común a ambos flavors)
│       ├── demo/             # Backend en memoria con datos de demostración (sin Firebase)
│       ├── prod/             # Repositorios Firebase, FCM, Remote Config
│       ├── test/             # Pruebas unitarias (JVM)
│       └── androidTest/      # Pruebas de UI (Compose)
├── firebase/
│   ├── firestore.rules       # Reglas de seguridad de Firestore
│   ├── storage.rules         # Reglas de Cloud Storage
│   ├── firestore.indexes.json
│   ├── firebase.json
│   ├── functions/            # Cloud Functions (TypeScript)
│   └── rules-tests/          # Pruebas de reglas con el emulador
├── ARCHITECTURE.md           # Arquitectura y decisiones técnicas
├── SETUP.md                  # Instalación desde cero
├── FIREBASE_SETUP.md         # Crear y conectar Firebase
└── PROJECT_STATUS.md         # Estado por módulo (FUNCIONA / REQUIERE CONFIGURACIÓN / USA MOCK / PENDIENTE)
```

## Tecnologías

| Área | Tecnología | Versión |
|---|---|---|
| Lenguaje | Kotlin | 2.0.21 |
| Build | Android Gradle Plugin / Gradle | 8.7.3 / 8.11.1 |
| UI | Jetpack Compose (BOM) + Material 3 | 2024.12.01 |
| Navegación | Navigation Compose | 2.8.5 |
| Asincronía | Coroutines + Flow | 1.9.0 |
| Backend | Firebase BoM (Auth, Firestore, Storage, FCM, Crashlytics, Analytics, Functions, Remote Config) | 33.7.0 |
| Mapas / ubicación | Maps Compose / Play Services Location | 6.4.0 / 21.3.0 |
| Cloud Functions | Node.js 22, firebase-functions v6 (2ª gen), TypeScript 5 | — |
| SDK Android | minSdk 26, target/compile 35, Java 17 | — |

Arquitectura: **MVVM + Clean Architecture simplificada** (capas `domain` / `data` / `presentation`), inyección de dependencias manual (`AppContainer`). Detalles en [ARCHITECTURE.md](ARCHITECTURE.md).

## Requisitos

- Android Studio Ladybug (2024.2) o superior, con JDK 17 (incluido en Android Studio).
- Android SDK Platform 35.
- Para el flavor `prod`: un proyecto Firebase (ver [FIREBASE_SETUP.md](FIREBASE_SETUP.md)).
- Para Cloud Functions: Node.js 22 y `firebase-tools` (`npm i -g firebase-tools`).

## Inicio rápido (modo demo, sin configurar nada)

1. Android Studio → **File → Open** → seleccionar la carpeta `driverlink/`.
2. Esperar la sincronización de Gradle.
3. En **Build Variants** elegir `demoDebug`.
4. Ejecutar en un emulador o dispositivo (▶).
5. Iniciar sesión con una cuenta demo (contraseña `demo1234`):
   - `demo@driverlink.cl` — plan Comunidad, puede activar la prueba Pro.
   - `pro@driverlink.cl` — DriverLink Pro y dueño de la empresa "Transportes Demo" (código `DL-DEM-2468`).
   - `expirado@driverlink.cl` — prueba Pro terminada (muestra el bloqueo Premium).

El flavor `demo` no usa Firebase: los datos viven en memoria y se reinician al cerrar la app. Está separado físicamente del código de producción (`src/demo`).

## Configurar Firebase y Maps (flavor prod)

- Firebase: [FIREBASE_SETUP.md](FIREBASE_SETUP.md). Resumen: crear proyecto, registrar la app `cl.driverlink.app`, copiar `google-services.json` a `app/`, desplegar reglas/índices/functions y ejecutar `seedBaseData`.
- Google Maps: crear una API key restringida a Android (paquete + SHA-1) con *Maps SDK for Android* y agregarla a `local.properties` (no versionado):

  ```properties
  MAPS_API_KEY=AIza...
  ```

  Sin clave, el mapa muestra las alertas en una lista (la app no falla).

## Ejecutar

```bash
./gradlew installDemoDebug        # demo en el dispositivo conectado
./gradlew installProdDebug        # producción (requiere google-services.json)
```

## Compilar APK

```bash
./gradlew assembleDemoDebug       # app/build/outputs/apk/demo/debug/
./gradlew assembleProdRelease     # requiere configurar firma (signingConfig) para instalar
```

## Pruebas

```bash
./gradlew testDemoDebugUnitTest               # unitarias: validaciones, políticas, casos de uso, ViewModels
./gradlew connectedDemoDebugAndroidTest       # UI (emulador/dispositivo)

cd firebase/functions && npm ci && npm test   # Cloud Functions
cd firebase/rules-tests && npm ci && npm run test:emulator   # reglas de seguridad (requiere Java + firebase-tools)
```

El workflow `.github/workflows/driverlink.yml` ejecuta todo lo anterior (excepto pruebas en dispositivo) en cada push.

## Qué está terminado, qué usa mocks y qué falta

Resumen (detalle completo en [PROJECT_STATUS.md](PROJECT_STATUS.md)):

- **Funciona (demo y prod):** registro, login, logout, perfil y edición, verificación manual, home, navegación, canales, chat paginado, radio (grabar/enviar/escuchar), mapa, crear/confirmar alertas, expiración, plan gratuito, trial, bloqueo Premium, SOS completo, contactos de emergencia, notificaciones configurables, bloqueo y denuncias, empresas (crear, invitar, aprobar, canales privados, vehículos, jornada con ubicación operacional).
- **Usa mock / en memoria:** todo el flavor `demo`; el avance de estados del SOS en demo lo simula un "operador" automático.
- **Requiere configuración externa:** proyecto Firebase + `google-services.json`, API key de Maps, plan Blaze de Firebase para Cloud Functions, producto de suscripción en Google Play Console (Play Billing), roles de administración asignados con `setUserRole`.
- **Pendiente:** cobro real con Google Play Billing en la app, paneles web (SOS, administración, empresa), mapa de flota, OAuth con plataformas (no existe integración autorizada), traducciones a inglés/francés/kreyòl.
