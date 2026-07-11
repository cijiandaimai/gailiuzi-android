package com.shangbaobao.app.business

enum class ServiceVertical(val displayName: String) {
    BEAUTY("美容/皮肤管理"),
    MASSAGE("按摩/推拿"),
    SPA("SPA/养生"),
    HAIR("美发"),
    NAILS("美甲美睫"),
    MEDICAL_BEAUTY("医疗美容"),
    FITNESS("健身/体态"),
    OTHER_SERVICE("其他服务业"),
}

enum class OrgLevel(val displayName: String) {
    HEADQUARTERS("总部"),
    REGION("区域"),
    STORE("门店"),
}

enum class BusinessRole(val displayName: String, val description: String) {
    BRAND_ADMIN("总部管理员", "全品牌配置、审计与数据汇总"),
    REGION_MANAGER("区域经理", "管理所属区域与门店任务"),
    STORE_OPERATOR("门店运营", "处理本门店评价和服务反馈"),
    COMPLIANCE_REVIEWER("合规审核员", "审核高风险回复与医疗宣称"),
    ANALYST("数据分析员", "只读查看趋势和报表"),
}

data class OrgUnit(
    val id: String,
    val name: String,
    val level: OrgLevel,
    val parentId: String? = null,
    val city: String = "",
    val storeCode: String = "",
    val onlineShopNames: List<String> = emptyList(),
    val enabled: Boolean = true,
)

data class ComplianceSettings(
    val medicalClaimsGuard: Boolean = true,
    val priceAccuracyGuard: Boolean = true,
    val personalDataGuard: Boolean = true,
    val negativeReviewEscalation: Boolean = true,
    val requireApprovalForRisk: Boolean = true,
    val customForbiddenTerms: List<String> = emptyList(),
)

data class BusinessProfile(
    val merchantName: String = "我的服务品牌",
    val vertical: ServiceVertical = ServiceVertical.BEAUTY,
    val currentRole: BusinessRole = BusinessRole.BRAND_ADMIN,
)

data class ReputationSnapshot(
    val onlineReviews: Int = 0,
    val offlineFeedback: Int = 0,
    val pendingReplies: Int = 0,
    val riskCases: Int = 0,
    val averageRating: Double? = null,
    val responseRate: Double? = null,
)

enum class FeedbackSource(val displayName: String, val online: Boolean) {
    DOUYIN("抖音", true),
    MEITUAN("美团/点评", true),
    XIAOHONGSHU("小红书", true),
    ONLINE_STORE("线上商品", true),
    OFFLINE_SURVEY("门店回访", false),
    CRM("CRM/客服", false),
    MANUAL("人工记录", false),
}

enum class FeedbackSentiment(val displayName: String) {
    POSITIVE("正向"),
    NEUTRAL("中性"),
    NEGATIVE("负向"),
}

enum class FeedbackStatus(val displayName: String) {
    NEW("待处理"),
    ASSIGNED("跟进中"),
    RESOLVED("已闭环"),
}

enum class ServiceIssueCategory(val displayName: String) {
    SERVICE_ATTITUDE("服务态度"),
    WAITING_TIME("等待时长"),
    PRICE("价格/套餐"),
    RESULT_EXPECTATION("效果预期"),
    ENVIRONMENT("环境卫生"),
    APPOINTMENT("预约核销"),
    PRODUCT("产品使用"),
    PRAISE("表扬推荐"),
    OTHER("其他"),
}

data class ReputationRecord(
    val id: String,
    val source: FeedbackSource,
    val orgUnitId: String,
    val content: String,
    val authorAlias: String = "匿名顾客",
    val rating: Int? = null,
    val sentiment: FeedbackSentiment = FeedbackSentiment.NEUTRAL,
    val category: ServiceIssueCategory = ServiceIssueCategory.OTHER,
    val status: FeedbackStatus = FeedbackStatus.NEW,
    val createdAt: Long = System.currentTimeMillis(),
)

data class BusinessState(
    val profile: BusinessProfile,
    val units: List<OrgUnit>,
    val currentScopeId: String,
    val compliance: ComplianceSettings,
    val snapshot: ReputationSnapshot = ReputationSnapshot(),
    val reputationRecords: List<ReputationRecord> = emptyList(),
) {
    val currentScope: OrgUnit?
        get() = units.firstOrNull { it.id == currentScopeId } ?: units.firstOrNull()
}
