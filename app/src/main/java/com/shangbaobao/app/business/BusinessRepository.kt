package com.shangbaobao.app.business

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class BusinessRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val mutableState = MutableStateFlow(loadState())
    val state: StateFlow<BusinessState> = mutableState.asStateFlow()

    fun saveProfile(profile: BusinessProfile) {
        preferences.edit {
            putString(KEY_MERCHANT_NAME, profile.merchantName.trim())
            putString(KEY_VERTICAL, profile.vertical.name)
            putString(KEY_ROLE, profile.currentRole.name)
        }
        reload()
    }

    fun setCurrentScope(id: String) {
        if (mutableState.value.units.none { it.id == id && it.enabled }) return
        preferences.edit { putString(KEY_CURRENT_SCOPE, id) }
        reload()
    }

    fun saveCompliance(settings: ComplianceSettings) {
        preferences.edit {
            putBoolean(KEY_MEDICAL_GUARD, settings.medicalClaimsGuard)
            putBoolean(KEY_PRICE_GUARD, settings.priceAccuracyGuard)
            putBoolean(KEY_PERSONAL_DATA_GUARD, settings.personalDataGuard)
            putBoolean(KEY_NEGATIVE_ESCALATION, settings.negativeReviewEscalation)
            putBoolean(KEY_REQUIRE_APPROVAL, settings.requireApprovalForRisk)
            putString(KEY_CUSTOM_FORBIDDEN, JSONArray(settings.customForbiddenTerms).toString())
        }
        reload()
    }

    fun upsertUnit(unit: OrgUnit) {
        val units = mutableState.value.units.toMutableList()
        val normalized = if (unit.id.isBlank()) unit.copy(id = UUID.randomUUID().toString()) else unit
        val index = units.indexOfFirst { it.id == normalized.id }
        if (index >= 0) units[index] = normalized else units += normalized
        saveUnits(units)
    }

    fun deleteUnit(id: String) {
        val state = mutableState.value
        val unit = state.units.firstOrNull { it.id == id } ?: return
        if (unit.level == OrgLevel.HEADQUARTERS) return
        val descendants = mutableSetOf(id)
        do {
            val before = descendants.size
            state.units.filter { it.parentId in descendants }.forEach { descendants += it.id }
        } while (descendants.size > before)
        saveUnits(state.units.filterNot { it.id in descendants })
    }

    fun upsertReputationRecord(record: ReputationRecord) {
        val records = mutableState.value.reputationRecords.toMutableList()
        val normalized = record.copy(
            id = record.id.ifBlank { UUID.randomUUID().toString() },
            content = record.content.trim().take(4_000),
            authorAlias = record.authorAlias.trim().ifBlank { "匿名顾客" },
        )
        val index = records.indexOfFirst { it.id == normalized.id }
        if (index >= 0) records[index] = normalized else records.add(0, normalized)
        saveReputationRecords(records.take(1_000))
    }

    fun updateReputationStatus(id: String, status: FeedbackStatus) {
        saveReputationRecords(
            mutableState.value.reputationRecords.map { record ->
                if (record.id == id) record.copy(status = status) else record
            },
        )
    }

    fun deleteReputationRecord(id: String) {
        saveReputationRecords(mutableState.value.reputationRecords.filterNot { it.id == id })
    }

    private fun reload() {
        mutableState.value = loadState()
    }

    private fun loadState(): BusinessState {
        val units = loadUnits()
        val currentScope = preferences.getString(KEY_CURRENT_SCOPE, null)
            ?.takeIf { saved -> units.any { it.id == saved } }
            ?: units.first().id
        val records = loadReputationRecords()
        val ratings = records.mapNotNull(ReputationRecord::rating)
        return BusinessState(
            profile = BusinessProfile(
                merchantName = preferences.getString(KEY_MERCHANT_NAME, "我的服务品牌")
                    .orEmpty().ifBlank { "我的服务品牌" },
                vertical = preferences.getString(KEY_VERTICAL, ServiceVertical.BEAUTY.name)
                    ?.let { saved -> ServiceVertical.entries.firstOrNull { it.name == saved } }
                    ?: ServiceVertical.BEAUTY,
                currentRole = preferences.getString(KEY_ROLE, BusinessRole.BRAND_ADMIN.name)
                    ?.let { saved -> BusinessRole.entries.firstOrNull { it.name == saved } }
                    ?: BusinessRole.BRAND_ADMIN,
            ),
            units = units,
            currentScopeId = currentScope,
            compliance = ComplianceSettings(
                medicalClaimsGuard = preferences.getBoolean(KEY_MEDICAL_GUARD, true),
                priceAccuracyGuard = preferences.getBoolean(KEY_PRICE_GUARD, true),
                personalDataGuard = preferences.getBoolean(KEY_PERSONAL_DATA_GUARD, true),
                negativeReviewEscalation = preferences.getBoolean(KEY_NEGATIVE_ESCALATION, true),
                requireApprovalForRisk = preferences.getBoolean(KEY_REQUIRE_APPROVAL, true),
                customForbiddenTerms = loadStringArray(KEY_CUSTOM_FORBIDDEN),
            ),
            snapshot = ReputationSnapshot(
                onlineReviews = records.count { it.source.online },
                offlineFeedback = records.count { !it.source.online },
                pendingReplies = records.count { it.status != FeedbackStatus.RESOLVED },
                riskCases = records.count {
                    it.sentiment == FeedbackSentiment.NEGATIVE && it.status != FeedbackStatus.RESOLVED
                },
                averageRating = if (ratings.isNotEmpty()) ratings.average() else null,
                responseRate = if (records.isNotEmpty()) {
                    records.count { it.status == FeedbackStatus.RESOLVED }.toDouble() / records.size
                } else {
                    null
                },
            ),
            reputationRecords = records,
        )
    }

    private fun loadUnits(): List<OrgUnit> {
        val raw = preferences.getString(KEY_UNITS, null) ?: return defaultUnits()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        OrgUnit(
                            id = item.getString("id"),
                            name = item.getString("name"),
                            level = OrgLevel.valueOf(item.getString("level")),
                            parentId = item.optString("parentId").takeIf(String::isNotBlank),
                            city = item.optString("city"),
                            storeCode = item.optString("storeCode"),
                            onlineShopNames = item.optJSONArray("onlineShops")?.let { shops ->
                                List(shops.length()) { shops.getString(it) }
                            }.orEmpty(),
                            enabled = item.optBoolean("enabled", true),
                        ),
                    )
                }
            }.ifEmpty(::defaultUnits)
        }.getOrElse { defaultUnits() }
    }

    private fun saveUnits(units: List<OrgUnit>) {
        val array = JSONArray()
        units.forEach { unit ->
            array.put(
                JSONObject()
                    .put("id", unit.id)
                    .put("name", unit.name)
                    .put("level", unit.level.name)
                    .put("parentId", unit.parentId.orEmpty())
                    .put("city", unit.city)
                    .put("storeCode", unit.storeCode)
                    .put("onlineShops", JSONArray(unit.onlineShopNames))
                    .put("enabled", unit.enabled),
            )
        }
        preferences.edit { putString(KEY_UNITS, array.toString()) }
        reload()
    }

    private fun loadReputationRecords(): List<ReputationRecord> {
        val raw = preferences.getString(KEY_REPUTATION_RECORDS, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                ReputationRecord(
                    id = item.getString("id"),
                    source = runCatching { FeedbackSource.valueOf(item.getString("source")) }
                        .getOrDefault(FeedbackSource.MANUAL),
                    orgUnitId = item.optString("orgUnitId", "hq"),
                    content = item.getString("content"),
                    authorAlias = item.optString("authorAlias", "匿名顾客"),
                    rating = item.optInt("rating", 0).takeIf { it in 1..5 },
                    sentiment = runCatching { FeedbackSentiment.valueOf(item.optString("sentiment")) }
                        .getOrDefault(FeedbackSentiment.NEUTRAL),
                    category = runCatching { ServiceIssueCategory.valueOf(item.optString("category")) }
                        .getOrDefault(ServiceIssueCategory.OTHER),
                    status = runCatching { FeedbackStatus.valueOf(item.optString("status")) }
                        .getOrDefault(FeedbackStatus.NEW),
                    createdAt = item.optLong("createdAt", System.currentTimeMillis()),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun saveReputationRecords(records: List<ReputationRecord>) {
        val array = JSONArray()
        records.forEach { record ->
            array.put(
                JSONObject()
                    .put("id", record.id)
                    .put("source", record.source.name)
                    .put("orgUnitId", record.orgUnitId)
                    .put("content", record.content)
                    .put("authorAlias", record.authorAlias)
                    .put("rating", record.rating ?: 0)
                    .put("sentiment", record.sentiment.name)
                    .put("category", record.category.name)
                    .put("status", record.status.name)
                    .put("createdAt", record.createdAt),
            )
        }
        preferences.edit { putString(KEY_REPUTATION_RECORDS, array.toString()) }
        reload()
    }

    private fun loadStringArray(key: String): List<String> = runCatching {
        val array = JSONArray(preferences.getString(key, "[]"))
        List(array.length()) { array.getString(it) }
    }.getOrDefault(emptyList())

    companion object {
        private const val PREFERENCES_NAME = "business_settings"
        private const val KEY_MERCHANT_NAME = "merchant_name"
        private const val KEY_VERTICAL = "vertical"
        private const val KEY_ROLE = "role"
        private const val KEY_CURRENT_SCOPE = "current_scope"
        private const val KEY_UNITS = "units"
        private const val KEY_MEDICAL_GUARD = "medical_guard"
        private const val KEY_PRICE_GUARD = "price_guard"
        private const val KEY_PERSONAL_DATA_GUARD = "personal_data_guard"
        private const val KEY_NEGATIVE_ESCALATION = "negative_escalation"
        private const val KEY_REQUIRE_APPROVAL = "require_approval"
        private const val KEY_CUSTOM_FORBIDDEN = "custom_forbidden"
        private const val KEY_REPUTATION_RECORDS = "reputation_records"

        private fun defaultUnits(): List<OrgUnit> = listOf(
            OrgUnit("hq", "品牌总部", OrgLevel.HEADQUARTERS),
            OrgUnit("region-east", "示范区域", OrgLevel.REGION, parentId = "hq"),
            OrgUnit(
                id = "store-demo",
                name = "示范门店",
                level = OrgLevel.STORE,
                parentId = "region-east",
                storeCode = "DEMO-001",
            ),
        )
    }
}
