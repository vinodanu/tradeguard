$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
python scripts/bootstrap.py
if ($LASTEXITCODE -ne 0) { throw 'Bootstrap failed' }
docker compose up --build -d --wait --wait-timeout 600
if ($LASTEXITCODE -ne 0) { throw 'Startup failed; inspect docker compose logs' }
Write-Output 'TradeGuard is ready at http://localhost:8080'
