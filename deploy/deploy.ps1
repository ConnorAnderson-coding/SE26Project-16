#Requires -Version 5.1
<#
.SYNOPSIS
  Full-stack Docker deploy: Nginx + frontend + Spring Boot + MySQL + Redis + ES + clustering

.DESCRIPTION
  For Windows cloud / Docker Desktop (Linux containers).
  1) docker compose up -d --build --wait
  2) database/init-es.ps1 for index + GTE (optional -SkipEmbedding)
  Entry: http://localhost or http://<server-ip>

.EXAMPLE
  .\deploy.ps1

.EXAMPLE
  .\deploy.ps1 -SkipEsInit -SkipEmbedding

.EXAMPLE
  .\deploy.ps1 -WithKibana
#>
[CmdletBinding()]
param(
    [switch]$ForceRecreate,
    [switch]$SkipEsInit,
    [Alias("SkipElser")]
    [switch]$SkipEmbedding,
    [switch]$WithKibana,
    [switch]$SkipBuild,
    [string]$EnvFile = ".env"
)

$ErrorActionPreference = "Stop"
$ScriptDir = $PSScriptRoot
$ProjectRoot = Split-Path $ScriptDir -Parent
$DatabaseDir = Join-Path $ProjectRoot "database"

function Write-Step($Message) { Write-Host "`n==> $Message" -ForegroundColor Cyan }
function Write-Ok($Message) { Write-Host "[OK] $Message" -ForegroundColor Green }
function Write-Warn($Message) { Write-Host "[WARN] $Message" -ForegroundColor Yellow }
function Write-Err($Message) { Write-Host "[ERROR] $Message" -ForegroundColor Red }

try {
    Write-Host "Campus Activity - full-stack Docker deploy" -ForegroundColor Magenta
    Push-Location $ScriptDir

    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        throw "docker not found. Install Docker Desktop and enable Linux containers."
    }

    $existingNames = @(docker ps -a --format '{{.Names}}' 2>$null)
    $watched = @(
        'campus-mysql'
        'campus-redis'
        'campus-elasticsearch'
        'campus-backend'
        'campus-nginx'
        'campus-clustering-service'
    )
    $conflict = @($existingNames | Where-Object { $watched -contains $_ })
    if ($conflict.Count -gt 0 -and -not $ForceRecreate) {
        Write-Warn ("Detected existing containers: " + ($conflict -join ', '))
        Write-Warn "Stop the start.ps1 / database stack first, or use -ForceRecreate."
    }

    $envPath = Join-Path $ScriptDir $EnvFile
    if (-not (Test-Path $envPath)) {
        $example = Join-Path $ScriptDir ".env.example"
        if (-not (Test-Path $example)) { throw "Missing .env.example" }
        Copy-Item $example $envPath
        Write-Warn "Created $EnvFile from .env.example. Edit before public deploy."
    }

    if ($ForceRecreate) {
        Write-Step "Remove volumes and recreate"
        docker compose --env-file $EnvFile --profile kibana down -v
        if ($LASTEXITCODE -ne 0) { throw "docker compose down failed" }
    }

    $composeArgs = @("compose", "--env-file", $EnvFile)
    if ($WithKibana) { $composeArgs += @("--profile", "kibana") }
    $composeArgs += @("up", "-d", "--wait")
    if (-not $SkipBuild) { $composeArgs += "--build" }

    Write-Step "Build and start full stack (first build may take several minutes)"
    docker @composeArgs
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose up failed. Ensure Docker Desktop is running, memory >= 8GB, and image registries are reachable."
    }
    Write-Ok "Containers are ready"

    if (-not $SkipEsInit) {
        Write-Step "Initialize Elasticsearch (index + GTE + pipeline)"
        $initScript = Join-Path $DatabaseDir "init-es.ps1"
        if (-not (Test-Path $initScript)) { throw "Missing $initScript" }
        $initParams = @{ EsPort = 9200 }
        if ($SkipEmbedding) { $initParams.SkipEmbedding = $true }
        Get-Content $envPath | ForEach-Object {
            if ($_ -match '^\s*ES_HTTP_PORT\s*=\s*(.+)\s*$') {
                $initParams.EsPort = [int](($Matches[1]).Trim().Trim([char]34))
            }
        }
        & $initScript @initParams
        if ($LASTEXITCODE -ne 0) {
            throw "Elasticsearch init failed"
        }
        Write-Ok "Elasticsearch init completed"
    }
    else {
        Write-Warn "Skipped Elasticsearch init (-SkipEsInit)"
    }

    $httpPort = "80"
    Get-Content $envPath | ForEach-Object {
        if ($_ -match '^\s*HTTP_PORT\s*=\s*(.+)\s*$') {
            $httpPort = ($Matches[1]).Trim().Trim([char]34)
        }
    }
    $base = if ($httpPort -eq "80") { "http://localhost" } else { "http://localhost:$httpPort" }

    Write-Host "`n--- Deploy finished ---" -ForegroundColor Magenta
    Write-Host "  Site     : $base" -ForegroundColor White
    Write-Host "  API      : $base/api/v1" -ForegroundColor White
    Write-Host "  Demo user: 524030910001 / 123456" -ForegroundColor White
    Write-Host "  MySQL    : 127.0.0.1:3307" -ForegroundColor Gray
    Write-Host "  ES       : 127.0.0.1:9200" -ForegroundColor Gray
    Write-Host "`nPerf tip: hit $base/api/v1 with 100 concurrent requests; target P95 < 3s." -ForegroundColor Gray
    Write-Ok "Done"
}
catch {
    Write-Err $_.Exception.Message
    exit 1
}
finally {
    Pop-Location
}