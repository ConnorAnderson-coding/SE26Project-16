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
    [int]$MysqlPort = 0,
    [int]$RedisPort = 6379,
    [int]$ElasticsearchPort = 9200,
    [int]$KibanaPort = 5601,
    [int]$ClusteringPort = 8000,
    [switch]$SkipElasticsearch,
    [switch]$SkipClustering
)

$ErrorActionPreference = "Stop"

if (-not $ProjectRoot) {
    $ProjectRoot = Split-Path $PSScriptRoot -Parent
}

if ($MysqlPort -le 0) {
    if ($env:MYSQL_PORT) {
        $MysqlPort = [int]$env:MYSQL_PORT
    }
    else {
        $MysqlPort = 3307
    }
}

# 与 deploy.ps1 映射端口对齐；Spring Boot 读 DB_URL，不读 MYSQL_PORT
if (-not $env:DB_URL) {
    $env:DB_URL = "jdbc:mysql://localhost:${MysqlPort}/campus_activity?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=UTF-8&connectionCollation=utf8mb4_unicode_ci"
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

function Throw-JdkRequirement($Detail) {
    throw "$Detail 本项目后端要求 JDK 25，请检查 JAVA_HOME 和 Path。"
}

function Test-Jdk25Home([string]$Candidate) {
    if ([string]::IsNullOrWhiteSpace($Candidate)) { return $false }
    $javaExe = Join-Path $Candidate "bin\java.exe"
    if (-not (Test-Path $javaExe -PathType Leaf)) { return $false }
    $saved = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $versionText = & $javaExe -version 2>&1 | Out-String
    }
    finally {
        $ErrorActionPreference = $saved
    }
    return $versionText -match '(?im)version\s+"25(?:[.\-+_"]|$)'
}

function Resolve-JavaHome25 {
    foreach ($scope in @("Process", "User", "Machine")) {
        $candidate = [Environment]::GetEnvironmentVariable("JAVA_HOME", $scope)
        if (Test-Jdk25Home $candidate) {
            return $candidate.TrimEnd('\')
        }
    }

    $searchRoots = @(
        "C:\Program Files\Java",
        "C:\Program Files\Eclipse Adoptium",
        "C:\Program Files\Microsoft",
        "C:\Program Files\Amazon Corretto",
        "C:\Program Files\Zulu"
    )
    foreach ($root in $searchRoots) {
        if (-not (Test-Path $root)) { continue }
        $matches = Get-ChildItem $root -Directory -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -match '(?i)jdk-?25' } |
            Sort-Object Name -Descending
        foreach ($dir in $matches) {
            if (Test-Jdk25Home $dir.FullName) {
                return $dir.FullName
            }
        }
    }
    return $null
}

if (-not (Test-Path $mvnw)) {
    throw "未找到后端 Maven Wrapper: $mvnw"
}
if (-not (Test-Path $frontendDir)) {
    throw "未找到前端目录: $frontendDir"
}
if (-not (Test-Path (Join-Path $frontendDir "package.json"))) {
    throw "未找到前端 package.json: $frontendDir"
}
if (-not (Get-Command npm.cmd -ErrorAction SilentlyContinue)) {
    throw "未找到 npm.cmd，请安装 Node.js 并检查 Path。"
}

$javaHome = Resolve-JavaHome25
if (-not $javaHome) {
    Throw-JdkRequirement "未找到可用的 JDK 25（JAVA_HOME 未设置或指向非 25 版本）。"
}
$env:JAVA_HOME = $javaHome
$javaHomeBin = Join-Path $javaHome "bin"
if ($env:Path -notlike "*${javaHomeBin}*") {
    $env:Path = "$javaHomeBin;$env:Path"
}
$javaHomeExecutable = Join-Path $javaHomeBin "java.exe"
Write-Ok "使用 JAVA_HOME=$javaHome"

$savedErrorActionPreference = $ErrorActionPreference
$ErrorActionPreference = "Continue"
try {
    $javaVersionLines = & $javaHomeExecutable -version 2>&1
    $javaVersionExitCode = $LASTEXITCODE
    $mavenVersionLines = & $mvnw -v 2>&1
    $mavenVersionExitCode = $LASTEXITCODE
}
finally {
    $ErrorActionPreference = $savedErrorActionPreference
}

$javaVersionText = $javaVersionLines -join [Environment]::NewLine
if ($javaVersionExitCode -ne 0 -or $javaVersionText -notmatch '(?im)version\s+"25(?:[.\-+_"]|$)') {
    Throw-JdkRequirement "java -version 未报告 Java 25。"
}

$mavenVersionText = $mavenVersionLines -join [Environment]::NewLine
if ($mavenVersionExitCode -ne 0) {
    Throw-JdkRequirement "Maven Wrapper 版本检查失败。"
}
if ($mavenVersionText -notmatch '(?im)Java version:\s*25(?:[.\-+,_\s]|$)') {
    Throw-JdkRequirement "Maven Wrapper 未使用 Java 25。"
}
Write-Ok "JDK 25 与 Maven Wrapper 检查通过"

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
        if (-not (Get-Command python -ErrorAction SilentlyContinue)) {
            throw "未找到 Python。社区聚类服务需要 Python 3.11 或更高版本。"
        }
        Write-Step "启动聚类服务 (FastAPI :$ClusteringPort)"
        Start-Process powershell -WorkingDirectory $ProjectRoot -ArgumentList @(
            "-NoExit", "-File", $clusteringScript, "-ProjectRoot", $ProjectRoot, "-Port", $ClusteringPort
        )
        Write-Ok "聚类服务启动命令已在新窗口发起"
    }
}

$clusteringEnabled = if ($SkipClustering) { "false" } else { "true" }
$elasticsearchEnabled = if ($SkipElasticsearch) { "false" } else { "true" }
$dbUrl = "jdbc:mysql://localhost:$MysqlPort/campus_activity?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=UTF-8&connectionCollation=utf8mb4_unicode_ci"
$corsOrigins = "http://localhost:$FrontendPort,http://127.0.0.1:$FrontendPort"
$frontendApiUrl = "http://localhost:$BackendPort/api/v1"

Write-Step "启动后端 (Spring Boot :$BackendPort, MySQL :$MysqlPort)"
Start-Process powershell -WorkingDirectory $backendDir -ArgumentList @(
    "-NoExit",
    "-Command",
    "`$env:JAVA_HOME='$javaHome'; `$env:Path='$javaHomeBin;' + `$env:Path; `$env:SERVER_PORT='$BackendPort'; `$env:DB_URL='$dbUrl'; `$env:REDIS_HOST='localhost'; `$env:REDIS_PORT='$RedisPort'; `$env:ELASTICSEARCH_URIS='http://localhost:$ElasticsearchPort'; `$env:ES_ENABLED='$elasticsearchEnabled'; `$env:ES_AUTO_REBUILD='$elasticsearchEnabled'; `$env:COMMUNITY_CLUSTERING_ENABLED='$clusteringEnabled'; `$env:COMMUNITY_CLUSTERING_URL='$clusteringUrl'; `$env:CORS_ORIGINS='$corsOrigins'; `$env:JACCOUNT_REDIRECT_URI='http://localhost:$BackendPort/api/v1/auth/jaccount/callback'; `$env:JACCOUNT_FRONTEND_CALLBACK_URI='http://localhost:$FrontendPort/oauth/callback'; `$env:JACCOUNT_FRONTEND_LOGOUT_URI='http://localhost:$FrontendPort/?from=logout'; Write-Host 'Campus Activity Backend' -ForegroundColor Cyan; Write-Host `"JAVA_HOME=`$env:JAVA_HOME`" -ForegroundColor Gray; .\mvnw.cmd spring-boot:run"
)
Write-Ok "后端启动命令已在新窗口发起"

Write-Step "启动前端 (Vite :$FrontendPort)"
Start-Process powershell -WorkingDirectory $frontendDir -ArgumentList @(
    "-NoExit",
    "-Command",
    "`$env:VITE_API_BASE_URL='$frontendApiUrl'; Write-Host 'Campus Activity Frontend' -ForegroundColor Cyan; if (-not (Test-Path node_modules)) { npm.cmd install; if (`$LASTEXITCODE -ne 0) { throw 'npm install 失败' } }; npm.cmd run dev -- --port $FrontendPort"
)
Write-Ok "前端启动命令已在新窗口发起"

Write-Host "`n--- 应用启动命令已发起，请以各窗口输出为准 ---" -ForegroundColor Magenta
Write-Host "  前端: http://localhost:$FrontendPort" -ForegroundColor White
Write-Host "  后端: http://localhost:$BackendPort/api/v1" -ForegroundColor White
Write-Host "  MySQL: localhost:$MysqlPort" -ForegroundColor White
Write-Host "  Redis: localhost:$RedisPort" -ForegroundColor White
if (-not $SkipElasticsearch) {
    Write-Host "  Elasticsearch: http://localhost:$ElasticsearchPort" -ForegroundColor White
    Write-Host "  Kibana: http://localhost:$KibanaPort" -ForegroundColor White
}
if (-not $SkipClustering) {
    Write-Host "  聚类健康检查: $clusteringUrl/internal/v1/health" -ForegroundColor White
}
Write-Host "  演示账号: 524030910001 / 123456" -ForegroundColor White
Write-Host "  后端首次启动会自动从 MySQL 重建空的 ES 活动索引" -ForegroundColor Gray
