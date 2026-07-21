#Requires -Version 5.1
<#
.SYNOPSIS
  重新导入 schema/seed，清空 Redis，并提示重建 Elasticsearch 索引。

.DESCRIPTION
  修改 seed.sql 后，MySQL 的 docker-entrypoint-initdb.d 只会在**空数据卷首次启动**时执行，
  已有数据时必须手动重导。

  若本机已安装 MySQL 并占用 3306，Docker 的 campus-mysql 往往无法发布端口，
  后端实际连的是本机 MySQL。此时请加 -Target Host（默认会自动探测）。

.EXAMPLE
  .\reload-demo-data.ps1
  .\reload-demo-data.ps1 -Target Host
  .\reload-demo-data.ps1 -Target Docker -SkipRedis
#>
[CmdletBinding()]
param(
    [ValidateSet("Auto", "Host", "Docker")]
    [string]$Target = "Auto",
    [switch]$SkipRedis,
    [switch]$SkipEsHint
)

$ErrorActionPreference = "Stop"
$ScriptDir = $PSScriptRoot
$SchemaFile = Join-Path $ScriptDir "schema.sql"
$SeedFile = Join-Path $ScriptDir "seed.sql"

function Write-Step($m) { Write-Host "`n==> $m" -ForegroundColor Cyan }
function Write-Ok($m) { Write-Host "[OK] $m" -ForegroundColor Green }
function Write-Warn($m) { Write-Host "[WARN] $m" -ForegroundColor Yellow }

function Test-DockerMysqlPublished {
    try {
        $ports = docker inspect campus-mysql --format "{{json .NetworkSettings.Ports}}" 2>$null
        return ($ports -match '"HostPort"\s*:\s*"3306"') -or ($ports -match '3306/tcp.:.\["')
    }
    catch { return $false }
}

function Test-HostMysqlCampus {
    $candidates = @(
        'C:\Program Files\MySQL\MySQL Server 9.6\bin\mysql.exe',
        'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe',
        'mysql'
    )
    foreach ($c in $candidates) {
        if ($c -ne 'mysql' -and -not (Test-Path $c)) { continue }
        $env:MYSQL_PWD = 'campus123'
        & $c --host=127.0.0.1 --port=3306 --user=campus -N -e "SELECT 1" 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) { return $c }
    }
    return $null
}

function Invoke-ReloadDocker {
    Write-Step "Reload into Docker campus-mysql (root) via docker cp (keeps UTF-8)"
    docker cp $SchemaFile "campus-mysql:/tmp/schema.sql"
    docker cp $SeedFile "campus-mysql:/tmp/seed.sql"
    docker exec campus-mysql mysql -uroot -proot123 --default-character-set=utf8mb4 -e "DROP DATABASE IF EXISTS campus_activity; CREATE DATABASE campus_activity CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
    if ($LASTEXITCODE -ne 0) { throw "failed to recreate database in Docker" }

    docker exec campus-mysql bash -c "mysql -uroot -proot123 --default-character-set=utf8mb4 campus_activity < /tmp/schema.sql"
    if ($LASTEXITCODE -ne 0) { throw "schema.sql failed (Docker)" }

    docker exec campus-mysql bash -c "mysql -uroot -proot123 --default-character-set=utf8mb4 campus_activity < /tmp/seed.sql"
    if ($LASTEXITCODE -ne 0) { throw "seed.sql failed (Docker)" }

    $count = docker exec campus-mysql mysql -uroot -proot123 -N -e "SELECT COUNT(*) FROM campus_activity.activity;"
    $sample = docker exec campus-mysql mysql -uroot -proot123 --default-character-set=utf8mb4 -N -e "SELECT title FROM campus_activity.activity WHERE id=2;"
    Write-Ok "Docker MySQL activity count = $count (sample id=2: $sample)"
}

function Invoke-ReloadHost([string]$MysqlExe) {
    Write-Step "Reload into host MySQL @127.0.0.1:3306 (user campus, UTF-8 via Python stdin)"
    # PowerShell Get-Content | mysql 常把中文弄成 '?'，用 Python 按 UTF-8 bytes 喂入。
    $py = @"
from pathlib import Path
import os, re, subprocess, sys
mysql = sys.argv[1]
schema_path, seed_path = sys.argv[2], sys.argv[3]
env = os.environ.copy()
env['MYSQL_PWD'] = 'campus123'
base = [mysql, '--host=127.0.0.1', '--port=3306', '--user=campus',
        '--default-character-set=utf8mb4', 'campus_activity']
reset = '''SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS activity_record;
DROP TABLE IF EXISTS favorite;
DROP TABLE IF EXISTS feedback;
DROP TABLE IF EXISTS registration;
DROP TABLE IF EXISTS activity;
DROP TABLE IF EXISTS ``user``;
SET FOREIGN_KEY_CHECKS = 1;
'''
schema = Path(schema_path).read_text(encoding='utf-8')
schema = re.sub(r'(?s)CREATE DATABASE IF NOT EXISTS campus_activity.*?USE campus_activity;',
                'USE campus_activity;', schema)
seed = Path(seed_path).read_text(encoding='utf-8')
for label, sql in [('reset', reset), ('schema', schema), ('seed', seed)]:
    p = subprocess.run(base, input=sql.encode('utf-8'), env=env, capture_output=True)
    if p.returncode != 0:
        sys.stderr.write(p.stderr.decode('utf-8', 'replace'))
        raise SystemExit(f'{label} failed rc={p.returncode}')
print('host reload ok')
"@
    python -c $py $MysqlExe $SchemaFile $SeedFile
    if ($LASTEXITCODE -ne 0) { throw "host MySQL reload failed" }

    $env:MYSQL_PWD = 'campus123'
    $count = & $MysqlExe --host=127.0.0.1 --port=3306 --user=campus --default-character-set=utf8mb4 -N -e "SELECT COUNT(*) FROM campus_activity.activity;"
    $sample = & $MysqlExe --host=127.0.0.1 --port=3306 --user=campus --default-character-set=utf8mb4 -N -e "SELECT title FROM campus_activity.activity WHERE id=2;"
    Write-Ok "Host MySQL activity count = $count (sample id=2: $sample)"
}

if (-not (Test-Path $SchemaFile)) { throw "missing $SchemaFile" }
if (-not (Test-Path $SeedFile)) { throw "missing $SeedFile" }

$resolved = $Target
if ($Target -eq "Auto") {
    $dockerOk = Test-DockerMysqlPublished
    $hostMysql = Test-HostMysqlCampus
    if ($hostMysql -and -not $dockerOk) {
        $resolved = "Host"
        Write-Warn "Detected host MySQL on :3306; Docker MySQL port not published. Using -Target Host (what the backend usually connects to)."
    }
    elseif ($dockerOk) {
        $resolved = "Docker"
    }
    elseif ($hostMysql) {
        $resolved = "Host"
    }
    else {
        throw "Neither published Docker MySQL nor host MySQL (campus/campus123) is available."
    }
}

if ($resolved -eq "Docker") {
    Invoke-ReloadDocker
}
else {
    $exe = Test-HostMysqlCampus
    if (-not $exe) { throw "Host MySQL not reachable as campus/campus123 on 127.0.0.1:3306" }
    Invoke-ReloadHost $exe
}

if (-not $SkipRedis) {
    Write-Step "Flush Redis cache"
    try {
        docker exec campus-redis redis-cli FLUSHALL | Out-Null
        Write-Ok "Docker Redis FLUSHALL done"
    }
    catch {
        Write-Warn "docker redis flush failed: $($_.Exception.Message)"
    }
    try {
        redis-cli -p 6379 FLUSHALL 2>$null | Out-Null
    }
    catch { }
}

if (-not $SkipEsHint) {
    Write-Step "Elasticsearch next steps"
    Write-Host "  1) Ensure E5 is up: cd database; .\init-es.ps1" -ForegroundColor White
    Write-Host "  2) Restart backend if needed, then as admin001:" -ForegroundColor White
    Write-Host "     POST http://localhost:8080/api/v1/search/index/rebuild" -ForegroundColor White
    Write-Host "  3) Or run: python backend/scripts/cosine-threshold-experiment.py --rebuild" -ForegroundColor White
}

Write-Ok "Demo data reload finished (target=$resolved)"
