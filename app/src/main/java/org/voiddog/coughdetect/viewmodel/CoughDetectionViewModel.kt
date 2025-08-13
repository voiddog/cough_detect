package org.voiddog.coughdetect.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import org.voiddog.coughdetect.data.CoughRecord
import org.voiddog.coughdetect.repository.CoughDetectionRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class CoughDetectionViewModel(application: Application) : AndroidViewModel(application) {
    
    companion object {
        private const val TAG = "CoughDetectionViewModel"
    }
    
    private val repository = CoughDetectionRepository(application)
    
    // UI States
    private val _uiState = MutableStateFlow(CoughDetectionUiState())
    val uiState: StateFlow<CoughDetectionUiState> = _uiState.asStateFlow()
    
    // Cough records from database
    val coughRecords: StateFlow<List<CoughRecord>> = repository.getAllCoughRecords()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    // Detection state
    val detectionState = repository.detectionState
    
    // Audio level for visualization
    val audioLevel = repository.audioLevel
    
    // Last detection result
    val lastDetectionResult = repository.lastDetectionResult
    
    // Errors
    val error = repository.error
    
    data class CoughDetectionUiState(
        val isInitialized: Boolean = false,
        val isLoading: Boolean = true,
        val showPermissionDialog: Boolean = false,
        val showClearConfirmDialog: Boolean = false,
        val selectedRecord: CoughRecord? = null,
        val message: String? = null
    )
    
    init {
        initializeRepository()
    }
    
    private fun initializeRepository() {
        viewModelScope.launch {
            try {
                val startTime = System.currentTimeMillis()
                Log.i(TAG, "🚀 ViewModel开始初始化...")
                
                _uiState.value = _uiState.value.copy(isLoading = true)
                
                val initialized = repository.initialize()
                val initTime = System.currentTimeMillis() - startTime
                
                _uiState.value = _uiState.value.copy(
                    isInitialized = initialized,
                    isLoading = false
                )
                
                if (initialized) {
                    Log.i(TAG, "✅ ViewModel初始化成功，总耗时: ${initTime}ms")
                    
                    // 启动数据流监控
                    startDataFlowMonitoring()
                } else {
                    Log.e(TAG, "❌ ViewModel初始化失败，耗时: ${initTime}ms")
                }
            } catch (e: Exception) {
                val initTime = System.currentTimeMillis() - System.currentTimeMillis()
                Log.e(TAG, "❌ ViewModel初始化异常，耗时: ${initTime}ms", e)
                _uiState.value = _uiState.value.copy(
                    isInitialized = false,
                    isLoading = false
                )
            }
        }
    }
    
    private fun startDataFlowMonitoring() {
        // 监控检测状态变化
        viewModelScope.launch {
            detectionState.collect { state ->
                Log.d(TAG, "检测状态变化: ${state.name}")
            }
        }
        
        // 监控咳嗽记录变化
        viewModelScope.launch {
            coughRecords.collect { records ->
                Log.d(TAG, "咳嗽记录更新: ${records.size}条记录")
            }
        }
        
        // 监控错误状态
        viewModelScope.launch {
            error.collect { error ->
                if (error != null) {
                    Log.e(TAG, "检测到错误: $error")
                }
            }
        }
    }
    
    // Detection Control Functions
    fun startDetection() {
        viewModelScope.launch {
            try {
                Log.i(TAG, "🎬 用户请求开始检测...")
                val startTime = System.currentTimeMillis()
                
                val success = repository.startDetection()
                val operationTime = System.currentTimeMillis() - startTime
                
                if (success) {
                    Log.i(TAG, "✅ 检测启动成功，耗时: ${operationTime}ms")
                    _uiState.value = _uiState.value.copy(
                        message = "开始咳嗽检测"
                    )
                } else {
                    Log.e(TAG, "❌ 检测启动失败，耗时: ${operationTime}ms")
                    _uiState.value = _uiState.value.copy(
                        message = "启动检测失败"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ 启动检测时发生异常", e)
                _uiState.value = _uiState.value.copy(
                    message = "启动异常: ${e.message}"
                )
            }
        }
    }
    
    fun pauseDetection() {
        viewModelScope.launch {
            try {
                repository.pauseDetection()
                Log.d(TAG, "Detection paused")
                _uiState.value = _uiState.value.copy(
                    message = "检测已暂停"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error pausing detection", e)
            }
        }
    }
    
    fun resumeDetection() {
        viewModelScope.launch {
            try {
                repository.resumeDetection()
                Log.d(TAG, "Detection resumed")
                _uiState.value = _uiState.value.copy(
                    message = "检测已恢复"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error resuming detection", e)
            }
        }
    }
    
    fun stopDetection() {
        viewModelScope.launch {
            try {
                repository.stopDetection()
                Log.d(TAG, "Detection stopped")
                _uiState.value = _uiState.value.copy(
                    message = "检测已停止"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping detection", e)
            }
        }
    }
    
    // Record Management Functions
    fun showClearConfirmDialog() {
        _uiState.value = _uiState.value.copy(showClearConfirmDialog = true)
    }
    
    fun hideClearConfirmDialog() {
        _uiState.value = _uiState.value.copy(showClearConfirmDialog = false)
    }
    
    fun clearAllRecords() {
        viewModelScope.launch {
            try {
                repository.clearAllCoughRecords()
                _uiState.value = _uiState.value.copy(
                    showClearConfirmDialog = false,
                    message = "所有记录已清空"
                )
                Log.d(TAG, "All records cleared")
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing records", e)
                _uiState.value = _uiState.value.copy(
                    showClearConfirmDialog = false
                )
            }
        }
    }
    
    fun deleteRecord(record: CoughRecord) {
        viewModelScope.launch {
            try {
                repository.deleteCoughRecord(record)
                _uiState.value = _uiState.value.copy(
                    message = "记录已删除"
                )
                Log.d(TAG, "Record deleted: ${record.id}")
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting record", e)
            }
        }
    }
    
    fun selectRecord(record: CoughRecord?) {
        _uiState.value = _uiState.value.copy(selectedRecord = record)
    }
    
    // Permission handling
    fun showPermissionDialog() {
        _uiState.value = _uiState.value.copy(showPermissionDialog = true)
    }
    
    fun hidePermissionDialog() {
        _uiState.value = _uiState.value.copy(showPermissionDialog = false)
    }
    
    fun shouldShowPermissionDialog(): Boolean {
        return !_uiState.value.isInitialized && !_uiState.value.isLoading
    }
    
    // Statistics Functions
    suspend fun getCoughRecordCount(): Int {
        return repository.getCoughRecordCount()
    }
    
    suspend fun getAverageConfidence(): Float? {
        return repository.getAverageConfidence()
    }
    
    suspend fun getCoughRecordsInTimeRange(startTime: Long, endTime: Long): Flow<List<CoughRecord>> {
        return repository.getCoughRecordsInTimeRange(startTime, endTime)
    }
    
    // Utility Functions
    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
    
    fun clearError() {
        repository.clearError()
    }
    
    fun isRecording(): Boolean {
        return repository.isRecording()
    }
    
    fun isPaused(): Boolean {
        return repository.isPaused()
    }
    
    fun isProcessing(): Boolean {
        return repository.isProcessing()
    }
    
    // Get detection status text
    fun getDetectionStatusText(): String {
        return when (detectionState.value) {
            CoughDetectionRepository.DetectionState.IDLE -> "未开始检测"
            CoughDetectionRepository.DetectionState.RECORDING -> "正在检测中..."
            CoughDetectionRepository.DetectionState.PAUSED -> "检测已暂停"
            CoughDetectionRepository.DetectionState.PROCESSING -> "处理中..."
        }
    }
    
    // Get button text based on current state
    fun getMainButtonText(): String {
        return when (detectionState.value) {
            CoughDetectionRepository.DetectionState.IDLE -> "开始检测"
            CoughDetectionRepository.DetectionState.RECORDING -> "暂停检测"
            CoughDetectionRepository.DetectionState.PAUSED -> "继续检测"
            CoughDetectionRepository.DetectionState.PROCESSING -> "处理中..."
        }
    }
    
    // Handle main button click
    fun onMainButtonClick() {
        when (detectionState.value) {
            CoughDetectionRepository.DetectionState.IDLE -> startDetection()
            CoughDetectionRepository.DetectionState.RECORDING -> pauseDetection()
            CoughDetectionRepository.DetectionState.PAUSED -> resumeDetection()
            CoughDetectionRepository.DetectionState.PROCESSING -> {
                // Do nothing when processing
            }
        }
    }
    
    // Get formatted statistics
    suspend fun getFormattedStats(): String {
        val count = getCoughRecordCount()
        val avgConfidence = getAverageConfidence()
        
        return buildString {
            appendLine("总咳嗽次数: $count")
            if (avgConfidence != null) {
                appendLine("平均置信度: ${"%.2f".format(avgConfidence * 100)}%")
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        repository.release()
        Log.d(TAG, "ViewModel cleared")
    }
}