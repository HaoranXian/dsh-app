# dsh-android 进度快照（2026-08-23 凌晨）

## 已完成

- M0 工程骨架：dsh-android（Kotlin）本地 assembleDebug 通过，APK 936KB（无快照）。
- GitHub 仓库 HaoranXian/dsh-app 已推送，SSH key 已配置。
- M2 核心代码已写：SnapshotExtractor（解压/校验/原子切换）、EngineManager（启动 node + 探活）、EngineService（看门狗自动重启）。本地编译通过。
- M1 CI 链路已跑通大部分：
  - GitHub Actions arm64 + termux/termux-docker:aarch64 正常；
  - Vengisk dsh-termux 轻量包安装成功（手动执行 install.js，绕过 npm allowScripts 门禁）；
  - 9 个 Android 补丁全部应用；
  - libandroid-spawn 已补装，pty/koffi 直接 require .node 成功。

## 当前唯一阻塞（M1 最后一公里）

koffi 包级加载报错：Error: Mismatched native Koffi modules（koffi/src/koffi/index.cjs:271）。

原因：Vengisk 预编译 koffi.node 对应 dsh 0.1.0-rc.6 时代的 koffi 版本；
而按用户要求装 npm latest @deepseek-ai/dsh（0.1.1-rc.2），其依赖的 koffi JS 包版本不同，
校验 native 模块元数据时 mismatch。

## 决策（用户已确认：坚持 latest）

- 选定 B：在 Termux Docker 内用源码重编 koffi 和 node-pty（容器已有 clang/cmake/ndk-sysroot），适配 dsh 0.1.1-rc.2 依赖的 koffi 版本；
  或等 Vengisk 更新预编译。不降级到 0.1.0-rc.6。

## 下一轮操作

1. 改 dsh-android/scripts/inside-termux-build.sh 的 DSH_VERSION（或给 workflow 传参）。
2. 打 tag v0.1.0-m12 触发 CI；成功后会发布 release 资产 snapshot.tar.xz + manifest.json。
3. 下载 release asset -> 放 dsh-android/app/src/main/assets/ -> 本地 ./gradlew assembleRelease。
4. 真机安装到 iQOO 15 冒烟（16KB 页 / 权限 / 软键盘 / 字号 / WebSocket）。

## 备注

- CI 调试日志会以 issue 形式出现（1~8 号），稳定后可关闭。
- 诊断用的 file/直接 require 打印保留在脚本里，跑通后可精简。
- 本地构建命令：JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assembleDebug --no-daemon
