package com.xianhaoran.dsh

import android.content.Context
import android.util.Log
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.tukaani.xz.XZInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

object SnapshotExtractor {
    private const val TAG = "SnapshotExtractor"
    const val SNAPSHOT_ASSET = "snapshot.tar.xz"
    const val MANIFEST_ASSET = "manifest.json"
    const val RUNTIME_DIR = "runtime"
    private const val STAGING_DIR = "runtime.staging"
    private const val OK_MARKER = "extracted.ok"

    /**
     * 确保运行时已解压并校验。
     * 流程：assets -> filesDir（sha256/大小校验）-> 解压到 staging -> 原子切换 runtime。
     */
    @Synchronized
    fun ensureExtracted(context: Context, onProgress: (String) -> Unit = {}): Boolean {
        val filesDir = context.filesDir
        val runtimeDir = File(filesDir, RUNTIME_DIR)
        val stagingDir = File(filesDir, STAGING_DIR)

        if (isExtracted(runtimeDir)) return true

        // 1) 复制 assets/snapshot.tar.xz 到 filesDir（assets 不可直接流式 sha256）
        val snapshotFile = File(filesDir, SNAPSHOT_ASSET)
        if (!snapshotFile.exists() || snapshotFile.length() == 0L) {
            onProgress("正在复制快照…")
            if (!copyAssetToFile(context, SNAPSHOT_ASSET, snapshotFile)) {
                return false
            }
        }

        // 2) manifest 校验（可选；有就校验 sha256 + 大小）
        val manifest = readManifest(context)
        val expectedSha = manifest?.get("sha256") as? String
        val expectedSize = manifest?.get("size")?.let { it as? Number }?.toLong()
        if (expectedSize != null && snapshotFile.length() != expectedSize) {
            Log.e(TAG, "snapshot size mismatch: " + snapshotFile.length() + " != " + expectedSize)
            return false
        }
        if (!expectedSha.isNullOrBlank()) {
            onProgress("校验快照摘要…")
            val actual = sha256(snapshotFile)
            if (!actual.equals(expectedSha, ignoreCase = true)) {
                Log.e(TAG, "snapshot sha256 mismatch: " + actual + " != " + expectedSha)
                return false
            }
        }

        // 3) 解压到 staging
        onProgress("解压运行时（约 10-60s）…")
        stagingDir.deleteRecursively()
        stagingDir.mkdirs()
        if (!extractTarXz(snapshotFile, stagingDir)) {
            stagingDir.deleteRecursively()
            return false
        }

        // 4) 原子切换
        onProgress("完成解压…")
        runtimeDir.deleteRecursively()
        try {
            Files.move(stagingDir.toPath(), runtimeDir.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
            if (!stagingDir.renameTo(runtimeDir)) {
                stagingDir.deleteRecursively()
                return false
            }
        }
        val marker = File(runtimeDir, OK_MARKER)
        marker.writeText(manifest?.get("version")?.toString() ?: "unknown")
        return true
    }

    fun isExtracted(runtimeDir: File): Boolean =
        runtimeDir.exists() && File(runtimeDir, "bin/node").exists() && File(runtimeDir, OK_MARKER).exists()

    /**
     * 可靠地把 asset 复制到文件。
     * 优先 openFd().createInputStream()（对 noCompress 的 Stored 资产最稳，避免
     * AssetManager 流在部分真机上截断大文件）；失败则回退 assets.open() 流式复制。
     */
    private fun copyAssetToFile(context: Context, assetName: String, dest: File): Boolean {
        dest.parentFile?.mkdirs()
        try {
            val afd = context.assets.openFd(assetName)
            try {
                afd.createInputStream().use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
            } finally {
                afd.close()
            }
            Log.i(TAG, "copied " + assetName + " -> " + dest.length())
            return true
        } catch (e: Exception) {
            Log.e(TAG, "openFd copy failed, falling back to open(): " + e.message)
            return try {
                context.assets.open(assetName).use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
                true
            } catch (e2: Exception) {
                Log.e(TAG, "fallback copy failed: " + e2.message)
                false
            }
        }
    }

    private fun readManifest(context: Context): Map<String, Any>? {
        return try {
            val raw = context.assets.open(MANIFEST_ASSET).bufferedReader().use { it.readText() }
            @Suppress("DEPRECATION")
            val json = org.json.JSONObject(raw)
            json.keys().asSequence().associateWith { json.get(it) }
        } catch (_: Exception) {
            null
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buf = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { b -> "%02x".format(b) }
    }

    private fun extractTarXz(archive: File, destDir: File): Boolean {
        return try {
            destDir.mkdirs()
            val base = destDir.toPath().toAbsolutePath().normalize()
            XZInputStream(BufferedInputStream(FileInputStream(archive))).use { xz ->
                TarArchiveInputStream(xz).use { tar ->
                    var entry: TarArchiveEntry? = tar.nextEntry
                    while (entry != null) {
                        val name = entry.name.removePrefix("./")
                        val target = base.resolve(name).normalize()
                        if (!target.startsWith(base)) {
                            Log.e(TAG, "path traversal: " + entry.name)
                            return false
                        }
                        when {
                            entry.isDirectory -> target.toFile().mkdirs()
                            entry.isSymbolicLink -> {
                                target.parent?.toFile()?.mkdirs()
                                val link = entry.linkName
                                try {
                                    Files.createSymbolicLink(target, java.nio.file.Paths.get(link))
                                } catch (_: Exception) {
                                    // SELinux 禁软链时降级为普通文件复制（相对链接尽力解析）
                                    val src = if (link.startsWith("/")) {
                                        File(link)
                                    } else {
                                        base.resolve(link).normalize().toFile()
                                    }
                                    if (src.exists() && !src.isDirectory) {
                                        src.copyTo(target.toFile(), overwrite = true)
                                    }
                                }
                            }
                            else -> {
                                target.parent?.toFile()?.mkdirs()
                                FileOutputStream(target.toFile()).use { out ->
                                    tar.copyTo(out)
                                }
                            }
                        }
                        entry = tar.nextEntry
                    }
                }
            }
            // 关键二进制直接置为可执行
            val bin = File(destDir, "bin")
            bin.listFiles()?.forEach { f -> f.setExecutable(true, true) }
            true
        } catch (e: Exception) {
            Log.e(TAG, "extract failed: " + e.message, e)
            false
        }
    }
}
