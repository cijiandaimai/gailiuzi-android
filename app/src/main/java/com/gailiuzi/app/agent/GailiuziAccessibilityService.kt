package com.gailiuzi.app.agent

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.gailiuzi.app.core.GenericPlatformRiskDetector
import com.gailiuzi.app.model.AgentEventType
import com.gailiuzi.app.model.Platform
import com.gailiuzi.app.platform.douyin.DouyinPageClassifier
import com.gailiuzi.app.platform.douyin.DouyinPageType
import com.gailiuzi.app.platform.kuaishou.KuaishouPageClassifier
import com.gailiuzi.app.platform.kuaishou.KuaishouPageType
import com.gailiuzi.app.platform.xhs.XhsPageClassifier
import com.gailiuzi.app.platform.xhs.XhsPageType

class GailiuziAccessibilityService : AccessibilityService() {
    private var lastRecordedAt = 0L
    private var lastPageKey: String? = null
    private var currentDouyinPage: DouyinPageType = DouyinPageType.UNKNOWN
    private var currentKuaishouPage: KuaishouPageType = KuaishouPageType.UNKNOWN
    private var currentXhsPage: XhsPageType = XhsPageType.UNKNOWN

    override fun onServiceConnected() {
        AgentEventStore.add(
            AgentEventType.SYSTEM,
            title = "操作辅助已授权",
            detail = "授权已就绪，启动辅助服务后才会观察受支持平台的前台页面。",
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val packageName = event.packageName?.toString() ?: return
        val platform = Platform.fromPackageName(packageName) ?: return
        if (!AgentEventStore.running.value) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) return

        val fullClassName = event.className?.toString().orEmpty()
        val rawPageName = fullClassName.substringAfterLast('.').ifBlank { "页面" }
        val douyinPage = if (platform == Platform.DOUYIN) {
            DouyinPageClassifier.classify(fullClassName).also { classified ->
                if (classified != DouyinPageType.UNKNOWN) currentDouyinPage = classified
            }.takeUnless { it == DouyinPageType.UNKNOWN } ?: currentDouyinPage
        } else {
            DouyinPageType.UNKNOWN
        }
        val kuaishouPage = if (platform == Platform.KUAISHOU) {
            KuaishouPageClassifier.classify(fullClassName).also { classified ->
                if (classified != KuaishouPageType.UNKNOWN) currentKuaishouPage = classified
            }.takeUnless { it == KuaishouPageType.UNKNOWN } ?: currentKuaishouPage
        } else {
            KuaishouPageType.UNKNOWN
        }
        val xhsPage = if (platform == Platform.XIAOHONGSHU) {
            XhsPageClassifier.classify(fullClassName).also { classified ->
                if (classified != XhsPageType.UNKNOWN) currentXhsPage = classified
            }.takeUnless { it == XhsPageType.UNKNOWN } ?: currentXhsPage
        } else {
            XhsPageType.UNKNOWN
        }
        val pageName = when {
            platform == Platform.DOUYIN && douyinPage != DouyinPageType.UNKNOWN -> douyinPage.displayName
            platform == Platform.KUAISHOU && kuaishouPage != KuaishouPageType.UNKNOWN ->
                kuaishouPage.displayName
            platform == Platform.XIAOHONGSHU && xhsPage != XhsPageType.UNKNOWN -> xhsPage.displayName
            else -> rawPageName
        }
        val pageKey = "$packageName:$pageName"
        val now = System.currentTimeMillis()
        if (pageKey == lastPageKey && now - lastRecordedAt < EVENT_THROTTLE_MS) return

        lastPageKey = pageKey
        lastRecordedAt = now
        val safetyPageDetected = when (platform) {
            Platform.DOUYIN -> douyinPage == DouyinPageType.LOGIN_OR_RISK
            Platform.KUAISHOU -> kuaishouPage == KuaishouPageType.LOGIN_OR_RISK
            Platform.XIAOHONGSHU -> xhsPage == XhsPageType.LOGIN_OR_RESTRICTED
            Platform.MEITUAN -> GenericPlatformRiskDetector.isRiskPage(fullClassName)
        }
        if (safetyPageDetected) {
            stopForSafety(platform, pageName, rawPageName)
            return
        }
        AgentEventStore.add(
            type = AgentEventType.PAGE_OBSERVED,
            title = "识别到${platform.displayName}页面",
            detail = when {
                platform == Platform.DOUYIN && douyinPage != DouyinPageType.UNKNOWN ->
                    "$pageName · $rawPageName · 官方 API 优先，UI 只作可见辅助"
                platform == Platform.KUAISHOU && kuaishouPage != KuaishouPageType.UNKNOWN ->
                    "$pageName · $rawPageName · 评论草稿可辅助，发布需人工确认"
                platform == Platform.XIAOHONGSHU && xhsPage != XhsPageType.UNKNOWN ->
                    "$pageName · $rawPageName · 只读识别，发布需人工确认"
                else -> pageName
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

    private fun stopForSafety(platform: Platform, pageName: String, rawPageName: String) {
        AgentEventStore.setRunning(false)
        AgentEventStore.add(
            type = AgentEventType.SAFETY,
            title = "${platform.displayName}需要人工处理",
            detail = "$pageName · $rawPageName · 已停止全部辅助流程",
            platform = platform,
        )
        AgentForegroundService.stop(this)
    }

    private companion object {
        const val EVENT_THROTTLE_MS = 2_000L
    }
}
