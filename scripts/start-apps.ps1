#Requires -Version 5.1
<#
.SYNOPSIS
  在新窗口启动聚类服务、后端 Spring Boot 与前端 Vite 开发服务器。

.EXAMPLE
  .\scripts\start-apps.ps1
#>
[CmdletBinding()]
param(
    [string]$ProjectRoot = "",
    [int]$BackendPort = 8080,
    [int]$FrontendPort = 5173,
    [int]$ClusteringPort = 8000,
    [switch]$SkipClustering
)

$ErrorActionPreference = "Stop"

if (-not $ProjectRoot) {
    $ProjectRoot = Split-Path $PSScriptRoot -Parent
}

$backendDir = Join-Path $ProjectRoot "backend"
$frontendDir = Join-Path $ProjectRoot "frontend"
$mvnw = Join-Path $backendDir "mvnw.cmd"
$clusteringScript = Join-Path $ProjectRoot "scripts\start-clustering-service.ps1"

function Write-Step($Message) {
    Write-Host "`n==> $Message" -ForegroundColor Cyan
}

function Write-Ok($Message) {
    Write-Host "[OK] $Message" -ForegroundColor Green
}

if (-not (Test-Path $mvnw) -and -not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    throw "未找到 Maven 或后端 Maven Wrapper"
}
if (-not (Test-Path $frontendDir)) {
    throw "未找到前端目录: $frontendDir"
}

$clusteringUrl = "http://127.0.0.1:$ClusteringPort"
$clusteringReady = $false
if (-not $SkipClustering) {
    try {
        $health = Invoke-RestMethod -Uri "$clusteringUrl/internal/v1/health" -TimeoutSec 2
        $clusteringReady = $health.status -eq "UP"
    }
    catch {
        $clusteringReady = $false
    }
    if ($clusteringReady) {
        Write-Ok "聚类服务已运行: $clusteringUrl"
    }
    else {
        if (-not (Test-Path $clusteringScript)) { throw "未找到聚类服务启动脚本: $clusteringScript" }
        Write-Step "启动聚类服务 (FastAPI :$ClusteringPort)"
        Start-Process powershell -WorkingDirectory $ProjectRoot -ArgumentList @(
            "-NoExit", "-File", $clusteringScript, "-ProjectRoot", $ProjectRoot, "-Port", $ClusteringPort
        )
    }
}

$backendLauncher = if (Get-Command mvn -ErrorAction SilentlyContinue) {
    "mvn spring-boot:run"
} else {
    ".\mvnw.cmd spring-boot:run"
}
$clusteringEnabled = if ($SkipClustering) { "false" } else { "true" }

Write-Step "启动后端 (Spring Boot :$BackendPort)"
Start-Process powershell -WorkingDirectory $backendDir -ArgumentList @(
    "-NoExit",
    "-Command",
    "`$env:SERVER_PORT='$BackendPort'; `$env:COMMUNITY_CLUSTERING_ENABLED='$clusteringEnabled'; `$env:COMMUNITY_CLUSTERING_URL='$clusteringUrl'; Write-Host 'Campus Activity Backend' -ForegroundColor Cyan; $backendLauncher"
)

Write-Step "启动前端 (Vite :$FrontendPort)"
Start-Process powershell -WorkingDirectory $frontendDir -ArgumentList @(
    "-NoExit",
    "-Command",
    "Write-Host 'Campus Activity Frontend' -ForegroundColor Cyan; if (-not (Test-Path node_modules)) { npm install }; npm run dev -- --port $FrontendPort"
)

Write-Ok "已启动应用服务"
Write-Host "  前端: http://localhost:$FrontendPort" -ForegroundColor White
Write-Host "  后端: http://localhost:$BackendPort/api/v1" -ForegroundColor White
if (-not $SkipClustering) {
    Write-Host "  聚类健康检查: $clusteringUrl/internal/v1/health" -ForegroundColor White
}
Write-Host "  演示账号: 524030910001 / 123456" -ForegroundColor White
Write-Host "  后端首次启动会自动从 MySQL 重建空的 ES 活动索引" -ForegroundColor Gray
