package org.voiddog.coughdetect.repository

import android.content.Context
import android.util.Log
import org.voiddog.coughdetect.data.CoughRecord
import org.voiddog.coughdetect.data.CoughDetectDatabase
import org.voiddog.coughdetect.data.CoughRecordDao
import org.voiddog.coughdetect.engine.CoughDetectEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.text.SimpleDateFormat
import java.util.*

class CoughDetectionRepository(private val context: Context) {
    
    companion object {
        private const val TAG = "CoughDetectionRepository"
        private const val MIN_COUGH_DURATION_MS = 200L // Minimum duration for a valid cough
        private const val MAX_COUGH_DURATION_MS = 3000L // Maximum duration for a single cough
    }
    
    private val database = CoughDetectDatabase.getDatabase(context)
    private val coughRecordDao: CoughRecordDao = database.coughRecordDao()
    private val coughDetectEngine = CoughDetectEngine(context)
    
    private val _detectionState = MutableStateFlow(DetectionState.IDLE)
    val detectionState: StateFlow<DetectionState> = _detectionState.asStateFlow()
    
    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    private val _lastDetectionResult = MutableStateFlow<CoughDetectionResult?>(null)
    val lastDetectionResult: StateFlow<CoughDetectionResult?> = _lastDetectionResult.asStateFlow()
    
    private var detectionJob: Job? = null
    private var currentCoughBuffer = mutableListOf<Float>()
    private var coughStartTime = 0L
    
    enum class DetectionState {
        IDLE, RECORDING, PAUSED, PROCESSING
    }
    
    data class CoughDetectionResult(
        val isCough: Boolean,
        val confidence: Float,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    // Initialize the repository
    suspend fun initialize(): Boolean {
        return try {
            val startTime = System.currentTimeMillis()
            Log.i(TAG, "🚀 开始初始化咳嗽检测仓库...")
            
            // 检查数据库连接
            try {
                val recordCount = coughRecordDao.getRecordCount()
                Log.d(TAG, "数据库连接正常，现有记录数: $recordCount")
            } catch (e: Exception) {
                Log.w(TAG, "数据库连接检查失败", e)
            }
            
            // 初始化检测引擎
            Log.d(TAG, "初始化咳嗽检测引擎...")
            val engineInitialized = coughDetectEngine.initialize()
            
            // 检查引擎初始化结果
            if (!engineInitialized) {
                val engineError = coughDetectEngine.getError()
                if (engineError != null) {
                    Log.e(TAG, "❌ 引擎初始化失败: $engineError")
                    _error.value = engineError
                } else {
                    Log.e(TAG, "❌ 引擎初始化失败，未知错误")
                    _error.value = "引擎初始化失败"
                }
                return false
            }
            
            Log.d(TAG, "✅ 引擎初始化成功，设置回调...")
            setupEngineCallbacks()
                
            val initTime = System.currentTimeMillis() - startTime
            Log.i(TAG, "✅ 仓库初始化完成! 总耗时: ${initTime}ms")
            
            // 创建音频文件目录
            val audioDir = java.io.File(context.getExternalFilesDir(null), "cough_audio")
            if (!audioDir.exists()) {
                val created = audioDir.mkdirs()
                Log.d(TAG, "音频目录创建${if (created) "成功" else "失败"}: ${audioDir.absolutePath}")
            }
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ 仓库初始化异常", e)
            _error.value = "初始化失败: ${e.message}"
            false
        }
    }
    
    private fun setupEngineCallbacks() {
        // Monitor engine state changes
        detectionJob = CoroutineScope(Dispatchers.Main).launch {
            coughDetectEngine.engineState.collect { state ->
                _detectionState.value = when (state) {
                    CoughDetectEngine.EngineState.IDLE -> DetectionState.IDLE
                    CoughDetectEngine.EngineState.RECORDING -> DetectionState.RECORDING
                    CoughDetectEngine.EngineState.PAUSED -> DetectionState.PAUSED
                    CoughDetectEngine.EngineState.PROCESSING -> DetectionState.PROCESSING
                }
            }
        }
        
        // Monitor audio level changes
        CoroutineScope(Dispatchers.Main).launch {
            coughDetectEngine.audioLevel.collect { level ->
                _audioLevel.value = level
            }
        }
        
        // Monitor audio events
        CoroutineScope(Dispatchers.Main).launch {
            coughDetectEngine.lastAudioEvent.collect { event ->
                event?.let { handleAudioEvent(it) }
            }
        }
        
        // Handle engine errors
        CoroutineScope(Dispatchers.Main).launch {
            coughDetectEngine.error.collect { error ->
                if (error != null) {
                    _error.value = error
                    stopDetection()
                }
            }
        }
    }
    
    private suspend fun handleAudioEvent(event: CoughDetectEngine.AudioEvent) {
        when (event.type) {
            CoughDetectEngine.AudioEventType.COUGH_DETECTED -> {
                val timeStr = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())
                    .format(java.util.Date(event.timestamp))
                Log.i(TAG, "🎯 Repository收到咳嗽事件 - 时间: $timeStr, 置信度: ${String.format("%.3f", event.confidence)}, 振幅: ${String.format("%.3f", event.amplitude)}")
                
                handleCoughDetection(event.confidence, event.amplitude, event.timestamp)
            }
            CoughDetectEngine.AudioEventType.AUDIO_LEVEL_CHANGED -> {
                // 定期输出音频电平统计
                if (event.timestamp % 10000 < 100) {  // 大约每10秒输出一次
                    Log.v(TAG, "音频电平: ${String.format("%.3f", event.amplitude)}")
                }
            }
            CoughDetectEngine.AudioEventType.ERROR_OCCURRED -> {
                Log.e(TAG, "❌ 引擎错误事件 - 时间戳: ${event.timestamp}")
                _error.value = "引擎错误"
            }
        }
    }
    
    suspend fun startDetection(): Boolean {
        return withContext(Dispatchers.Main) {
            try {
                if (_detectionState.value == DetectionState.RECORDING) {
                    Log.w(TAG, "Detection already running")
                    return@withContext true
                }
                
                _detectionState.value = DetectionState.RECORDING
                _error.value = null
                
                val success = coughDetectEngine.start()
                if (success) {
                    Log.d(TAG, "Cough detection started")
                } else {
                    Log.e(TAG, "Failed to start detection")
                    _error.value = "启动检测失败"
                }
                success
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start detection", e)
                _error.value = "启动检测失败: ${e.message}"
                _detectionState.value = DetectionState.IDLE
                false
            }
        }
    }
    
    suspend fun pauseDetection() {
        withContext(Dispatchers.Main) {
            if (_detectionState.value == DetectionState.RECORDING) {
                coughDetectEngine.pause()
                Log.d(TAG, "Cough detection paused")
            }
        }
    }
    
    suspend fun resumeDetection() {
        withContext(Dispatchers.Main) {
            if (_detectionState.value == DetectionState.PAUSED) {
                coughDetectEngine.resume()
                Log.d(TAG, "Cough detection resumed")
            }
        }
    }
    
    suspend fun stopDetection() {
        withContext(Dispatchers.Main) {
            coughDetectEngine.stop()
            
            // Process any remaining audio in buffer
            if (currentCoughBuffer.isNotEmpty()) {
                processPendingCough()
            }
            
            Log.d(TAG, "Cough detection stopped")
        }
    }
    
    private suspend fun handleCoughDetection(
        confidence: Float, 
        amplitude: Float, 
        timestamp: Long
    ) {
        // Start new cough buffer if empty
        if (currentCoughBuffer.isEmpty()) {
            coughStartTime = timestamp
        }
        
        // Add dummy audio data to buffer (since we don't have the actual audio data in this simplified version)
        // In a real implementation, you would store the actual audio data
        currentCoughBuffer.add(amplitude)
        
        // Check if cough is too long
        val currentDuration = currentCoughBuffer.size * 100L // Assuming 100ms chunks
        
        if (currentDuration > MAX_COUGH_DURATION_MS) {
            // Save the cough record
            saveCoughRecord(confidence, amplitude, timestamp)
            
            // Clear buffer for next cough
            clearCoughBuffer()
        }
        
        // Update the last detection result
        _lastDetectionResult.value = CoughDetectionResult(
            isCough = true,
            confidence = confidence,
            timestamp = timestamp
        )
    }
    
    private suspend fun processPendingCough() {
        if (currentCoughBuffer.isNotEmpty()) {
            val amplitude = currentCoughBuffer.average().toFloat()
            val timestamp = coughStartTime
            
            // Use a default confidence for pending coughs
            val confidence = 0.5f
            
            saveCoughRecord(confidence, amplitude, timestamp)
            clearCoughBuffer()
        }
    }
    
    private suspend fun saveCoughRecord(
        confidence: Float,
        amplitude: Float,
        timestamp: Long
    ) {
        try {
            val saveStartTime = System.currentTimeMillis()
            
            // Generate filename with timestamp
            val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault())
            val filename = "cough_${dateFormat.format(Date(timestamp))}"
            
            // Create audio file path
            val audioDir = java.io.File(context.getExternalFilesDir(null), "cough_audio")
            val audioFilePath = "${audioDir.absolutePath}/$filename.wav"
            
            // Create database record
            val duration = currentCoughBuffer.size * 100L // Assuming 100ms chunks
            val coughRecord = CoughRecord(
                timestamp = timestamp,
                audioFilePath = audioFilePath,
                duration = duration,
                confidence = confidence,
                amplitude = amplitude,
                createdAt = Date(timestamp)
            )
            
            // Save to database
            val recordId = coughRecordDao.insertRecord(coughRecord)
            val saveTime = System.currentTimeMillis() - saveStartTime
            
            // 获取当前总记录数
            val totalRecords = coughRecordDao.getRecordCount()
            
            Log.i(TAG, "💾 咳嗽记录已保存 - ID: $recordId, 文件: $filename, 置信度: ${String.format("%.3f", confidence)}, " +
                    "时长: ${duration}ms, 保存耗时: ${saveTime}ms, 总记录数: $totalRecords")
            
            // 检查存储空间
            val freeSpace = audioDir.freeSpace / 1024 / 1024
            if (freeSpace < 100) {  // 小于100MB时警告
                Log.w(TAG, "⚠️ 存储空间不足: ${freeSpace}MB")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 保存咳嗽记录失败", e)
            _error.value = "保存咳嗽记录失败: ${e.message}"
        }
    }
    
    private fun clearCoughBuffer() {
        currentCoughBuffer.clear()
        coughStartTime = 0L
    }
    
    // Database operations
    fun getAllCoughRecords(): Flow<List<CoughRecord>> {
        return coughRecordDao.getAllRecords()
    }
    
    suspend fun getCoughRecordById(id: Long): CoughRecord? {
        return coughRecordDao.getRecordById(id)
    }
    
    suspend fun deleteCoughRecord(record: CoughRecord) {
        try {
            // Delete audio file
            val audioFile = java.io.File(record.audioFilePath)
            if (audioFile.exists()) {
                audioFile.delete()
            }
            
            // Delete database record
            coughRecordDao.deleteRecord(record)
            
            Log.d(TAG, "Cough record deleted: ${record.id}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete cough record", e)
            _error.value = "删除记录失败: ${e.message}"
        }
    }
    
    suspend fun clearAllCoughRecords() {
        try {
            // Get all records first
            val records = coughRecordDao.getAllRecords().first()
            
            // Delete audio files
            records.forEach { record ->
                val audioFile = java.io.File(record.audioFilePath)
                if (audioFile.exists()) {
                    audioFile.delete()
                }
            }
            
            // Clear database
            coughRecordDao.deleteAllRecords()
            
            Log.d(TAG, "All cough records cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear all records", e)
            _error.value = "清空记录失败: ${e.message}"
        }
    }
    
    suspend fun getCoughRecordCount(): Int {
        return coughRecordDao.getRecordCount()
    }
    
    suspend fun getCoughRecordsInTimeRange(startTime: Long, endTime: Long): Flow<List<CoughRecord>> {
        return coughRecordDao.getRecordsInTimeRange(startTime, endTime)
    }
    
    suspend fun getAverageConfidence(): Float? {
        return coughRecordDao.getAverageConfidence()
    }
    
    fun clearError() {
        _error.value = null
        coughDetectEngine.clearError()
    }
    
    fun release() {
        detectionJob?.cancel()
        coughDetectEngine.release()
        clearCoughBuffer()
        Log.d(TAG, "Repository released")
    }
    
    // Utility functions
    fun isRecording(): Boolean {
        return coughDetectEngine.isRecording()
    }
    
    fun isPaused(): Boolean {
        return coughDetectEngine.isPaused()
    }
    
    fun isProcessing(): Boolean {
        return coughDetectEngine.isProcessing()
    }
}