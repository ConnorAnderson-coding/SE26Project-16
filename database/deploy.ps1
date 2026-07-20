#Requires -Version 5.1
<#
.SYNOPSIS
  一键部署：Docker 建库 + Redis + Elasticsearch 初始化（含 GTE 模型）

.DESCRIPTION
  1. docker compose up -d --wait（MySQL 首次启动自动执行 schema.sql + seed.sql）
  2. init-es.ps1（创建 campus_activities 索引、部署 campus_gte、注册 embedding pipeline）
  3. 启动后端后自动从 MySQL 重建活动索引（app.elasticsearch.auto-rebuild-on-startup=true）

.EXAMPLE
  .\deploy.ps1
  标准部署（保留已有数据卷）

.EXAMPLE
  .\deploy.ps1 -ForceRecreateDb
  清空 MySQL/Redis/ES 数据卷后重新建库并初始化 ES

.EXAMPLE
  .\deploy.ps1 -SkipEmbedding
  仅创建 ES 索引与 pipeline，跳过 GTE 下载（可稍后重新执行 deploy.ps1）
#>
[CmdletBinding()]
param(
    [switch]$ForceRecreateDb,
    [switch]$ForceRecreateIndex,
    [Alias("SkipElser")]
    [switch]$SkipEmbedding,
    [switch]$SkipElasticsearch,
    [switch]$StartApps,
    [int]$ElasticsearchPort = 9200
)

$ErrorActionPreference = "Stop"
$ScriptDir = $PSScriptRoot

function Write-Step($Message) {
    Write-Host "`n==> $Message" -ForegroundColor Cyan
}

function Write-Ok($Message) {
    Write-Host "[OK] $Message" -ForegroundColor Green
}

function Write-Warn($Message) {
    Write-Host "[WARN] $Message" -ForegroundColor Yellow
}

function Write-Err($Message) {
    Write-Host "[ERROR] $Message" -ForegroundColor Red
}

try {
    Write-Host "校园活动平台 - 基础设施一键部署" -ForegroundColor Magenta
    Push-Location $ScriptDir

    if ($ForceRecreateDb) {
        Write-Step "清空数据卷并重建容器"
        docker compose down -v
    }

    Write-Step "启动 Docker 服务（MySQL / Redis / Elasticsearch / Kibana）"
    docker compose up -d --wait
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose up 失败，请检查 Docker Desktop 是否运行"
    }
    Write-Ok "Docker 服务已就绪"

    # ForceRecreateDb 后 seed 较大时，healthcheck 可能早于业务表可查；重试读数
    Write-Step "验证 MySQL 示例数据"
    $userCount = $null
    $activityCount = $null
    for ($i = 1; $i -le 30; $i++) {
        $userCount = docker exec campus-mysql mysql -ucampus -pcampus123 -N -e `
            "SELECT COUNT(*) FROM campus_activity.user;" 2>$null
        $activityCount = docker exec campus-mysql mysql -ucampus -pcampus123 -N -e `
            "SELECT COUNT(*) FROM campus_activity.activity;" 2>$null
        if ($userCount -and $activityCount -and ([int]$activityCount -gt 0)) {
            break
        }
        Start-Sleep -Seconds 2
    }
    if ($userCount -and $activityCount) {
        Write-Ok "MySQL: users=$($userCount.Trim()), activities=$($activityCount.Trim())"
        if ($ForceRecreateDb -and [int]$activityCount -lt 100) {
            Write-Warn "活动数偏少 ($($activityCount.Trim()))，seed 可能未完整导入；可运行 .\reload-demo-data.ps1"
        }
    }
    else {
        Write-Warn "无法读取 MySQL 行数（容器可能仍在初始化），稍后可手动验证或运行 .\reload-demo-data.ps1"
    }

    if (-not $SkipElasticsearch) {
        Write-Step "初始化 Elasticsearch（索引 + GTE 模型 + ingest pipeline）"
        $initParams = @{
            EsPort = $ElasticsearchPort
        }
        if ($ForceRecreateIndex) {
            $initParams.ForceRecreateIndex = $true
        }
        if ($SkipEmbedding) {
            $initParams.SkipEmbedding = $true
        }
        & (Join-Path $ScriptDir "init-es.ps1") @initParams
        if ($LASTEXITCODE -ne 0) {
            throw "Elasticsearch 初始化失败，请查看 init-es.ps1 输出"
        }
        Write-Ok "Elasticsearch 初始化完成"
    }
    else {
        Write-Warn "已跳过 Elasticsearch 初始化（-SkipElasticsearch）"
    }

    Write-Host "`n--- 部署完成 ---" -ForegroundColor Magenta
    Write-Host "  MySQL         : localhost:3306 / campus / campus123 / campus_activity" -ForegroundColor White
    Write-Host "  Redis         : localhost:6379" -ForegroundColor White
    Write-Host "  Elasticsearch : http://localhost:$ElasticsearchPort" -ForegroundColor White
    Write-Host "  Kibana        : http://localhost:5601" -ForegroundColor White

    $ProjectRoot = Split-Path $ScriptDir -Parent
    if ($StartApps) {
        $startAppsScript = Join-Path $ProjectRoot "scripts\start-apps.ps1"
        if (-not (Test-Path $startAppsScript)) {
            throw "未找到启动脚本: $startAppsScript"
        }
        Write-Step "启动前后端"
        & $startAppsScript -ProjectRoot $ProjectRoot
    }
    else {
        Write-Host "`n下一步:" -ForegroundColor White
        Write-Host "  .\start.ps1 -SkipDeploy          # 仅启动前后端" -ForegroundColor White
        Write-Host "  .\start.ps1                      # 完整一键启动" -ForegroundColor White
        Write-Host "  后端启动后会自动从 MySQL 重建空的活动索引" -ForegroundColor Gray
        Write-Host "  演示账号: 524030910001 / 123456" -ForegroundColor White
    }
    Write-Ok "完成"
}
catch {
    Write-Err $_.Exception.Message
    exit 1
}
finally {
    Pop-Location
}
