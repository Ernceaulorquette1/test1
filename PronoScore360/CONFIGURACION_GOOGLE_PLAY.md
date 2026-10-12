# Google Play: Billing, Firebase, firma y publicación

> ⚠ Todo lo de este documento está **implementado pero NO verificado contra Google** (faltan cuenta de desarrollador, Play Console y credenciales) y el módulo Android `:app` **no se compiló** en el entorno de generación. Usa pruebas internas antes de cualquier cosa.

## 1. Identificadores
* `applicationId`: `com.pronoscore360.app` (cámbialo antes de publicar si quieres otro; también `GOOGLE_PLAY_PACKAGE_NAME`).
* Producto de suscripción: `pronoscore360_pro_monthly` (en Play Console crea una **suscripción** con ese ID y un *plan base mensual*; precios regionales: CLP 9.990 en Chile, USD 9,99 en EE. UU., resto con conversión de Google).

## 2. Verificación de compras en el backend
1. Google Cloud: habilita **Google Play Android Developer API**. Crea una **cuenta de servicio** y descarga su JSON (no lo subas a Git).
2. Play Console → *Usuarios y permisos* → invita el email de la cuenta de servicio con permisos de **ver datos financieros** y **gestionar pedidos y suscripciones**.
3. Backend: `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON=/ruta/play-sa.json` (archivo montado como secreto) y `GOOGLE_PLAY_PACKAGE_NAME`.
4. **RTDN** (notificaciones): Play Console → *Monetizar → Configuración de monetización → Notificaciones para desarrolladores en tiempo real*: crea un tema de Pub/Sub, suscripción **push** a `https://api.tudominio.com/api/v1/webhooks/google-play` con autenticación OIDC (cuenta de servicio de tu elección). Configura `GOOGLE_PLAY_RTDN_AUDIENCE` (la URL) y `GOOGLE_PLAY_RTDN_SERVICE_ACCOUNT` (su email). Sin el JWT válido el endpoint responde 403.
5. Pruebas: Play Console → *Probadores de licencias* (cuentas que compran sin cobro; las compras de prueba llevan `testPurchase`, el backend las marca `is_test`).

## 3. Firebase Cloud Messaging (opcional)
Crea un proyecto Firebase, añade la app Android (`com.pronoscore360.app`), descarga `google-services.json` a `android/app/` (**no se versiona**). Sin ese archivo el plugin no se aplica y la app compila sin push. El servidor guarda tokens (`POST /me/devices`); **el envío push desde el servidor no está implementado** (hoy las alertas son notificaciones en la app); hay que añadir el envío con FCM HTTP v1 (pendiente).

## 4. Compilar APK (pruebas) y AAB (publicación)
```bash
# APK debug (emulador): 
bash scripts/build-android.sh debug
# Teléfono físico en tu red: API_BASE_URL=http://IP_PC:8000/api/v1/ bash scripts/build-android.sh debug
```
**Firma del AAB:**
```bash
keytool -genkeypair -v -keystore android/pronoscore360-upload.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
```
Crea `android/keystore.properties` (ignorado por Git):
```
storeFile=pronoscore360-upload.jks
storePassword=...
keyAlias=upload
keyPassword=...
```
```bash
API_BASE_URL=https://api.tudominio.com/api/v1/ VERSION_CODE=2 VERSION_NAME=1.0.1 bash scripts/build-android.sh release
# -> android/app/build/outputs/bundle/release/app-release.aab
```
Windows: `powershell -File scripts\build-android.ps1 release`. Guarda el keystore y sus contraseñas en un gestor seguro (si lo pierdes no podrás actualizar la app; se recomienda **Play App Signing**: subes con la *upload key*).
**Versionado:** `versionCode` debe aumentar en cada subida (`-PversionCode`); `versionName` es el visible.

## 5. Pistas de publicación
Pruebas **internas** (hasta 100 testers, sin revisión larga) → **cerradas** (los requisitos de testers/duración para cuentas personales nuevas deben comprobarse en las reglas vigentes) → producción por *rollout* escalonado. No se puede asegurar que la revisión se apruebe.

## 6. Ficha de Play Store → [docs/FICHA_GOOGLE_PLAY.md](docs/FICHA_GOOGLE_PLAY.md)
