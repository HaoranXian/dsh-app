#!/usr/bin/env bash
# 在 Termux Docker (aarch64) 容器内构建 dsh 运行时快照。
# 调用方：build-snapshot.sh（已挂载 /repo 与 /work）
set -euo pipefail

export TERM=xterm-256color
DSH_VERSION="${DSH_VERSION:-latest}"
echo "== 安装 Termux 基础工具链 =="
pkg update -y
pkg install -y nodejs-lts bash coreutils git python ripgrep make clang cmake patch tar xz-utils

echo "== 安装 dsh (${DSH_VERSION}) =="
if [ "${DSH_VERSION}" = "latest" ]; then
  npm install -g @deepseek-ai/dsh@latest
else
  npm install -g "@deepseek-ai/dsh@${DSH_VERSION}"
fi

echo "== 应用 Android 兼容补丁 =="
if [ -d /repo/patches ]; then
  for p in /repo/patches/*.patch; do
    [ -e "$p" ] || continue
    echo "== apply $(basename "$p") =="
    pushd "$(npm root -g)" >/dev/null
    patch -p1 < "$p" || true
    popd >/dev/null
  done
fi

echo "== 预编译原生模块（node-pty/koffi 等）=="
# TODO(phase 1): 从 Vengisk release 合并 android-arm64 预编译产物，或在本容器内
#   clang + cmake 编译 node-pty/koffi（NDK sysroot 来自 Termux）。

echo "== 装配梁神模式 =="
# 使用 npm 已发布包；默认 profile web 由 dsh 自动创建
export DSH_HOME=/data/dshhome
export PATH="$PREFIX/bin:$PATH"
node --expose-internals "$(npm root -g)/@deepseek-ai/dsh/lib/bin.js" plugin --profile web add @linxin666/dsh-liangshen || true

echo "== 打移动适配补丁（mobile.css/mobile.js 注入 dsh-web-frontend dist）=="
# TODO(phase 1): 把仓库 mobile-patch/ 下的文件注入 dist/index.html；或在插件层实现。

echo "== 打包快照 =="
SNAPSHOT=/work/snapshot.tar.xz
tar -cJf "$SNAPSHOT" -C "$PREFIX" .
size=$(wc -c < "$SNAPSHOT" | tr -d ' ')
sha=$(sha256sum "$SNAPSHOT" | awk '{print $1}')
cat > /work/manifest.json <<EOF
{"version":"${DSH_VERSION}","arch":"aarch64","pageSize":16,"size":${size},"sha256":"${sha}","file":"snapshot.tar.xz"}
EOF
echo "done: size=${size} sha256=${sha}"
