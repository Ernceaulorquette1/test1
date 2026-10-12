<#
 PronoScore360 - preparación en Windows (PowerShell). Requisitos: Docker Desktop (en ejecución) y Git.
 Uso:  powershell -ExecutionPolicy Bypass -File scripts\setup-windows.ps1
 Hace: valida requisitos, crea .env desde .env.example (con JWT_SECRET aleatorio), levanta Docker Compose y espera a que la API responda.
 NO instala Docker, Android Studio ni claves de API por ti.
#>
$ErrorActionPreference = "Stop"
Set-Location (Split-Path -Parent $PSScriptRoot)

function Need($cmd, $hint) { if (-not (Get-Command $cmd -ErrorAction SilentlyContinue)) { Write-Host "Falta '$cmd'. $hint" -ForegroundColor Red; exit 1 } }
Need git "Instala Git para Windows: https://git-scm.com/download/win"
Need docker "Instala Docker Desktop: https://www.docker.com/products/docker-desktop/ y ábrelo."
docker info *> $null; if ($LASTEXITCODE -ne 0) { Write-Host "Docker Desktop no está en ejecución. Ábrelo y espera a que diga 'Running'." -ForegroundColor Red; exit 1 }

if (-not (Test-Path .env)) {
  Copy-Item .env.example .env
  $bytes = New-Object byte[] 48; [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
  $secret = [Convert]::ToBase64String($bytes) -replace '[+/=]', 'x'
  (Get-Content .env) -replace '^JWT_SECRET=.*', "JWT_SECRET=$secret" | Set-Content .env
  Write-Host "Creado .env con un JWT_SECRET aleatorio. Revisa PROVIDER_MODE (demo por defecto)." -ForegroundColor Green
} else { Write-Host ".env ya existe: no se modifica." }

docker compose up -d --build
if ($LASTEXITCODE -ne 0) { Write-Host "docker compose falló. Revisa el mensaje anterior." -ForegroundColor Red; exit 1 }

Write-Host "Esperando a la API (la primera vez tarda: migraciones + datos DEMO)..."
$ok = $false
for ($i = 0; $i -lt 90; $i++) {
  try { if ((Invoke-RestMethod http://localhost:8000/api/v1/health -TimeoutSec 2).status -eq "ok") { $ok = $true; break } } catch { }
  Start-Sleep -Seconds 2
}
if (-not $ok) { Write-Host "La API no respondió. Mira los logs:  docker compose logs api" -ForegroundColor Red; exit 1 }

Write-Host "`nListo:" -ForegroundColor Green
Write-Host "  Web:      http://localhost:5173"
Write-Host "  API/Swagger: http://localhost:8000/docs"
Write-Host "  Cuentas DEMO (sólo local): admin@pronoscore-demo.example.com / Demo#Admin2026 ; pro@... / Demo#Pro2026 ; free@... / Demo#Free2026"
Write-Host "  Android: abre la carpeta 'android' en Android Studio (ver INSTALL_WINDOWS.md)."
