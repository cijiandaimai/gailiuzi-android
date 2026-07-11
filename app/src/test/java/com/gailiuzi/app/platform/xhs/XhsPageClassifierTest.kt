package com.gailiuzi.app.platform.xhs

import org.junit.Assert.assertEquals
import org.junit.Test

class XhsPageClassifierTest {
    @Test
    fun `recognizes note detail variants`() {
        assertEquals(
            XhsPageType.NOTE_DETAIL,
            XhsPageClassifier.classify("com.xingin.matrix.notedetail.NoteDetailActivity"),
        )
        assertEquals(
            XhsPageType.NOTE_DETAIL,
            XhsPageClassifier.classify("com.xingin.matrix.detail.activity.DetailFeedActivity"),
        )
    }

    @Test
    fun `recognizes comment and interaction pages`() {
        assertEquals(
            XhsPageType.COMMENT_INPUT,
            XhsPageClassifier.classify("com.xingin.comment.input.ui.NoteCommentActivity"),
        )
        assertEquals(
            XhsPageType.INTERACTION_NOTIFICATIONS,
            XhsPageClassifier.classify("com.xingin.im.ui.message.notificationV2.MsgNotificationV2Activity"),
        )
    }

    @Test
    fun `recognizes search and profile variants`() {
        assertEquals(
            XhsPageType.SEARCH,
            XhsPageClassifier.classify("com.xingin.alioth.search.GlobalSearchActivity"),
        )
        assertEquals(
            XhsPageType.PROFILE,
            XhsPageClassifier.classify("com.xingin.matrix.v2.profile.newpage.NewOtherUserActivity"),
        )
    }

    @Test
    fun `unknown widgets do not create false page classification`() {
        assertEquals(XhsPageType.UNKNOWN, XhsPageClassifier.classify("androidx.recyclerview.widget.RecyclerView"))
    }
}
