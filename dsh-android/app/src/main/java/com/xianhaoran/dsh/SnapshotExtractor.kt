package com.xianhaoran.dsh

import android.content.Context
import java.io.File

object SnapshotExtractor {
    const val SNAPSHOT_ASSET = "snapshot.tar.xz"
    const val RUNTIME_DIR = "runtime"

    /**
     * v0 骨架：仅检查运行时目录是否存在。
     *
     * phase 2 实现（参考 kelai141/dsh-mobile-apk 的 SnapshotExtractor）：
     * 1. 从 assets 读 snapshot.tar.xz 到 filesDir/runtime
     * 2. 校验 manifest（size / sha256 / arch=aarch64 / pageSize）
     * 3. 用 commons-compress + xz 解包（防路径穿越、防设备节点、原子晋升）
     * 4. 按 LINKS.txt 重建 lib/ 软链（Android SELinux 禁 link(2)）
     */
    fun ensureExtracted(context: Context): Boolean {
        val runtimeDir = File(context.filesDir, RUNTIME_DIR)
        // TODO(phase 2): 真正解压并校验
        return runtimeDir.exists()
    }
}
