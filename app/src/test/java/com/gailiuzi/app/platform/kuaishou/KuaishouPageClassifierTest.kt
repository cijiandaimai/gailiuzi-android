package com.gailiuzi.app.platform.kuaishou

import org.junit.Assert.assertEquals
import org.junit.Test

class KuaishouPageClassifierTest {
    @Test
    fun `recognizes home search and photo detail pages`() {
        assertEquals(
            KuaishouPageType.HOME,
            KuaishouPageClassifier.classify("com.yxcorp.gifshow.HomeActivity"),
        )
        assertEquals(
            KuaishouPageType.SEARCH,
            KuaishouPageClassifier.classify("com.yxcorp.plugin.search.SearchActivity"),
        )
        assertEquals(
            KuaishouPageType.PHOTO_DETAIL,
            KuaishouPageClassifier.classify("com.yxcorp.gifshow.detail.PhotoDetailActivity"),
        )
    }

    @Test
    fun `recognizes comment notification and profile pages`() {
        assertEquals(
            KuaishouPageType.COMMENT_CONTEXT,
            KuaishouPageClassifier.classify("com.yxcorp.gifshow.comment.common.album.CommentAlbumActivity"),
        )
        assertEquals(
            KuaishouPageType.INTERACTION_NOTIFICATIONS,
            KuaishouPageClassifier.classify("com.yxcorp.gifshow.relation.user.activity.NewFansActivity"),
        )
        assertEquals(
            KuaishouPageType.PROFILE,
            KuaishouPageClassifier.classify("com.yxcorp.gifshow.profile.activity.UserProfileActivity"),
        )
    }

    @Test
    fun `recognizes open platform authorization and risk pages`() {
        assertEquals(
            KuaishouPageType.OPEN_PLATFORM_AUTH,
            KuaishouPageClassifier.classify("com.kwai.auth.login.kwailogin.applogin.RouteHandlerActivity"),
        )
        assertEquals(
            KuaishouPageType.LOGIN_OR_RISK,
            KuaishouPageClassifier.classify("com.yxcorp.login.authorization.UserBannedActivity"),
        )
        assertEquals(
            KuaishouPageType.LOGIN_OR_RISK,
            KuaishouPageClassifier.classify("com.yxcorp.login.bind.AccountRiskCheckActivity"),
        )
    }

    @Test
    fun `unrelated merchant search and generic widgets remain unknown`() {
        assertEquals(
            KuaishouPageType.UNKNOWN,
            KuaishouPageClassifier.classify("com.kuaishou.merchant.shop.MerchantSellerSearchActivity"),
        )
        assertEquals(KuaishouPageType.UNKNOWN, KuaishouPageClassifier.classify("android.widget.FrameLayout"))
    }
}
