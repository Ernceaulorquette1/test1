# Publicación: GitHub, web, backend, PostgreSQL, HTTPS, dominios

## GitHub
```bash
git init && git add . && git commit -m "PronoScore360"
git remote add origin git@github.com:TU_USUARIO/pronoscore360.git && git push -u origin main
```
* `.gitignore` excluye `.env`, keystores y JSON de servicio. **Revisa `git status` antes de cada push**; el workflow `security.yml` falla si detecta secretos típicos.
* CI (`.github/workflows/ci.yml`): analytics, backend con PostgreSQL, web, integración y build Android.
* Si el proyecto vive en una subcarpeta de un repositorio mayor, usa el workflow `pronoscore360-ci.yml` de la raíz.

## Despliegue de referencia (un servidor cloud: VPS/EC2/Droplet…)
1. Servidor Linux con Docker; dominios `app.tudominio.com` y `api.tudominio.com` con registros A al servidor.
2. `cp infrastructure/.env.prod.example .env.prod` y completa **todos** los secretos (`openssl rand -base64 48`). `PAYMENTS_PROVIDER=none`, `PROVIDER_MODE=real` (con tus claves) o `demo`.
3. `docker compose -f infrastructure/docker-compose.prod.yml --env-file .env.prod up -d --build`
   * **Caddy** emite y renueva HTTPS automáticamente. PostgreSQL y Redis no se exponen.
   * Las migraciones se aplican al arrancar la API (`alembic upgrade head`). Con varias réplicas de API, ejecútalas como paso previo.
   * `worker` ejecuta las tareas programadas (un solo proceso).
4. Verifica: `curl https://api.tudominio.com/api/v1/ready`, `https://api.tudominio.com/docs`.
5. Primer administrador en producción (no hay cuentas demo): regístrate en la web y promuévete con SQL: `UPDATE users SET role_id=(SELECT id FROM roles WHERE code='ADMIN') WHERE email='tu@correo';`
6. Copias de seguridad: `docker compose exec db pg_dump -U $POSTGRES_USER $POSTGRES_DB | gzip > backup_$(date +%F).sql.gz` programado (cron) y copiado fuera del servidor; **prueba la restauración**.
> `docker-compose.prod.yml` y Caddy **no se probaron** en el entorno de generación (sin daemon Docker).

## Alternativas
* Web estática (`web/dist`) en Netlify/Vercel/Cloudflare Pages con `VITE_API_URL=https://api.tudominio.com/api/v1` en tiempo de *build*; backend en Fly.io/Render/Cloud Run + PostgreSQL gestionado (Neon, RDS, Cloud SQL) + Redis gestionado.
* Cloud Run: ejecuta el worker como servicio aparte o usa Cloud Scheduler llamando a un endpoint/CLI.

## Checklist previo a producción → [docs/PENDIENTES_PRODUCCION.md](docs/PENDIENTES_PRODUCCION.md)
