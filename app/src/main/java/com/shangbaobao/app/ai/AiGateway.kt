package com.shangbaobao.app.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class AiGateway {
    suspend fun testConnection(config: AiProviderConfig, apiKey: String): AiCallResult =
        generate(
            config = config,
            apiKey = apiKey,
            prompt = ReplyPrompt(
                instructions = "这是API连通性测试。",
                input = "只回复：连接成功",
            ),
            maxOutputTokens = 32,
        )

    suspend fun generate(
        config: AiProviderConfig,
        apiKey: String,
        prompt: ReplyPrompt,
        maxOutputTokens: Int = 500,
    ): AiCallResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext AiCallResult(false, error = "API Key未配置")
        if (config.baseUrl.isBlank() || config.model.isBlank()) {
            return@withContext AiCallResult(false, error = "Base URL或模型未配置")
        }

        runCatching {
            val endpoint = config.baseUrl.trimEnd('/') + "/responses"
            val body = JSONObject()
                .put("model", config.model)
                .put("input", if (config.provider == AiProvider.DOUBAO) {
                    "${prompt.instructions}\n\n${prompt.input}"
                } else {
                    prompt.input
                })
                .put("max_output_tokens", maxOutputTokens)
            if (config.provider == AiProvider.OPENAI) body.put("instructions", prompt.instructions)

            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 20_000
                readTimeout = 45_000
                doOutput = true
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val responseCode = connection.responseCode
            val responseText = (if (responseCode in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            connection.disconnect()

            if (responseCode !in 200..299) {
                val message = runCatching {
                    JSONObject(responseText).optJSONObject("error")?.optString("message")
                }.getOrNull().orEmpty().ifBlank { "HTTP $responseCode" }
                AiCallResult(false, error = message)
            } else {
                val text = extractOutputText(JSONObject(responseText))
                if (text.isBlank()) AiCallResult(false, error = "API返回中没有可用文本")
                else AiCallResult(true, text = text)
            }
        }.getOrElse { error ->
            AiCallResult(false, error = error.message ?: error.javaClass.simpleName)
        }
    }

    private fun extractOutputText(root: JSONObject): String {
        root.optString("output_text").takeIf { it.isNotBlank() }?.let { return it }
        val output = root.optJSONArray("output") ?: return ""
        val parts = mutableListOf<String>()
        for (index in 0 until output.length()) {
            val item = output.optJSONObject(index) ?: continue
            val content = item.optJSONArray("content") ?: continue
            for (contentIndex in 0 until content.length()) {
                val block = content.optJSONObject(contentIndex) ?: continue
                if (block.optString("type") == "output_text") {
                    block.optString("text").takeIf { it.isNotBlank() }?.let(parts::add)
                }
            }
        }
        return parts.joinToString("\n").trim()
    }
}
