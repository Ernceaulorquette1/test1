# Seguridad — NutriScan AI

## Autenticación y autorización
- **Firebase Authentication** (Google/Apple/email) en el cliente; el backend **verifica el ID token** con Firebase Admin y emite **JWT propios**: access (30 min) + refresh (30 días), firmados HS256 con secreto ≥ 256 bits.
- Tipo de token verificado en cada validación (un refresh no sirve como access — cubierto por tests).
- Claims mínimos (userId, premium). API stateless: sin sesiones ni cookies.
- Todo recurso se consulta **scoped al usuario del token** (`findByIdAndUserId`), impidiendo acceso horizontal a datos ajenos.

## Protección OWASP
| Amenaza | Mitigación |
|---|---|
| SQL Injection | JPA/Hibernate con consultas parametrizadas; sin SQL concatenado; validación de entrada (Bean Validation) y `CHECK` en BD |
| XSS | API JSON pura (sin render HTML); escape en cliente Flutter; `X-Content-Type-Options: nosniff` por defecto de Spring Security |
| CSRF | No aplicable a API stateless con Bearer tokens (CSRF deshabilitado explícita y justificadamente) |
| Fuerza bruta / abuso | Cuota diaria por usuario en análisis IA; rate limiting adicional en el Ingress/Cloud Armor |
| Exposición de datos | `include-stacktrace: never`, handler global de errores con mensajes genéricos |
| Dependencias vulnerables | CI con build reproducible; revisión de dependencias en cada release |

## Facturación
- Los purchase tokens de Google Play se **verifican en el servidor** (`purchases.subscriptionsv2.get`) antes de activar Premium: un cliente manipulado no puede autoconcederse la suscripción. El modo `dev` (sin verificación) queda restringido a entornos de staging.

## Cifrado
- **En tránsito**: HTTPS extremo a extremo (certificado gestionado de Google en el Ingress; `allow-http: false`).
- **En reposo**: discos cifrados por defecto en Google Cloud (AES-256); secretos fuera del código (`Secret` de Kubernetes / Secret Manager); fotos en Firebase Storage con reglas por usuario.
- Contraseñas: nunca llegan al backend (las gestiona Firebase con scrypt).

## Contenedores e infraestructura
- Imagen mínima JRE Alpine, usuario **no root**, `readOnlyRootFilesystem`, capabilities eliminadas.
- Secretos inyectados por entorno, nunca en la imagen ni en el repositorio (`.gitignore` cubre `.env`, `secrets.yaml`, service accounts).
- Probes de liveness/readiness y HPA: resiliencia ante picos y fallos.

## Privacidad (Google Play Data Safety)
- Datos recopilados: email, nombre, medidas corporales, fotos de comidas y registros de consumo.
- Eliminación de cuenta = borrado en cascada de todos los datos (FK `ON DELETE CASCADE`).
- Las fotos analizadas no se persisten en el backend; solo se suben a Storage si el usuario guarda la comida.
