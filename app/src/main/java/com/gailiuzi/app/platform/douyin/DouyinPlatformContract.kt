package com.gailiuzi.app.platform.douyin

enum class DouyinPageType(val displayName: String) {
    HOME("首页"),
    SEARCH("搜索页"),
    VIDEO_DETAIL("视频详情"),
    COMMENT_MANAGER("评论管理"),
    COMMENT_DETAIL("评论详情"),
    INTERACTION_NOTIFICATIONS("互动通知"),
    PROFILE("用户主页"),
    OPEN_PLATFORM_AUTH("开放平台授权"),
    LOGIN_OR_RISK("登录、验证或风险提示"),
    UNKNOWN("未识别页面"),
}

enum class DouyinExecutionChannel(val displayName: String) {
    OFFICIAL_API("官方 API"),
    ANDROID_ASSISTED("Android 可见辅助"),
    HUMAN_APPROVAL("人工审批"),
}

data class DouyinCapabilityProfile(
    val ownedVideoComments: DouyinExecutionChannel = DouyinExecutionChannel.OFFICIAL_API,
    val approvedKeywordSearch: DouyinExecutionChannel = DouyinExecutionChannel.OFFICIAL_API,
    val uiFallbackRead: DouyinExecutionChannel = DouyinExecutionChannel.ANDROID_ASSISTED,
    val uiFallbackWrite: DouyinExecutionChannel = DouyinExecutionChannel.HUMAN_APPROVAL,
)

object DouyinPlatformContract {
    const val PACKAGE_NAME = "com.ss.android.ugc.aweme"
    const val VALIDATED_APP_VERSION = "39.5.0"
    const val VALIDATED_VERSION_CODE = 390_501
    const val TARGET_SDK = 34
    const val POLICY_VERSION = "douyin-api-first-assisted-v1"
    const val OPEN_PLATFORM_URL = "https://open.douyin.com/"
    const val REQUIRED_SCOPES = "video.list,video.data,video.comment,video.search,video.search.comment"

    val capabilities = DouyinCapabilityProfile()
}

object DouyinPageClassifier {
    fun classify(className: String?): DouyinPageType {
        val name = className.orEmpty()
        if (name.isBlank()) return DouyinPageType.UNKNOWN
        return when {
            name.endsWith("com.ss.android.ugc.aweme.main.MainActivity") -> DouyinPageType.HOME
            containsAny(
                name,
                "SearchResultActivity",
                "SearchPartShowResultActivity",
                "SearchSynthesisBulletActivity",
                "HotSearchAndDiscoveryActivity",
                "DiscussSearchLandingActivity",
            ) -> DouyinPageType.SEARCH
            containsAny(
                name,
                "detail.ui.DetailActivity",
                "SingleTaskDetailActivity",
                "UltraDetailActivity",
                "VideoPlayActivity",
                "NearbyUgcDetailActivity",
            ) -> DouyinPageType.VIDEO_DETAIL
            containsAny(name, "CommentManagerActivity", "CmtReplyDetailActivity") ->
                DouyinPageType.COMMENT_MANAGER
            containsAny(
                name,
                "CommentFeedActivity",
                "AdCommentDetailActivity",
                "CommentMergeInfoActivity",
                "NotificationDetailActivity",
            ) -> DouyinPageType.COMMENT_DETAIL
            containsAny(name, "LikeUserListActivity", "FansDetailActivity") ->
                DouyinPageType.INTERACTION_NOTIFICATIONS
            containsAny(name, "UserProfileActivity", "ProfileIndividualCollectionActivity") ->
                DouyinPageType.PROFILE
            containsAny(
                name,
                "SchemaAuthorizedActivity",
                "AwemeAuthorizedActivity",
                "AwemeAuthSilentActivity",
            ) -> DouyinPageType.OPEN_PLATFORM_AUTH
            containsAny(
                name,
                "DYLoginActivity",
                "LoginDeeplinkActivity",
                "VerifyActivity",
                "DyVerifyActivity",
                "PushVerifyActivity",
                "TwoStepAuthActivity",
                "BannedDialogActivity",
                "FlowUserBanAppearActivity",
            ) -> DouyinPageType.LOGIN_OR_RISK
            else -> DouyinPageType.UNKNOWN
        }
    }

    private fun containsAny(value: String, vararg tokens: String): Boolean = tokens.any(value::contains)
}
