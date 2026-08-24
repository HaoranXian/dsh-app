#!/usr/bin/env bash
# 在 Termux Docker (aarch64) 容器内构建 dsh 运行时快照（坚持 npm latest）。
# 原生模块（node-pty/koffi）由 Vengisk 官方 install.sh 在容器内用源码编译，
# 与 dsh 0.1.1-rc.2 依赖的 koffi 版本匹配，不再使用 rc.6 时代的预编译产物。
set -euo pipefail

export TERM=xterm-256color
export HOME="${HOME:-/data/data/com.termux/files/home}"
mkdir -p "$HOME/tmp"
export TMPDIR="$HOME/tmp"

echo "== 1/6 基础工具链（curl/tar/xz，其余由 install.sh 处理）=="
pkg update -y
pkg install -y curl tar xz-utils coreutils

echo "== 2/6 运行 Vengisk 官方 install.sh（安装 dsh@latest + 源码编译 node-pty/koffi）=="
mkdir -p "$HOME/tmp/vengisk"
rm -rf "$HOME/tmp/vengisk/patches"
cp -r /repo/patches "$HOME/tmp/vengisk/patches"
curl -fsSL -o "$HOME/tmp/vengisk/install.sh" \
  https://raw.githubusercontent.com/Vengisk/deepseek-harness-termux/main/install.sh
chmod +x "$HOME/tmp/vengisk/install.sh"
cd "$HOME/tmp/vengisk"
bash install.sh
cd /

echo "== 3/6 验证 dsh 与编译后的原生模块 =="
DSH_BIN="$(npm root -g)/@deepseek-ai/dsh/lib/bin.js"
node --expose-internals "$DSH_BIN" --version
node -e "require('$(npm root -g)/@deepseek-ai/dsh/node_modules/node-pty'); require('$(npm root -g)/@deepseek-ai/dsh/node_modules/koffi'); console.log('natives ok')"

echo "== 4/6 装配梁神模式 =="
# DSH_HOME 放 $PREFIX/dsh-home：既能写入，又能随快照打包、到设备后可迁移
export DSH_HOME="$PREFIX/dsh-home"
mkdir -p "$DSH_HOME/profiles/web"
node --expose-internals "$DSH_BIN" plugin --profile web add @linxin666/dsh-liangshen || true
# 移动布局：自研 mobile.css/mobile.js（见 mobile-patch/），不再装 dsh-web-mobile（与 rc.2 不兼容卡服务）
# 兜底：把梁神 presets 目录整体复制进 .agent-presets（自包含，避免 pnpm 绝对软链在设备上失效）
LS_PRESET="$DSH_HOME/profiles/web/node_modules/@linxin666/dsh-liangshen/presets/liangshen"
if [ -d "$LS_PRESET" ]; then
  mkdir -p "$DSH_HOME/.agent-presets/liangshen"
  cp -r "$LS_PRESET/." "$DSH_HOME/.agent-presets/liangshen/"
  echo "  [OK] copied liangshen presets -> $DSH_HOME/.agent-presets/liangshen"
else
  echo "  [WARN] liangshen presets not found at $LS_PRESET"
fi

echo "== 5/6 注入移动适配（mobile.css / mobile.js）=="
INDEX_HTML="$(find "$(npm root -g)/@deepseek-ai" -type f -path '*dist*' -name index.html 2>/dev/null | head -1)"
if [ -n "$INDEX_HTML" ]; then
  DIST_DIR="$(dirname "$INDEX_HTML")"
  cp /repo/mobile-patch/mobile.css "$DIST_DIR/mobile.css"
  cp /repo/mobile-patch/mobile.js "$DIST_DIR/mobile.js"
  if ! grep -q 'name="viewport"' "$INDEX_HTML"; then
    sed -i 's#</head>#<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover"></head>#' "$INDEX_HTML"
  fi
  sed -i 's#</head>#<link rel="stylesheet" href="./mobile.css"></head>#' "$INDEX_HTML"
  sed -i 's#</body>#<script src="./mobile.js"></script></body>#' "$INDEX_HTML"
  echo "  injected -> $DIST_DIR"
else
  echo "  [WARN] 未找到 dsh web index.html，移动适配未注入"
fi

echo "== 6/6 打包快照 =="
SNAPSHOT=/work/snapshot.tar.xz
tar -cJf "$SNAPSHOT" -C "$PREFIX" .
size=$(wc -c < "$SNAPSHOT" | tr -d ' ')
sha=$(sha256sum "$SNAPSHOT" | awk '{print $1}')
cat > /work/manifest.json <<EOF
{"version":"latest","dshVersion":"latest","arch":"aarch64","pageSize":16,"size":${size},"sha256":"${sha}","file":"snapshot.tar.xz"}
EOF
echo "done: size=${size} sha256=${sha}"
