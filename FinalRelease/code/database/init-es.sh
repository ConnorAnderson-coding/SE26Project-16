#!/usr/bin/env bash
# Elasticsearch 初始化（Ubuntu / Linux）：创建索引、部署 GTE、注册 ingest pipeline
# 等价于 database/init-es.ps1
#
# 用法:
#   ./init-es.sh
#   ./init-es.sh --skip-embedding
#   ./init-es.sh --force-recreate-index
#   ./init-es.sh --es-host 127.0.0.1 --es-port 9200
#   HF_ENDPOINT=https://hf-mirror.com ./init-es.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INDEX_FILE="${SCRIPT_DIR}/elasticsearch/activity-index.json"

ES_HOST="127.0.0.1"
ES_PORT="9200"
INDEX_NAME="campus_activities"
HUB_MODEL_ID="thenlper/gte-small-zh"
EMBEDDING_MODEL_ID="campus_gte"
INGEST_PIPELINE="campus-activity-embedding"
EMBEDDING_DIMS="512"
ELAND_IMAGE="docker.elastic.co/eland/eland:8.15.0"
HF_ENDPOINT="${HF_ENDPOINT:-https://huggingface.co}"
HEALTH_WAIT_SECONDS=180
SKIP_EMBEDDING=0
FORCE_RECREATE_INDEX=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --es-host) ES_HOST="$2"; shift 2 ;;
    --es-port) ES_PORT="$2"; shift 2 ;;
    --index-name) INDEX_NAME="$2"; shift 2 ;;
    --hf-endpoint) HF_ENDPOINT="$2"; shift 2 ;;
    --eland-image) ELAND_IMAGE="$2"; shift 2 ;;
    --skip-embedding|--skip-elser) SKIP_EMBEDDING=1; shift ;;
    --force-recreate-index) FORCE_RECREATE_INDEX=1; shift ;;
    --health-wait-seconds) HEALTH_WAIT_SECONDS="$2"; shift 2 ;;
    -h|--help)
      sed -n '2,12p' "$0"
      exit 0
      ;;
    *)
      echo "[ERROR] Unknown argument: $1" >&2
      exit 1
      ;;
  esac
done

ES_BASE="http://${ES_HOST}:${ES_PORT}"

step() { printf '\n==> %s\n' "$1"; }
ok() { printf '[OK] %s\n' "$1"; }
warn() { printf '[WARN] %s\n' "$1"; }
err() { printf '[ERROR] %s\n' "$1" >&2; }

need_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    err "缺少命令: $1"
    exit 1
  fi
}

es_curl() {
  # usage: es_curl METHOD PATH [curl args...]
  local method="$1"
  local path="$2"
  shift 2
  curl -sS -f -X "$method" "${ES_BASE}${path}" "$@"
}

wait_for_elasticsearch() {
  step "等待 Elasticsearch 就绪 (最多 ${HEALTH_WAIT_SECONDS}s)"
  local deadline=$((SECONDS + HEALTH_WAIT_SECONDS))
  local attempt=0
  while (( SECONDS < deadline )); do
    attempt=$((attempt + 1))
    if curl -sS -f --max-time 5 "${ES_BASE}/" >/dev/null 2>&1; then
      local status
      status="$(curl -sS --max-time 35 \
        "${ES_BASE}/_cluster/health?wait_for_status=yellow&timeout=30s" \
        | sed -n 's/.*"status"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' | head -n1)"
      if [[ "$status" == "yellow" || "$status" == "green" ]]; then
        ok "集群状态: ${status}"
        return 0
      fi
    fi
    if (( attempt % 5 == 1 )); then
      printf '  仍在等待 %s ... (%s)\n' "$ES_BASE" "$attempt"
    fi
    sleep 3
  done
  err "Elasticsearch 在 ${HEALTH_WAIT_SECONDS}s 内未就绪 (${ES_BASE})"
  err "请检查: docker ps --filter name=campus-elasticsearch"
  exit 1
}

index_exists() {
  local code
  code="$(curl -sS -o /dev/null -w '%{http_code}' --max-time 10 "${ES_BASE}/${INDEX_NAME}" || true)"
  [[ "$code" == "200" ]]
}

initialize_activity_index() {
  if [[ ! -f "$INDEX_FILE" ]]; then
    err "未找到索引定义: $INDEX_FILE"
    exit 1
  fi

  if index_exists && [[ "$FORCE_RECREATE_INDEX" -eq 0 ]]; then
    ok "索引 '${INDEX_NAME}' 已存在，跳过创建（使用 --force-recreate-index 可重建）"
    return 0
  fi

  if index_exists && [[ "$FORCE_RECREATE_INDEX" -eq 1 ]]; then
    warn "删除现有索引 '${INDEX_NAME}'"
    curl -sS -f -X DELETE --max-time 30 "${ES_BASE}/${INDEX_NAME}" >/dev/null
  fi

  step "创建索引 '${INDEX_NAME}'（IK 分词 + dense_vector ${EMBEDDING_DIMS} cosine / GTE）"
  curl -sS -f -X PUT --max-time 60 \
    -H 'Content-Type: application/json; charset=utf-8' \
    --data-binary @"${INDEX_FILE}" \
    "${ES_BASE}/${INDEX_NAME}" >/dev/null
  ok "索引 '${INDEX_NAME}' 创建成功"
}

ensure_ml_license() {
  local type
  type="$(curl -sS --max-time 15 "${ES_BASE}/_license" \
    | sed -n 's/.*"type"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' | head -n1 || true)"
  if [[ "$type" == "trial" ]]; then
    return 0
  fi
  printf '  当前许可证: %s，尝试启动 trial...\n' "${type:-unknown}"
  if curl -sS -f -X POST --max-time 30 \
    "${ES_BASE}/_license/start_trial?acknowledge=true" >/dev/null 2>&1; then
    ok "已启动 trial 许可证（ML / embedding 需要）"
  else
    warn "许可证检查/启动 trial 跳过或失败（若已是 trial 可忽略）"
  fi
}

remove_legacy_embedding_artifacts() {
  local mid
  for mid in .elser_model_2 .multilingual-e5-small campus_e5; do
    curl -sS -X POST --max-time 60 \
      "${ES_BASE}/_ml/trained_models/${mid}/deployment/_stop?force=true" >/dev/null 2>&1 || true
  done
  curl -sS -X DELETE --max-time 15 \
    "${ES_BASE}/_ingest/pipeline/campus-activity-elser" >/dev/null 2>&1 || true
}

es_container_network() {
  docker inspect campus-elasticsearch \
    --format '{{range $k, $v := .NetworkSettings.Networks}}{{$k}} {{end}}' 2>/dev/null \
    | awk '{print $1}'
}

gte_model_ready() {
  local body state
  body="$(curl -sS --max-time 30 "${ES_BASE}/_ml/trained_models/${EMBEDDING_MODEL_ID}/_stats" 2>/dev/null || true)"
  [[ -z "$body" ]] && return 1
  if echo "$body" | grep -Eq '"state"[[:space:]]*:[[:space:]]*"(started|fully_allocated)"'; then
    return 0
  fi
  return 1
}

deploy_embedding_model() {
  step "部署 GTE 稠密向量模型 (${HUB_MODEL_ID} → ${EMBEDDING_MODEL_ID})"
  ensure_ml_license
  remove_legacy_embedding_artifacts

  if gte_model_ready; then
    ok "GTE 已在运行 (model_id=${EMBEDDING_MODEL_ID})"
    return 0
  fi

  local network
  network="$(es_container_network)"
  if [[ -z "$network" ]]; then
    err "找不到 campus-elasticsearch 的 Docker 网络。请先启动 elasticsearch 容器。"
    exit 1
  fi

  printf '  通过 Eland 8.15 导入（首次约 5-20 分钟；HF_ENDPOINT=%s）...\n' "$HF_ENDPOINT"
  docker run --rm --dns 8.8.8.8 \
    --network "$network" \
    -e "HF_ENDPOINT=${HF_ENDPOINT}" \
    "$ELAND_IMAGE" \
    eland_import_hub_model \
      --url http://campus-elasticsearch:9200 \
      --hub-model-id "$HUB_MODEL_ID" \
      --es-model-id "$EMBEDDING_MODEL_ID" \
      --task-type text_embedding \
      --start \
      --clear-previous

  local deadline=$((SECONDS + 1500))
  while (( SECONDS < deadline )); do
    if gte_model_ready; then
      ok "GTE 模型已就绪 (model_id=${EMBEDDING_MODEL_ID})"
      return 0
    fi
    printf '  等待 GTE deployment...\n'
    sleep 10
  done
  warn "GTE 仍在启动中，可通过 GET _ml/trained_models/${EMBEDDING_MODEL_ID}/_stats 查看进度"
}

ensure_ingest_pipeline() {
  step "Create embedding ingest pipeline (${INGEST_PIPELINE})"
  local body
  body="$(cat <<EOF
{
  "description": "Campus activity GTE multilingual dense embedding (cosine kNN, ${EMBEDDING_DIMS}-d)",
  "processors": [
    {
      "inference": {
        "model_id": "${EMBEDDING_MODEL_ID}",
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
EOF
)"
  if curl -sS -f -X PUT --max-time 30 \
    -H 'Content-Type: application/json; charset=utf-8' \
    -d "$body" \
    "${ES_BASE}/_ingest/pipeline/${INGEST_PIPELINE}" >/dev/null; then
    ok "Ingest pipeline '${INGEST_PIPELINE}' ready (GTE)"
  else
    warn "Ingest pipeline 失败（通常需要 GTE 已部署）"
  fi
}

show_summary() {
  printf '\n--- Elasticsearch 环境摘要 ---\n'
  printf '  REST API : %s\n' "$ES_BASE"
  printf '  索引     : %s\n' "$INDEX_NAME"
  printf '  向量模型 : %s\n' "$EMBEDDING_MODEL_ID"
  printf '  Pipeline : %s\n' "$INGEST_PIPELINE"
  printf '  Kibana   : http://%s:5601\n' "$ES_HOST"
  printf '  验证命令 : curl %s/%s/_mapping\n' "$ES_BASE" "$INDEX_NAME"
  printf '  后端配置 : spring.elasticsearch.uris=%s\n' "$ES_BASE"
}

main() {
  printf '校园活动平台 - Elasticsearch 初始化 (bash)\n'
  need_cmd curl
  need_cmd docker

  wait_for_elasticsearch
  initialize_activity_index

  if [[ "$SKIP_EMBEDDING" -eq 0 ]]; then
    deploy_embedding_model
    ensure_ingest_pipeline
  else
    warn "已跳过 GTE 部署 (--skip-embedding)。稍后可重新执行本脚本。"
    ensure_ingest_pipeline || true
  fi

  show_summary
  ok "Elasticsearch 初始化完成"
}

main "$@"
