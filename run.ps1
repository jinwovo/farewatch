# farewatch backend launcher.
#
# Why this exists: Spring does NOT read .env files (that's a Node convention). The real
# fare sources (Travelpayouts/Amadeus) are config-gated on environment variables, so a
# plain `java -jar` after a restart silently degrades to no-op providers and watches stop
# getting fresh data. This script injects .env into the process environment first, brings
# the DB/Redis containers up, and launches the jar memory-bounded (dispatch runs -Xmx3g
# on this machine — the backend must stay small).
#
#   .\run.ps1           # start containers + run the jar (build it first if missing)
#   .\run.ps1 -Build    # force a fresh bootJar before launching

param(
    [switch]$Build
)

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$jar = Join-Path $root 'build\libs\farewatch-0.0.1-SNAPSHOT.jar'

# --- 1. .env -> process environment -------------------------------------------------
# Accepts both the standard names (TRAVELPAYOUTS_TOKEN=...) and the short aliases the
# .env currently uses (Travelpaypouts=..., AMADEUS=key:secret).
$dotenv = Join-Path $root '.env'
if (Test-Path $dotenv) {
    foreach ($line in Get-Content $dotenv) {
        if ($line -match '^\s*(#|$)') { continue }
        $name, $value = $line -split '=', 2
        $name = $name.Trim(); $value = $value.Trim().Trim('"')
        if (-not $name -or -not $value) { continue }
        switch -Regex ($name) {
            '^(Travelpaypouts|Travelpayouts|TRAVELPAYOUTS_TOKEN)$' {
                $env:TRAVELPAYOUTS_TOKEN = $value
                $env:TRAVELPAYOUTS_ENABLED = 'true'
                Write-Host 'env: TRAVELPAYOUTS_TOKEN set (source enabled)'
            }
            '^AMADEUS$' {
                # single AMADEUS entry = "key:secret"; keep the source OFF the sweep
                # (metered free tier) - it turns on only with an explicit AMADEUS_ENABLED=true
                $k, $s = $value -split ':', 2
                if ($k -and $s) {
                    $env:AMADEUS_API_KEY = $k
                    $env:AMADEUS_API_SECRET = $s
                    Write-Host 'env: AMADEUS credentials set (source stays disabled unless AMADEUS_ENABLED=true)'
                }
            }
            '^(AMADEUS_API_KEY|AMADEUS_API_SECRET|AMADEUS_ENABLED)$' {
                Set-Item -Path "env:$name" -Value $value
                Write-Host "env: $name set"
            }
        }
    }
} else {
    Write-Host 'no .env found - real fare sources will be no-op (simulator only)'
}

# --- 2. containers (fw-postgres :5435, fw-redis :6381) ------------------------------
docker compose -f (Join-Path $root 'docker-compose.yml') up -d
# wait until Postgres actually accepts connections (docker up returns before ready)
$deadline = (Get-Date).AddSeconds(60)
while ($true) {
    docker exec fw-postgres pg_isready -U farewatch -q 2>$null
    if ($LASTEXITCODE -eq 0) { break }
    if ((Get-Date) -gt $deadline) { throw 'fw-postgres did not become ready within 60s' }
    Start-Sleep -Seconds 2
}
Write-Host 'fw-postgres ready'

# --- 3. jar ---------------------------------------------------------------------------
if ($Build -or -not (Test-Path $jar)) {
    Write-Host 'building bootJar...'
    & (Join-Path $root 'gradlew.bat') bootJar -q
    if ($LASTEXITCODE -ne 0) { throw 'bootJar build failed' }
}

Write-Host "starting farewatch on :8101 (jar: $jar)"
java -Xmx512m -jar $jar
