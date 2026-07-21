#Requires -Version 5.1
<#
.SYNOPSIS
  初始化 Elasticsearch：创建活动索引、部署 GTE 稠密向量模型（语义检索）

.DESCRIPTION
  对应《doc/技术选型.md》第 1/2 部分：
  - 索引 campus_activities：IK 分词（BM25）+ dense_vector 512 维（kNN cosine）
  - 经 Eland 导入 thenlper/gte-small-zh，注册为 campus_gte（中文专用，本地友好）
  - ingest pipeline 将 search_text 向量化写入 activity_embedding（无需 E5 的 query:/passage: 前缀）

  从 E5 / 其它维数模型切换时必须：
    .\init-es.ps1 -ForceRecreateIndex
  然后后端 POST /api/v1/search/index/rebuild

.EXAMPLE
  .\init-es.ps1
  等待 ES 就绪后创建索引并部署 GTE

.EXAMPLE
  .\init-es.ps1 -SkipEmbedding
  仅创建索引，跳过 GTE（首次 Eland 导入较慢，可稍后单独部署）

.EXAMPLE
  .\init-es.ps1 -HfEndpoint "https://huggingface.co"
  使用指定 Hugging Face endpoint 导入 GTE

.EXAMPLE
  .\init-es.ps1 -ForceRecreateIndex
  删除并重建 campus_activities 索引（换维数 / 换模型后必需）
#>
[CmdletBinding()]
param(
    [string]$EsHost = "127.0.0.1",
    [int]$EsPort = 9200,
    [string]$IndexName = "campus_activities",
    [string]$HfEndpoint = "",
    [string]$ElandImage = "docker.elastic.co/eland/eland:8.15.0",
    [switch]$SkipEmbedding,
    [switch]$ForceRecreateIndex,
    [int]$HealthWaitSeconds = 180,
    [switch]$SeedDocuments
)

$ErrorActionPreference = "Stop"
$ScriptDir = $PSScriptRoot
$IndexFile = Join-Path $ScriptDir "elasticsearch\activity-index.json"
# Hugging Face GTE Chinese small → ES model id campus_gte (512-d)
$HubModelId = "thenlper/gte-small-zh"
$EmbeddingModelId = "campus_gte"
$EmbeddingDeploymentId = "campus_gte"
$IngestPipeline = "campus-activity-embedding"
$EmbeddingDims = 512

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

function Get-EsBaseUrl {
    return "http://${EsHost}:${EsPort}"
}

function Test-ElasticsearchConnection {
    try {
        $response = Invoke-RestMethod -Uri "$(Get-EsBaseUrl)/" -Method Get -TimeoutSec 5
        return ($null -ne $response.version.number)
    }
    catch {
        return $false
    }
}

function Wait-ForElasticsearch {
    Write-Step "等待 Elasticsearch 就绪 (最多 ${HealthWaitSeconds}s)"
    $deadline = (Get-Date).AddSeconds($HealthWaitSeconds)
    $attempt = 0
    while ((Get-Date) -lt $deadline) {
        $attempt++
        try {
            if (-not (Test-ElasticsearchConnection)) {
                throw "connection refused"
            }
            $healthUri = "$(Get-EsBaseUrl)/_cluster/health?wait_for_status=yellow" + '&timeout=30s'
            $health = Invoke-RestMethod -Uri $healthUri -Method Get -TimeoutSec 35
            if ($health.status -in @("yellow", "green")) {
                Write-Ok "集群状态: $($health.status) (节点 $($health.number_of_nodes))"
                return $true
            }
        }
        catch {
            if (($attempt % 5) -eq 1) {
                Write-Host "  仍在等待 $(Get-EsBaseUrl) ... ($attempt)" -ForegroundColor Gray
            }
        }
        Start-Sleep -Seconds 3
    }
    throw @(
        "Elasticsearch 在 ${HealthWaitSeconds}s 内未就绪 ($(Get-EsBaseUrl))",
        "请检查: docker ps --filter name=campus-elasticsearch",
        "或: cd database; docker compose up -d elasticsearch"
    ) -join "`n"
}

function Test-IndexExists {
    param([string]$Name)
    try {
        Invoke-RestMethod -Uri "$(Get-EsBaseUrl)/$Name" -Method Get -TimeoutSec 10 | Out-Null
        return $true
    }
    catch {
        if ($_.Exception.Response -and $_.Exception.Response.StatusCode.value__ -eq 404) {
            return $false
        }
        throw
    }
}

function Initialize-ActivityIndex {
    if (-not (Test-Path $IndexFile)) {
        throw "未找到索引定义: $IndexFile"
    }

    $exists = Test-IndexExists -Name $IndexName
    if ($exists -and -not $ForceRecreateIndex) {
        Write-Ok "索引 '$IndexName' 已存在，跳过创建（使用 -ForceRecreateIndex 可重建）"
        return
    }

    if ($exists -and $ForceRecreateIndex) {
        Write-Warn "删除现有索引 '$IndexName'"
        Invoke-RestMethod -Uri "$(Get-EsBaseUrl)/$IndexName" -Method Delete -TimeoutSec 30 | Out-Null
    }

    Write-Step "创建索引 '$IndexName'（IK 分词 + dense_vector $EmbeddingDims cosine / GTE）"
    $body = Get-Content -Path $IndexFile -Raw -Encoding UTF8
    $result = Invoke-RestMethod -Uri "$(Get-EsBaseUrl)/$IndexName" -Method Put -Body $body -ContentType "application/json; charset=utf-8" -TimeoutSec 60
    if (-not $result.acknowledged) {
        throw "创建索引失败: $($result | ConvertTo-Json -Compress)"
    }
    Write-Ok "索引 '$IndexName' 创建成功"
}

function Ensure-MlLicense {
    try {
        $lic = Invoke-RestMethod -Uri "$(Get-EsBaseUrl)/_license" -Method Get -TimeoutSec 15
        if ($lic.license.type -eq "trial") {
            return
        }
        Write-Host "  当前许可证: $($lic.license.type)，尝试启动 trial..." -ForegroundColor Gray
        $trial = Invoke-RestMethod -Uri "$(Get-EsBaseUrl)/_license/start_trial?acknowledge=true" -Method Post -TimeoutSec 30
        if ($trial.trial_was_started) {
            Write-Ok "已启动 trial 许可证（ML / embedding 需要）"
        }
    }
    catch {
        Write-Warn "许可证检查跳过: $($_.Exception.Message)"
    }
}

function Remove-LegacyEmbeddingArtifacts {
    # Free ML RAM: stop ELSER / E5 leftovers from earlier iterations.
    $base = Get-EsBaseUrl
    foreach ($mid in @(".elser_model_2", ".multilingual-e5-small", "campus_e5")) {
        try {
            Invoke-RestMethod -Uri "$base/_ml/trained_models/$mid/deployment/_stop?force=true" `
                -Method Post -TimeoutSec 60 | Out-Null
            Write-Ok "Stopped leftover deployment: $mid"
            Start-Sleep -Seconds 2
        }
        catch { }
    }
    try {
        Invoke-RestMethod -Uri "$base/_ingest/pipeline/campus-activity-elser" -Method Delete -TimeoutSec 15 | Out-Null
        Write-Ok "Removed leftover pipeline campus-activity-elser"
    }
    catch { }
}

function Get-EsContainerNetwork {
    try {
        $nets = docker inspect campus-elasticsearch --format '{{range $k, $v := .NetworkSettings.Networks}}{{$k}} {{end}}' 2>$null
        if ($nets) {
            return ($nets.ToString().Trim() -split '\s+')[0]
        }
    }
    catch { }
    return $null
}

function Test-GteModelReady {
    $base = Get-EsBaseUrl
    try {
        $stats = Invoke-RestMethod -Uri "$base/_ml/trained_models/$EmbeddingModelId/_stats" -Method Get -TimeoutSec 30
        $state = $stats.trained_model_stats[0].deployment_stats.allocation_status.state
        return ($state -in @("started", "fully_allocated"))
    }
    catch {
        return $false
    }
}

function Deploy-EmbeddingModel {
    Write-Step "部署 GTE 稠密向量模型 ($HubModelId → $EmbeddingModelId)"
    Ensure-MlLicense
    Remove-LegacyEmbeddingArtifacts

    if (Test-GteModelReady) {
        Write-Ok "GTE 已在运行 (model_id=$EmbeddingModelId)"
        return
    }

    Write-Host "  通过 Eland 8.15 导入（首次约 5-20 分钟；默认走 huggingface.co）..." -ForegroundColor Gray
    $network = Get-EsContainerNetwork
    if (-not $network) {
        throw "找不到 campus-elasticsearch 的 Docker 网络。请先: docker compose up -d elasticsearch"
    }

    # latest eland image speaks ES 9 Accept header and fails against ES 8.15
    $HfEndpoint = if ($HfEndpoint) { $HfEndpoint } elseif ($env:HF_ENDPOINT) { $env:HF_ENDPOINT } else { "https://huggingface.co" }

    $elandArgs = @(
        "run", "--rm",
        "--dns", "8.8.8.8",
        "--network", $network,
        "-e", "HF_ENDPOINT=$HfEndpoint",
        $ElandImage,
        "eland_import_hub_model",
        "--url", "http://campus-elasticsearch:9200",
        "--hub-model-id", $HubModelId,
        "--es-model-id", $EmbeddingModelId,
        "--task-type", "text_embedding",
        "--start",
        "--clear-previous"
    )
    Write-Host ("  docker " + ($elandArgs -join " ")) -ForegroundColor Gray
    & docker @elandArgs
    if ($LASTEXITCODE -ne 0) {
        throw @'
Eland 导入 GTE 失败 (exit=$LASTEXITCODE)。
请确认当前机器能访问: $HfEndpoint
或使用其它可用的 Hugging Face endpoint：
  .\init-es.ps1 -HfEndpoint "https://huggingface.co"
或设置环境变量:
  $env:HF_ENDPOINT = 'https://huggingface.co'

可手动执行（需能访问 Hugging Face 或设置 HF_ENDPOINT 镜像）:
  docker run --rm --dns 8.8.8.8 --network $network `
    -e HF_ENDPOINT=$HfEndpoint `
    $ElandImage `
    eland_import_hub_model `
      --url http://campus-elasticsearch:9200 `
      --hub-model-id $HubModelId `
      --es-model-id $EmbeddingModelId `
      --task-type text_embedding --start --clear-previous
'@
    }

    $deadline = (Get-Date).AddMinutes(25)
    while ((Get-Date) -lt $deadline) {
        if (Test-GteModelReady) {
            Write-Ok "GTE 模型已就绪 (model_id=$EmbeddingModelId)"
            return
        }
        Write-Host "  等待 GTE deployment..." -ForegroundColor Gray
        Start-Sleep -Seconds 10
    }
    Write-Warn "GTE 仍在启动中，可通过 GET _ml/trained_models/$EmbeddingModelId/_stats 查看进度"
}

function Ensure-IngestPipeline {
    Write-Step "Create embedding ingest pipeline ($IngestPipeline)"
    $base = Get-EsBaseUrl
    # GTE: embed search_text directly (no E5 passage: prefix)
    $body = @"
{
  "description": "Campus activity GTE multilingual dense embedding (cosine kNN, ${EmbeddingDims}-d)",
  "processors": [
    {
      "inference": {
        "model_id": "$EmbeddingModelId",
        "target_field": "gte_inference",
        "field_map": {
          "search_text": "text_field"
        }
      }
    },
    {
      "set": {
        "field": "activity_embedding",
        "copy_from": "gte_inference.predicted_value"
      }
    },
    {
      "remove": {
        "field": ["gte_inference"],
        "ignore_missing": true
      }
    }
  ]
}
"@
    try {
        Invoke-RestMethod -Uri "$base/_ingest/pipeline/$IngestPipeline" -Method Put `
            -ContentType "application/json; charset=utf-8" -Body $body -TimeoutSec 30 | Out-Null
        Write-Ok "Ingest pipeline '$IngestPipeline' ready (GTE)"
    }
    catch {
        $detail = $_.ErrorDetails.Message
        if (-not $detail) { $detail = $_.Exception.Message }
        Write-Warn "Ingest pipeline failed (need GTE deployed): $detail"
    }
}

function Show-Summary {
    Write-Host "`n--- Elasticsearch 环境摘要 ---" -ForegroundColor Magenta
    Write-Host "  REST API : $(Get-EsBaseUrl)" -ForegroundColor White
    Write-Host "  索引     : $IndexName" -ForegroundColor White
    Write-Host "  向量模型 : $EmbeddingModelId ($EmbeddingDeploymentId)" -ForegroundColor White
    Write-Host "  Pipeline : $IngestPipeline" -ForegroundColor White
    Write-Host "  Kibana   : http://${EsHost}:5601" -ForegroundColor White
    Write-Host "  验证命令 : curl $(Get-EsBaseUrl)/campus_activities/_mapping" -ForegroundColor White
    Write-Host "  后端配置 : spring.elasticsearch.uris=$(Get-EsBaseUrl)" -ForegroundColor White
}

try {
    Write-Host "校园活动平台 - Elasticsearch 初始化" -ForegroundColor Magenta

    # 不要在等待前做一次性连通性检查：ForceRecreateDb 后容器 health 与宿主机
    # 端口可达之间常有短暂窗口，一次失败会误杀整个启动流程。
    Wait-ForElasticsearch | Out-Null
    Initialize-ActivityIndex

    if ($SeedDocuments) {
        Write-Step "导入示例活动文档（bulk seed）"
        $seedScript = Join-Path $ScriptDir "index-seed.ps1"
        & $seedScript -EsHost $EsHost -EsPort $EsPort -IndexName $IndexName
        if ($LASTEXITCODE -ne 0) {
            throw "示例数据导入失败"
        }
    }

    if (-not $SkipEmbedding) {
        Deploy-EmbeddingModel
        Ensure-IngestPipeline
    }
    else {
        Write-Warn "Skipped GTE deploy (-SkipEmbedding). Re-run init-es.ps1 without the switch later."
        Ensure-IngestPipeline
    }

    Show-Summary
    Write-Ok "Elasticsearch 初始化完成"
}
catch {
    Write-Err $_.Exception.Message
    exit 1
}
