#Requires -Version 5.1
<#
.SYNOPSIS
  将 seed 示例活动 bulk 写入 Elasticsearch（无需启动后端）

.EXAMPLE
  .\index-seed.ps1
#>
[CmdletBinding()]
param(
    [string]$EsHost = "127.0.0.1",
    [int]$EsPort = 9200,
    [string]$IndexName = "campus_activities"
)

$ErrorActionPreference = "Stop"
$ScriptDir = $PSScriptRoot
$BulkFile = Join-Path $ScriptDir "elasticsearch\seed-documents.ndjson"

function Write-Ok($Message) { Write-Host "[OK] $Message" -ForegroundColor Green }
function Write-Err($Message) { Write-Host "[ERROR] $Message" -ForegroundColor Red }

try {
    if (-not (Test-Path $BulkFile)) {
        throw "未找到 bulk 数据文件: $BulkFile"
    }

    $baseUrl = "http://${EsHost}:${EsPort}"
    Invoke-RestMethod -Uri "$baseUrl/" -Method Get -TimeoutSec 5 | Out-Null

    Write-Host "Bulk 导入示例活动 -> $IndexName" -ForegroundColor Cyan
    $bytes = [System.IO.File]::ReadAllBytes($BulkFile)
    $response = Invoke-RestMethod -Uri "$baseUrl/_bulk" -Method Post `
        -ContentType "application/x-ndjson" `
        -Body $bytes `
        -TimeoutSec 60

    if ($response.errors) {
        throw "Bulk 导入存在错误，请检查 ES 日志"
    }

    Invoke-RestMethod -Uri "$baseUrl/$IndexName/_refresh" -Method Post | Out-Null
    $count = (Invoke-RestMethod -Uri "$baseUrl/$IndexName/_count" -Method Get).count
    Write-Ok "导入完成，索引 $IndexName 当前文档数: $count"
    Write-Host "Kibana 分词演示: database\elasticsearch\kibana-analyze-demo.md" -ForegroundColor White
    Write-Host "Kibana Dev Tools: http://${EsHost}:5601/app/dev_tools" -ForegroundColor White
}
catch {
    Write-Err $_.Exception.Message
    exit 1
}
