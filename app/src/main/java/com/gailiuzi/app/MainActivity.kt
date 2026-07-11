package com.gailiuzi.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.gailiuzi.app.ai.AiGateway
import com.gailiuzi.app.ai.AiSettingsRepository
import com.gailiuzi.app.agent.AgentForegroundService
import com.gailiuzi.app.business.BusinessRepository
import com.gailiuzi.app.data.ModeSettingsRepository
import com.gailiuzi.app.knowledge.KnowledgeBaseRepository
import com.gailiuzi.app.platform.PermissionInspector
import com.gailiuzi.app.ui.GailiuziApp
import com.gailiuzi.app.ui.theme.GailiuziTheme

class MainActivity : ComponentActivity() {
    private val modeSettings by lazy { ModeSettingsRepository(applicationContext) }
    private val aiSettings by lazy { AiSettingsRepository(applicationContext) }
    private val aiGateway by lazy { AiGateway() }
    private val businessSettings by lazy { BusinessRepository(applicationContext) }
    private val knowledgeBase by lazy { KnowledgeBaseRepository(applicationContext) }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refreshPermissions() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        refreshPermissions()

        setContent {
            GailiuziTheme {
                GailiuziApp(
                    modeSettingsRepository = modeSettings,
                    aiSettingsRepository = aiSettings,
                    aiGateway = aiGateway,
                    businessRepository = businessSettings,
                    knowledgeRepository = knowledgeBase,
                    openAccessibilitySettings = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    openNotificationSettings = {
                        startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                    },
                    requestNotificationPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    startAgent = { AgentForegroundService.start(this) },
                    stopAgent = { AgentForegroundService.stop(this) },
                    refreshPermissions = ::refreshPermissions,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissions()
    }

    private fun refreshPermissions() {
        PermissionInspector.refresh(applicationContext)
    }
}
