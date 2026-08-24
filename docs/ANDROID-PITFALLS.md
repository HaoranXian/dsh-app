# dsh-android 已知坑位与规避（ANDROID-PITFALLS）

> 目的：把踩过的坑、原因、修复、以及“下次如何预判”记录下来，避免重复试错。
> 关联：docs/STATUS-android.md（进度）、.github/workflows/build-snapshot.yml（CI）。

## 环境类（Termux Docker + GitHub Actions arm64）

| # | 坑 | 现象 | 原因 | 修复 | 下次如何预判 |
|---|----|------|------|------|--------------|
| 1 | 容器内 /tmp 不可写 | curl: (23) client returned ERROR on write | Termux Docker 里 /tmp 非用户可写 | 下载改到 $HOME/tmp 并 export TMPDIR | 容器入口先 touch $HOME/tmp/w、df -h |
| 2 | 容器内 /work 挂载不可写 | tar: /work/...: Permission denied | Docker bind mount 目录权限为 root | host 侧 chmod 777 $WORK 再挂载 | 进容器 touch /work/w 自检 |
| 3 | /data 无权限 | mkdir /data/dshhome: Permission denied | 容器用户对 / 只读 | DSH_HOME 放 $PREFIX/dsh-home | 一律用 $PREFIX 或 $HOME 下的路径 |
| 4 | npm 11 脚本门禁 | install.js 未运行（allowScripts 警告） | Termux npm 默认禁止未批准脚本 | 直接 node install.js 或 --allow-scripts | 用 Vengisk 官方 install.sh，不要自造 npm 装法 |
| 5 | GNU stat 缺失 | install.sh 看门狗误判 | Termux 默认 stat 可能非 GNU | 先 pkg install coreutils | 依赖 GNU coreutils 的脚本先装 coreutils |
| 6 | libandroid-spawn 缺失 | dlopen failed: libandroid-spawn.so not found | node-pty/koffi 预编译依赖该 Termux 包 | pkg install libandroid-spawn | 阅读上游 install.sh 的 SYSTEM_PKGS |

## 版本/ABI 类

| # | 坑 | 现象 | 原因 | 修复 | 预判 |
|---|----|------|------|------|------|
| 7 | koffi 预编译版本不匹配 | Mismatched native Koffi modules | Vengisk 预编译 koffi 对应 dsh rc.6，latest rc.2 依赖不同 koffi | 用源码重编（Vengisk install.sh 已处理） | 用上游 install.sh 而不是手放预编译 |
| 8 | 16KB page size | （未实际踩到，预警） | 新机 16KB 页 | 用最新 Termux 包 + 真机 getconf PAGE_SIZE 验证 | 发布前真机冒烟项 |

## GitHub Actions 机制类

| # | 坑 | 现象 | 原因 | 修复 | 预判 |
|---|----|------|------|------|------|
| 9 | 日志/artifact 下载需认证 | API 403 | 该端点要求管理员权限 | 用“失败自动发 Issue 含日志”机制 | 一开始就建好诊断基建 |
| 10 | 发布 Release 权限不足 | Resource not accessible by integration (generate-release-notes) | permissions 只有 read | 设 contents: write | 建 workflow 时就查 permissions 模型 |
| 11 | Node20 action 弃用警告 | annotation warning | actions/checkout@v4 等 | 升级 action 大版本 | 建 workflow 时用最新大版本 |

## 规避原则（以后怎么做）

1. 复用上游已验证脚本（Vengisk install.sh / kelai 引擎代码），不要自造第二套。
2. 容器入口先跑“环境自检”：id、权限、/tmp、/work、node 版本、磁盘、PATH、GNU 工具。
3. CI 诊断基建一步到位：失败自动发 Issue、日志 tee 到 artifact、if: always() 上传。
4. 每轮尽量批量修 + 高信息量日志，减少 5 分钟/轮的试错成本。
5. 踩到新坑就补进本表。
