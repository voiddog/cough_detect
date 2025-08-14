package org.voiddog.coughdetect.engine

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.voiddog.coughdetect.audio.AudioRecorder
import org.voiddog.coughdetect.ml.TensorFlowLiteDetector
import java.util.concurrent.atomic.AtomicBoolean

class CoughDetectEngine(private val context: Context) {

    companion object {
        private const val TAG = "CoughDetectEngine"
        private const val AUDIO_BUFFER_DURATION_MS = 1000 // 1 second buffers
        private const val DETECTION_INTERVAL_MS = 500 // Run detection every 500ms
        private const val MIN_CONFIDENCE_THRESHOLD = 0.6f
    }

    private val audioRecorder = AudioRecorder(context)
    private val tensorFlowDetector = TensorFlowLiteDetector(context)
    
    private var detectionJob: Job? = null
    private val isInitialized = AtomicBoolean(false)
    
    // Audio buffer for accumulating samples
    private val audioBuffer = mutableListOf<Float>()
    private val bufferLock = Any()
    private val targetBufferSize = (audioRecorder.getSampleRate() * AUDIO_BUFFER_DURATION_MS) / 1000

    // Engine states
    enum class EngineState(val value: Int) {
        IDLE(0),
        RECORDING(1),
        PAUSED(2),
        PROCESSING(3)
    }

    // Audio event types
    enum class AudioEventType(val value: Int) {
        COUGH_DETECTED(0),
        SNORING_DETECTED(1),
        AUDIO_LEVEL_CHANGED(2),
        ERROR_OCCURRED(3)
    }

    // Audio event data
    data class AudioEvent(
        val type: AudioEventType,
        val confidence: Float,
        val amplitude: Float,
        val timestamp: Long,
        val audioData: FloatArray? = null
    ) {
        // Override equals and hashCode to handle FloatArray properly
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as AudioEvent

            if (type != other.type) return false
            if (confidence != other.confidence) return false
            if (amplitude != other.amplitude) return false
            if (timestamp != other.timestamp) return false
            if (audioData != null) {
                if (other.audioData == null) return false
                if (!audioData.contentEquals(other.audioData)) return false
            } else if (other.audioData != null) return false

            return true
        }

        override fun hashCode(): Int {
            var result = type.hashCode()
            result = 31 * result + confidence.hashCode()
            result = 31 * result + amplitude.hashCode()
            result = 31 * result + timestamp.hashCode()
            result = 31 * result + (audioData?.contentHashCode() ?: 0)
            return result
        }
    }

    // State flows
    private val _engineState = MutableStateFlow(EngineState.IDLE)
    val engineState: StateFlow<EngineState> = _engineState.asStateFlow()

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    private val _lastAudioEvent = MutableStateFlow<AudioEvent?>(null)
    val lastAudioEvent: StateFlow<AudioEvent?> = _lastAudioEvent.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // 统计变量
    private var coughDetectionCount = 0
    private var firstCoughTime = 0L

    init {
        // Set up audio data callback
        audioRecorder.setAudioDataCallback { audioData, amplitude ->
            onAudioData(audioData, amplitude)
        }
    }

    // Callback for audio data from AudioRecorder
    private fun onAudioData(audioData: FloatArray, amplitude: Float) {
        try {
            // Update audio level
            _audioLevel.value = amplitude
            
            // Emit audio level change event
            val event = AudioEvent(
                type = AudioEventType.AUDIO_LEVEL_CHANGED,
                confidence = 1.0f,
                amplitude = amplitude,
                timestamp = System.currentTimeMillis()
            )
            _lastAudioEvent.value = event
            
            // Add to buffer for cough detection
            synchronized(bufferLock) {
                audioBuffer.addAll(audioData.toList())
                
                // Keep buffer size manageable
                while (audioBuffer.size > targetBufferSize * 2) {
                    audioBuffer.removeFirst()
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "处理音频数据时发生异常", e)
            _error.value = "音频处理异常: ${e.message}"
        }
    }

    // Callback for cough detection results
    private fun onCoughDetected(confidence: Float, amplitude: Float, detectedAudioData: FloatArray) {
        try {
            val currentTime = System.currentTimeMillis()
            val timeStr = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())
                .format(java.util.Date(currentTime))

            Log.i(TAG, "🎯 咳嗽检测成功! 时间: $timeStr, 置信度: ${String.format("%.3f", confidence)}, " +
                    "振幅: ${String.format("%.3f", amplitude)}, 音频数据长度: ${detectedAudioData.size}")

            // Use the exact audio data that was used for detection
            val event = AudioEvent(
                type = AudioEventType.COUGH_DETECTED,
                confidence = confidence,
                amplitude = amplitude,
                timestamp = currentTime,
                audioData = detectedAudioData // 使用实际检测的音频数据
            )
            _lastAudioEvent.value = event

            // 统计咳嗽检测频率
            coughDetectionCount++
            if (coughDetectionCount == 1) {
                firstCoughTime = currentTime
            }

            if (coughDetectionCount % 5 == 0) {
                val avgInterval = (currentTime - firstCoughTime) / (coughDetectionCount - 1)
                Log.i(TAG, "咳嗽检测统计 - 总数: $coughDetectionCount, 平均间隔: ${avgInterval}ms")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "处理咳嗽检测结果时发生异常", e)
            _error.value = "检测处理异常: ${e.message}"
        }
    }

    // Initialize the engine
    fun initialize(modelPath: String? = null): Boolean {
        return try {
            val startTime = System.currentTimeMillis()
            Log.i(TAG, "🚀 开始初始化咳嗽检测引擎...")

            if (isInitialized.get()) {
                Log.w(TAG, "引擎已经初始化")
                return true
            }

            // Initialize audio recorder
            if (!audioRecorder.initialize()) {
                Log.e(TAG, "❌ 音频录制器初始化失败")
                _error.value = "音频录制器初始化失败"
                return false
            }

            // Initialize TensorFlow Lite detector (async)
            CoroutineScope(Dispatchers.IO).launch {
                val tfSuccess = tensorFlowDetector.initialize()
                Log.i(TAG, if (tfSuccess) "✅ TensorFlow Lite初始化成功" else "⚠️ TensorFlow Lite初始化失败，使用规则检测")
            }

            val initTime = System.currentTimeMillis() - startTime
            isInitialized.set(true)
            _engineState.value = EngineState.IDLE

            // 重置统计
            coughDetectionCount = 0
            firstCoughTime = 0L

            Log.i(TAG, "✅ 引擎初始化成功! 耗时: ${initTime}ms")
            true

        } catch (e: Exception) {
            Log.e(TAG, "❌ 初始化过程中发生异常", e)
            _error.value = "初始化失败: ${e.message}"
            false
        }
    }

    // Start detection
    fun start(): Boolean {
        return try {
            val startTime = System.currentTimeMillis()
            Log.i(TAG, "🎬 开始启动咳嗽检测...")

            if (!isInitialized.get()) {
                Log.e(TAG, "❌ 引擎未初始化，无法启动检测")
                _error.value = "引擎未初始化"
                return false
            }

            // 检查当前状态
            val currentState = getState()
            Log.d(TAG, "当前引擎状态: ${currentState.name}")

            if (currentState == EngineState.RECORDING) {
                Log.w(TAG, "⚠️ 检测已在运行中")
                return true
            }

            // Start audio recording
            if (!audioRecorder.start()) {
                Log.e(TAG, "❌ 音频录制启动失败")
                _error.value = "音频录制启动失败"
                return false
            }

            // Start detection job
            startDetectionJob()

            _engineState.value = EngineState.RECORDING
            val startDuration = System.currentTimeMillis() - startTime

            Log.i(TAG, "✅ 检测启动成功! 耗时: ${startDuration}ms")
            true

        } catch (e: Exception) {
            Log.e(TAG, "❌ 启动过程中发生异常", e)
            _error.value = "启动失败: ${e.message}"
            false
        }
    }

    // Stop detection
    fun stop() {
        try {
            Log.i(TAG, "⏹️ 停止咳嗽检测...")

            val currentState = getState()
            Log.d(TAG, "停止前状态: ${currentState.name}")

            // Stop detection job
            detectionJob?.cancel()
            detectionJob = null

            // Stop audio recording
            audioRecorder.stop()

            // Clear audio buffer
            synchronized(bufferLock) {
                audioBuffer.clear()
            }

            _engineState.value = EngineState.IDLE

            Log.i(TAG, "✅ 检测已停止，新状态: ${getState().name}")

            // 输出检测统计
            if (coughDetectionCount > 0) {
                val totalTime = System.currentTimeMillis() - firstCoughTime
                val avgInterval = if (coughDetectionCount > 1) totalTime / (coughDetectionCount - 1) else 0
                Log.i(TAG, "本次检测统计 - 总咳嗽数: $coughDetectionCount, 平均间隔: ${avgInterval}ms")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 停止过程中发生异常", e)
            _error.value = "停止失败: ${e.message}"
        }
    }

    // Pause detection
    fun pause() {
        try {
            if (getState() == EngineState.RECORDING) {
                audioRecorder.pause()
                _engineState.value = EngineState.PAUSED
                Log.i(TAG, "⏸️ 检测已暂停")
            }
        } catch (e: Exception) {
            Log.e(TAG, "暂停时发生异常", e)
            _error.value = "暂停失败: ${e.message}"
        }
    }

    // Resume detection
    fun resume() {
        try {
            if (getState() == EngineState.PAUSED) {
                audioRecorder.resume()
                _engineState.value = EngineState.RECORDING
                Log.i(TAG, "▶️ 检测已恢复")
            }
        } catch (e: Exception) {
            Log.e(TAG, "恢复时发生异常", e)
            _error.value = "恢复失败: ${e.message}"
        }
    }

    // Get current engine state
    fun getState(): EngineState {
        return _engineState.value
    }

    // Get current audio level
    fun getAudioLevel(): Float {
        return _audioLevel.value
    }

    // Get sample rate
    fun getSampleRate(): Int {
        return audioRecorder.getSampleRate()
    }

    // Check if engine is ready
    fun isReady(): Boolean {
        return isInitialized.get()
    }

    // Clear error
    fun clearError() {
        _error.value = null
        audioRecorder.clearError()
    }

    // Release resources
    fun release() {
        try {
            Log.i(TAG, "🧹 释放引擎资源...")
            
            // Stop detection
            stop()
            
            // Release audio recorder
            audioRecorder.release()
            
            // Release TensorFlow detector
            tensorFlowDetector.cleanup()
            
            // Clear buffer
            synchronized(bufferLock) {
                audioBuffer.clear()
            }
            
            isInitialized.set(false)
            _engineState.value = EngineState.IDLE
            
            Log.i(TAG, "✅ 引擎资源已释放")
            
        } catch (e: Exception) {
            Log.e(TAG, "释放资源时发生异常", e)
        }
    }

    // Start detection job
    private fun startDetectionJob() {
        detectionJob = CoroutineScope(Dispatchers.Default).launch {
            while (isActive && getState() == EngineState.RECORDING) {
                try {
                    // Get audio data for detection
                    val audioData = synchronized(bufferLock) {
                        if (audioBuffer.size >= targetBufferSize) {
                            val data = audioBuffer.take(targetBufferSize).toFloatArray()
                            // Remove processed data (keep some overlap)
                            val removeCount = targetBufferSize / 2
                            repeat(removeCount) {
                                if (audioBuffer.isNotEmpty()) {
                                    audioBuffer.removeFirst()
                                }
                            }
                            data
                        } else {
                            null
                        }
                    }

                    audioData?.let { data ->
                        _engineState.value = EngineState.PROCESSING
                        
                        // Run cough detection
                        val result = tensorFlowDetector.detectCough(data)
                        
                        if (result.isCough && result.confidence >= MIN_CONFIDENCE_THRESHOLD) {
                            val amplitude = _audioLevel.value
                            // Pass the actual audio data that was used for detection
                            onCoughDetected(result.confidence, amplitude, data)
                        }
                        
                        _engineState.value = EngineState.RECORDING
                    }

                    // Wait before next detection
                    delay(DETECTION_INTERVAL_MS.toLong())
                    
                } catch (e: Exception) {
                    Log.e(TAG, "检测任务中发生异常", e)
                    _error.value = "检测异常: ${e.message}"
                    delay(1000) // Wait before retrying
                }
            }
        }
    }

    // Check if recording
    fun isRecording(): Boolean {
        return engineState.value == EngineState.RECORDING
    }

    // Check if paused
    fun isPaused(): Boolean {
        return engineState.value == EngineState.PAUSED
    }

    // Check if processing
    fun isProcessing(): Boolean {
        return engineState.value == EngineState.PROCESSING
    }

    fun getError(): String? {
        return _error.value
    }
}
