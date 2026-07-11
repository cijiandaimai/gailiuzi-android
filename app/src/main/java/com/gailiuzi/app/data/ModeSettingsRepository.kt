package com.gailiuzi.app.data

import android.content.Context
import androidx.core.content.edit
import com.gailiuzi.app.model.AutomationLevel
import com.gailiuzi.app.model.AutomationMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ModeSettings(
    val enabledModes: Set<AutomationMode> = setOf(AutomationMode.FRONT_DESK),
    val automationLevel: AutomationLevel = AutomationLevel.DRAFT,
)

class ModeSettingsRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val mutableState = MutableStateFlow(load())
    val state: StateFlow<ModeSettings> = mutableState.asStateFlow()

    fun setModeEnabled(mode: AutomationMode, enabled: Boolean) {
        val nextModes = mutableState.value.enabledModes.toMutableSet().apply {
            if (enabled) add(mode) else remove(mode)
        }
        update(mutableState.value.copy(enabledModes = nextModes))
    }

    fun setAutomationLevel(level: AutomationLevel) {
        update(mutableState.value.copy(automationLevel = level))
    }

    private fun update(settings: ModeSettings) {
        preferences.edit {
            putStringSet(KEY_MODES, settings.enabledModes.map { it.name }.toSet())
            putString(KEY_LEVEL, settings.automationLevel.name)
        }
        mutableState.value = settings
    }

    private fun load(): ModeSettings {
        val modes = preferences.getStringSet(KEY_MODES, null)
            ?.mapNotNull { stored -> AutomationMode.entries.firstOrNull { it.name == stored } }
            ?.toSet()
            ?: setOf(AutomationMode.FRONT_DESK)
        val levelName = preferences.getString(KEY_LEVEL, AutomationLevel.DRAFT.name)
        val level = AutomationLevel.entries.firstOrNull { it.name == levelName } ?: AutomationLevel.DRAFT
        return ModeSettings(enabledModes = modes, automationLevel = level)
    }

    private companion object {
        const val PREFERENCES_NAME = "gailiuzi_mode_settings"
        const val KEY_MODES = "enabled_modes"
        const val KEY_LEVEL = "automation_level"
    }
}
