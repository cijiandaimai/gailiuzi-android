package com.gailiuzi.app.platform.xhs

enum class XhsPageType(val displayName: String, val actionable: Boolean = false) {
    HOME("首页"),
    SEARCH("搜索页"),
    NOTE_DETAIL("笔记详情"),
    COMMENT_INPUT("评论输入页", actionable = true),
    COMMENT_HISTORY("评论记录"),
    INTERACTION_NOTIFICATIONS("互动通知"),
    PROFILE("个人主页"),
    LOGIN_OR_RESTRICTED("登录或风险提示"),
    UNKNOWN("未识别页面"),
}

enum class XhsExecutionChannel(val displayName: String) {
    OFFICIAL_API("官方 API"),
    ANDROID_ASSISTED("Android 可见辅助"),
    HUMAN_ONLY("仅人工执行"),
    UNAVAILABLE("不可用"),
}

data class XhsCapabilityProfile(
    val ecommerce: XhsExecutionChannel = XhsExecutionChannel.OFFICIAL_API,
    val communitySearch: XhsExecutionChannel = XhsExecutionChannel.ANDROID_ASSISTED,
    val communityRead: XhsExecutionChannel = XhsExecutionChannel.ANDROID_ASSISTED,
    val communityDraft: XhsExecutionChannel = XhsExecutionChannel.ANDROID_ASSISTED,
    val communityPublish: XhsExecutionChannel = XhsExecutionChannel.HUMAN_ONLY,
    val unattendedLikeFollowMessage: XhsExecutionChannel = XhsExecutionChannel.UNAVAILABLE,
)

object XhsPlatformContract {
    const val PACKAGE_NAME = "com.xingin.xhs"
    const val VALIDATED_APP_VERSION = "9.37.3"
    const val VALIDATED_VERSION_CODE = 9_373_801
    const val TARGET_SDK = 35
    const val POLICY_VERSION = "xhs-community-manual-send-v1"
    const val OPEN_PLATFORM_URL = "https://open.xiaohongshu.com/"
    const val OPEN_PLATFORM_API_DOCS = "https://open.xiaohongshu.com/document/api"

    val capabilities = XhsCapabilityProfile()
}

object XhsPageClassifier {
    fun classify(className: String?): XhsPageType {
        val name = className.orEmpty()
        if (name.isBlank()) return XhsPageType.UNKNOWN
        return when {
            name.endsWith("IndexActivityV2") -> XhsPageType.HOME
            containsAny(
                name,
                "GlobalSearchActivity",
                "GlobalSearchHalfScreenActivity",
                "SearchAgentPageActivity",
                "SearchUsersActivity",
            ) -> XhsPageType.SEARCH
            containsAny(
                name,
                "NoteDetailActivity",
                "DetailFeedActivity",
                "MediumVideoPageActivity",
                "FloatingNoteDetailActivity",
            ) -> XhsPageType.NOTE_DETAIL
            name.endsWith("NoteCommentActivity") -> XhsPageType.COMMENT_INPUT
            name.endsWith("HistoryCommentActivity") -> XhsPageType.COMMENT_HISTORY
            containsAny(
                name,
                "MsgNotificationV2Activity",
                "FoldCommentActivity",
                "PreloadNotificationListActivity",
            ) -> XhsPageType.INTERACTION_NOTIFICATIONS
            containsAny(
                name,
                "MyUserActivity",
                "NewOtherUserActivity",
                "FloatingOtherUserActivity",
            ) -> XhsPageType.PROFILE
            containsAny(name, "Login", "RestrictAccessActivity") -> XhsPageType.LOGIN_OR_RESTRICTED
            else -> XhsPageType.UNKNOWN
        }
    }

    private fun containsAny(value: String, vararg tokens: String): Boolean = tokens.any(value::contains)
}
