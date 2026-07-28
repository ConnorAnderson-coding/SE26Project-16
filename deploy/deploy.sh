#!/usr/bin/env bash
# Ubuntu / Linux 全栈 Docker 一键部署
# 等价于 deploy/deploy.ps1（云服务器推荐用本脚本）
#
# 用法:
#   ./deploy.sh
#   ./deploy.sh --skip-build
#   ./deploy.sh --skip-es-init
#   ./deploy.sh --skip-embedding
#   ./deploy.sh --force-recreate
#   ./deploy.sh --with-kibana
#   ./deploy.sh --env-file .env

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
DATABASE_DIR="${PROJECT_ROOT}/database"
ENV_FILE=".env"

FORCE_RECREATE=0
SKIP_ES_INIT=0
SKIP_EMBEDDING=0
WITH_KIBANA=0
SKIP_BUILD=0
RESTART_BACKEND_AFTER_ES=1

step() { printf '\n==> %s\n' "$1"; }
ok() { printf '[OK] %s\n' "$1"; }
warn() { printf '[WARN] %s\n' "$1"; }
err() { printf '[ERROR] %s\n' "$1" >&2; }

while [[ $# -gt 0 ]]; do
  case "$1" in
    --force-recreate) FORCE_RECREATE=1; shift ;;
    --skip-es-init) SKIP_ES_INIT=1; shift ;;
    --skip-embedding|--skip-elser) SKIP_EMBEDDING=1; shift ;;
    --with-kibana) WITH_KIBANA=1; shift ;;
    --skip-build) SKIP_BUILD=1; shift ;;
    --no-restart-backend) RESTART_BACKEND_AFTER_ES=0; shift ;;
    --env-file) ENV_FILE="$2"; shift 2 ;;
    -h|--help)
      sed -n '2,14p' "$0"
      exit 0
      ;;
    *)
      err "Unknown argument: $1"
      exit 1
      ;;
  esac
done

read_env_value() {
  local key="$1"
  local default="${2:-}"
  local file="${SCRIPT_DIR}/${ENV_FILE}"
  if [[ -f "$file" ]]; then
    local line
    line="$(grep -E "^[[:space:]]*${key}=" "$file" | tail -n1 || true)"
    if [[ -n "$line" ]]; then
      local val="${line#*=}"
      val="${val%$'\r'}"
      val="${val%\"}"
      val="${val#\"}"
      val="${val%\'}"
      val="${val#\'}"
      printf '%s' "$val"
      return 0
    fi
  fi
  printf '%s' "$default"
}

ensure_vm_max_map_count() {
  local current
  current="$(sysctl -n vm.max_map_count 2>/dev/null || echo 0)"
  if [[ "$current" -lt 262144 ]]; then
    warn "vm.max_map_count=${current} < 262144，尝试设置（需要 sudo）"
    if command -v sudo >/dev/null 2>&1; then
      sudo sysctl -w vm.max_map_count=262144 >/dev/null
      echo 'vm.max_map_count=262144' | sudo tee /etc/sysctl.d/99-elasticsearch.conf >/dev/null
      ok "已设置 vm.max_map_count=262144"
    else
      err "请先执行: sysctl -w vm.max_map_count=262144"
      exit 1
    fi
  fi
}

main() {
  printf '校园活动平台 - Ubuntu 全栈 Docker 部署\n'
  cd "$SCRIPT_DIR"

  if ! command -v docker >/dev/null 2>&1; then
    err "未找到 docker。请先安装 Docker Engine + Compose 插件。"
    exit 1
  fi
  if ! docker compose version >/dev/null 2>&1; then
    err "未找到 docker compose 插件。"
    exit 1
  fi

  ensure_vm_max_map_count

  local env_path="${SCRIPT_DIR}/${ENV_FILE}"
  if [[ ! -f "$env_path" ]]; then
    if [[ ! -f "${SCRIPT_DIR}/.env.example" ]]; then
      err "缺少 .env.example"
      exit 1
    fi
    cp "${SCRIPT_DIR}/.env.example" "$env_path"
    warn "已从 .env.example 生成 ${ENV_FILE}，公网部署前请修改 PUBLIC_BASE_URL / CORS_ORIGINS / JWT_SECRET"
  fi

  if [[ "$FORCE_RECREATE" -eq 1 ]]; then
    step "清空数据卷并重建"
    docker compose --env-file "$ENV_FILE" --profile kibana down -v
  fi

  local compose_args=(compose --env-file "$ENV_FILE")
  if [[ "$WITH_KIBANA" -eq 1 ]]; then
    compose_args+=(--profile kibana)
  fi
  # 不用 --wait：backend 首次启动可能较慢；改为后面单独等待
  compose_args+=(up -d)
  if [[ "$SKIP_BUILD" -eq 0 ]]; then
    compose_args+=(--build)
  fi

  step "Build and start full stack (first build may take a long time)"
  if ! docker "${compose_args[@]}"; then
    err "docker compose up failed. Check registry access, memory (>=8GB), and logs:"
    err "  docker compose --env-file ${ENV_FILE} logs --tail=80 backend"
    exit 1
  fi
  ok "Containers started"

  step "Wait for backend liveness (up to ~8 minutes)"
  local ready=0
  local i
  for i in $(seq 1 48); do
    if docker exec campus-backend curl -sf --max-time 3 \
      http://127.0.0.1:8080/actuator/health/liveness 2>/dev/null | grep -q UP; then
      ready=1
      break
    fi
    if (( i % 6 == 0 )); then
      printf '  still waiting for backend... (%s/48)\n' "$i"
    fi
    sleep 10
  done
  if [[ "$ready" -eq 1 ]]; then
    ok "Backend is live"
  else
    warn "Backend not healthy yet; continuing. Check: docker logs --tail=100 campus-backend"
    docker compose --env-file "$ENV_FILE" ps || true
  fi

  # Ensure nginx is up even if backend health lagged
  docker compose --env-file "$ENV_FILE" up -d nginx || true
  ok "Stack is up (nginx may proxy once backend is ready)"

  if [[ "$SKIP_ES_INIT" -eq 0 ]]; then
    step "初始化 Elasticsearch（索引 + GTE + pipeline）"
    local init_script="${DATABASE_DIR}/init-es.sh"
    if [[ ! -x "$init_script" ]]; then
      chmod +x "$init_script" 2>/dev/null || true
    fi
    if [[ ! -f "$init_script" ]]; then
      err "未找到 ${init_script}"
      exit 1
    fi

    local es_port
    es_port="$(read_env_value ES_HTTP_PORT 9200)"
    local init_args=(--es-host 127.0.0.1 --es-port "$es_port")
    if [[ "$SKIP_EMBEDDING" -eq 1 ]]; then
      init_args+=(--skip-embedding)
    fi
    if [[ -n "${HF_ENDPOINT:-}" ]]; then
      init_args+=(--hf-endpoint "$HF_ENDPOINT")
    fi

    bash "$init_script" "${init_args[@]}"
    ok "Elasticsearch 初始化完成"

    if [[ "$RESTART_BACKEND_AFTER_ES" -eq 1 ]]; then
      step "重启 backend 以触发活动索引重建（ES_AUTO_REBUILD）"
      docker compose --env-file "$ENV_FILE" restart backend || true
    fi
  else
    warn "已跳过 Elasticsearch 初始化 (--skip-es-init)"
  fi

  local http_port
  http_port="$(read_env_value HTTP_PORT 80)"
  local base
  if [[ "$http_port" == "80" ]]; then
    base="http://localhost"
  else
    base="http://localhost:${http_port}"
  fi

  printf '\n--- 部署完成 ---\n'
  printf '  站点入口 : %s\n' "$base"
  printf '  API      : %s/api/v1\n' "$base"
  printf '  演示账号 : 524030910001 / 123456\n'
  printf '  MySQL    : 127.0.0.1:%s\n' "$(read_env_value MYSQL_PORT 3307)"
  printf '  ES       : 127.0.0.1:%s\n' "$(read_env_value ES_HTTP_PORT 9200)"
  printf '\n查看状态: docker compose --env-file %s ps\n' "$ENV_FILE"
  printf '后端日志: docker compose --env-file %s logs -f backend\n' "$ENV_FILE"
  ok "完成"
}

main "$@"
