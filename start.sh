#!/usr/bin/env bash
# Ubuntu / Linux 一键启动入口
# 云服务器全栈 Docker 部署请用本脚本（Windows 本地开发仍用 start.ps1）
#
# 用法:
#   ./start.sh
#   ./start.sh --skip-build
#   ./start.sh --skip-es-init
#   ./start.sh --skip-embedding
#   ./start.sh --force-recreate
#   HF_ENDPOINT=https://hf-mirror.com ./start.sh

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_SH="${ROOT_DIR}/deploy/deploy.sh"

if [[ ! -f "$DEPLOY_SH" ]]; then
  echo "[ERROR] 未找到 ${DEPLOY_SH}" >&2
  exit 1
fi

chmod +x "$DEPLOY_SH" "${ROOT_DIR}/database/init-es.sh" 2>/dev/null || true

echo "校园活动平台 - Ubuntu 一键启动（全栈 Docker）"
exec bash "$DEPLOY_SH" "$@"
