#Requires -Version 5.1
<#
.SYNOPSIS
  在新窗口启动后端 Spring Boot 与前端 Vite 开发服务器。

.EXAMPLE
  .\scripts\start-apps.ps1
#>
[CmdletBinding()]
param(
    [string]$ProjectRoot = "",
    [int]$BackendPort = 8080,
    [int]$FrontendPort = 5173
)

$ErrorActionPreference = "Stop"

if (-not $ProjectRoot) {
    $ProjectRoot = Split-Path $PSScriptRoot -Parent
}

$backendDir = Join-Path $ProjectRoot "backend"
$frontendDir = Join-Path $ProjectRoot "frontend"
$mvnw = Join-Path $backendDir "mvnw.cmd"

function Write-Step($Message) {
    Write-Host "`n==> $Message" -ForegroundColor Cyan
}

function Write-Ok($Message) {
    Write-Host "[OK] $Message" -ForegroundColor Green
}

if (-not (Test-Path $mvnw)) {
    throw "未找到后端启动脚本: $mvnw"
}
if (-not (Test-Path $frontendDir)) {
    throw "未找到前端目录: $frontendDir"
}

Write-Step "启动后端 (Spring Boot :$BackendPort)"
Start-Process powershell -WorkingDirectory $backendDir -ArgumentList @(
    "-NoExit",
    "-Command",
    "Write-Host 'Campus Activity Backend' -ForegroundColor Cyan; .\mvnw.cmd spring-boot:run"
)

Write-Step "启动前端 (Vite :$FrontendPort)"
Start-Process powershell -WorkingDirectory $frontendDir -ArgumentList @(
    "-NoExit",
    "-Command",
    "Write-Host 'Campus Activity Frontend' -ForegroundColor Cyan; if (-not (Test-Path node_modules)) { npm install }; npm run dev"
)

Write-Ok "已在新窗口启动前后端"
Write-Host "  前端: http://localhost:$FrontendPort" -ForegroundColor White
Write-Host "  后端: http://localhost:$BackendPort/api/v1" -ForegroundColor White
Write-Host "  演示账号: 524030910001 / 123456" -ForegroundColor White
Write-Host "  后端首次启动会自动从 MySQL 重建空的 ES 活动索引" -ForegroundColor Gray
