# mobile-patch

面向官方 DSH Web 前端的移动适配补丁（M1 最小实现）：

- `mobile.css`：字号下限（16px）、文本缩放禁用、代码块换行、安全区变量
- `mobile.js`：`AbortSignal.timeout` polyfill + VisualViewport 软键盘高度

快照构建脚本（`scripts/inside-termux-build.sh`）会在 Termux 容器内把这两个文件
注入 `dsh-web-frontend/dist/index.html`。

后续迭代（M3）：窄屏侧栏抽屉化、触摸目标尺寸、更深层的桌面 UI 重排。
