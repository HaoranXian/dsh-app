# dsh-android 设计文档（v0 草稿）

日期：2026-08-23
状态：已完成调研与决策访谈；v0 工程骨架已初始化，功能按里程碑逐步实现。

## 1. 目标

把 DeepSeek Harness（dsh）打包成可在 Android 手机（首发目标 iQOO 15，arm64）上
**完整本地运行**的 APK：agent 直接读写手机文件、在手机 Termux 用户空间执行 bash，
打开即用，无需外部电脑、无需 Termux App。

## 2. 决策记录（访谈定稿）

| # | 决策点 | 结论 |
|---|--------|------|
| 1 | 形态 | 本机完整版：DSH 跑在手机上 |
| 2 | 运行时载体 | 内嵌 Termux aarch64 快照（node + bash + coreutils + dsh + 补丁），解压即跑 |
| 3 | UI | 官方 DSH Web 界面 + 自写 mobile.css/mobile.js 移动适配 |
| 4 | 工程归属 | 在 dsh-app 目录下新建自研最小壳 dsh-android（Kotlin） |
| 5 | 快照来源 | 自建离线自包含快照，随 APK 打包 |
| 6 | 快照构建环境 | GitHub Actions arm64 runner + termux/termux-docker:aarch64（本机无 Docker/Intel） |
| 7 | dsh 版本 | 构建时取 npm 最新，并记录进 manifest（M1 实现：DSH_VERSION=latest，经 Vengisk dsh-termux 轻量包安装 0.1.1-rc.2 + 9 个 Android 补丁） |
| 8 | 首版功能 | 极简：官方内核 + 梁神模式预设 + 移动适配 |
| 9 | 工作区 | /storage/emulated/0/DeepSeekHarness/workspace（需要「所有文件访问」） |
| 10 | 更新 | 随 APK 升级，v1 不做在线热更 |
| 11 | 保活 | 前台服务 + 5s 看门狗，设置里可开关 |
| 12 | 特权 | Shizuku 可选，默认关，设置页开关（用户确认：要做但可关） |
| 13 | 本地安全 | 一次性随机 Basic 认证，内部 WebView 自动应答 |
| 14 | 构建/分发 | 本地 Gradle 构建 + 自建 keystore + adb/文件传输安装 |
| 15 | 原生壳 | 极简壳 + 小型设置页（保活/Shizuku/所有文件/日志/重启） |
| 16 | 首启 | 一小页权限引导，一键授权后进入 |
| 17 | CI | 新建公有仓库 dsh-app，GitHub Actions arm64 构建快照 |
| 18 | 本地 JDK | 使用 Android Studio 自带 JBR 21.0.6 |
| 19 | API Key 首启 | 预配置 DeepSeek 提供商；检测到无 Key 时 WebView 自动跳设置页 |
| 20 | 应用身份 | 包名 com.xianhaoran.dsh、应用名 DeepSeek Harness、沿用鲸鱼图标 |

## 3. 架构

```
APK (com.xianhaoran.dsh, targetSdk 34, minSdk 26)
├── 首次启动: 权限引导页（所有文件访问/通知/可选 Shizuku/电池优化）
├── 引擎启动:
│   1. 解压 assets/snapshot.tar.xz -> filesDir/runtime
│   2. 校验 manifest（size / sha256 / arch=aarch64 / pageSize / version）
│   3. 按 LINKS.txt 重建 runtime/lib 软链（SELinux 禁 link(2)，用 rename(2) 兜底）
│   4. 启动 node（PATH/LD_LIBRARY_PATH/TERM/HOME/DSH_HOME）
│      node --expose-internals .../dsh/lib/bin.js web --host 127.0.0.1 --port 3080
│   5. 生成一次性 256 位 token，Node preload 校验 HTTP/WS；内部 WebView 透明应答 Basic
├── WebView: http://127.0.0.1:3080（官方 DSH 界面 + mobile.css/mobile.js）
├── EngineService: 前台服务（"dsh 引擎运行中"）+ 5s 看门狗（崩溃自动重启，可开关）
└── 设置页: 保活开关 / Shizuku 开关 / 所有文件访问 / 日志 / 重启引擎
```

快照构建（开发者侧，非用户侧）：

```
GitHub Actions (ubuntu-24.04-arm)
└── docker run termux/termux-docker:aarch64
    ├── pkg install nodejs-lts bash coreutils git python ripgrep make clang cmake patch ...
    ├── npm install -g @deepseek-ai/dsh@latest
    ├── 应用 Android 兼容补丁（Vengisk patch 集 或 kelai dsh-shell-termux 方案）
    ├── 装配 @linxin666/dsh-liangshen（梁神模式）
    ├── 注入 mobile.css/mobile.js 到 dsh-web-frontend dist
    └── tar -cJf snapshot.tar.xz $PREFIX + manifest.json（sha256/大小/架构/版本）
```

## 4. 关键实现要点

- **targetSdk 34**：Android 15+ 禁止 targetSdk 35+ 在应用可写数据目录执行 ELF；
  targetSdk 34 允许在 filesDir 执行 node 二进制（woaiys3 用 28，kelai v2 用 34，先按 34 实现，真机验证）。
- **Node 版本**：Termux nodejs-lts（24.x），满足 dsh 引擎要求 ^22.19.0 || >=24.0.0，**不能用 23**。
- **--expose-internals**：cordis-plugin-hmr 访问 node:internal 模块，必须命令行参数传入。
- **原生模块**：node-pty / koffi 无 Android 官方预编译，需 Vengisk prebuilt（android-arm64）
  或容器内用 Termux NDK 编译；sharp 换 @img/sharp-wasm32。
- **bubblewrap sandbox**：Android sepolicy 拒绝，dsh 会降级 SandboxUnavailableError，不崩溃。
- **移动 UI 兼容**：WebView 可能缺 AbortSignal.timeout（Chrome <= 102），需 polyfill 注入 index.html。
- **16KB page size**：iQOO 15 可能 16KB 页；构建用最新 Termux 包（2025-08 起 bootstrap 已 16KB 对齐），
  发布前必须在真机验证 getconf PAGE_SIZE + APK 冒烟。
- **外部工作区权限**：MANAGE_EXTERNAL_STORAGE（特殊权限，用户手动授予）；OriginOS 可能需关闭电池优化。
- **API Key**：只存应用私有 dsh home（filesDir/dshhome），绝不写外部目录、不打包进 APK。

## 5. 里程碑

| 阶段 | 内容 | 验收 |
|------|------|------|
| M0（本次） | 设计文档 + 工程骨架 + GitHub Actions 占位 | 仓库可编译（待验证）、推送成功 |
| M1 | 快照构建跑通（Termux Docker/aarch64 产出 snapshot.tar.xz + manifest） | CI 出工件，含 dsh + 梁神模式 + 移动适配 |
| M2 | Android 壳：解压/校验/软链/启动 node/WebView/探活 | 真机会话可发起、bash 可执行 |
| M3 | 保活/看门狗/设置页/权限引导/Shizuku 开关 | 锁屏任务不被杀；开关生效 |
| M4 | 一次性 Basic 认证 + 日志 + 真机冒烟（16KB 页、字号、软键盘） | 通过验证清单 |

## 6. 本机前置（已确认）

- Android SDK：~/Library/Android/sdk（platform android-36.1、build-tools 36）
- JDK：/Applications/Android Studio.app/Contents/jbr/Contents/Home（21.0.6）
- Node 22.22.3 / pnpm 11.7.0（构建脚本/本地工具）
- GitHub：新建公有仓库 HaoranXian/dsh-app，SSH key 已注册并验证

## 7. 风险与对策

| 风险 | 对策 |
|------|------|
| 16KB page size 不兼容 | 用最新 Termux 包 + NDK r28c 级对齐；真机 getconf PAGE_SIZE 验证；不行回退真机 Termux 构建 |
| OriginOS/ColorOS 杀后台 | 前台服务 + 看门狗 + 设置内引导电池优化白名单 |
| WebView 兼容（WebSocket/Clipboard/AbortSignal） | androidBridge 桥 + polyfill + 针对性 CSS |
| dsh 升级破坏补丁 | 快照构建脚本固定补丁集 commit；manifest 记录 dsh 版本与补丁 tag |
| 应用私有目录执行限制 | targetSdk 34；如某机型仍 EACCES，迁移 node 到 nativeLibraryDir（进阶方案） |
| APK 体积（~100-150MB） | v1 只装极简内容；后续再按需加插件 |
