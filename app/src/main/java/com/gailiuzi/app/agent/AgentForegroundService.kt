package com.gailiuzi.app.agent

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.gailiuzi.app.MainActivity
import com.gailiuzi.app.R
import com.gailiuzi.app.model.AgentEventType

class AgentForegroundService : Service() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification())
        AgentEventStore.setRunning(true)
        AgentEventStore.add(
            AgentEventType.SYSTEM,
            title = "辅助服务已启动",
            detail = "当前只执行页面观察、任务准备和安全检查。",
        )
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        AgentEventStore.setRunning(false)
        AgentEventStore.add(
            AgentEventType.SYSTEM,
            title = "辅助服务已停止",
            detail = "所有设备端任务已暂停。",
        )
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle("改流子正在运行")
        .setContentText("页面观察和任务队列已开启")
        .setOngoing(true)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        .addAction(
            0,
            "停止",
            PendingIntent.getService(
                this,
                1,
                Intent(this, AgentForegroundService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        .build()

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "改流子运行状态",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "显示设备端辅助任务的运行状态"
            },
        )
    }

    companion object {
        private const val CHANNEL_ID = "agent_runtime"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_START = "com.gailiuzi.app.action.START_AGENT"
        private const val ACTION_STOP = "com.gailiuzi.app.action.STOP_AGENT"

        fun start(context: Context) {
            val intent = Intent(context, AgentForegroundService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, AgentForegroundService::class.java).setAction(ACTION_STOP),
            )
        }
    }
}
