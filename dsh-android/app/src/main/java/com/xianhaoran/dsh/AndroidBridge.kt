package com.xianhaoran.dsh

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.webkit.JavascriptInterface

/** JS 桥 v1（骨架）。phase 2 按需扩展：pickDirectory / pickImage / setTextZoom / 日志等。 */
class AndroidBridge(private val activity: android.app.Activity) {

    @JavascriptInterface
    fun version(): String {
        return try {
            activity.packageManager.getPackageInfo(activity.packageName, 0).versionName ?: "0.0.0"
        } catch (_: Exception) {
            "0.0.0"
        }
    }

    @JavascriptInterface
    fun checkEngine(): String {
        val running = EngineProbe.isRunning()
        return """{"running":$running,"latencyMs":0,"error":null}"""
    }

    @JavascriptInterface
    fun hasAllFilesAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    @JavascriptInterface
    fun copyText(text: String): Boolean {
        return try {
            val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("dsh", text))
            true
        } catch (_: Exception) {
            false
        }
    }
}
