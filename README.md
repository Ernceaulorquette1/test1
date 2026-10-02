# 🍏 NutriScan AI

> **Nuevo proyecto en este repositorio:** [`driverlink/`](driverlink/README.md) — **DriverLink Chile**, app Android (Kotlin + Jetpack Compose + Firebase) para conductores de aplicaciones de transporte.

**Tu asistente inteligente de nutrición.** Escanea tus comidas, calcula calorías y nutrientes al instante con inteligencia artificial.

NutriScan AI es una aplicación móvil comercial (Android / Google Play) que reconoce alimentos a partir de fotografías, estima porciones y calcula calorías y macronutrientes, con historial, estadísticas, chat nutricional con IA, escáner de código de barras, registro de agua y planes Premium por suscripción.

## Estructura del monorepo

```
.
├── mobile/            # App Flutter (Material Design 3, Clean Architecture + MVVM)
├── backend/           # API REST Java 17 + Spring Boot 3 (Clean Architecture)
├── k8s/               # Manifiestos Kubernetes (GKE) — deployments, HPA, ingress
├── docs/              # SRS IEEE 830, arquitectura, UML, ER, plan de pruebas, manuales
├── docker-compose.yml # Entorno local: API + PostgreSQL + Redis
└── .github/workflows/ # CI: build, tests y análisis estático
```

## Stack tecnológico

| Capa | Tecnología |
|---|---|
| Frontend móvil | Flutter (Material Design 3, Riverpod, go_router) |
| Backend | Java 17, Spring Boot 3, Clean Architecture, SOLID |
| Base de datos | PostgreSQL 16 + Flyway (migraciones) |
| Cache | Redis 7 |
| Autenticación | Firebase Authentication (Google / Apple / email) + JWT propio |
| Almacenamiento | Firebase Storage (fotos de comidas) |
| Notificaciones | Firebase Cloud Messaging |
| IA de visión | OpenAI Vision / Gemini Vision — proveedor intercambiable vía interfaz `VisionProvider` |
| Infraestructura | Docker, Kubernetes, Google Cloud |
| Pagos | Google Play Billing (suscripciones mensual/anual) |

## Puesta en marcha local

### Backend + base de datos

```bash
cp .env.example .env          # completar claves (OpenAI/Gemini, Firebase)
docker compose up --build
# API:      http://localhost:8080
# Swagger:  http://localhost:8080/swagger-ui.html
```

### App Flutter

```bash
cd mobile
flutter pub get
flutter run --dart-define=API_BASE_URL=http://10.0.2.2:8080
```

### Tests

```bash
cd backend && ./mvnw test     # tests backend (JUnit 5)
cd mobile && flutter test     # tests Flutter
```

## Documentación

| Documento | Ruta |
|---|---|
| SRS (IEEE 830) | [docs/SRS.md](docs/SRS.md) |
| Arquitectura de software | [docs/ARQUITECTURA.md](docs/ARQUITECTURA.md) |
| Casos de uso | [docs/CASOS_DE_USO.md](docs/CASOS_DE_USO.md) |
| Diagramas UML (clases, secuencia) | [docs/DIAGRAMAS_UML.md](docs/DIAGRAMAS_UML.md) |
| Modelo entidad-relación y BD | [docs/BASE_DE_DATOS.md](docs/BASE_DE_DATOS.md) |
| API REST (referencia + Swagger) | [docs/API.md](docs/API.md) |
| Plan y casos de prueba | [docs/PLAN_DE_PRUEBAS.md](docs/PLAN_DE_PRUEBAS.md) |
| Manual técnico | [docs/MANUAL_TECNICO.md](docs/MANUAL_TECNICO.md) |
| Manual de usuario | [docs/MANUAL_USUARIO.md](docs/MANUAL_USUARIO.md) |
| Seguridad | [docs/SEGURIDAD.md](docs/SEGURIDAD.md) |

## Seguridad

- JWT firmado (HS256) con expiración corta + refresh token.
- HTTPS extremo a extremo (TLS terminado en el Ingress de GKE).
- Protección SQL Injection (JPA + consultas parametrizadas), XSS (sanitización + cabeceras), CSRF (API stateless con tokens Bearer).
- Cifrado de datos sensibles en reposo (columnas cifradas AES-256) y en tránsito.
- Ver [docs/SEGURIDAD.md](docs/SEGURIDAD.md).

## Licencia

Proyecto propietario — © 2026 NutriScan AI. Todos los derechos reservados.
