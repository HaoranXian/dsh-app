#!/bin/bash
# dsh-app 一键打包：制作双快照（harness + web-ui）→ 打出 macOS x64 dmg/zip
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DESKTOP="$ROOT/dsh-desktop"

echo "== 1/2 制作源码快照（deepseek-harness + dsh-web-ui）=="
bash "$DESKTOP/scripts/bundle-dsh.sh"

echo "== 2/2 打包 dmg/zip =="
cd "$DESKTOP"
if [ -z "${ELECTRON_MIRROR:-}" ]; then
  export ELECTRON_MIRROR=https://npmmirror.com/mirrors/electron/
fi
npx electron-builder --mac

echo "完成。产物在 $DESKTOP/dist/"
