# assets

`snapshot.tar.xz`（可选）+ `manifest.json`（可选）由 GitHub Actions 的
`build-snapshot` 工作流产出。

- 本地构建 APK 前：把 artifact 里的 `snapshot.tar.xz` 复制到本目录。
- 该文件很大（约 70MB），**不入库**（根 .gitignore 已忽略）。
