package com.gailiuzi.app.agent

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.gailiuzi.app.model.AgentEventType
import com.gailiuzi.app.model.Platform

class GailiuziNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val platform = Platform.fromPackageName(sbn.packageName) ?: return
        AgentEventStore.add(
            type = AgentEventType.NOTIFICATION_DISCOVERED,
            title = "发现${platform.displayName}通知",
            detail = "已记录通知事件，正文暂不在设备日志中保存。",
            platform = platform,
        )
    }
}

