#!/usr/bin/env bash
set -euo pipefail

APP_NAME="ping-relay-server"
INSTALL_DIR="/opt/${APP_NAME}"
SERVICE_NAME="ping-relay"
RUN_USER="${SUDO_USER:-$USER}"
PORT="${PORT:-8787}"

if [[ "${EUID}" -ne 0 ]]; then
  echo "Bitte mit sudo ausführen: sudo bash install.sh"
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "[1/5] Node.js installieren (falls nötig)"
if ! command -v node >/dev/null 2>&1; then
  apt-get update
  apt-get install -y ca-certificates curl gnupg
  install -d -m 0755 /etc/apt/keyrings
  curl -fsSL https://deb.nodesource.com/gpgkey/nodesource-repo.gpg.key | gpg --dearmor -o /etc/apt/keyrings/nodesource.gpg
  echo "deb [signed-by=/etc/apt/keyrings/nodesource.gpg] https://deb.nodesource.com/node_20.x nodistro main" > /etc/apt/sources.list.d/nodesource.list
  apt-get update
  apt-get install -y nodejs
fi

echo "[2/5] Dateien nach ${INSTALL_DIR} kopieren"
mkdir -p "${INSTALL_DIR}"
cp "${SCRIPT_DIR}/package.json" "${INSTALL_DIR}/package.json"
cp "${SCRIPT_DIR}/index.js" "${INSTALL_DIR}/index.js"

echo "[3/5] Dependencies installieren"
cd "${INSTALL_DIR}"
npm install --omit=dev

echo "[4/5] Systemd-Service anlegen"
cat >/etc/systemd/system/${SERVICE_NAME}.service <<EOF
[Unit]
Description=Minecraft Ping Relay Server
After=network.target

[Service]
Type=simple
User=${RUN_USER}
WorkingDirectory=${INSTALL_DIR}
Environment=PORT=${PORT}
ExecStart=/usr/bin/node ${INSTALL_DIR}/index.js
Restart=always
RestartSec=3

[Install]
WantedBy=multi-user.target
EOF

echo "[5/5] Service starten"
systemctl daemon-reload
systemctl enable --now ${SERVICE_NAME}.service

echo "Fertig. Status prüfen mit: sudo systemctl status ${SERVICE_NAME}"
echo "Logs anzeigen mit: sudo journalctl -u ${SERVICE_NAME} -f"
