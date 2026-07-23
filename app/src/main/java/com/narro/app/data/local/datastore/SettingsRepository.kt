package com.narro.app.data.local.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

data class AppSettings(
    val speechRate: Float = 1.0f,
    val lockEnabled: Boolean = false,
    val biometricEnabled: Boolean = false,
)

class SettingsRepository(private val context: Context) {
    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { preferences ->
        AppSettings(
            speechRate = preferences[SPEECH_RATE] ?: 1.0f,
            lockEnabled = preferences[LOCK_ENABLED] ?: false,
            biometricEnabled = preferences[BIOMETRIC_ENABLED] ?: false,
        )
    }

    suspend fun setSpeechRate(value: Float) {
        context.settingsDataStore.edit { it[SPEECH_RATE] = value.coerceIn(0.5f, 2.0f) }
    }

    suspend fun setLockEnabled(enabled: Boolean) {
        context.settingsDataStore.edit {
            it[LOCK_ENABLED] = enabled
            if (!enabled) it[BIOMETRIC_ENABLED] = false
        }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[BIOMETRIC_ENABLED] = enabled }
    }

    companion object {
        private val SPEECH_RATE = floatPreferencesKey("speech_rate")
        private val LOCK_ENABLED = booleanPreferencesKey("lock_enabled")
        private val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
    }
}
