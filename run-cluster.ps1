# farewatch multi-instance launcher — the distributed sweep, made visible.
#
# Starts N backend instances on ports 8101, 8102, ... all sharing the ONE fw-postgres +
# fw-redis, plus the Prometheus + Grafana observability stack. Every instance runs the sweep
# and drains its share of the shared Redis-Streams consumer group, so the Grafana panel
# "sweep · poll throughput by instance" shows the load splitting across the fleet with
# exactly-once delivery (no double-poll). Open the dashboard at http://localhost:3004.
#
#   .\run-cluster.ps1                 # 2 instances (:8101,:8102) + observability
#   .\run-cluster.ps1 -Instances 3    # 3 instances (:8101..:8103)
#   .\run-cluster.ps1 -Simulator      # also enable the offline SIMULATOR source (rich demo data, no token/network)
#   .\run-cluster.ps1 -Build          # rebuild the jar first
#
# Each instance is -Xmx-bounded (default 384m) so the fleet stays small on a shared host.
# A shortened sweep interval (default 5s vs the 60s production heartbeat) keeps the dashboard
# lively during a demo. Stop everything with .\stop-cluster.ps1.

param(
    [int]$Instances = 2,
    [string]$Xmx = '384m',
    [int]$SweepIntervalMs = 5000,
    [switch]$Simulator,
    [switch]$Build
)

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$jar = Join-Path $root 'build\libs\farewatch-0.0.1-SNAPSHOT.jar'
$logDir = Join-Path $root 'build\cluster-logs'
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

# --- 1. .env -> process environment (real fare sources are env-gated; Spring does not read .env) ---
$dotenv = Join-Path $root '.env'
if (Test-Path $dotenv) {
    foreach ($line in Get-Content $dotenv) {
        if ($line -match '^\s*(#|$)') { continue }
        $name, $value = $line -split '=', 2
        $name = $name.Trim(); $value = $value.Trim().Trim('"')
        if (-not $name -or -not $value) { continue }
        switch -Regex ($name) {
            '^(Travelpaypouts|Travelpayouts|TRAVELPAYOUTS_TOKEN)$' {
                $env:TRAVELPAYOUTS_TOKEN = $value; $env:TRAVELPAYOUTS_ENABLED = 'true'
                Write-Host 'env: TRAVELPAYOUTS_TOKEN set (source enabled)'
            }
            '^AMADEUS$' {
                $k, $s = $value -split ':', 2
                if ($k -and $s) { $env:AMADEUS_API_KEY = $k; $env:AMADEUS_API_SECRET = $s }
            }
            '^(AMADEUS_API_KEY|AMADEUS_API_SECRET|AMADEUS_ENABLED)$' { Set-Item -Path "env:$name" -Value $value }
        }
    }
}

# --- 2. infra + observability (pg :5435, redis :6381, prometheus :9096, grafana :3004) ---
docker compose -f (Join-Path $root 'docker-compose.yml') up -d
$deadline = (Get-Date).AddSeconds(60)
while ($true) {
    docker exec fw-postgres pg_isready -U farewatch -q 2>$null
    if ($LASTEXITCODE -eq 0) { break }
    if ((Get-Date) -gt $deadline) { throw 'fw-postgres did not become ready within 60s' }
    Start-Sleep -Seconds 2
}
Write-Host 'fw-postgres ready'

# --- 3. jar ---
if ($Build -or -not (Test-Path $jar)) {
    Write-Host 'building bootJar...'
    & (Join-Path $root 'gradlew.bat') bootJar -q
    if ($LASTEXITCODE -ne 0) { throw 'bootJar build failed' }
}

# --- 4. optional: enable the offline simulator source for rich, dependency-free demo data ---
if ($Simulator) {
    docker exec fw-postgres psql -U farewatch -d farewatch -c "UPDATE fare_source SET enabled=true WHERE code='SIMULATOR';" | Out-Null
    Write-Host 'source: SIMULATOR enabled (offline demo data)'
}

# --- 5. launch N instances on :8101, :8102, ... sharing the one PG + Redis ---
$env:SWEEP_INTERVAL_MS = "$SweepIntervalMs"
$env:SWEEP_INITIAL_DELAY_MS = '5000'
$procs = @()
for ($i = 0; $i -lt $Instances; $i++) {
    $port = 8101 + $i
    $env:SERVER_PORT = "$port"
    $log = Join-Path $logDir "fw-$port.log"
    $p = Start-Process -FilePath 'java' `
        -ArgumentList "-Xmx$Xmx", '-jar', $jar `
        -RedirectStandardOutput $log -RedirectStandardError "$log.err" `
        -PassThru -WindowStyle Hidden
    $procs += [pscustomobject]@{ Port = $port; Pid = $p.Id; Log = $log }
    Write-Host "instance :$port starting (pid $($p.Id)) -> $log"
    # Wait for THIS instance to be healthy before launching the next. Booting multiple JVMs
    # at once starves each other's CPU on a shared host and Spring context init can stall, so
    # boot them one at a time (also sidesteps any Flyway migration race on first run).
    $healthy = $false; $wait = (Get-Date).AddSeconds(120)
    while ((Get-Date) -lt $wait) {
        Start-Sleep -Seconds 3
        try { if ((Invoke-RestMethod "http://localhost:$port/actuator/health" -TimeoutSec 3).status -eq 'UP') { $healthy = $true; break } } catch {}
    }
    if ($healthy) { Write-Host "instance :$port UP" } else { Write-Host "instance :$port did not report UP within 120s (check $log)" }
}

# --- 6. summary ---
Write-Host ''
Write-Host '=== farewatch cluster up ==='
$procs | Format-Table -AutoSize
Write-Host "Grafana   : http://localhost:3004  (anonymous, farewatch dashboard)"
Write-Host "Prometheus: http://localhost:9096/targets"
Write-Host "Stop with : .\stop-cluster.ps1"
