$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)

# Normalize again in case the project was edited or extracted by a Windows tool.
$linuxScripts = @(
    'infra/app-entrypoint.sh', 'infra/kafka-init.sh', 'infra/mysql-init.sh',
    'infra/aws/host-bootstrap.sh', 'scripts/start.sh',
    'scripts/aws-prepare.sh', 'scripts/renew-tls.sh'
)
$utf8 = New-Object System.Text.UTF8Encoding($false)
foreach ($file in $linuxScripts) {
    $path = Join-Path (Get-Location) $file
    $content = [System.IO.File]::ReadAllText($path).Replace("`r`n", "`n")
    [System.IO.File]::WriteAllText($path, $content, $utf8)
}

# Start existing infrastructure without deleting or recreating its data volumes.
docker compose up -d --wait --wait-timeout 180 mysql kafka mongo
if ($LASTEXITCODE -ne 0) {
    docker compose logs --tail=80 mysql kafka mongo
    throw 'Infrastructure did not become healthy. Share the logs above; no volumes were deleted.'
}

# An interrupted first MySQL initialization can leave the server initialized but
# the application databases/users missing. This script is safe to run again.
docker compose exec -T mysql bash /docker-entrypoint-initdb.d/01-users.sh
if ($LASTEXITCODE -ne 0) { throw 'MySQL application database initialization failed.' }

# Cached build stages are reused. The corrected runtime entrypoint is copied into
# each service image, and failed one-shot initialization services are rerun.
docker compose up --build -d --wait --wait-timeout 600
if ($LASTEXITCODE -ne 0) {
    docker compose logs --tail=80 mysql kafka-init mongo-init trade-service compliance-service investigation-service live-view-service web
    throw 'Startup failed. Share these logs; all data volumes are preserved.'
}

Write-Output 'TradeGuard is ready at http://localhost:8080'
