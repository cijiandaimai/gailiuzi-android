package com.shangbaobao.app.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class AiGateway {
    suspend fun testConnection(
        config: AiProviderConfig,
        apiKey: String,
        route: ResolvedAiRoute = ResolvedAiRoute(config.baseUrl, false),
        connectTimeoutSeconds: Int = 20,
    ): AiCallResult = generate(
        config = config,
        apiKey = apiKey,
        prompt = ReplyPrompt(
            instructions = "这是API连通性测试。",
            input = "只回复：连接成功",
        ),
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
    ): AiCallResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext AiCallResult(false, error = "API Key未配置")
        if (route.baseUrl.isBlank() || config.model.isBlank()) {
            return@withContext AiCallResult(false, error = "Base URL或模型未配置")
        }

        runCatching {
            val endpoint = route.baseUrl.trimEnd('/') + when (config.provider.protocol) {
                AiApiProtocol.RESPONSES -> "/responses"
                AiApiProtocol.CHAT_COMPLETIONS -> "/chat/completions"
            }
            val body = when (config.provider.protocol) {
                AiApiProtocol.RESPONSES -> responsesBody(config, prompt, maxOutputTokens)
                AiApiProtocol.CHAT_COMPLETIONS -> chatCompletionsBody(config, prompt, maxOutputTokens)
            }
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = connectTimeoutSeconds.coerceIn(5, 60) * 1_000
                readTimeout = 60_000
                doOutput = true
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                if (config.provider == AiProvider.GEMINI) {
                    setRequestProperty("x-goog-api-client", "gailiuzi-android/0.3.0")
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
            val responseCode = connection.responseCode
            val responseText = (if (responseCode in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            connection.disconnect()

            if (responseCode !in 200..299) {
                AiCallResult(false, error = extractError(responseText).ifBlank { "HTTP $responseCode" })
            } else {
                val root = JSONObject(responseText)
                val text = when (config.provider.protocol) {
                    AiApiProtocol.RESPONSES -> extractResponsesText(root)
                    AiApiProtocol.CHAT_COMPLETIONS -> extractChatText(root)
                }
                if (text.isBlank()) AiCallResult(false, error = "API返回中没有可用文本")
                else AiCallResult(true, text = text)
            }
        }.getOrElse { error ->
            AiCallResult(false, error = error.message ?: error.javaClass.simpleName)
        }
    }

    private fun responsesBody(
        config: AiProviderConfig,
        prompt: ReplyPrompt,
        maxOutputTokens: Int,
    ): JSONObject = JSONObject()
        .put("model", config.model)
        .put(
            "input",
            if (config.provider == AiProvider.DOUBAO) {
                "${prompt.instructions}\n\n${prompt.input}"
            } else {
                prompt.input
            },
        )
        .put("max_output_tokens", maxOutputTokens)
        .put("store", false)
        .also { body ->
            if (config.provider == AiProvider.OPENAI) body.put("instructions", prompt.instructions)
        }

    private fun chatCompletionsBody(
        config: AiProviderConfig,
        prompt: ReplyPrompt,
        maxOutputTokens: Int,
    ): JSONObject = JSONObject()
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
        val parts = mutableListOf<String>()
        for (index in 0 until output.length()) {
            val content = output.optJSONObject(index)?.optJSONArray("content") ?: continue
            for (contentIndex in 0 until content.length()) {
                val block = content.optJSONObject(contentIndex) ?: continue
                if (block.optString("type") in setOf("output_text", "text")) {
                    block.optString("text").takeIf { it.isNotBlank() }?.let(parts::add)
                }
            }
        }
        return parts.joinToString("\n").trim()
    }

    private fun extractChatText(root: JSONObject): String {
        val content = root.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.opt("content")
        return when (content) {
            is String -> content.trim()
            is JSONArray -> buildList {
                for (index in 0 until content.length()) {
                    content.optJSONObject(index)?.optString("text")
                        ?.takeIf(String::isNotBlank)?.let(::add)
                }
            }.joinToString("\n").trim()
            else -> ""
        }
    }

    private fun extractError(responseText: String): String = runCatching {
        val error = JSONObject(responseText).opt("error")
        when (error) {
            is JSONObject -> error.optString("message")
            is String -> error
            else -> ""
        }
    }.getOrDefault("")
}
