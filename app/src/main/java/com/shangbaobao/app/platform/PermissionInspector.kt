package com.shangbaobao.app.platform

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.shangbaobao.app.agent.ShangBaoAccessibilityService
import com.shangbaobao.app.model.PermissionSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object PermissionInspector {
    private val mutableState = MutableStateFlow(PermissionSnapshot())
    val state: StateFlow<PermissionSnapshot> = mutableState.asStateFlow()

    fun refresh(context: Context) {
        mutableState.value = PermissionSnapshot(
            accessibilityEnabled = isAccessibilityEnabled(context),
            notificationListenerEnabled =
                context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context),
            notificationPermissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }

    private fun isAccessibilityEnabled(context: Context): Boolean {
        val expected = ComponentName(context, ShangBaoAccessibilityService::class.java)
            .flattenToString()
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        return enabledServices.split(':').any { it.equals(expected, ignoreCase = true) }
    }
}

