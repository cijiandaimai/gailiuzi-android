package com.gailiuzi.app.agent

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.gailiuzi.app.model.AgentEventType
import com.gailiuzi.app.model.Platform

class GailiuziAccessibilityService : AccessibilityService() {
    private var lastRecordedAt = 0L
    private var lastPageKey: String? = null

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
        val pageName = event.className?.toString()?.substringAfterLast('.') ?: "页面"
        val pageKey = "$packageName:$pageName"
        val now = System.currentTimeMillis()

        if (pageKey == lastPageKey && now - lastRecordedAt < EVENT_THROTTLE_MS) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) return

        lastPageKey = pageKey
        lastRecordedAt = now
        AgentEventStore.add(
            type = AgentEventType.PAGE_OBSERVED,
            title = "识别到${platform.displayName}页面",
            detail = pageName,
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

