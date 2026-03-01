#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR_PATH="${1:-}"

if [[ -z "${JAR_PATH}" ]]; then
  JAR_PATH="$(ls -1t "${SCRIPT_DIR}"/build/libs/*.jar 2>/dev/null | grep -v -- '-sources\.jar' | head -n 1 || true)"
fi

if [[ -z "${JAR_PATH}" || ! -f "${JAR_PATH}" ]]; then
  echo "Kein Mod-JAR gefunden."
  echo "Build zuerst ausführen, z.B. ./gradlew build"
  echo "Oder JAR-Pfad direkt übergeben: ./install-mod.sh /pfad/zur/mod.jar"
  exit 1
fi

MODS_DIR="/c/Users/mikov/.lunarclient/profiles/vanilla/1.21/mods/fabric-1.21.11"

mkdir -p "${MODS_DIR}"
cp -f "${JAR_PATH}" "${MODS_DIR}/"

echo "Mod installiert: ${JAR_PATH} -> ${MODS_DIR}"
echo "Starte Minecraft mit Fabric Loader 1.21.11 + Fabric API."
