# Android 兼容补丁（已 vendor）

来源：Vengisk/deepseek-harness-termux（MIT）的 `patches/` 目录，固定于
`v0.1.0-termux.1` release 所附 `dsh-termux.tgz` 包内版本。共 9 个：

| 补丁 | 目标包 |
|------|--------|
| 01-terminal-bash-android-shell.patch | dsh-terminal-bash |
| 02-session-persistence-link-rename.patch | dsh-session-persistence-jsonl |
| 03-subprocess-local-android.patch | dsh-subprocess-local |
| 04-host-apiproxy-termux-open-index.patch | dsh-host-apiproxy |
| 04-host-apiproxy-termux-open-opener.patch | dsh-host-apiproxy |
| 05-host-directory-picker-native-android.patch | dsh-host-directory-picker-native |
| 06-workspace-archive-skip-session-known-check.patch | dsh-workspace |
| 07-sandbox-local-proot-runner.patch | dsh-sandbox-local |
| koffi-statx.patch | koffi（非 @deepseek-ai scope） |

构建流程：`scripts/inside-termux-build.sh` 通过安装 Vengisk 轻量包
（`dsh-termux.tgz`，DSH_VERSION 可覆盖为 latest）来完成打补丁与预编译原生模块，
与仓库内这些补丁保持同一来源；如需离线/自研路线，可用这些 `*.patch` + Termux NDK 自行编译。
