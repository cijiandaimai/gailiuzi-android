package com.gailiuzi.app.knowledge

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.UUID

class KnowledgeBaseRepository(private val context: Context) {
    private val storageFile = File(context.filesDir, "merchant_knowledge_base.json")
    private val mutableState = MutableStateFlow(loadState())
    val state: StateFlow<KnowledgeState> = mutableState.asStateFlow()

    @Synchronized
    fun saveSettings(settings: KnowledgeSettings) {
        persist(mutableState.value.copy(settings = settings.copy(retrievalTopK = settings.retrievalTopK.coerceIn(1, 10))))
    }

    @Synchronized
    fun upsertItem(item: KnowledgeItem): KnowledgeItem {
        val normalized = item.copy(
            id = item.id.ifBlank { UUID.randomUUID().toString() },
            title = item.title.trim(),
            content = item.content.trim().take(MAX_ITEM_CHARS),
            updatedAt = System.currentTimeMillis(),
        )
        val items = mutableState.value.items.toMutableList()
        val index = items.indexOfFirst { it.id == normalized.id }
        if (index >= 0) items[index] = normalized else items.add(0, normalized)
        persist(mutableState.value.copy(items = items.take(MAX_ITEMS)))
        return normalized
    }

    @Synchronized
    fun deleteItem(id: String) {
        persist(mutableState.value.copy(items = mutableState.value.items.filterNot { it.id == id }))
    }

    @Synchronized
    fun upsertTrainingSample(sample: TrainingSample): TrainingSample {
        val normalized = sample.copy(
            id = sample.id.ifBlank { UUID.randomUUID().toString() },
            question = sample.question.trim(),
            preferredAnswer = sample.preferredAnswer.trim(),
            updatedAt = System.currentTimeMillis(),
        )
        val samples = mutableState.value.trainingSamples.toMutableList()
        val index = samples.indexOfFirst { it.id == normalized.id }
        if (index >= 0) samples[index] = normalized else samples.add(0, normalized)
        persist(mutableState.value.copy(trainingSamples = samples.take(MAX_TRAINING_SAMPLES)))
        return normalized
    }

    @Synchronized
    fun deleteTrainingSample(id: String) {
        persist(
            mutableState.value.copy(
                trainingSamples = mutableState.value.trainingSamples.filterNot { it.id == id },
            ),
        )
    }

    suspend fun importTextDocument(uri: Uri, scopeId: String?): KnowledgeItem = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }.orEmpty().ifBlank { "导入文档" }
        val bytes = resolver.openInputStream(uri)?.use { input ->
            input.readLimited(MAX_IMPORT_BYTES + 1)
        } ?: error("无法读取文件")
        require(bytes.size <= MAX_IMPORT_BYTES) { "单个文件不能超过2MB" }
        val content = bytes.toString(Charsets.UTF_8).trim()
        require(content.isNotBlank()) { "文件没有可学习的文本内容" }
        upsertItem(
            KnowledgeItem(
                id = "",
                title = name.substringBeforeLast('.').ifBlank { name },
                content = content,
                sourceType = KnowledgeSourceType.FILE,
                sourceName = name,
                scopeId = scopeId,
            ),
        )
    }

    fun getItem(id: String): KnowledgeItem? = mutableState.value.items.firstOrNull { it.id == id }

    fun markLearning(id: String) {
        updateItem(id) { it.copy(status = KnowledgeStatus.LEARNING, learningError = "") }
    }

    fun saveLearningResult(id: String, summary: String, requireReview: Boolean) {
        updateItem(id) {
            it.copy(
                aiSummary = summary.trim(),
                learningError = "",
                status = if (requireReview) KnowledgeStatus.NEEDS_REVIEW else KnowledgeStatus.READY,
            )
        }
    }

    fun approveLearning(id: String) {
        updateItem(id) { it.copy(status = KnowledgeStatus.READY) }
    }

    fun saveLearningFailure(id: String, error: String) {
        updateItem(id) { it.copy(status = KnowledgeStatus.FAILED, learningError = error.take(300)) }
    }

    fun retrieve(query: String, allowedScopeIds: Set<String>, topK: Int? = null): List<KnowledgeSnippet> {
        val current = mutableState.value
        if (!current.settings.enabled || query.isBlank()) return emptyList()
        val terms = tokenize(query)
        val limit = (topK ?: current.settings.retrievalTopK).coerceIn(1, 10)
        return current.items.asSequence()
            .filter { it.enabled && (it.scopeId == null || it.scopeId in allowedScopeIds) }
            .flatMap { item ->
                chunk(item.content).asSequence().map { chunk ->
                    val searchable = "${item.title}\n${item.aiSummary}\n$chunk".lowercase()
                    val hits = terms.sumOf { term -> if (searchable.contains(term)) 1 else 0 }
                    val titleBonus = terms.count { item.title.lowercase().contains(it) } * 3
                    KnowledgeSnippet(item.id, item.title, chunk, hits * 10 + titleBonus)
                }
            }
            .filter { it.score > 0 }
            .sortedByDescending(KnowledgeSnippet::score)
            .take(limit)
            .toList()
    }

    fun matchingTrainingSamples(
        query: String,
        allowedScopeIds: Set<String>,
        limit: Int = 2,
    ): List<TrainingSample> {
        val terms = tokenize(query)
        return mutableState.value.trainingSamples.asSequence()
            .filter { it.approved && (it.scopeId == null || it.scopeId in allowedScopeIds) }
            .map { sample -> sample to terms.count { sample.question.lowercase().contains(it) } }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
            .toList()
    }

    @Synchronized
    private fun updateItem(id: String, transform: (KnowledgeItem) -> KnowledgeItem) {
        val items = mutableState.value.items.map { item ->
            if (item.id == id) transform(item).copy(updatedAt = System.currentTimeMillis()) else item
        }
        persist(mutableState.value.copy(items = items))
    }

    @Synchronized
    private fun persist(state: KnowledgeState) {
        val json = serialize(state)
        val temporary = File(storageFile.parentFile, "${storageFile.name}.tmp")
        temporary.writeText(json, Charsets.UTF_8)
        if (storageFile.exists()) storageFile.delete()
        check(temporary.renameTo(storageFile)) { "无法保存知识库" }
        mutableState.value = state
    }

    private fun loadState(): KnowledgeState {
        if (!storageFile.exists()) return KnowledgeState(KnowledgeSettings(), emptyList(), emptyList())
        return runCatching { deserialize(storageFile.readText(Charsets.UTF_8)) }
            .getOrElse { KnowledgeState(KnowledgeSettings(), emptyList(), emptyList()) }
    }

    private fun serialize(state: KnowledgeState): String = JSONObject()
        .put(
            "settings",
            JSONObject()
                .put("enabled", state.settings.enabled)
                .put("autoLearn", state.settings.autoLearnWithAi)
                .put("requireReview", state.settings.requireReviewAfterLearning)
                .put("topK", state.settings.retrievalTopK),
        )
        .put(
            "items",
            JSONArray().also { array ->
                state.items.forEach { item ->
                    array.put(
                        JSONObject()
                            .put("id", item.id)
                            .put("title", item.title)
                            .put("content", item.content)
                            .put("sourceType", item.sourceType.name)
                            .put("sourceName", item.sourceName)
                            .put("scopeId", item.scopeId.orEmpty())
                            .put("enabled", item.enabled)
                            .put("status", item.status.name)
                            .put("aiSummary", item.aiSummary)
                            .put("learningError", item.learningError)
                            .put("updatedAt", item.updatedAt),
                    )
                }
            },
        )
        .put(
            "trainingSamples",
            JSONArray().also { array ->
                state.trainingSamples.forEach { sample ->
                    array.put(
                        JSONObject()
                            .put("id", sample.id)
                            .put("question", sample.question)
                            .put("preferredAnswer", sample.preferredAnswer)
                            .put("scopeId", sample.scopeId.orEmpty())
                            .put("approved", sample.approved)
                            .put("updatedAt", sample.updatedAt),
                    )
                }
            },
        )
        .toString()

    private fun deserialize(raw: String): KnowledgeState {
        val root = JSONObject(raw)
        val settingsJson = root.optJSONObject("settings") ?: JSONObject()
        val itemsJson = root.optJSONArray("items") ?: JSONArray()
        val samplesJson = root.optJSONArray("trainingSamples") ?: JSONArray()
        return KnowledgeState(
            settings = KnowledgeSettings(
                enabled = settingsJson.optBoolean("enabled", true),
                autoLearnWithAi = settingsJson.optBoolean("autoLearn", true),
                requireReviewAfterLearning = settingsJson.optBoolean("requireReview", true),
                retrievalTopK = settingsJson.optInt("topK", 4).coerceIn(1, 10),
            ),
            items = List(itemsJson.length()) { index ->
                val item = itemsJson.getJSONObject(index)
                KnowledgeItem(
                    id = item.getString("id"),
                    title = item.getString("title"),
                    content = item.getString("content"),
                    sourceType = runCatching { KnowledgeSourceType.valueOf(item.getString("sourceType")) }
                        .getOrDefault(KnowledgeSourceType.MANUAL),
                    sourceName = item.optString("sourceName"),
                    scopeId = item.optString("scopeId").takeIf(String::isNotBlank),
                    enabled = item.optBoolean("enabled", true),
                    status = runCatching { KnowledgeStatus.valueOf(item.optString("status")) }
                        .getOrDefault(KnowledgeStatus.INDEXED),
                    aiSummary = item.optString("aiSummary"),
                    learningError = item.optString("learningError"),
                    updatedAt = item.optLong("updatedAt", System.currentTimeMillis()),
                )
            },
            trainingSamples = List(samplesJson.length()) { index ->
                val sample = samplesJson.getJSONObject(index)
                TrainingSample(
                    id = sample.getString("id"),
                    question = sample.getString("question"),
                    preferredAnswer = sample.getString("preferredAnswer"),
                    scopeId = sample.optString("scopeId").takeIf(String::isNotBlank),
                    approved = sample.optBoolean("approved", true),
                    updatedAt = sample.optLong("updatedAt", System.currentTimeMillis()),
                )
            },
        )
    }

    companion object {
        private const val MAX_IMPORT_BYTES = 2 * 1024 * 1024
        private const val MAX_ITEM_CHARS = 200_000
        private const val MAX_ITEMS = 200
        private const val MAX_TRAINING_SAMPLES = 500

        private fun tokenize(text: String): Set<String> {
            val normalized = text.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            val words = normalized.split(' ').filter { it.length >= 2 }
            val chineseRuns = Regex("[\\u4e00-\\u9fff]{2,}").findAll(normalized).flatMap { match ->
                val value = match.value
                sequence {
                    yield(value)
                    for (index in 0 until value.length - 1) yield(value.substring(index, index + 2))
                }
            }
            return (words.asSequence() + chineseRuns).take(80).toSet()
        }

        private fun chunk(content: String, size: Int = 900, overlap: Int = 120): List<String> {
            if (content.length <= size) return listOf(content)
            val result = mutableListOf<String>()
            var start = 0
            while (start < content.length) {
                val end = (start + size).coerceAtMost(content.length)
                result += content.substring(start, end)
                if (end == content.length) break
                start = (end - overlap).coerceAtLeast(start + 1)
            }
            return result
        }

        private fun InputStream.readLimited(limit: Int): ByteArray {
            val output = ByteArrayOutputStream(limit.coerceAtMost(64 * 1024))
            val buffer = ByteArray(8 * 1024)
            var total = 0
            while (total < limit) {
                val count = read(buffer, 0, minOf(buffer.size, limit - total))
                if (count < 0) break
                output.write(buffer, 0, count)
                total += count
            }
            return output.toByteArray()
        }
    }
}
