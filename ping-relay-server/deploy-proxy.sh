#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "Usage: ./deploy-proxy.sh <user@host> [port]"
  exit 1
fi

TARGET="$1"
PORT="${2:-8787}"
REMOTE_DIR="/tmp/ping-relay-deploy"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "[1/4] Copy files to ${TARGET}:${REMOTE_DIR}"
ssh "${TARGET}" "mkdir -p '${REMOTE_DIR}'"
scp "${SCRIPT_DIR}/index.js" "${SCRIPT_DIR}/package.json" "${SCRIPT_DIR}/install.sh" "${TARGET}:${REMOTE_DIR}/"

echo "[2/4] Install/update service on remote proxy"
ssh "${TARGET}" "cd '${REMOTE_DIR}'; chmod +x install.sh; sudo PORT='${PORT}' bash install.sh"

echo "[3/4] Restart service"
ssh "${TARGET}" "sudo systemctl restart ping-relay"

echo "[4/4] Service status"
ssh "${TARGET}" "sudo systemctl --no-pager status ping-relay | sed -n '1,12p'"

echo "Deployment completed successfully."
