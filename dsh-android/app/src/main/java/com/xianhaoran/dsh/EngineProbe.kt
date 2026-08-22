package com.xianhaoran.dsh

import java.net.InetSocketAddress
import java.net.Socket

object EngineProbe {
    const val DEFAULT_HOST = "127.0.0.1"
    const val DEFAULT_PORT = 3080

    /** 探活：只判断端口可连。phase 2 换 /api 探测（响应体含 DeepSeek Harness）。 */
    fun isRunning(host: String = DEFAULT_HOST, port: Int = DEFAULT_PORT, timeoutMs: Int = 1500): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                true
            }
        } catch (_: Exception) {
            false
        }
    }
}
