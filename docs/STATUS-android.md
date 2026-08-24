# dsh-android 进度快照（2026-08-23 凌晨）

## 已完成

- M0 工程骨架：dsh-android（Kotlin）本地 assembleDebug 通过，APK 936KB（无快照）。
- GitHub 仓库 HaoranXian/dsh-app 已推送，SSH key 已配置。
- M2 核心代码已写：SnapshotExtractor（解压/校验/原子切换）、EngineManager（启动 node + 探活）、EngineService（看门狗自动重启）。本地编译通过。
- M1 快照 CI 全绿：
  - GitHub Actions arm64 + termux/termux-docker:aarch64 正常；
  - 采用 Vengisk 官方 install.sh（源码编译 node-pty/koffi，适配 dsh 0.1.1-rc.2）；
  - 9 个 Android 补丁全部应用；libandroid-spawn/coreutils 已补装；
  - release 资产已发布：v0.1.0-m15 的 snapshot.tar.xz（265MB）+ manifest.json；
  - 本地已用快照打出 267MB release APK 并签名（debug key）。
- 梁神模式：profile 已装配，另把 presets 目录复制进 .agent-presets（自包含，防软链迁移失效）——随 m16 重打。

## 当前唯一阻塞（M1 最后一公里）

koffi 包级加载报错：Error: Mismatched native Koffi modules（koffi/src/koffi/index.cjs:271）。

原因：Vengisk 预编译 koffi.node 对应 dsh 0.1.0-rc.6 时代的 koffi 版本；
而按用户要求装 npm latest @deepseek-ai/dsh（0.1.1-rc.2），其依赖的 koffi JS 包版本不同，
校验 native 模块元数据时 mismatch。

## 决策（用户已确认：坚持 latest）

- 选定 B：在 Termux Docker 内用源码重编 koffi 和 node-pty（容器已有 clang/cmake/ndk-sysroot），适配 dsh 0.1.1-rc.2 依赖的 koffi 版本；
  或等 Vengisk 更新预编译。不降级到 0.1.0-rc.6。

## 当前状态（2026-08-24）

- M1 快照 CI 全绿：v0.1.0-m16 已发布 release 资产（snapshot.tar.xz 265MB + manifest.json，sha256 已校验）。
- 本地已用 m16 快照打出 267MB release APK 并用 debug key 签名。
- 产物路径：dsh-android/app/build/outputs/apk/release/app-release.apk

## 下一步：真机安装冒烟（需要你在 iQOO 15 上操作）

1. 把 app-release.apk 传到手机（USB/网盘），允许“安装未知来源应用”后安装。
2. 首次启动会解压快照（265MB -> 数 GB），耐心等；之后自动起 dsh web（127.0.0.1:3080）进官方界面。
3. 重点验证：16KB 页、权限引导、引擎启动、对话、bash 工具、梁神模式预设、软键盘/字号/WebSocket。
4. 记录任何报错（engine.log / 崩溃），回传给我；顺带看 APK 体积是否可接受（265MB 偏大，可后续精简）。

## 备注

- CI 调试日志会以 issue 形式出现（1~8 号），稳定后可关闭。
- 诊断用的 file/直接 require 打印保留在脚本里，跑通后可精简。
- 本地构建命令：JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assembleDebug --no-daemon
