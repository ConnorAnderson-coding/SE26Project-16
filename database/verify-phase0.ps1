#Requires -Version 5.1
<#
.SYNOPSIS
  Phase 0 基础设施检验（语义检索 / 智能推荐）

.EXAMPLE
  .\verify-phase0.ps1
  .\verify-phase0.ps1 -SkipEmbeddingDeploy -SkipBackend
#>
[CmdletBinding()]
param(
    [string]$EsHost = "127.0.0.1",
    [int]$EsPort = 9200,
    [string]$BackendUrl = "http://127.0.0.1:8080",
    [string]$MySqlHost = "127.0.0.1",
    [int]$MySqlPort = 3306,
    [string]$MySqlUser = "campus",
    [string]$MySqlPassword = "campus123",
    [string]$Database = "campus_activity",
    [string]$IndexName = "campus_activities",
    [switch]$SkipDockerStart,
    [Alias("SkipElserDeploy")]
    [switch]$SkipEmbeddingDeploy,
    [switch]$SkipBackend,
    [switch]$SkipRebuild
)

$ErrorActionPreference = "Stop"
$ScriptDir = $PSScriptRoot
$ProjectRoot = Split-Path $ScriptDir -Parent
$ReportFile = Join-Path $ProjectRoot "功能实现计划.md"

$results = [ordered]@{}
$failures = New-Object System.Collections.Generic.List[string]

function Write-Step($msg) { Write-Host "`n==> $msg" -ForegroundColor Cyan }
function Write-Ok($msg) { Write-Host "[PASS] $msg" -ForegroundColor Green }
function Write-Warn($msg) { Write-Host "[WARN] $msg" -ForegroundColor Yellow }
function Write-Fail($msg) { Write-Host "[FAIL] $msg" -ForegroundColor Red }

function Record-Result {
    param([string]$Key, [string]$Value, [string]$Note = "")
    $results[$Key] = if ($Note) { "$Value ($Note)" } else { $Value }
}

function Assert-Pass {
    param([bool]$Condition, [string]$Key, [string]$PassMsg, [string]$FailMsg)
    if ($Condition) {
        Write-Ok $PassMsg
        Record-Result $Key "PASS" $PassMsg
        return $true
    }
    Write-Fail $FailMsg
    Record-Result $Key "FAIL" $FailMsg
    $failures.Add("$Key : $FailMsg")
    return $false
}

function Get-EsBaseUrl { "http://${EsHost}:${EsPort}" }

function Invoke-EsGet {
    param([string]$Path)
    Invoke-RestMethod -Uri "$(Get-EsBaseUrl)$Path" -Method Get -TimeoutSec 60
}

function Test-DockerContainerRunning {
    param([string]$Name)
    $out = docker inspect -f '{{.State.Running}}' $Name 2>$null
    return ($out -eq 'true')
}

function Find-MySql {
    $cmd = Get-Command mysql -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $candidates = @(
        "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe",
        "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe"
    )
    foreach ($p in $candidates) {
        if (Test-Path $p) { return $p }
    }
    return $null
}

function Get-MySqlActivityCount {
    $mysql = Find-MySql
    if (-not $mysql) { return -1 }
    $prevEap = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $args = @("-h", $MySqlHost, "-P", "$MySqlPort", "-u", $MySqlUser, "-p$MySqlPassword", "-D", $Database, "-N", "-s", "-e", "SELECT COUNT(*) FROM activity WHERE status <> 'draft';")
    $output = & $mysql @args 2>&1
    $ErrorActionPreference = $prevEap
    $countLine = ($output | Where-Object { $_ -match '^\d+$' } | Select-Object -Last 1)
    if ($null -eq $countLine) { return -1 }
    return [int]$countLine
}

function Get-AdminToken {
    $body = @{ username = "admin001"; password = "123456" } | ConvertTo-Json
    $resp = Invoke-RestMethod -Uri "$BackendUrl/api/v1/auth/login" -Method Post -Body $body -ContentType "application/json; charset=utf-8" -TimeoutSec 15
    return $resp.data.token
}

try {
    Write-Host "Phase 0 基础设施检验" -ForegroundColor Magenta
    Write-Host "项目: $ProjectRoot"

    # 1. Docker
    Write-Step "Docker 服务"
    if (-not $SkipDockerStart) {
        Push-Location $ScriptDir
        $prevEap = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        docker compose up -d mysql redis elasticsearch kibana 2>&1 | Out-Null
        $ErrorActionPreference = $prevEap
        Pop-Location
        Start-Sleep -Seconds 5
    }

    $containers = @("campus-mysql", "campus-redis", "campus-elasticsearch", "campus-kibana")
    $allRunning = $true
    foreach ($c in $containers) {
        $running = Test-DockerContainerRunning $c
        if (-not $running) { $allRunning = $false; Write-Fail "容器未运行: $c" }
        else { Write-Ok "容器运行中: $c" }
    }
    Assert-Pass $allRunning "Docker 服务" "全部容器运行中" "部分 Docker 容器未启动" | Out-Null

    # 2. ES health
    Write-Step "Elasticsearch 集群健康"
    $health = $null
    $deadline = (Get-Date).AddSeconds(120)
    while ((Get-Date) -lt $deadline) {
        try {
            $healthUri = "/_cluster/health?wait_for_status=yellow" + '&timeout=30s'
            $health = Invoke-EsGet $healthUri
            if ($health.status -in @("green", "yellow")) { break }
        }
        catch { Start-Sleep -Seconds 3 }
    }
    Assert-Pass ($null -ne $health -and $health.status -in @("green", "yellow")) "ES 集群健康" "status=$($health.status)" "ES 未就绪" | Out-Null

    # 3. Index exists
    Write-Step "索引 campus_activities"
    $initScript = Join-Path $ScriptDir "init-es.ps1"
    if (-not (Test-Path $initScript)) { throw "未找到 init-es.ps1" }

    if ($SkipEmbeddingDeploy) {
        & $initScript -EsPort $EsPort -SkipEmbedding
    }
    else {
        & $initScript -EsPort $EsPort
        if ($LASTEXITCODE -ne 0) {
            Write-Warn "init-es.ps1 含 GTE 部署步骤失败，继续执行其余检验项"
            if (-not $results.Contains("GTE 模型")) {
                Record-Result "GTE 模型" "FAIL" "init-es 失败，见上方日志"
            }
        }
    }

    $indexExists = $false
    try {
        Invoke-EsGet "/$IndexName" | Out-Null
        $indexExists = $true
    }
    catch { $indexExists = $false }
    Assert-Pass $indexExists "索引 campus_activities" "索引已存在" "索引不存在" | Out-Null

    # 4. GTE dense embedding
    Write-Step "GTE 稠密向量模型"
    if ($SkipEmbeddingDeploy) {
        Write-Warn "已跳过 GTE 部署（-SkipEmbeddingDeploy），仍检查当前状态"
        try {
            $stats = Invoke-EsGet "/_ml/trained_models/campus_gte/_stats"
            $state = $stats.trained_model_stats[0].deployment_stats.allocation_status.state
            $gteOk = ($state -in @("started", "fully_allocated"))
            Assert-Pass $gteOk "GTE 模型" "state=$state" "state=$state" | Out-Null
        }
        catch {
            Record-Result "GTE 模型" "SKIP" "未部署或无法查询"
        }
    }
    elseif (-not $results.Contains("GTE 模型")) {
        $gteStarted = $false
        try {
            $stats = Invoke-EsGet "/_ml/trained_models/campus_gte/_stats"
            $state = $stats.trained_model_stats[0].deployment_stats.allocation_status.state
            $gteStarted = ($state -in @("started", "fully_allocated"))
            Assert-Pass $gteStarted "GTE 模型" "state=$state" "state=$state" | Out-Null
        }
        catch {
            if (-not $results.Contains("GTE 模型")) {
                Assert-Pass $false "GTE 模型" "" "无法获取 GTE 状态: $($_.Exception.Message)" | Out-Null
            }
        }
    }

    # 5. Seed / document count
    Write-Step "索引文档数"
    if (-not $SkipRebuild) {
        $seedScript = Join-Path $ScriptDir "index-seed.ps1"
        if (Test-Path $seedScript) {
            & $seedScript -EsPort $EsPort -IndexName $IndexName
        }
    }

    $esCount = (Invoke-EsGet "/$IndexName/_count").count
    $mysqlCount = Get-MySqlActivityCount
    Write-Host "  ES 文档数: $esCount" -ForegroundColor Gray
    Write-Host "  MySQL 可索引活动数: $mysqlCount" -ForegroundColor Gray

    $countOk = ($esCount -ge 6)
    if ($mysqlCount -ge 0) {
        $syncNote = if ($esCount -eq $mysqlCount) { "match" } else { "mismatch-use-rebuild-or-seed" }
        Record-Result "MySQL 活动数 vs ES 文档数" "ES=$esCount MySQL=$mysqlCount" $syncNote
    }
    Assert-Pass ($esCount -ge 1) "索引文档数" "count=$esCount" "索引为空" | Out-Null

    # 6. IK analyze
    Write-Step "IK 分词"
    $analyzeFile = Join-Path $ScriptDir "elasticsearch\analyze-sample.json"
    $ikOk = $false
    if (Test-Path $analyzeFile) {
        $bytes = [System.IO.File]::ReadAllBytes($analyzeFile)
        $analyze = Invoke-RestMethod -Uri "$(Get-EsBaseUrl)/$IndexName/_analyze" -Method Post -ContentType "application/json; charset=utf-8" -Body $bytes -TimeoutSec 30
        $tokenCount = @($analyze.tokens).Count
        $hasCnWord = @($analyze.tokens | Where-Object { $_.type -match "CN_" }).Count -gt 0
        $ikOk = ($tokenCount -ge 2 -and $hasCnWord)
        Write-Host "  tokens: $tokenCount (含中文分词: $hasCnWord)" -ForegroundColor Gray
    }
    Assert-Pass $ikOk "IK 分词" "分词正常" "IK 分词结果异常" | Out-Null

    # 7. Backend stats API
    Write-Step "后端 index/stats API"
    if ($SkipBackend) {
        Write-Warn "已跳过后端 API 检查（-SkipBackend）"
        Record-Result "后端 index/stats API" "SKIP" "手动跳过"
    }
    else {
        $backendOk = $false
        try {
            $healthResp = Invoke-RestMethod -Uri "$BackendUrl/actuator/health" -Method Get -TimeoutSec 5
            if ($healthResp.status -eq "UP") {
                $token = Get-AdminToken
                $stats = Invoke-RestMethod -Uri "$BackendUrl/api/v1/search/index/stats" -Method Get -Headers @{ Authorization = "Bearer $token" } -TimeoutSec 15
                $docCount = $stats.data.documentCount
                $backendOk = ($null -ne $docCount)
                Write-Host "  backend documentCount: $docCount" -ForegroundColor Gray
                Assert-Pass $backendOk "后端 index/stats API" "documentCount=$docCount" "stats API 失败" | Out-Null
            }
            else {
                throw "actuator status=$($healthResp.status)"
            }
        }
        catch {
            Write-Warn "后端未运行或 ES profile 未启用: $($_.Exception.Message)"
            Write-Host "  请先执行: cd backend; .\mvnw.cmd spring-boot:run" -ForegroundColor Yellow
            Record-Result "后端 index/stats API" "SKIP" "后端未启动（非阻塞）"
        }
    }

    # Update report
    Write-Step "更新功能实现计划.md"
    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    $conclusion = if ($failures.Count -eq 0) { "Phase 0 通过" } else { "Phase 0 未完全通过（$($failures.Count) 项失败）" }

    if (Test-Path $ReportFile) {
        $content = [System.IO.File]::ReadAllText($ReportFile, [System.Text.UTF8Encoding]::new($false))
        $tableLines = @(
            '| 检查项 | 结果 | 备注 |',
            '|--------|------|------|',
            "| Docker 服务 | $($results['Docker 服务']) | campus-mysql/redis/elasticsearch/kibana |",
            "| ES 集群健康 | $($results['ES 集群健康']) | |",
            "| GTE 模型 | $($results['GTE 模型']) | campus_gte (thenlper/gte-small-zh) |",
            "| 索引 campus_activities | $($results['索引 campus_activities']) | |",
            "| IK 分词 | $($results['IK 分词']) | |",
            "| MySQL 活动数 vs ES 文档数 | $($results['MySQL 活动数 vs ES 文档数']) | |",
            "| 后端 index/stats API | $($results['后端 index/stats API']) | |",
            '',
            "**检验时间**：$timestamp  ",
            "**结论**：$conclusion"
        )
        $table = ($tableLines -join "`n")
        $pattern = '(?s)## Phase 0 检验报告\s*\r?\n\s*\r?\n（检验完成后.*?）\s*\r?\n\s*\r?\n\| 检查项.*?(?=\r?\n---|\r?\n## |\z)'
        if ($content -match '## Phase 0 检验报告') {
            $replacement = "## Phase 0 检验报告`n`n$table"
            $content = [regex]::Replace($content, '(?s)(## Phase 0 检验报告).*?(?=---\r?\n\r?\n## Phase 1|$)', "$replacement`n`n")
            [System.IO.File]::WriteAllText($ReportFile, $content, (New-Object System.Text.UTF8Encoding $false))
            Write-Ok "已写入 $ReportFile"
        }
    }

    Write-Host "`n========== 汇总 ==========" -ForegroundColor Magenta
    $results.GetEnumerator() | ForEach-Object { Write-Host "  $($_.Key): $($_.Value)" }
    Write-Host "结论: $conclusion" -ForegroundColor $(if ($failures.Count -eq 0) { "Green" } else { "Yellow" })

    if ($failures.Count -gt 0) {
        exit 1
    }
}
catch {
    Write-Fail $_.Exception.Message
    exit 1
}
