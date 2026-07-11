package com.gailiuzi.app.agent

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.gailiuzi.app.model.AgentEventType
import com.gailiuzi.app.model.Platform
import com.gailiuzi.app.platform.xhs.XhsPageClassifier
import com.gailiuzi.app.platform.xhs.XhsPageType

class GailiuziAccessibilityService : AccessibilityService() {
    private var lastRecordedAt = 0L
    private var lastPageKey: String? = null
    private var currentXhsPage: XhsPageType = XhsPageType.UNKNOWN

    override fun onServiceConnected() {
        AgentEventStore.add(
            AgentEventType.SYSTEM,
            title = "操作辅助已授权",
            detail = "已开始识别受支持平台的可见页面。",
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val packageName = event.packageName?.toString() ?: return
        val platform = Platform.fromPackageName(packageName) ?: return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) return

        val fullClassName = event.className?.toString().orEmpty()
        val rawPageName = fullClassName.substringAfterLast('.').ifBlank { "页面" }
        val xhsPage = if (platform == Platform.XIAOHONGSHU) {
            XhsPageClassifier.classify(fullClassName).also { classified ->
                if (classified != XhsPageType.UNKNOWN) currentXhsPage = classified
            }.takeUnless { it == XhsPageType.UNKNOWN } ?: currentXhsPage
        } else {
            XhsPageType.UNKNOWN
        }
        val pageName = if (platform == Platform.XIAOHONGSHU && xhsPage != XhsPageType.UNKNOWN) {
            xhsPage.displayName
        } else {
            rawPageName
        }
        val pageKey = "$packageName:$pageName"
        val now = System.currentTimeMillis()
        if (pageKey == lastPageKey && now - lastRecordedAt < EVENT_THROTTLE_MS) return

        lastPageKey = pageKey
        lastRecordedAt = now
        AgentEventStore.add(
            type = AgentEventType.PAGE_OBSERVED,
            title = "识别到${platform.displayName}页面",
            detail = if (platform == Platform.XIAOHONGSHU && xhsPage != XhsPageType.UNKNOWN) {
                "$pageName · $rawPageName · 只读识别，发布需人工确认"
            } else {
                pageName
            },
            platform = platform,
        )
    }

    override fun onInterrupt() {
        AgentEventStore.add(
            AgentEventType.SAFETY,
            title = "操作辅助被中断",
            detail = "当前不会继续执行设备操作。",
        )
    }

    private companion object {
        const val EVENT_THROTTLE_MS = 2_000L
    }
}
