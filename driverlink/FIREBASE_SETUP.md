# FIREBASE_SETUP — Crear y conectar Firebase

Paso a paso para conectar el flavor **prod** de DriverLink a un proyecto Firebase propio.
Nada de esto se versiona: `google-services.json`, `.firebaserc` y cuentas de servicio están en `.gitignore`.

## 1. Crear el proyecto

1. <https://console.firebase.google.com> → **Agregar proyecto** → nombre `driverlink-prod` (usar otro proyecto, p. ej. `driverlink-dev`, para desarrollo).
2. Activar **Google Analytics** (necesario para Analytics y recomendado para Crashlytics).
3. Cambiar a plan **Blaze** (pago por uso): obligatorio para Cloud Functions y llamadas a APIs externas. Configurar un **presupuesto con alertas** en Google Cloud Billing.

## 2. Registrar la app Android

1. *Configuración del proyecto → Tus apps → Android*.
2. Paquete: **`cl.driverlink.app`**. Apodo: DriverLink.
3. SHA-1 de depuración: `./gradlew signingReport` (variante `prodDebug`). Agregar también el SHA-1/SHA-256 de release y el de *Play App Signing* cuando corresponda.
4. Descargar **`google-services.json`** y copiarlo en `driverlink/app/google-services.json`.

> El build aplica los plugins `google-services` y `crashlytics` automáticamente solo si ese archivo existe.

## 3. Habilitar servicios

| Servicio | Configuración |
|---|---|
| **Authentication** | *Sign-in method → Correo/contraseña*: habilitar. *Settings → Plantillas*: traducir el correo de restablecimiento al español. |
| **Cloud Firestore** | *Crear base de datos* → modo **producción** → ubicación **`southamerica-west1` (Santiago)**. |
| **Storage** | *Comenzar* → misma ubicación. |
| **Cloud Messaging** | Habilitado por defecto (API FCM v1). |
| **Crashlytics** | *Crashlytics → Habilitar*; aparece tras el primer error reportado. |
| **Remote Config** | Crear parámetros con los mismos nombres que `app/src/prod/res/xml/remote_config_defaults.xml` (opcional: los valores por defecto ya vienen en la app). |
| **App Check** (recomendado) | Registrar *Play Integrity* y activar *enforcement* para Firestore, Storage y Functions una vez publicada la app. |

## 4. Desplegar reglas, índices y Cloud Functions

```bash
npm install -g firebase-tools
firebase login
cd driverlink/firebase
cp .firebaserc.example .firebaserc        # editar con el ID real del proyecto
cd functions && npm ci && cd ..
firebase deploy --only firestore:rules,firestore:indexes,storage
firebase deploy --only functions
```

- La región de Functions es **`southamerica-west1`**, definida en `functions/src/config.ts` y en `FUNCTIONS_REGION` (`app/src/prod/.../FirestoreSupport.kt`). Si se cambia, cambiar ambos.
- Los índices tardan unos minutos en construirse.

## 5. Primer administrador y datos base

1. Registrarse en la app (flavor prod) con la cuenta del administrador.
2. Asignar el rol ADMIN (solo la primera vez, desde una máquina segura con credenciales del proyecto):
   ```bash
   cd driverlink/firebase/functions
   gcloud auth application-default login
   node -e "const a=require('firebase-admin');a.initializeApp({projectId:'TU-PROYECTO'});a.auth().getUserByEmail('admin@tu-dominio.cl').then(u=>a.auth().setCustomUserClaims(u.uid,{role:'ADMIN'})).then(()=>console.log('ok'))"
   ```
   Los siguientes roles (MODERATOR, SOS_OPERATOR, ADMIN) se asignan con la Function `setUserRole`.
3. Cerrar sesión y volver a entrar (para refrescar el token) y ejecutar la Function **`seedBaseData`** (desde el futuro panel web o con la Firebase CLI shell: `firebase functions:shell` → `seedBaseData({data:{}}, {auth:{uid:'...', token:{role:'ADMIN'}}})`). Crea:
   - canales comunitarios (Santiago, Aeropuerto, Santiago Centro, Providencia, Maipú, Valparaíso, Viña del Mar, Concepción) y radios (Santiago, Aeropuerto, Valparaíso);
   - catálogo `platforms`;
   - `adminConfig/app` con los valores por defecto.
4. Nuevas regiones/zonas/radios: Function `upsertChannel` o editar `channels/{id}` en la consola. No requiere publicar la app.

## 6. Configuración de negocio (`adminConfig/app`)

| Campo | Defecto | Uso |
|---|---|---|
| `trialDays` | 30 | Duración de la prueba Pro (cambiar a 7 después del lanzamiento). |
| `sosEnabled` | true | Interruptor del SOS en servidor. |
| `companyFeaturesEnabled` | true | Interruptor de DriverLink Flotas. |
| `alertExpirationMinutes` | mapa por categoría | Vencimiento de alertas (el servidor recalcula `expiresAt`). |
| `alertResolveThreshold` | 3 | Votos "Ya terminó"/"Incorrecto" para resolver/ocultar. |
| `reportsToHideMessage` | 3 | Denuncias para ocultar un mensaje en espera de revisión. |
| `locationRetentionDays` | 30 | Retención de recorridos de jornada. |
| `playPackageName` | null | Habilita la validación de compras de Google Play. |

Los valores visibles en la app (precio, duración máxima de audio, modo mantención, etc.) se ajustan en **Remote Config**.

## 7. Notificaciones

- La app guarda el token en `users/{uid}/devices/{token}` y se suscribe a los temas `alerts_<ciudad>` y `admin` según las preferencias del usuario.
- Los operadores SOS deben suscribirse al tema `sos_operators` (panel web futuro).
- Android 13+: la app pide el permiso de notificaciones con explicación.

## 8. Google Play Billing (DriverLink Pro)

1. Publicar la app en una pista de prueba de Play Console.
2. *Monetizar → Suscripciones* → crear **`driverlink_pro_monthly`** con precio de referencia **$5.990 CLP/mes**.
3. Vincular Play Console con el proyecto de Google Cloud y dar acceso a la cuenta de servicio de Cloud Functions (*Usuarios y permisos → Ver datos financieros / Administrar pedidos*).
4. Definir `adminConfig/app.playPackageName = "cl.driverlink.app"`.
5. Implementar `BillingGateway` con la librería Play Billing (ver PROJECT_STATUS.md) enviando el `purchaseToken` a `verifyPlayPurchase`.

## 9. Probar con emuladores (sin tocar producción)

```bash
cd driverlink/firebase
firebase emulators:start --project demo-driverlink
# Pruebas de reglas:
cd rules-tests && npm ci && npm run test:emulator
```

## 10. Checklist de seguridad antes de producción

- [ ] Reglas e índices desplegados y pruebas de reglas en verde.
- [ ] API key de Maps restringida por paquete + SHA-1.
- [ ] App Check activado.
- [ ] Presupuesto y alertas de facturación.
- [ ] Solo cuentas de confianza con rol ADMIN / SOS_OPERATOR.
- [ ] Ningún `google-services.json`, keystore ni cuenta de servicio en el repositorio.
