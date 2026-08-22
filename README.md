# dsh-app

DeepSeek Harness macOS 桌面应用的一体化开发目录：三个独立 git 仓库 + 统一打包入口。

## 目录结构

```
dsh-app/
├── deepseek-harness/   # dsh 本体（官方仓库 clone，remote = deepseek-ai/deepseek-harness）
├── dsh-desktop/        # Electron app 壳（本目录的核心工程，含双快照与双更新逻辑）
├── dsh-web-ui/         # Web GUI 插件全家桶（remote = zhu1090093659/dsh-web-ui）
├── scripts/build-app.sh  # 一键打包：制作双快照 → electron-builder 出 dmg/zip
└── README.md
```

三个仓库各自独立 `git pull`，互不影响。

## Android 版（dsh-android）

新增目录 [dsh-android/](dsh-android/)：把 dsh 打包成 Android APK（本机完整版，内嵌 Termux 快照）。
设计文档见 [docs/design-android.md](docs/design-android.md)。

- 快照构建：GitHub Actions arm64 runner（`termux/termux-docker:aarch64`），手动触发 `workflow_dispatch` 或打 `v*` tag
- APK 构建：本地 `dsh-android` 目录，`JAVA_HOME=<Android Studio JBR>` `./gradlew assembleRelease`
- 目标设备：iQOO 15（arm64 / Android 15+）

## 打包

```sh
bash scripts/build-app.sh
```

产物：`dsh-desktop/dist/DeepSeek-Harness-<version>-mac-x64.dmg`。

## 运行时行为

- app 首次启动从内置快照解出 `userData/dsh`（harness）与 `userData/web-ui`（插件仓库），安装依赖并构建。
- 启动不再自动更新：直接使用现有受管副本（缺失依赖时补装），快速可用。
- 手动更新：菜单「服务 → 检查更新并重启」或启动页「检查更新」按钮。流程：
  拉取两个仓库（`git pull --ff-only`，15s 超时，失败/离线静默用现有副本）→ harness 重建 →
  web-ui 重装/构建/`link-profile.mjs` 链接/`dsh plugin --profile web add link:` 注册 → 重启服务并校验：
  - harness 服务端启动失败回滚 `lastGoodHead`；
  - 渲染层 UI 致命错误（double boot / web boot 失败）同样视为启动失败并回滚；
  - web-ui 失败回滚 `webUiLastGoodHead`；首次装配失败且无旧版可回滚时自动移除插件保证可启动。
- 插件装进 `~/.dsh/profiles/web`（`DSH_HOME` 优先）。注意 `link-profile.mjs` 硬编码 `~/.dsh`，若将来设置 `DSH_HOME` 需同步处理。
- git/pnpm 网络走代理：config.json 的 `proxy` 字段显式指定（如 `http://127.0.0.1:7897`），留空自动读 macOS 系统代理。
- 两仓库更新互不连坐，失败静默使用现有版本，不阻塞启动。

## 开发注意事项

- 不要同时手动跑 checkout 的 `pnpm dsh web` 和 app：端口 3080 与 `~/.dsh/profiles/node_modules` 链接会打架。
- 配置：`~/Library/Application Support/DeepSeek Harness/config.json`；日志：`~/Library/Logs/DeepSeek Harness/server.log`。
- 测试隔离环境变量：`DSH_DESKTOP_USER_DATA`、`DSH_HOME`（`HOME` 需同步指向同根目录以对齐 `link-profile.mjs`）。
- **本地未推送提交与快照**：dsh-web-ui checkout 若有未推送到 GitHub 的 commit（如 `ee73f0d` 的 dsh-skin 修复），快照会带上它；但受管副本 pull GitHub 时会因分叉失败，日志里每次启动都会出现一行 `web-ui update failed`（静默继续用快照版本）。推送到 GitHub 后自动恢复更新。
- **web-ui lib/ 自动构建兜底**：`lib/` 是 git 忽略的构建产物，快照可能缺失。app 在启动前按聚合包 include 清单核验每个包的 `lib/index.js`，缺失则自动 `pnpm run build` 补齐后再启动，保证 GitHub 拉取失败时插件仍可加载。
- app 自带 profile patch 自动修复兜底：`cordis.patch.yml` 损坏（双文档/无法解析）时备份为 `.corrupt-<时间戳>` 并修复后重启，不会卡死在启动报错。
