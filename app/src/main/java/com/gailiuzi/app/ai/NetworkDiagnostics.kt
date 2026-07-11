package com.gailiuzi.app.ai

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import kotlin.system.measureTimeMillis

data class NetworkProbeResult(
    val target: String,
    val reachable: Boolean,
    val latencyMs: Long = 0,
    val detail: String,
)

class NetworkDiagnostics(private val context: Context) {
    fun activeNetworkSummary(): String {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val network = manager.activeNetwork ?: return "当前没有可用网络"
        val capabilities = manager.getNetworkCapabilities(network) ?: return "无法读取网络能力"
        val transport = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "移动网络"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "有线网络"
            else -> "其他网络"
        }
        val validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        return "$transport · ${if (validated) "互联网已验证" else "互联网未验证"}"
    }

    suspend fun probe(baseUrl: String, timeoutSeconds: Int = 8): NetworkProbeResult =
        withContext(Dispatchers.IO) {
            if (baseUrl.isBlank()) {
                return@withContext NetworkProbeResult("未配置", false, detail = "Base URL为空")
            }
            runCatching {
                val url = URL(baseUrl)
                val host = url.host
                var responseCode = 0
                val elapsed = measureTimeMillis {
                    InetAddress.getByName(host)
                    val connection = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        instanceFollowRedirects = false
                        connectTimeout = timeoutSeconds.coerceIn(3, 30) * 1_000
                        readTimeout = timeoutSeconds.coerceIn(3, 30) * 1_000
                        setRequestProperty("User-Agent", "gailiuzi-android/0.3.0")
                    }
                    responseCode = connection.responseCode
                    connection.disconnect()
                }
                NetworkProbeResult(
                    target = host,
                    reachable = responseCode in 100..599,
                    latencyMs = elapsed,
                    detail = "HTTP $responseCode · ${elapsed}ms",
                )
            }.getOrElse { error ->
                NetworkProbeResult(
                    target = runCatching { URL(baseUrl).host }.getOrDefault(baseUrl),
                    reachable = false,
                    detail = error.message ?: error.javaClass.simpleName,
                )
            }
        }
}
