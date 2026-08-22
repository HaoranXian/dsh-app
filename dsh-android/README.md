# dsh-android

DeepSeek Harness 的 Android 本机版壳（自研最小壳）。

- 运行时：APK 内嵌 Termux aarch64 快照（node + bash + coreutils + dsh + 补丁），解压即跑
- UI：官方 DSH Web 界面 + 自写 mobile.css/mobile.js 移动适配（窄屏、字号下限、软键盘）
- 工作区：`/storage/emulated/0/DeepSeekHarness/workspace`（需「所有文件访问」）
- 保活：前台服务 + 5s 看门狗（可开关）
- 特权：Shizuku 可选（默认关，设置页开关）
- 安全：引擎仅绑 127.0.0.1 + 一次性随机 Basic 认证

## 目录

```
dsh-android/
├── app/                          # Android 应用（Kotlin）
│   └── src/main/
│       ├── assets/               # 构建时放入 snapshot.tar.xz（CI 产出）
│       ├── java/com/xianhaoran/dsh/
│       │   ├── MainActivity.kt   # WebView 壳
│       │   ├── EngineService.kt  # 前台服务 + 看门狗
│       │   ├── EngineManager.kt  # 引擎生命周期
│       │   ├── EngineProbe.kt    # 127.0.0.1:3080 探活
│       │   ├── SnapshotExtractor.kt # 解压/校验（骨架）
│       │   └── AndroidBridge.kt  # JS 桥（骨架）
│       └── res/
├── gradle/wrapper/
├── scripts/
│   ├── build-snapshot.sh         # GitHub Actions arm64 构建快照
│   └── inside-termux-build.sh    # 在 Termux Docker 容器内执行的构建
├── patches/                      # Android 兼容补丁（待 vendor，详见 README）
└── docs/design-android.md        # 设计文档（本仓库根 docs/ 处也有副本）
```

## 本地构建 APK

```bash
# 1. 准备快照（缺一不可）
#    GitHub Actions -> dsh-snapshot 工件 -> snapshot.tar.xz
cp snapshot.tar.xz app/src/main/assets/

# 2. 构建（用 Android Studio 自带 JBR 21）
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assembleRelease
# 产物：app/build/outputs/apk/release/app-release.apk
```

## 状态

v0 骨架：可编译的工程结构与占位实现。引擎启动/解压/桥接按设计文档 phase 2 实现。
