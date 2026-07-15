# NutriScan AI — App Flutter

App móvil de NutriScan AI (Material Design 3, Clean Architecture + MVVM con Riverpod).

## Requisitos

- Flutter estable (>= 3.22)
- Un proyecto Firebase (Auth con Google/Apple/email, Storage y FCM)

## Primer arranque

```bash
# 1. Genera el scaffolding de plataformas (android/, ios/) sin tocar lib/
flutter create --org ai.nutriscan --platforms android,ios .

# 2. Configura Firebase (genera lib/firebase_options.dart y google-services.json)
dart pub global activate flutterfire_cli
flutterfire configure

# 3. Dependencias y ejecución
flutter pub get
flutter run --dart-define=API_BASE_URL=http://10.0.2.2:8080
```

Permisos requeridos en `android/app/src/main/AndroidManifest.xml`:
`INTERNET`, `CAMERA`, `POST_NOTIFICATIONS`.

## Estructura

```
lib/
├── main.dart                  # bootstrap + Firebase + ProviderScope
├── app.dart                   # MaterialApp.router + temas claro/oscuro
├── core/
│   ├── theme/                 # Material 3, paleta verde/morado
│   ├── router/                # go_router con guardas de sesión
│   ├── network/               # Dio + interceptor JWT + refresh automático
│   ├── models/                # modelos compartidos (nutrición, comidas)
│   └── session/               # estado de sesión (tokens, login/logout)
└── features/<feature>/        # una carpeta por funcionalidad (MVVM)
    └── presentation/          # pantallas y viewmodels (Riverpod)
```
