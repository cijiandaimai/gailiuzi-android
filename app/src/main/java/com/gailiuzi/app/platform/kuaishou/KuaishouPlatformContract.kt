package com.gailiuzi.app.platform.kuaishou

enum class KuaishouPageType(val displayName: String) {
    HOME("首页"),
    SEARCH("搜索页"),
    PHOTO_DETAIL("作品详情"),
    COMMENT_CONTEXT("评论上下文"),
    INTERACTION_NOTIFICATIONS("互动通知"),
    PROFILE("用户主页"),
    OPEN_PLATFORM_AUTH("开放平台授权"),
    LOGIN_OR_RISK("登录、验证或风险提示"),
    UNKNOWN("未识别页面"),
}

enum class KuaishouExecutionChannel(val displayName: String) {
    OFFICIAL_API("官方 API"),
    ANDROID_ASSISTED("Android 可见辅助"),
    HUMAN_ONLY("仅人工执行"),
    UNAVAILABLE("不可用"),
}

data class KuaishouCapabilityProfile(
    val authorizedUserInfo: KuaishouExecutionChannel = KuaishouExecutionChannel.OFFICIAL_API,
    val ownedVideoInfo: KuaishouExecutionChannel = KuaishouExecutionChannel.OFFICIAL_API,
    val communitySearch: KuaishouExecutionChannel = KuaishouExecutionChannel.ANDROID_ASSISTED,
    val communityCommentDraft: KuaishouExecutionChannel = KuaishouExecutionChannel.ANDROID_ASSISTED,
    val communityCommentPublish: KuaishouExecutionChannel = KuaishouExecutionChannel.HUMAN_ONLY,
    val unattendedLikeFollowMessage: KuaishouExecutionChannel = KuaishouExecutionChannel.UNAVAILABLE,
)

object KuaishouPlatformContract {
    const val PACKAGE_NAME = "com.smile.gifmaker"
    const val VALIDATED_APP_VERSION = "14.6.10.49019"
    const val VALIDATED_VERSION_CODE = 49_019
    const val MIN_SDK = 21
    const val TARGET_SDK = 30
    const val POLICY_VERSION = "kuaishou-community-manual-send-v1"
    const val OPEN_PLATFORM_URL = "https://open.kuaishou.com/"
    const val OPEN_PLATFORM_API_DOCS = "https://open.kuaishou.com/platform/openApi?menu=12"
    const val RECOMMENDED_SCOPES = "user_info,user_video_info"

    val capabilities = KuaishouCapabilityProfile()
}

object KuaishouPageClassifier {
    fun classify(className: String?): KuaishouPageType {
        val name = className.orEmpty()
        if (name.isBlank()) return KuaishouPageType.UNKNOWN
        return when {
            name == "com.yxcorp.gifshow.HomeActivity" -> KuaishouPageType.HOME
            containsAny(
                name,
                "com.yxcorp.plugin.search.SearchActivity",
                "SearchStubActivity_01",
                "SearchTransparentActivity",
                "SearchGroupResultActivity",
                "SearchHalfVerticalSceneActivity",
                "SearchVerticalSceneActivity",
                "SearchUserListAcitivity",
                "ExploreSearchActivity",
            ) -> KuaishouPageType.SEARCH
            containsAny(
                name,
                "com.yxcorp.gifshow.detail.PhotoDetailActivity",
                "PhotoDetailListenVideoActivity",
                "RtcCallPhotoDetailActivity",
                "com.yxcorp.plugin.tag.topic.TopicDetailActivity",
            ) -> KuaishouPageType.PHOTO_DETAIL
            containsAny(
                name,
                "CommentAlbumActivity",
                "comment.image.ImageDetail",
                "comment.emotion.detail.EmotionDetailActivity",
            ) -> KuaishouPageType.COMMENT_CONTEXT
            containsAny(
                name,
                "NewFansActivity",
                "NoticeBoxDetailActivity",
                "GoNotificationPassThroughActivity",
            ) -> KuaishouPageType.INTERACTION_NOTIFICATIONS
            containsAny(
                name,
                "profile.activity.MyProfileActivity",
                "profile.activity.UserProfileActivity",
                "ProfilePreviewScreenActivity",
            ) -> KuaishouPageType.PROFILE
            containsAny(
                name,
                "kwailogin.applogin.RouteHandlerActivity",
                "kwailogin.h5login.KwaiH5LoginActivity",
                "login.authorization.AuthActivity",
            ) -> KuaishouPageType.OPEN_PLATFORM_AUTH
            containsAny(
                name,
                "com.yxcorp.login.userlogin.LoginActivity",
                "userlogin.activity.LoginActivity",
                "FullScreenLoginActivity",
                "PhoneLoginActivity",
                "QRCodeLoginActivity",
                "VerifyPhoneActivity",
                "VerifyPhoneV2Activity",
                "UserBannedActivity",
                "AccountRiskCheckActivity",
                "ChildVerifyActivity",
            ) -> KuaishouPageType.LOGIN_OR_RISK
            else -> KuaishouPageType.UNKNOWN
        }
    }

    private fun containsAny(value: String, vararg tokens: String): Boolean = tokens.any(value::contains)
}
