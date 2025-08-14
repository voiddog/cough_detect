package org.voiddog.coughdetect.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class SettingsManager private constructor(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("cough_detect_settings", Context.MODE_PRIVATE)
    
    companion object {
        private const val KEY_MAX_AUDIO_CACHE_SIZE_MB = "max_audio_cache_size_mb"
        private const val KEY_GAODE_API_KEY = "gaode_api_key"
        
        @Volatile
        private var INSTANCE: SettingsManager? = null
        
        fun getInstance(context: Context): SettingsManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SettingsManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    var maxAudioCacheSizeMB: Long
        get() = prefs.getLong(KEY_MAX_AUDIO_CACHE_SIZE_MB, 1024) // 默认1GB
        set(value) = prefs.edit { putLong(KEY_MAX_AUDIO_CACHE_SIZE_MB, value) }
        
    var gaodeApiKey: String
        get() = prefs.getString(KEY_GAODE_API_KEY, "") ?: ""
        set(value) = prefs.edit { putString(KEY_GAODE_API_KEY, value) }
    
    fun getSettings(): Settings {
        return Settings(
            maxAudioCacheSizeMB = maxAudioCacheSizeMB,
            gaodeApiKey = gaodeApiKey
        )
    }
    
    fun saveSettings(settings: Settings) {
        maxAudioCacheSizeMB = settings.maxAudioCacheSizeMB
        gaodeApiKey = settings.gaodeApiKey
    }
}