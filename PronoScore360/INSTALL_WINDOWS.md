# Instalación en Windows (VS Code + Docker Desktop + Git + Android Studio)

Tiempo estimado: 30-45 min la primera vez (descargas). **No existe un único comando que lo haga todo**: sigue las etapas.

## 0. Requisitos (una vez)
1. **Git**: https://git-scm.com/download/win
2. **Docker Desktop** (con WSL2): https://www.docker.com/products/docker-desktop/ → ábrelo y espera a "Running".
3. **VS Code** (opcional, para editar) y **Android Studio** (Koala 2024.1+ o posterior; incluye JDK 17 y el SDK).

## 1. Descomprimir y abrir
```powershell
Expand-Archive PronoScore360_Proyecto_Completo.zip -DestinationPath C:\dev
cd C:\dev\PronoScore360
code .
```
## 2. Variables de entorno
```powershell
copy .env.example .env
```
Déjalo así para el modo **DEMO** (no necesita claves). Cambia `JWT_SECRET` por un valor largo (el script del paso 3 lo hace por ti).
Claves reales de API-SPORTS: ver [CONFIGURACION_API.md](CONFIGURACION_API.md).

## 3. Levantar todo con Docker Compose
Opción automática:
```powershell
powershell -ExecutionPolicy Bypass -File scripts\setup-windows.ps1
```
Opción manual:
```powershell
docker compose up -d --build
docker compose logs -f api      # espera "Application startup complete" (migraciones + datos DEMO)
```
## 4. Abrir la web y Swagger
* Web: http://localhost:5173 · Swagger: http://localhost:8000/docs · Salud: http://localhost:8000/api/v1/health
* Inicia sesión con los botones **Admin / Pro / Gratis** de la pantalla de login (sólo aparecen en entorno local).

## 5. Android Studio
1. *Open* → carpeta `C:\dev\PronoScore360\android`. Acepta instalar el SDK (API 35) y espera al *Gradle sync*.
2. **Emulador** (AVD con Google Play recomendado: Pixel, API 34/35). El emulador ve el PC anfitrión como `10.0.2.2`, así que **no necesitas cambiar nada**: el build *debug* apunta a `http://10.0.2.2:8000/api/v1/`.
3. Pulsa ▶ *Run 'app'*.
4. **Teléfono físico** (mismo Wi-Fi que el PC):
   * Activa *Opciones de desarrollador → Depuración USB* y conecta el cable.
   * Averigua la IP del PC: `ipconfig` (p. ej. `192.168.1.50`).
   * Permite el puerto en el firewall de Windows: `netsh advfirewall firewall add rule name="PronoScore API" dir=in action=allow protocol=TCP localport=8000`
   * Compila apuntando a esa IP: `set API_BASE_URL=http://192.168.1.50:8000/api/v1/` y luego `powershell -File scripts\build-android.ps1 debug`, o en Android Studio: *Build Variants* + en `gradle.properties` de usuario `apiBaseUrl=http://192.168.1.50:8000/api/v1/`.
   * Instala: `adb install -r android\app\build\outputs\apk\debug\app-debug.apk`
   * (El tráfico HTTP sin cifrar sólo está permitido en el build *debug*; *release* exige HTTPS.)
5. Compilar por línea de comandos: `powershell -File scripts\build-android.ps1 debug` → `android\app\build\outputs\apk\debug\app-debug.apk`.

> ⚠ El módulo Android `:app` **no se pudo compilar en el entorno donde se generó** (sin Android SDK). Es posible que Android Studio muestre errores de compilación que habrá que corregir en la primera sincronización; el módulo `:core` (red y lógica) sí está compilado y probado. Ver [ESTADO_PROYECTO.md](ESTADO_PROYECTO.md).

## 6. Probar cuentas Gratis y Pro
* **Gratis:** analiza 5 partidos distintos → el 6.º responde "límite alcanzado". Reabrir uno ya analizado no descuenta.
* **Pro demo:** ve el listado de pronósticos con filtros, todos los mercados, top-5 de marcadores y selecciones.
* Para probar el flujo de pago simulado: cuenta Gratis → *Planes* → «Activar Pro de prueba (simulador local)». En *Suscripción* puedes simular renovación/cancelación/gracia/vencimiento/reembolso.
* **Administrador:** menú «Administración» (sólo visible para ADMIN; el servidor lo exige igualmente).

## 7. Probar sin Docker (alternativa)
Necesitas PostgreSQL local y Python 3.11+/Node 20+ (en Git Bash o WSL): `bash scripts/dev-local.sh`.

## 8. Ejecutar las pruebas
`bash scripts/test-all.sh` (Git Bash/WSL) — ver [TESTING.md](TESTING.md).

## Problemas frecuentes
| Síntoma | Solución |
|---|---|
| `docker compose` no conecta | Abre Docker Desktop y espera a que esté "Running". |
| Puerto 5432/8000/5173 ocupado | Detén el servicio que lo usa o cambia el puerto en `docker-compose.yml`. |
| La app Android dice "No se pudo conectar" | Emulador: usa `10.0.2.2`. Teléfono: IP del PC, firewall abierto, misma red, backend activo. |
| Login demo no aparece | Sólo existe con `ENV=local` y `SEED_DEMO_ON_START=true`. |
