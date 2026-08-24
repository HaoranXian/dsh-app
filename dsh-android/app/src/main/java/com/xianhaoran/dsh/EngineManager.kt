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
    private val dshHome: File get() = File(runtimeDir, "dsh-home")
    private val logFile: File get() = File(context.filesDir, "engine.log")
    private val tmpDir: File get() = File(context.filesDir, "tmp")
    private val workspaceDir: File get() = File(context.filesDir, "workspace")

    fun startEngineAsync(onProgress: (String) -> Unit = {}, onResult: (Boolean, String) -> Unit) {
        Thread {
            val (ok, message) = startEngineBlocking(onProgress)
            mainHandler.post { onResult(ok, message) }
        }.start()
    }

    private fun startEngineBlocking(onProgress: (String) -> Unit): Pair<Boolean, String> =
        synchronized(START_LOCK) { startEngineBlockingLocked(onProgress) }

    private fun startEngineBlockingLocked(onProgress: (String) -> Unit): Pair<Boolean, String> {
        if (EngineProbe.isRunning()) return true to "dsh 服务已在运行"

        if (!SnapshotExtractor.ensureExtracted(context, onProgress)) {
            return false to "快照解压/校验失败（请确认 assets/snapshot.tar.xz 已随包）"
        }
        // 准备默认工作区：app 私有目录（v1 先用私有；外部目录需等 M3 的存储权限流程）
        workspaceDir.mkdirs()
        // HOME 指向 runtime/home，dsh 目录浏览默认列 HOME，必须先创建
        File(runtimeDir, "home").mkdirs()
        patchAndroidDirectoryPicker()
        onProgress("启动 dsh 引擎…")

        val node = File(runtimeDir, "bin/node")
        val binJs = File(runtimeDir, "lib/node_modules/@deepseek-ai/dsh/lib/bin.js")
        Log.d("EngineManager", "node exists=" + node.exists() + " isFile=" + node.isFile + " canExec=" + node.canExecute())
        if (node.exists() && !node.canExecute()) {
            Log.d("EngineManager", "trying chmod +x node")
            node.setExecutable(true, true)
            node.setExecutable(true, false)
            Log.d("EngineManager", "after chmod canExec=" + node.canExecute())
        }
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
            // OpenSSL 默认去读编译期绝对路径，迁移后必须重定向
            "OPENSSL_CONF" to runtimeDir.absolutePath + "/etc/tls/openssl.cnf",
            "SSL_CERT_FILE" to runtimeDir.absolutePath + "/etc/tls/cert.pem",
            "DSH_DEFAULT_WORKSPACE" to workspaceDir.absolutePath,
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
            monitorProcess(process!!)

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

    /** 运行时把 host 目录选择器在 android 上改为返回默认工作区（原逻辑走 zenity，设备上无 GUI）。 */
    private fun patchAndroidDirectoryPicker() {
        val picker = File(
            runtimeDir,
            "lib/node_modules/@deepseek-ai/dsh/node_modules/@deepseek-ai/dsh-host-directory-picker-native/lib/index.js"
        )
        if (!picker.exists()) {
            Log.w("EngineManager", "directory-picker lib not found: " + picker.absolutePath)
            return
        }
        try {
            val text = picker.readText()
            if (text.contains("DSH_DEFAULT_WORKSPACE")) return
            val old = "if (platform === \"linux\" || platform === \"android\") {"
            if (!text.contains(old)) {
                Log.w("EngineManager", "directory-picker pattern not found; skip patch")
                return
            }
            val patched = text.replace(
                old,
                "if (platform === \"android\") { return outputPath(process.env.DSH_DEFAULT_WORKSPACE || \"/\"); } if (platform === \"linux\") {"
            )
            picker.writeText(patched)
            Log.d("EngineManager", "patched directory picker: android -> DSH_DEFAULT_WORKSPACE")
        } catch (e: Exception) {
            Log.e("EngineManager", "patch picker failed: " + e.message)
        }
    }

    /** 后台监控：进程退出时把退出码 + engine.log 尾部打到 logcat，便于真机排查。 */
    private fun monitorProcess(proc: Process) {
        Thread {
            try {
                val code = proc.waitFor()
                Log.e("EngineManager", "engine exited code=" + code)
                val lines = logFile.readLines().takeLast(30)
                for (l in lines) Log.e("EngineManager", "engine.log> " + l)
            } catch (_: Exception) {
            }
        }.start()
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

    companion object {
        /** 全局单锁：MainActivity 与看门狗是不同 EngineManager 实例，必须用静态锁防止双开引擎。 */
        private val START_LOCK = Any()
    }
}
