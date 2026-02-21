#!/usr/bin/env bash
set -euo pipefail

APP_NAME="ping-relay-server"
INSTALL_DIR="/opt/${APP_NAME}"
SERVICE_NAME="ping-relay"
RUN_USER="pingrelay"
RUN_GROUP="pingrelay"
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

if [[ -f "${SCRIPT_DIR}/package-lock.json" ]]; then
  cp "${SCRIPT_DIR}/package-lock.json" "${INSTALL_DIR}/package-lock.json"
fi

echo "[2.5/5] Service-User anlegen (falls nicht vorhanden)"
if ! id -u "${RUN_USER}" >/dev/null 2>&1; then
  useradd --system --no-create-home --shell /usr/sbin/nologin "${RUN_USER}"
fi

echo "[3/5] Dependencies installieren"
cd "${INSTALL_DIR}"
npm install --omit=dev
chown -R "${RUN_USER}:${RUN_GROUP}" "${INSTALL_DIR}"
chmod -R o-rwx "${INSTALL_DIR}"

echo "[3.5/5] Umgebungsdatei anlegen"
cat >/etc/default/${SERVICE_NAME} <<EOF
PORT=${PORT}
MAX_MESSAGE_BYTES=4096
MAX_TEXT_LENGTH=96
MAX_PLAYER_LENGTH=32
MAX_INVALID_MESSAGES=8
PING_RATE_WINDOW_MS=1000
PING_RATE_MAX=12
EOF
chmod 0640 /etc/default/${SERVICE_NAME}

echo "[4/5] Systemd-Service anlegen"
cat >/etc/systemd/system/${SERVICE_NAME}.service <<EOF
[Unit]
Description=Minecraft Ping Relay Server
After=network.target

[Service]
Type=simple
User=${RUN_USER}
Group=${RUN_GROUP}
WorkingDirectory=${INSTALL_DIR}
EnvironmentFile=/etc/default/${SERVICE_NAME}
ExecStart=/usr/bin/node --unhandled-rejections=strict --max-old-space-size=128 ${INSTALL_DIR}/index.js
Restart=always
RestartSec=3
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ProtectHome=true
ProtectControlGroups=true
ProtectKernelTunables=true
ProtectKernelModules=true
ProtectClock=true
PrivateDevices=true
LockPersonality=true
RestrictSUIDSGID=true
RestrictRealtime=true
RestrictNamespaces=true
ReadWritePaths=${INSTALL_DIR}
UMask=0077
LimitNOFILE=65536
TasksMax=256

[Install]
WantedBy=multi-user.target
EOF

echo "[5/5] Service starten"
systemctl daemon-reload
systemctl enable --now ${SERVICE_NAME}.service

echo "Fertig. Status prüfen mit: sudo systemctl status ${SERVICE_NAME}"
echo "Logs anzeigen mit: sudo journalctl -u ${SERVICE_NAME} -f"
