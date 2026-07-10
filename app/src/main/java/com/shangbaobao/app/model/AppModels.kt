package com.shangbaobao.app.model

enum class Platform(
    val displayName: String,
    val packageNames: Set<String>,
) {
    DOUYIN("抖音", setOf("com.ss.android.ugc.aweme")),
    MEITUAN("美团", setOf("com.sankuai.meituan", "com.dianping.v1")),
    XIAOHONGSHU("小红书", setOf("com.xingin.xhs")),
    ;

    companion object {
        fun fromPackageName(packageName: String?): Platform? =
            entries.firstOrNull { packageName in it.packageNames }
    }
}

enum class AutomationMode(
    val title: String,
    val subtitle: String,
    val description: String,
) {
    FRONT_DESK(
        title = "前台模式",
        subtitle = "管理自己的评论",
        description = "发现自有账号和门店的新评论，完成分类、草稿和审批。",
    ),
    SHOPPING(
        title = "逛街模式",
        subtitle = "筛选高潜客户",
        description = "先理解内容和对话，再复核入围对象公开主页，生成针对性互动建议。",
    ),
    PUBLIC_RELATIONS(
        title = "公关模式",
        subtitle = "监控品牌相关内容",
        description = "识别品牌提及和负面风险，将高风险内容升级为待办事件。",
    ),
}

enum class AutomationLevel(val title: String, val description: String) {
    MONITOR("L0 监控", "只发现和分析，不生成草稿"),
    DRAFT("L1 草稿", "生成草稿，由人工发送"),
    ASSISTED("L2 半自动", "低风险进入确认队列，中高风险升级"),
}

enum class AgentEventType {
    SYSTEM,
    PAGE_OBSERVED,
    NOTIFICATION_DISCOVERED,
    SAFETY,
}

data class AgentEvent(
    val id: Long,
    val type: AgentEventType,
    val title: String,
    val detail: String,
    val timestamp: Long,
    val platform: Platform? = null,
)

data class PermissionSnapshot(
    val accessibilityEnabled: Boolean = false,
    val notificationListenerEnabled: Boolean = false,
    val notificationPermissionGranted: Boolean = false,
)

