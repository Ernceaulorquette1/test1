# Manual Técnico — NutriScan AI

## 1. Requisitos de desarrollo
- JDK 17+ y Maven 3.9+ (backend)
- Flutter estable ≥ 3.22 y Android Studio (mobile)
- Docker + Docker Compose (entorno local)
- Proyecto Firebase y claves de OpenAI y/o Gemini

## 2. Entorno local

```bash
git clone <repo> && cd <repo>
cp .env.example .env            # completar OPENAI_API_KEY / GEMINI_API_KEY
docker compose up --build       # API :8080, PostgreSQL :5432, Redis :6379
```

Verificación: `curl localhost:8080/actuator/health` → `{"status":"UP"}` y Swagger en `http://localhost:8080/swagger-ui.html`.

### Backend sin Docker
```bash
cd backend
mvn spring-boot:run     # requiere PostgreSQL y Redis locales
mvn test                # 12 tests unitarios
```

### App móvil
```bash
cd mobile
flutter create --org ai.nutriscan --platforms android,ios .   # genera android/ e ios/
flutterfire configure                                          # vincula Firebase
flutter pub get
flutter run --dart-define=API_BASE_URL=http://10.0.2.2:8080
```

## 3. Configuración (variables de entorno del backend)

| Variable | Descripción |
|---|---|
| `SPRING_DATASOURCE_URL/USERNAME/PASSWORD` | Conexión PostgreSQL |
| `SPRING_DATA_REDIS_HOST` | Host de Redis |
| `NUTRISCAN_JWT_SECRET` | Secreto HS256 (≥ 256 bits) |
| `NUTRISCAN_AI_PROVIDER` | `openai` o `gemini` (modelo intercambiable) |
| `OPENAI_API_KEY` / `GEMINI_API_KEY` | Credenciales de IA |
| `GOOGLE_APPLICATION_CREDENTIALS` | Service account Firebase (local); en GKE usar Workload Identity |
| `NUTRISCAN_BILLING_MODE` | `play` = verificación real contra Google Play Developer API; `dev` = sin validación externa (solo staging) |
| `NUTRISCAN_PACKAGE_NAME` | Package de la app en Play Console (para verificar compras) |
| `NUTRISCAN_NOTIFICATIONS_ENABLED` | `true` activa recordatorios push FCM (agua y cena) |

## 4. Cambiar el proveedor de IA
El puerto `ai.nutriscan.domain.ai.VisionProvider` desacopla el negocio del modelo:
1. Cambio rápido: `NUTRISCAN_AI_PROVIDER=gemini` y reiniciar.
2. Modelo nuevo (p. ej. propio): implementar `VisionProvider`, registrarlo como bean con un qualifier y añadir el caso en `AiProviderConfig`. Ningún servicio de negocio cambia.

## 5. Despliegue en producción (GKE)
Ver `k8s/README.md`. Resumen: build de imagen → Artifact Registry → `kubectl apply` de namespace, config, secrets, postgres/redis (o Cloud SQL/Memorystore), deployment con HPA e ingress HTTPS con certificado gestionado.

## 6. Publicación en Google Play
1. `flutter build appbundle --release` (firmar con keystore propio).
2. Crear los productos de suscripción `nutriscan_premium_monthly` y `nutriscan_premium_annual` en Play Console.
   En producción configurar `NUTRISCAN_BILLING_MODE=play`: el backend valida cada purchase token
   contra `purchases.subscriptionsv2.get` (requiere service account con rol "Ver información
   financiera" vinculada en Play Console).
3. Completar Data Safety (se recopilan: email, medidas corporales, fotos de comidas — cifradas en tránsito; eliminables bajo demanda).
4. Pruebas internas → cerradas → producción escalonada (10% → 50% → 100%).

## 7. Estructura de ramas y CI
- `main` protegida; features en ramas `claude/*` o `feature/*` con PR.
- CI (GitHub Actions): build+tests backend, analyze+tests Flutter, build de imagen Docker.
