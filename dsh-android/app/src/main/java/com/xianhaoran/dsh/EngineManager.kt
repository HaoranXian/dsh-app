package com.xianhaoran.dsh

import android.content.Context
import android.os.Handler
import android.os.Looper

class EngineManager(private val context: Context) {
    private val mainHandler = Handler(Looper.getMainLooper())

    fun startEngineAsync(onResult: (Boolean, String) -> Unit) {
        Thread {
            val (ok, message) = startEngineBlocking()
            mainHandler.post { onResult(ok, message) }
        }.start()
    }

    private fun startEngineBlocking(): Pair<Boolean, String> {
        // phase 2 流程：
        //   ensureExtracted -> 建软链 -> spawn node（PATH/LD_LIBRARY_PATH/TERM/HOME/DSH_HOME）
        //   node --expose-internals <dsh>/lib/bin.js web --host 127.0.0.1 --port 3080
        if (SnapshotExtractor.ensureExtracted(context)) {
            val ok = EngineProbe.isRunning()
            return ok to if (ok) "dsh 服务已在运行" else "快照已就绪，引擎启动逻辑待实现（骨架）"
        }
        // 骨架阶段：没有快照时只允许复用外部已启动的 dsh
        val ok = EngineProbe.isRunning()
        return ok to if (ok) "检测到已有 dsh 服务" else "快照未就绪（骨架）"
    }
}
