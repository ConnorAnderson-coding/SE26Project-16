#Requires -Version 5.1
<#
.SYNOPSIS
  本地 MySQL 建库 + 可选启动 Redis 检查 / 后端 / 前端

.DESCRIPTION
  1. 使用 root 创建 campus 用户与 campus_activity 库
  2. 执行 schema.sql、seed.sql
  3. 可选检测 Redis / Elasticsearch，并新开窗口启动 Spring Boot 与 Vite

.EXAMPLE
  .\setup-local.ps1
  交互输入 root 密码并完成建库

.EXAMPLE
  .\setup-local.ps1 -RootPassword "your_root_password" -StartApps
  建库并启动前后端

.EXAMPLE
  .\setup-local.ps1 -SetupOnly -SkipSeed
  仅建表，不导入示例数据

.EXAMPLE
  .\setup-local.ps1 -StartApps
  建库、确保 Redis 运行并启动前后端
#>
[CmdletBinding()]
param(
    [string]$MySqlHost = "127.0.0.1",
    [int]$MySqlPort = 3306,
    [string]$RootUser = "root",
    [string]$RootPassword = "",
    [string]$AppUser = "campus",
    [string]$AppPassword = "campus123",
    [string]$Database = "campus_activity",
    [string]$MySqlExe = "",
    [int]$RedisPort = 6379,
    [int]$ElasticsearchPort = 9200,
    [switch]$SetupOnly,
    [switch]$SkipSeed,
    [switch]$StartApps,
    [switch]$ForceRecreate,
    [switch]$SkipRedis,
    [switch]$SkipElasticsearch,
    [switch]$InitElasticsearch,
    [Alias("SkipElser")]
    [switch]$SkipEmbedding
)

$ErrorActionPreference = "Stop"
$ScriptDir = $PSScriptRoot
$ProjectRoot = Split-Path $ScriptDir -Parent
$SchemaFile = Join-Path $ScriptDir "schema.sql"
$SeedFile = Join-Path $ScriptDir "seed.sql"

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

function Find-MySqlExecutable {
    if ($MySqlExe -and (Test-Path $MySqlExe)) {
        return (Resolve-Path $MySqlExe).Path
    }

    $cmd = Get-Command mysql -ErrorAction SilentlyContinue
    if ($cmd) {
        return $cmd.Source
    }

    $candidates = @(
        "C:\Program Files\MySQL\MySQL Server 9.6\bin\mysql.exe",
        "C:\Program Files\MySQL\MySQL Server 9.5\bin\mysql.exe",
        "C:\Program Files\MySQL\MySQL Server 9.4\bin\mysql.exe",
        "C:\Program Files\MySQL\MySQL Server 9.3\bin\mysql.exe",
        "C:\Program Files\MySQL\MySQL Server 9.2\bin\mysql.exe",
        "C:\Program Files\MySQL\MySQL Server 9.1\bin\mysql.exe",
        "C:\Program Files\MySQL\MySQL Server 9.0\bin\mysql.exe",
        "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe",
        "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
    )

    foreach ($path in $candidates) {
        if (Test-Path $path) {
            return $path
        }
    }

    throw "未找到 mysql 客户端。请安装 MySQL 或将 mysql.exe 加入 PATH，或用 -MySqlExe 指定路径。"
}

function Get-RootPassword {
    if ($RootPassword) {
        return $RootPassword
    }

    $secure = Read-Host "请输入 MySQL root 密码（无密码直接回车）" -AsSecureString
    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try {
        return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
    }
    finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
    }
}

function Invoke-MySql {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments
    )

    $output = @(& $mysql $Arguments 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw ($output | Out-String).Trim()
    }

    return @($output | ForEach-Object {
        if ($_ -is [System.Management.Automation.ErrorRecord]) {
            $msg = $_.Exception.Message
            if ($msg -match '\[Warning\]') {
                return $null
            }
            throw $msg
        }
        if ($_ -match '^\s*mysql:\s*\[Warning\]') {
            return $null
        }
        $_
    } | Where-Object { $null -ne $_ })
}

function Get-MySqlScalarInt {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments
    )

    $result = Invoke-MySql -Arguments $Arguments
    foreach ($line in $result) {
        $text = [string]$line
        if ($text -match '^\d+$') {
            return [int]$text
        }
    }

    throw ("无法解析 MySQL 数值结果: " + ($result | Out-String).Trim())
}

function Invoke-MySqlFile {
    param(
        [Parameter(Mandatory = $true)]
        [string]$User,
        [string]$Password,
        [Parameter(Mandatory = $true)]
        [string]$FilePath,
        [switch]$IgnoreDuplicateEntry
    )

    if (-not (Test-Path $FilePath)) {
        throw "SQL 文件不存在: $FilePath"
    }

    $args = @(
        "-h", $MySqlHost,
        "-P", "$MySqlPort",
        "-u", $User,
        "--default-character-set=utf8mb4"
    )
    if ($Password) {
        $args += "-p$Password"
    }

    $sql = Get-Content -Path $FilePath -Raw -Encoding UTF8
    $output = @($sql | & $mysql $args 2>&1)
    if ($LASTEXITCODE -ne 0) {
        $message = ($output | Out-String).Trim()
        if ($IgnoreDuplicateEntry -and $message -match '1062|Duplicate entry') {
            Write-Warn "检测到重复数据，跳过 seed 导入（数据库已有示例数据）"
            return
        }
        throw $message
    }
}

function Get-TableRowCount {
    param(
        [Parameter(Mandatory = $true)]
        [string]$TableName
    )

    $args = @(
        "-h", $MySqlHost,
        "-P", "$MySqlPort",
        "-u", $AppUser,
        "-p$AppPassword",
        "-D", $Database,
        "-N",
        "-s",
        "-e",
        "SELECT COUNT(*) FROM ``$TableName``;"
    )
    return Get-MySqlScalarInt -Arguments $args
}

function Test-RedisConnection {
    $redisCli = Get-Command redis-cli -ErrorAction SilentlyContinue
    if (-not $redisCli) {
        return $false
    }

    $pong = & $redisCli.Source -p $RedisPort ping 2>$null
    return ($pong -eq "PONG")
}

function Start-RedisViaDocker {
    $docker = Get-Command docker -ErrorAction SilentlyContinue
    if (-not $docker) {
        return $false
    }

    $composeFile = Join-Path $ScriptDir "docker-compose.yml"
    if (-not (Test-Path $composeFile)) {
        return $false
    }

    Write-Host "  尝试通过 Docker Compose 启动 Redis..." -ForegroundColor Gray
    Push-Location $ScriptDir
    try {
        & docker compose up -d redis 2>&1 | Out-Null
        if ($LASTEXITCODE -ne 0) {
            return $false
        }
        Start-Sleep -Seconds 3
        return (Test-RedisConnection)
    }
    finally {
        Pop-Location
    }
}

function Start-RedisStandalone {
    $redisServer = Get-Command redis-server -ErrorAction SilentlyContinue
    if (-not $redisServer) {
        return $false
    }

    Write-Host "  尝试启动本地 redis-server (端口 $RedisPort)..." -ForegroundColor Gray
    Start-Process $redisServer.Source -ArgumentList @("--port", "$RedisPort") -WindowStyle Hidden
    Start-Sleep -Seconds 2
    return (Test-RedisConnection)
}

function Test-ElasticsearchConnection {
    try {
        $response = Invoke-RestMethod -Uri "http://127.0.0.1:${ElasticsearchPort}/" -Method Get -TimeoutSec 5
        return ($null -ne $response.version.number)
    }
    catch {
        return $false
    }
}

function Start-ElasticsearchViaDocker {
    $docker = Get-Command docker -ErrorAction SilentlyContinue
    if (-not $docker) {
        return $false
    }

    $composeFile = Join-Path $ScriptDir "docker-compose.yml"
    if (-not (Test-Path $composeFile)) {
        return $false
    }

    Write-Host "  尝试通过 Docker Compose 启动 Elasticsearch..." -ForegroundColor Gray
    Push-Location $ScriptDir
    try {
        & docker compose up -d --build elasticsearch 2>&1 | Out-Null
        if ($LASTEXITCODE -ne 0) {
            return $false
        }

        $deadline = (Get-Date).AddSeconds(120)
        while ((Get-Date) -lt $deadline) {
            if (Test-ElasticsearchConnection) {
                return $true
            }
            Start-Sleep -Seconds 5
        }
        return $false
    }
    finally {
        Pop-Location
    }
}

function Ensure-ElasticsearchRunning {
    param(
        [switch]$Required
    )

    if ($SkipElasticsearch) {
        Write-Warn "已跳过 Elasticsearch 检查（语义检索/推荐功能暂不可用）"
        return $false
    }

    if (Test-ElasticsearchConnection) {
        Write-Ok "Elasticsearch 已运行 (http://127.0.0.1:$ElasticsearchPort)"
        return $true
    }

    Write-Step "Elasticsearch 未响应，尝试通过 Docker 启动"
    if (Start-ElasticsearchViaDocker) {
        Write-Ok "Elasticsearch 已通过 Docker 启动 (端口 $ElasticsearchPort)"
        return $true
    }

    $message = @(
        "Elasticsearch 未运行且无法自动启动。语义检索与活动推荐依赖 ES，请先执行：",
        "  1) cd database; docker compose up -d --build elasticsearch",
        "  2) .\init-es.ps1   # 创建索引并部署 GTE embedding",
        "  临时跳过: .\setup-local.ps1 -SkipElasticsearch"
    ) -join "`n"

    if ($Required) {
        throw $message
    }

    Write-Warn $message
    return $false
}

function Initialize-ElasticsearchIfRequested {
    param(
        [bool]$EsRunning
    )

    if (-not $InitElasticsearch -or -not $EsRunning) {
        return
    }

    $initScript = Join-Path $ScriptDir "init-es.ps1"
    if (-not (Test-Path $initScript)) {
        Write-Warn "未找到 init-es.ps1，跳过 ES 索引初始化"
        return
    }

    Write-Step "初始化 Elasticsearch 索引与 GTE embedding"
    $initArgs = @("-EsPort", "$ElasticsearchPort")
    if ($SkipEmbedding) {
        $initArgs += "-SkipEmbedding"
    }
    & $initScript @initArgs
    if ($LASTEXITCODE -ne 0) {
        throw "Elasticsearch 初始化失败，请查看 init-es.ps1 输出"
    }
}

function Ensure-RedisRunning {
    param(
        [switch]$Required
    )

    if ($SkipRedis) {
        Write-Warn "已跳过 Redis 检查（后端缓存将不可用，除非自行配置 Redis）"
        return $false
    }

    if (Test-RedisConnection) {
        Write-Ok "Redis 已运行 (PONG, 端口 $RedisPort)"
        return $true
    }

    Write-Step "Redis 未响应，尝试自动启动"
    if (Start-RedisViaDocker) {
        Write-Ok "Redis 已通过 Docker 启动 (端口 $RedisPort)"
        return $true
    }
    if (Start-RedisStandalone) {
        Write-Ok "Redis 已通过 redis-server 启动 (端口 $RedisPort)"
        return $true
    }

    $message = @(
        "Redis 未运行且无法自动启动。后端 Spring Cache 依赖 Redis，请先执行以下任一方式：",
        "  1) cd database; docker compose up -d redis",
        "  2) 安装 Memurai / Redis for Windows 并启动服务",
        "  3) 使用 WSL 运行 redis-server",
        "  临时跳过: .\setup-local.ps1 -SkipRedis"
    ) -join "`n"

    if ($Required) {
        throw $message
    }

    Write-Warn $message
    return $false
}

function Start-ProjectApps {
    $backendDir = Join-Path $ProjectRoot "backend"
    $frontendDir = Join-Path $ProjectRoot "campus-activity"
    $mvnw = Join-Path $backendDir "mvnw.cmd"

    if (-not (Test-Path $mvnw)) {
        throw "未找到后端启动脚本: $mvnw"
    }
    if (-not (Test-Path $frontendDir)) {
        throw "未找到前端目录: $frontendDir"
    }

    Ensure-RedisRunning -Required:(-not $SkipRedis) | Out-Null

    Write-Step "启动后端 (Spring Boot :8080, Redis Cache 已启用)"
    Start-Process powershell -WorkingDirectory $backendDir -ArgumentList @(
        "-NoExit",
        "-Command",
        "Write-Host 'Starting backend (ES enabled by default)...' -ForegroundColor Cyan; .\mvnw.cmd spring-boot:run"
    )

    Write-Step "启动前端 (Vite :5173)"
    Start-Process powershell -WorkingDirectory $frontendDir -ArgumentList @(
        "-NoExit",
        "-Command",
        "Write-Host 'Starting frontend...' -ForegroundColor Cyan; if (-not (Test-Path node_modules)) { npm install }; npm run dev"
    )

    Write-Ok "已在新窗口启动前后端"
    Write-Host "前端: http://localhost:5173" -ForegroundColor White
    Write-Host "后端: http://localhost:8080/api/v1" -ForegroundColor White
    Write-Host "Redis: 127.0.0.1:$RedisPort (Spring Cache 缓存前缀 campus:)" -ForegroundColor White
    if (Test-ElasticsearchConnection) {
        Write-Host "Elasticsearch: http://127.0.0.1:$ElasticsearchPort (索引 campus_activities)" -ForegroundColor White
        Write-Host "Kibana: http://127.0.0.1:5601" -ForegroundColor White
    }
    Write-Host "演示账号: 524030910001 / 123456" -ForegroundColor White
}

try {
    Write-Host "校园活动平台 - 本地建库/启动脚本" -ForegroundColor Magenta
    Write-Host "项目目录: $ProjectRoot"

    $mysql = Find-MySqlExecutable
    Write-Ok "MySQL 客户端: $mysql"

    $RootPassword = Get-RootPassword

    Write-Step "创建数据库与应用用户"
    $initSql = @"
CREATE DATABASE IF NOT EXISTS $Database
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS '$AppUser'@'localhost' IDENTIFIED BY '$AppPassword';
CREATE USER IF NOT EXISTS '$AppUser'@'127.0.0.1' IDENTIFIED BY '$AppPassword';
ALTER USER '$AppUser'@'localhost' IDENTIFIED BY '$AppPassword';
ALTER USER '$AppUser'@'127.0.0.1' IDENTIFIED BY '$AppPassword';
GRANT ALL PRIVILEGES ON ${Database}.* TO '$AppUser'@'localhost';
GRANT ALL PRIVILEGES ON ${Database}.* TO '$AppUser'@'127.0.0.1';
FLUSH PRIVILEGES;
"@

    $initArgs = @("-h", $MySqlHost, "-P", "$MySqlPort", "-u", $RootUser, "--default-character-set=utf8mb4")
    if ($RootPassword) { $initArgs += "-p$RootPassword" }
    $initOutput = $initSql | & $mysql $initArgs 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw ($initOutput | Out-String).Trim()
    }
    Write-Ok "数据库与用户就绪: $Database / $AppUser"

    if ($ForceRecreate) {
        Write-Step "强制重建：删除现有表"
        $dropSql = @"
USE $Database;
SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS feedback;
DROP TABLE IF EXISTS activity_record;
DROP TABLE IF EXISTS favorite;
DROP TABLE IF EXISTS registration;
DROP TABLE IF EXISTS activity;
DROP TABLE IF EXISTS ``user``;
SET FOREIGN_KEY_CHECKS = 1;
"@
        $dropArgs = @("-h", $MySqlHost, "-P", "$MySqlPort", "-u", $AppUser, "--default-character-set=utf8mb4", "-p$AppPassword")
        $dropOutput = $dropSql | & $mysql $dropArgs 2>&1
        if ($LASTEXITCODE -ne 0) {
            throw ($dropOutput | Out-String).Trim()
        }
        Write-Ok "旧表已删除"
    }

    Write-Step "执行 schema.sql（使用 root，因脚本含 CREATE DATABASE）"
    Invoke-MySqlFile -User $RootUser -Password $RootPassword -FilePath $SchemaFile
    Write-Ok "表结构创建完成"

    if (-not $SkipSeed) {
        $existingUsers = 0
        try {
            $existingUsers = Get-TableRowCount -TableName "user"
        }
        catch {
            $existingUsers = 0
        }

        if ($existingUsers -gt 0 -and -not $ForceRecreate) {
            Write-Warn "检测到已有 $existingUsers 条用户数据，跳过 seed.sql（避免主键冲突）"
            Write-Host "      如需清空并重新导入，请使用: .\setup-local.ps1 -ForceRecreate" -ForegroundColor Yellow
        }
        else {
            Write-Step "执行 seed.sql（导入示例数据）"
            Invoke-MySqlFile -User $AppUser -Password $AppPassword -FilePath $SeedFile -IgnoreDuplicateEntry
            Write-Ok "示例数据导入完成"
        }
    }
    else {
        Write-Warn "已跳过 seed.sql"
    }

    Write-Step "验证数据"
    $verifyArgs = @(
        "-h", $MySqlHost,
        "-P", "$MySqlPort",
        "-u", $AppUser,
        "-p$AppPassword",
        "-D", $Database,
        "-N",
        "-e",
        "SELECT CONCAT('users=', COUNT(*)) FROM user; SELECT CONCAT('activities=', COUNT(*)) FROM activity;"
    )
    $verify = Invoke-MySql -Arguments $verifyArgs
    $verify | ForEach-Object { Write-Ok $_ }

    Write-Step "检查 Redis（后端 Spring Cache 依赖）"
    Ensure-RedisRunning | Out-Null

    Write-Step "检查 Elasticsearch（语义检索 / 活动推荐）"
    $esRunning = Ensure-ElasticsearchRunning
    Initialize-ElasticsearchIfRequested -EsRunning $esRunning

    Write-Host "`n后端默认连接配置:" -ForegroundColor White
    Write-Host "  DB_URL=jdbc:mysql://${MySqlHost}:${MySqlPort}/${Database}?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"
    Write-Host "  DB_USERNAME=$AppUser"
    Write-Host "  DB_PASSWORD=$AppPassword"
    Write-Host "  REDIS_HOST=127.0.0.1"
    Write-Host "  REDIS_PORT=$RedisPort"
    Write-Host "  spring.cache.type=redis"
    Write-Host "  ELASTICSEARCH_URIS=http://127.0.0.1:$ElasticsearchPort"
    Write-Host "  ES_ACTIVITIES_INDEX=campus_activities"

    if ($SetupOnly) {
        Write-Ok "建库完成（SetupOnly）"
        exit 0
    }

    if ($StartApps) {
        Start-ProjectApps
        Write-Ok "全部完成"
        exit 0
    }

    $answer = Read-Host "`n是否现在启动前后端? [y/N]"
    if ($answer -match '^(y|Y|yes|YES)$') {
        Start-ProjectApps
    }
    else {
        Write-Host "`n手动启动命令:" -ForegroundColor White
        Write-Host "  cd database; docker compose up -d redis"
        Write-Host "  cd database; docker compose up -d --build elasticsearch; .\init-es.ps1"
        Write-Host "  cd backend; .\mvnw.cmd spring-boot:run"
        Write-Host "  cd campus-activity; npm run dev"
    }

    Write-Ok "完成"
}
catch {
    Write-Err $_.Exception.Message
    exit 1
}
