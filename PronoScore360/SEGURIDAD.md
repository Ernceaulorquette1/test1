# Seguridad y privacidad

## Implementado (con prueba automática salvo indicación)
| Control | Detalle |
|---|---|
| Contraseñas | Argon2id (`argon2-cffi`), re-hash automático; política: ≥10 caracteres con mayúsculas, minúsculas y números |
| Sesiones | Access JWT 15 min + refresh opaco 30 días guardado **sólo como hash**; **rotación** y revocación de toda la familia si se reutiliza un refresh; tope de sesiones por usuario; logout; cambio/reset de contraseña revoca sesiones |
| Fuerza bruta | 8 fallos ⇒ bloqueo 15 min; mismo error para usuario inexistente (con coste de hash simulado); *rate limit* por IP en auth (10/min) y global (120/min) — Redis en producción |
| Autorización | Roles `USER/ADMIN` verificados **en el backend** en cada endpoint admin (React sólo oculta); un admin no puede quitarse el rol ni desactivarse; usuarios desactivados pierden acceso inmediato |
| Cuotas | cálculo del día con reloj del servidor + zona de cuenta (cambio de zona limitado a 1/7 días); `UNIQUE(user,fecha,partido)` impide carreras |
| Pagos | Pro sólo tras verificación del servidor; ID de cuenta ofuscado para vincular compras; token de compra guardado como hash; RTDN autenticado con JWT de Pub/Sub; eventos idempotentes; simulador bloqueado fuera de local/test (también en validación de configuración) |
| Entrada | Pydantic (tipos, rangos, patrones), consultas ORM parametrizadas (sin SQL concatenado), límites de paginación, errores uniformes sin trazas |
| Cabeceras / CORS | CORS por lista blanca, `nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy`, HSTS fuera de dev |
| Secretos | sólo en variables de entorno/archivos montados; `.env`, keystores y JSON de servicio en `.gitignore`; workflow de CI que busca secretos versionados; `.env.example` sin credenciales |
| Producción | `Settings` **se niega a arrancar** con JWT por defecto, sin Redis o con pagos demo; cuentas demo desactivadas fuera de local/test |
| Auditoría | `audit_logs` (login, bloqueo, cambios de plan/ajustes/usuarios, jobs, backtests, pagos) con IP |
| Privacidad | borrado real de cuenta (CASCADE) desde web y Android; recuperación sin revelar si el correo existe; no se guardan tarjetas |
| Dependencias | `pip-audit` y `npm audit` en CI semanal (`security.yml`) — **no ejecutado todavía** en este entorno |

## Riesgos y pendientes (honestos)
* **Tokens web en `localStorage`**: un XSS podría robarlos. Mitigaciones: React escapa por defecto, sin `dangerouslySetInnerHTML`; recomendable añadir CSP estricta en el proxy y evaluar cookies HttpOnly.
* No hay 2FA, ni verificación de correo obligatoria, ni CAPTCHA en registro.
* Falta una **auditoría de seguridad/pentest** independiente y un análisis legal de privacidad (GDPR/Ley 19.628/21.719 en Chile…). Las políticas incluidas son **borradores**.
* El RTDN y Google Sign-In se probaron con simulación, no con Google real.
* Copias de seguridad de PostgreSQL y rotación de secretos: procedimiento documentado, no automatizado.
* Rate limiting por IP depende de `X-Forwarded-For`: confía sólo en tu proxy (Caddy/nginx) y no expongas la API directamente.
