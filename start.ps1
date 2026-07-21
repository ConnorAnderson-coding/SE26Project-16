#Requires -Version 5.1
<#
.SYNOPSIS
  一键启动：Docker 基础设施 + ES 初始化 + 后端 + 前端

.DESCRIPTION
  1. database/deploy.ps1 — MySQL / Redis / Elasticsearch（含 GTE 模型）
  2. scripts/start-apps.ps1 — 新窗口启动 Spring Boot 与 Vite

.EXAMPLE
  .\start.ps1
  标准一键启动（保留已有数据卷）

.EXAMPLE
  .\start.ps1 -SkipDeploy
  仅启动前后端（假定 Docker 服务已在运行）

.EXAMPLE
  .\start.ps1 -InfraOnly
  仅部署基础设施，不启动前后端

.EXAMPLE
  .\start.ps1 -ForceRecreateDb
  清空数据卷后重新建库并启动全部服务
#>
[CmdletBinding()]
param(
    [switch]$SkipDeploy,
    [switch]$InfraOnly,
    [switch]$ForceRecreateDb,
    [switch]$ForceRecreateIndex,
    [Alias("SkipElser")]
    [switch]$SkipEmbedding,
    [switch]$SkipElasticsearch
)

$ErrorActionPreference = "Stop"
$ProjectRoot = $PSScriptRoot
$DatabaseDir = Join-Path $ProjectRoot "database"
$DeployScript = Join-Path $DatabaseDir "deploy.ps1"
$StartAppsScript = Join-Path $ProjectRoot "scripts\start-apps.ps1"

function Write-Err($Message) {
    Write-Host "[ERROR] $Message" -ForegroundColor Red
}

try {
    Write-Host "校园活动平台 - 一键启动" -ForegroundColor Magenta

    if (-not $SkipDeploy) {
        if (-not (Test-Path $DeployScript)) {
            throw "未找到部署脚本: $DeployScript"
        }
        $deployParams = @{}
        if ($ForceRecreateDb) { $deployParams.ForceRecreateDb = $true }
        if ($ForceRecreateIndex) { $deployParams.ForceRecreateIndex = $true }
        if ($SkipEmbedding) { $deployParams.SkipEmbedding = $true }
        if ($SkipElasticsearch) { $deployParams.SkipElasticsearch = $true }
        & $DeployScript @deployParams
        if ($LASTEXITCODE -ne 0) {
            exit $LASTEXITCODE
        }
    }
    else {
        Write-Host "[跳过] 基础设施部署（-SkipDeploy）" -ForegroundColor Yellow
    }

    if ($InfraOnly) {
        Write-Host "[完成] 仅部署基础设施（-InfraOnly）" -ForegroundColor Green
        exit 0
    }

    if (-not (Test-Path $StartAppsScript)) {
        throw "未找到启动脚本: $StartAppsScript"
    }
    & $StartAppsScript -ProjectRoot $ProjectRoot
}
catch {
    Write-Err $_.Exception.Message
    exit 1
}
