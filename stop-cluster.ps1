# Stop the farewatch cluster started by run-cluster.ps1.
#
# Kills the host JVM instances on :8101.. and stops the Prometheus + Grafana containers.
# Leaves fw-postgres / fw-redis running (shared, long-lived infra — like the other projects).
#
#   .\stop-cluster.ps1              # stop instances :8101..:8105 + observability
#   .\stop-cluster.ps1 -Ports 8101,8102,8103

param(
    [int[]]$Ports = @(8101, 8102, 8103, 8104, 8105)
)

$root = $PSScriptRoot

foreach ($port in $Ports) {
    $conns = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    foreach ($c in $conns) {
        try {
            Stop-Process -Id $c.OwningProcess -Force -ErrorAction Stop
            Write-Host "stopped instance on :$port (pid $($c.OwningProcess))"
        } catch { }
    }
}

# stop the observability containers; keep pg/redis up
docker compose -f (Join-Path $root 'docker-compose.yml') stop grafana prometheus | Out-Null
Write-Host 'stopped fw-grafana, fw-prometheus (fw-postgres / fw-redis left running)'
