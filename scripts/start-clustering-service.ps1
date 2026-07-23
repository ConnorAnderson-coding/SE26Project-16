#Requires -Version 5.1
[CmdletBinding()]
param(
    [string]$ProjectRoot = "",
    [int]$Port = 8000
)

$ErrorActionPreference = "Stop"
if (-not $ProjectRoot) {
    $ProjectRoot = Split-Path $PSScriptRoot -Parent
}

$serviceDir = Join-Path $ProjectRoot "clustering-service"
$requirements = Join-Path $serviceDir "requirements.txt"
$venvDir = Join-Path $serviceDir ".venv"
$venvPython = Join-Path $venvDir "Scripts\python.exe"

if (-not (Test-Path $requirements)) {
    throw "未找到聚类服务依赖文件: $requirements"
}
if (-not (Get-Command python -ErrorAction SilentlyContinue)) {
    throw "未找到 Python。社区聚类服务需要 Python 3.11 或更高版本。"
}

Push-Location $serviceDir
try {
    if (-not (Test-Path $venvPython)) {
        Write-Host "创建聚类服务虚拟环境..." -ForegroundColor Cyan
        python -m venv $venvDir
        if ($LASTEXITCODE -ne 0) { throw "创建 Python 虚拟环境失败" }
    }

    & $venvPython -m pip install -r $requirements
    if ($LASTEXITCODE -ne 0) { throw "安装聚类服务依赖失败" }

    Write-Host "Campus Clustering Service :$Port" -ForegroundColor Cyan
    & $venvPython -m uvicorn app.main:app --host 127.0.0.1 --port $Port
    exit $LASTEXITCODE
}
finally {
    Pop-Location
}
