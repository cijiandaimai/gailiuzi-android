package com.gailiuzi.app.platform.douyin

import org.junit.Assert.assertEquals
import org.junit.Test

class DouyinPageClassifierTest {
    @Test
    fun `recognizes search and video detail pages`() {
        assertEquals(
            DouyinPageType.SEARCH,
            DouyinPageClassifier.classify("com.ss.android.ugc.aweme.search.activity.SearchResultActivity"),
        )
        assertEquals(
            DouyinPageType.VIDEO_DETAIL,
            DouyinPageClassifier.classify("com.ss.android.ugc.aweme.detail.ui.DetailActivity"),
        )
    }

    @Test
    fun `recognizes comment management pages`() {
        assertEquals(
            DouyinPageType.COMMENT_MANAGER,
            DouyinPageClassifier.classify("com.ss.android.ugc.aweme.cmtmanager.CommentManagerActivity"),
        )
        assertEquals(
            DouyinPageType.COMMENT_DETAIL,
            DouyinPageClassifier.classify("com.ss.android.ugc.aweme.commentfeed.CommentFeedActivity"),
        )
    }

    @Test
    fun `recognizes profile authorization and risk pages`() {
        assertEquals(
            DouyinPageType.PROFILE,
            DouyinPageClassifier.classify("com.ss.android.ugc.aweme.profile.ui.UserProfileActivity"),
        )
        assertEquals(
            DouyinPageType.OPEN_PLATFORM_AUTH,
            DouyinPageClassifier.classify("com.ss.android.ugc.aweme.openplatform.auth.activity.SchemaAuthorizedActivity"),
        )
        assertEquals(
            DouyinPageType.LOGIN_OR_RISK,
            DouyinPageClassifier.classify("com.ss.android.ugc.aweme.account.business.verify.DyVerifyActivity"),
        )
    }

    @Test
    fun `generic widgets remain unknown`() {
        assertEquals(DouyinPageType.UNKNOWN, DouyinPageClassifier.classify("android.widget.FrameLayout"))
    }
}
