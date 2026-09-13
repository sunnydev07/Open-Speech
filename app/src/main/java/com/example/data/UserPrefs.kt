package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ai.CefrLevel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("openspeech_prefs")

/** Small user settings persisted across process death (F2, F10). */
class UserPrefs(private val context: Context) {
    private object Keys {
        val DAILY_GOAL_MINUTES = intPreferencesKey("daily_goal_minutes")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val USER_LEVEL = stringPreferencesKey("user_level")
    }

    val dailyGoalMinutes: Flow<Int> =
        context.dataStore.data.map { it[Keys.DAILY_GOAL_MINUTES] ?: 15 }

    val onboardingDone: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.ONBOARDING_DONE] ?: false }

    val userLevel: Flow<CefrLevel> =
        context.dataStore.data.map { prefs ->
            runCatching { CefrLevel.valueOf(prefs[Keys.USER_LEVEL] ?: CefrLevel.B1.name) }
                .getOrDefault(CefrLevel.B1)
        }

    suspend fun setDailyGoalMinutes(minutes: Int) {
        context.dataStore.edit { it[Keys.DAILY_GOAL_MINUTES] = minutes.coerceIn(1, 120) }
    }

    suspend fun setOnboardingDone() {
        context.dataStore.edit { it[Keys.ONBOARDING_DONE] = true }
    }

    suspend fun setUserLevel(level: CefrLevel) {
        context.dataStore.edit { it[Keys.USER_LEVEL] = level.name }
    }
}
