#!/usr/bin/env bash
# dsh Android 运行时快照构建：GitHub Actions arm64 runner 上运行。
# 依赖：docker（GitHub Actions 自带），termux/termux-docker:aarch64。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="${ROOT}/build"
WORK="${OUT}/snapshot-work"
rm -rf "$WORK" "$OUT/snapshot.tar.xz" "$OUT/manifest.json"
mkdir -p "$WORK" "$OUT"

echo "== 在 Termux Docker (aarch64) 中构建快照 =="
docker run --rm \
  -v "${ROOT}:/repo" \
  -v "${WORK}:/work" \
  -e DSH_VERSION="${DSH_VERSION:-latest}" \
  termux/termux-docker:aarch64 \
  bash /repo/scripts/inside-termux-build.sh

echo "== 产出 =="
ls -lh "$WORK"/snapshot.tar.xz "$WORK"/manifest.json
cp "$WORK/snapshot.tar.xz" "$OUT/snapshot.tar.xz"
cp "$WORK/manifest.json" "$OUT/manifest.json"
echo "快照就绪：$OUT/snapshot.tar.xz"
