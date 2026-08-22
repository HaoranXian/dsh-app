# Android 兼容补丁（phase 1 落地）

快照内的 `@deepseek-ai/dsh` 需要 Android/bionic 兼容补丁。两个候选来源：

1. **Vengisk/deepseek-harness-termux**（MIT）：源码补丁集，已实测覆盖
   - `01-terminal-bash-android-shell.patch`（bash 路径）
   - `02-session-persistence-link-rename.patch`（link→rename）
   - `03-subprocess-local-android.patch`（platform === android）
   - `04-host-apiproxy-termux-open-*.patch`（termux-open）
   - `05-host-directory-picker-native-android.patch`
   - `koffi-statx.patch`
2. **kelai141/dsh-shell-termux**（MIT）：作为 `ctx.shell` 实现 Termux bash 能力（不改 dsh-terminal-bash 源码）。

实施时二选一（或组合），并固定到补丁集的 commit。**本目录现在只有说明**，
实际 `*.patch` 在 phase 1 落地时按上面来源 vendor 进仓库（MIT 许可，注意保留 NOTICE/版权）。
