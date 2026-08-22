#!/usr/bin/env bash
# 在 Termux Docker (aarch64) 容器内构建 dsh 运行时快照。
# 调用方：build-snapshot.sh（已挂载 /repo 与 /work）
# 说明：使用 Vengisk dsh-termux 轻量预编译包：
#   - DSH_VERSION 继承自环境变量（CI 传 latest 或具体版本）
#   - 包内 postinstall 会安装对应版本 @deepseek-ai/dsh、应用 9 个 Android 补丁、
#     放入 android-arm64 预编译 node-pty/koffi、装 sharp-wasm32、修正 shebang
set -euo pipefail

export TERM=xterm-256color
export PREFIX="${PREFIX:-/data/data/com.termux/files/usr}"
export PATH="$PREFIX/bin:/system/bin:$PATH"
export LD_LIBRARY_PATH="$PREFIX/lib${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
DSH_VERSION="${DSH_VERSION:-latest}"

echo "== 1/6 安装 Termux 基础工具链 =="
pkg update -y
pkg install -y nodejs-lts bash coreutils git python ripgrep make clang cmake patch tar xz-utils curl

echo "== 2/6 安装 dsh-termux 轻量预编译包（DSH_VERSION=${DSH_VERSION}）=="
mkdir -p "$HOME/tmp"
curl -fsSL -o "$HOME/tmp/dsh-termux.tgz" \
  https://github.com/Vengisk/deepseek-harness-termux/releases/download/v0.1.0-termux.1/dsh-termux.tgz
DSH_VERSION="${DSH_VERSION}" npm install -g "$HOME/tmp/dsh-termux.tgz"

echo "== 3/6 验证 dsh 与预编译原生模块 =="
DSH_BIN="$(npm root -g)/@deepseek-ai/dsh/lib/bin.js"
node --expose-internals "$DSH_BIN" --version
node -e "require('$(npm root -g)/@deepseek-ai/dsh/node_modules/node-pty'); require('$(npm root -g)/@deepseek-ai/dsh/node_modules/koffi'); console.log('natives ok')"

echo "== 4/6 装配梁神模式 =="
export DSH_HOME=/data/dshhome
mkdir -p "$DSH_HOME/profiles/web"
node --expose-internals "$DSH_BIN" plugin --profile web add @linxin666/dsh-liangshen || true

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
{"version":"${DSH_VERSION}","dshVersion":"${DSH_VERSION}","arch":"aarch64","pageSize":16,"size":${size},"sha256":"${sha}","file":"snapshot.tar.xz"}
EOF
echo "done: size=${size} sha256=${sha}"
