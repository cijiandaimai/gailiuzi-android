package com.gailiuzi.app.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import kotlin.math.min

class AiGateway {
    suspend fun testConnection(
        config: AiProviderConfig,
        apiKey: String,
        route: ResolvedAiRoute = ResolvedAiRoute(config.baseUrl, false),
        connectTimeoutSeconds: Int = 20,
    ): AiCallResult = generate(
        config = config,
        apiKey = apiKey,
        prompt = ReplyPrompt("这是API连通性测试。", "只回复：连接成功"),
        maxOutputTokens = 32,
        route = route,
        connectTimeoutSeconds = connectTimeoutSeconds,
    )

    suspend fun generate(
        config: AiProviderConfig,
        apiKey: String,
        prompt: ReplyPrompt,
        maxOutputTokens: Int = 500,
        route: ResolvedAiRoute = ResolvedAiRoute(config.baseUrl, false),
        connectTimeoutSeconds: Int = 20,
        maxAttempts: Int = 3,
    ): AiCallResult = withContext(Dispatchers.IO) {
        validate(config, apiKey, route)?.let { return@withContext it }
        val attemptsLimit = maxAttempts.coerceIn(1, 4)
        var last = AiCallResult(false, error = "未知错误", errorType = AiErrorType.UNKNOWN)
        for (attempt in 1..attemptsLimit) {
            currentCoroutineContext().ensureActive()
            last = executeOnce(config, apiKey, prompt, maxOutputTokens, route, connectTimeoutSeconds)
                .copy(attempts = attempt)
            if (last.success || !last.retryable || attempt == attemptsLimit) return@withContext last
            delay(retryDelayMillis(attempt))
        }
        last
    }

    private fun validate(
        config: AiProviderConfig,
        apiKey: String,
        route: ResolvedAiRoute,
    ): AiCallResult? {
        if (apiKey.isBlank()) return configurationError("API Key未配置")
        if (route.baseUrl.isBlank() || config.model.isBlank()) return configurationError("Base URL或模型未配置")
        val url = runCatching { URL(route.baseUrl) }.getOrNull()
            ?: return configurationError("Base URL格式无效")
        if (url.protocol != "https" || url.host.isBlank() || url.userInfo != null) {
            return configurationError("Base URL必须是无内嵌凭据的HTTPS地址")
        }
        return null
    }

    private fun configurationError(message: String) = AiCallResult(
        success = false,
        error = message,
        errorType = AiErrorType.INVALID_CONFIGURATION,
    )

    private suspend fun executeOnce(
        config: AiProviderConfig,
        apiKey: String,
        prompt: ReplyPrompt,
        maxOutputTokens: Int,
        route: ResolvedAiRoute,
        connectTimeoutSeconds: Int,
    ): AiCallResult {
        var connection: HttpURLConnection? = null
        return try {
            val endpoint = route.baseUrl.trimEnd('/') + when (config.provider.protocol) {
                AiApiProtocol.RESPONSES -> "/responses"
                AiApiProtocol.CHAT_COMPLETIONS -> "/chat/completions"
            }
            val body = when (config.provider.protocol) {
                AiApiProtocol.RESPONSES -> responsesBody(config, prompt, maxOutputTokens)
                AiApiProtocol.CHAT_COMPLETIONS -> chatCompletionsBody(config, prompt, maxOutputTokens)
            }
            connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = connectTimeoutSeconds.coerceIn(5, 60) * 1_000
                readTimeout = 60_000
                doOutput = true
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                if (config.provider == AiProvider.GEMINI) {
                    setRequestProperty("x-goog-api-client", "gailiuzi-android/0.4.4")
                }
                if (route.viaGateway) {
                    setRequestProperty("X-Gailiuzi-Provider", config.provider.routeKey)
                    setRequestProperty("X-Gailiuzi-Upstream-Base", config.baseUrl)
                    if (route.gatewayToken.isNotBlank()) {
                        setRequestProperty("X-Gailiuzi-Gateway-Authorization", "Bearer ${route.gatewayToken}")
                    }
                }
            }
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            currentCoroutineContext().ensureActive()
            val responseCode = connection.responseCode
            val responseText = (if (responseCode in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            currentCoroutineContext().ensureActive()

            if (responseCode !in 200..299) {
                httpError(responseCode, extractError(responseText), route.viaGateway)
            } else {
                val root = JSONObject(responseText)
                val text = when (config.provider.protocol) {
                    AiApiProtocol.RESPONSES -> extractResponsesText(root)
                    AiApiProtocol.CHAT_COMPLETIONS -> extractChatText(root)
                }
                if (text.isBlank()) AiCallResult(
                    false,
                    error = "API返回中没有可用文本",
                    errorType = AiErrorType.INVALID_RESPONSE,
                ) else AiCallResult(true, text = text)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (timeout: SocketTimeoutException) {
            AiCallResult(false, error = "网络请求超时", errorType = AiErrorType.TIMEOUT, retryable = true)
        } catch (network: IOException) {
            AiCallResult(
                false,
                error = network.message ?: "网络连接失败",
                errorType = AiErrorType.NETWORK,
                retryable = true,
            )
        } catch (error: Exception) {
            AiCallResult(false, error = error.message ?: error.javaClass.simpleName, errorType = AiErrorType.UNKNOWN)
        } finally {
            connection?.disconnect()
        }
    }

    internal fun httpError(status: Int, message: String, viaGateway: Boolean): AiCallResult {
        val normalized = message.lowercase()
        val type = when {
            viaGateway && status in 500..599 -> AiErrorType.GATEWAY
            status == 401 || status == 403 -> AiErrorType.AUTHENTICATION
            status == 404 -> AiErrorType.MODEL_NOT_FOUND
            status == 429 && ("quota" in normalized || "balance" in normalized || "credit" in normalized) -> AiErrorType.QUOTA_EXCEEDED
            status == 429 -> AiErrorType.RATE_LIMIT
            status == 408 -> AiErrorType.TIMEOUT
            status in 500..599 -> AiErrorType.SERVER
            "content" in normalized && ("filter" in normalized || "policy" in normalized || "safety" in normalized) -> AiErrorType.CONTENT_REJECTED
            else -> AiErrorType.UNKNOWN
        }
        val retryable = status == 408 || status == 429 || status in setOf(500, 502, 503, 504)
        return AiCallResult(
            success = false,
            error = message.ifBlank { "HTTP $status" },
            errorType = type,
            statusCode = status,
            retryable = retryable,
        )
    }

    internal fun retryDelayMillis(attempt: Int): Long =
        min(8_000L, 500L * (1L shl (attempt - 1).coerceIn(0, 4)))

    private fun responsesBody(config: AiProviderConfig, prompt: ReplyPrompt, maxOutputTokens: Int): JSONObject =
        JSONObject()
            .put("model", config.model)
            .put("input", if (config.provider == AiProvider.DOUBAO) "${prompt.instructions}\n\n${prompt.input}" else prompt.input)
            .put("max_output_tokens", maxOutputTokens)
            .put("store", false)
            .also { if (config.provider == AiProvider.OPENAI) it.put("instructions", prompt.instructions) }

    private fun chatCompletionsBody(config: AiProviderConfig, prompt: ReplyPrompt, maxOutputTokens: Int): JSONObject =
        JSONObject()
            .put("model", config.model)
            .put("max_tokens", maxOutputTokens)
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", prompt.instructions))
                    .put(JSONObject().put("role", "user").put("content", prompt.input)),
            )

    private fun extractResponsesText(root: JSONObject): String {
        root.optString("output_text").takeIf { it.isNotBlank() }?.let { return it }
        val output = root.optJSONArray("output") ?: return ""
        return buildList {
            for (index in 0 until output.length()) {
                val content = output.optJSONObject(index)?.optJSONArray("content") ?: continue
                for (contentIndex in 0 until content.length()) {
                    val block = content.optJSONObject(contentIndex) ?: continue
                    if (block.optString("type") in setOf("output_text", "text")) {
                        block.optString("text").takeIf(String::isNotBlank)?.let(::add)
                    }
                }
            }
        }.joinToString("\n").trim()
    }

    private fun extractChatText(root: JSONObject): String {
        val content = root.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.opt("content")
        return when (content) {
            is String -> content.trim()
            is JSONArray -> buildList {
                for (index in 0 until content.length()) {
                    content.optJSONObject(index)?.optString("text")?.takeIf(String::isNotBlank)?.let(::add)
                }
            }.joinToString("\n").trim()
            else -> ""
        }
    }

    private fun extractError(responseText: String): String = runCatching {
        when (val error = JSONObject(responseText).opt("error")) {
            is JSONObject -> error.optString("message")
            is String -> error
            else -> ""
        }
    }.getOrDefault("")
}
