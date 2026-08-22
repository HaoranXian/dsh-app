package com.xianhaoran.dsh

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import java.io.FileOutputStream

class EngineManager(private val context: Context) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var process: Process? = null
    @Volatile private var stopping = false

    private val runtimeDir: File get() = File(context.filesDir, SnapshotExtractor.RUNTIME_DIR)
    private val dshHome: File get() = File(context.filesDir, "dshhome")
    private val logFile: File get() = File(context.filesDir, "engine.log")
    private val tmpDir: File get() = File(context.filesDir, "tmp")

    fun startEngineAsync(onProgress: (String) -> Unit = {}, onResult: (Boolean, String) -> Unit) {
        Thread {
            val (ok, message) = startEngineBlocking(onProgress)
            mainHandler.post { onResult(ok, message) }
        }.start()
    }

    private fun startEngineBlocking(onProgress: (String) -> Unit): Pair<Boolean, String> {
        if (EngineProbe.isRunning()) return true to "dsh 服务已在运行"

        if (!SnapshotExtractor.ensureExtracted(context, onProgress)) {
            return false to "快照解压/校验失败（请确认 assets/snapshot.tar.xz 已随包）"
        }
        onProgress("启动 dsh 引擎…")

        val node = File(runtimeDir, "bin/node")
        val binJs = File(runtimeDir, "lib/node_modules/@deepseek-ai/dsh/lib/bin.js")
        if (!node.exists() || !binJs.exists()) {
            return false to "运行时缺少 node 或 dsh lib/bin.js（快照不完整？）"
        }

        dshHome.mkdirs()
        tmpDir.mkdirs()

        val env = mapOf(
            "PATH" to runtimeDir.absolutePath + "/bin:/system/bin:/usr/bin",
            "LD_LIBRARY_PATH" to runtimeDir.absolutePath + "/lib",
            "HOME" to runtimeDir.absolutePath + "/home",
            "PREFIX" to runtimeDir.absolutePath,
            "TERM" to "xterm-256color",
            "DSH_HOME" to dshHome.absolutePath,
            "TMPDIR" to tmpDir.absolutePath,
            "SHELL" to "bash",
        )

        return try {
            val pb = ProcessBuilder(
                node.absolutePath,
                "--expose-internals",
                binJs.absolutePath,
                "web",
                "--host", "127.0.0.1",
                "--port", EngineProbe.DEFAULT_PORT.toString(),
            )
            pb.directory(runtimeDir)
            pb.redirectErrorStream(true)
            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile))
            pb.redirectError(ProcessBuilder.Redirect.appendTo(logFile))
            val envp = pb.environment()
            env.forEach { (k, v) -> envp[k] = v }

            process = pb.start()
            Log.d("EngineManager", "engine started, cwd=" + runtimeDir.absolutePath)

            val ready = waitReady(60_000)
            if (ready) {
                true to "dsh 引擎已就绪"
            } else {
                process?.destroy()
                process = null
                false to "引擎启动超时（查看 engine.log）"
            }
        } catch (e: Exception) {
            Log.e("EngineManager", "start failed", e)
            false to "引擎启动失败: " + e.message
        }
    }

    fun stopEngine() {
        stopping = true
        try {
            process?.destroy()
            process?.waitFor(3_000, java.util.concurrent.TimeUnit.MILLISECONDS)
            process?.destroyForcibly()
        } finally {
            process = null
            stopping = false
        }
    }

    private fun waitReady(timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (EngineProbe.isRunning()) return true
            Thread.sleep(500)
        }
        return false
    }
}
