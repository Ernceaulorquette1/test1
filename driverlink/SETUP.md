# SETUP — DriverLink desde cero

Instrucciones exactas para dejar el proyecto funcionando en un computador nuevo.

## 1. Instalar herramientas

1. **Android Studio** Ladybug (2024.2.1) o más reciente: <https://developer.android.com/studio>.
2. En el primer inicio aceptar la instalación del **Android SDK**. Luego, en *Settings → Languages & Frameworks → Android SDK*:
   - *SDK Platforms*: **Android 15 (API 35)**.
   - *SDK Tools*: Android SDK Build-Tools 35, Android Emulator, Android SDK Platform-Tools.
3. JDK: usar el **JBR 17+ incluido** en Android Studio (*Settings → Build Tools → Gradle → Gradle JDK*).
4. (Opcional, para backend) **Node.js 22** y **Firebase CLI**: `npm install -g firebase-tools`.
5. (Opcional, para pruebas de reglas) **Java 11+** en el PATH.

## 2. Obtener el código

```bash
git clone https://github.com/Ernceaulorquette1/test1.git
cd test1/driverlink
```

## 3. Abrir en Android Studio

1. *File → Open…* → seleccionar la carpeta **`driverlink`** (la que contiene `settings.gradle.kts`).
2. Esperar *Gradle Sync* (la primera vez descarga Gradle 8.11.1 y las dependencias).
3. Android Studio crea `local.properties` con `sdk.dir`. Este archivo **no se versiona**.

## 4. Ejecutar la demo (sin Firebase)

1. *Build → Select Build Variant…* → módulo `app` → **demoDebug**.
2. Crear un emulador: *Device Manager → Create Device → Pixel 7 → API 35 (Google APIs o Google Play)*.
3. Presionar ▶ *Run*.
4. Entrar con `demo@driverlink.cl` / `demo1234` (o crear una cuenta nueva: en demo vive en memoria).

Recorrido sugerido para validar el MVP:

| Paso | Dónde |
|---|---|
| Ver home, alertas activas y canales | Inicio |
| Abrir canal Santiago, enviar, responder, eliminar, denunciar y bloquear; deslizar hacia arriba para paginar | Chat → Santiago |
| Mantener **MANTENER PARA HABLAR**, soltar, Enviar o Cancelar; reproducir audios | Radio |
| Ver mapa / ubicación propia (aceptar permiso). En emulador: *Extended controls → Location* | Mapa |
| Reportar → categoría → descripción → ubicación → Publicar; ver la alerta en el mapa | Botón Reportar |
| Votar CONFIRMO / YA TERMINÓ / INCORRECTO en otra alerta (solo una vez) | Mapa / Detalle |
| Intentar SOS sin Pro → bloqueo; *Ver planes* → *Probar Pro gratis* | SOS → Planes |
| SOS: mantener 3 s → tipo → ENVIAR SOS → ver "Buscando asistencia → Recibida → En atención" | SOS |
| Contactos de emergencia, notificaciones, privacidad, usuarios bloqueados | Perfil |
| Cerrar sesión y entrar con `expirado@driverlink.cl` para ver el mensaje de fin de Pro | Perfil |
| Empresa: con `demo@` unirse con `DL-DEM-2468`; con `pro@` aprobar en *Miembros*, crear/asignar vehículos, iniciar/finalizar jornada | Chat → Mi empresa |

## 5. Configurar Google Maps (opcional en demo)

1. En Google Cloud Console (mismo proyecto que Firebase) habilitar **Maps SDK for Android**.
2. *APIs y servicios → Credenciales → Crear credencial → Clave de API*.
3. Restringirla: *Aplicaciones Android* → paquete `cl.driverlink.app` + SHA-1 (obtenerlo con `./gradlew signingReport`).
4. Agregar en `driverlink/local.properties`:
   ```properties
   MAPS_API_KEY=AIzaSy...
   ```
5. Re-sincronizar Gradle. Sin clave, el mapa se reemplaza por una lista de alertas.

## 6. Configurar producción (Firebase)

Seguir **[FIREBASE_SETUP.md](FIREBASE_SETUP.md)** y luego:

1. Copiar `google-services.json` a `driverlink/app/`.
2. Build variant **prodDebug** → ▶ *Run*.

## 7. Línea de comandos

```bash
./gradlew testDemoDebugUnitTest      # pruebas unitarias
./gradlew assembleDemoDebug          # APK demo → app/build/outputs/apk/demo/debug/app-demo-debug.apk
./gradlew assembleProdDebug          # APK prod (con google-services.json)
./gradlew lintDemoDebug              # análisis estático
```

### APK de release firmado

1. Crear keystore: *Build → Generate Signed Bundle / APK* (guardarlo fuera del repositorio).
2. Agregar un `signingConfig` en `app/build.gradle.kts` leyendo contraseñas desde `local.properties` o variables de entorno (nunca en el código).
3. `./gradlew assembleProdRelease` o `bundleProdRelease` (AAB para Play Store).

## 8. Problemas comunes

| Síntoma | Solución |
|---|---|
| `SDK location not found` | Abrir con Android Studio o crear `local.properties` con `sdk.dir=/ruta/al/Android/Sdk`. |
| La app prod se cierra al abrir: *Firebase no está configurado* | Falta `app/google-services.json`. |
| Mapa en gris | API key sin *Maps SDK for Android* o SHA-1 incorrecto. |
| `FAILED_PRECONDITION: The query requires an index` | `firebase deploy --only firestore:indexes` y esperar a que terminen de construirse. |
| Error al activar trial / SOS en prod | Las Cloud Functions no están desplegadas (requiere plan Blaze) o la región no coincide. |
