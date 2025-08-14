package org.voiddog.coughdetect.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.voiddog.coughdetect.data.Settings
import org.voiddog.coughdetect.data.SettingsManager
import java.io.File

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsManager = SettingsManager.getInstance(application)
    
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    
    // 公开访问高德API Key，用于UI显示
    val gaodeApiKey: String
        get() = _uiState.value.gaodeApiKey
    
    init {
        loadSettings()
        calculateCurrentCacheSize()
    }
    
    private fun loadSettings() {
        viewModelScope.launch {
            try {
                val settings = settingsManager.getSettings()
                _uiState.value = _uiState.value.copy(
                    maxAudioCacheSizeMB = settings.maxAudioCacheSizeMB,
                    gaodeApiKey = settings.gaodeApiKey,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "加载设置失败: ${e.message}",
                    isLoading = false
                )
            }
        }
    }
    
    private fun calculateCurrentCacheSize() {
        viewModelScope.launch {
            try {
                // 设置计算状态为true
                _uiState.value = _uiState.value.copy(isCalculatingCacheSize = true)
                
                // 在IO线程中进行计算
                val currentSizeMB = withContext(Dispatchers.IO) {
                    val audioDir = File(getApplication<Application>().filesDir, "audio_events")
                    if (audioDir.exists()) {
                        val wavFiles = audioDir.listFiles { file -> file.extension.equals("wav", ignoreCase = true) }
                        if (wavFiles != null) {
                            var currentSize = 0L
                            for (file in wavFiles) {
                                currentSize += file.length()
                            }
                            currentSize / (1024 * 1024) // 转换为MB
                        } else {
                            0L
                        }
                    } else {
                        0L
                    }
                }
                
                // 更新UI状态
                _uiState.value = _uiState.value.copy(
                    currentAudioCacheSizeMB = currentSizeMB,
                    isCalculatingCacheSize = false
                )
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "计算当前缓存大小失败", e)
                _uiState.value = _uiState.value.copy(
                    isCalculatingCacheSize = false,
                    error = "计算缓存大小失败: ${e.message}"
                )
            }
        }
    }
    
    fun updateMaxAudioCacheSize(sizeMB: Long) {
        _uiState.value = _uiState.value.copy(maxAudioCacheSizeMB = sizeMB)
    }
    
    fun updateGaodeApiKey(apiKey: String) {
        _uiState.value = _uiState.value.copy(gaodeApiKey = apiKey)
    }
    
    fun saveSettings() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isSaving = true, error = null)
                val settings = Settings(
                    maxAudioCacheSizeMB = uiState.value.maxAudioCacheSizeMB,
                    gaodeApiKey = uiState.value.gaodeApiKey
                )
                settingsManager.saveSettings(settings)
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    message = "设置已保存"
                )
                // 保存设置后重新计算当前缓存大小
                calculateCurrentCacheSize()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    error = "保存设置失败: ${e.message}"
                )
            }
        }
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}

data class SettingsUiState(
    val maxAudioCacheSizeMB: Long = 1024, // 默认1GB
    val currentAudioCacheSizeMB: Long = 0, // 当前使用的磁盘空间大小
    val gaodeApiKey: String = "", // 高德API Key
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isCalculatingCacheSize: Boolean = false, // 是否正在计算缓存大小
    val error: String? = null,
    val message: String? = null
)