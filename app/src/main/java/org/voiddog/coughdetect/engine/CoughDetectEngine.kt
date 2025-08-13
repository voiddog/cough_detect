package org.voiddog.coughdetect.engine

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class CoughDetectEngine(private val context: Context) {

    companion object {
        private const val TAG = "CoughDetectEngine"
        private var isLibraryLoaded = false

        // Load native library
        init {
            try {
                System.loadLibrary("coughdetect")
                isLibraryLoaded = true
                Log.d(TAG, "Native library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load native library - UnsatisfiedLinkError", e)
                isLibraryLoaded = false
            } catch (e: SecurityException) {
                Log.e(TAG, "Failed to load native library - SecurityException", e)
                isLibraryLoaded = false
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load native library - Unexpected exception", e)
                isLibraryLoaded = false
            }
        }

        fun isNativeLibraryLoaded(): Boolean {
            return isLibraryLoaded
        }

        private fun getAvailableNativeLibraries(): String {
            try {
                val field = ClassLoader::class.java.getDeclaredField("loadedLibraryNames")
                field.isAccessible = true
                val libraries = field.get(Thread.currentThread().contextClassLoader) as? MutableList<*>
                return libraries?.joinToString(", ") ?: "Unknown"
            } catch (e: Exception) {
                return "Unable to retrieve library list: ${e.message}"
            }
        }
    }

    // Native method declarations
    private external fun nativeCreate(): Long
    private external fun nativeDestroy(enginePtr: Long)
    private external fun nativeInitialize(enginePtr: Long, modelPath: String?): Boolean
    private external fun nativeStart(enginePtr: Long): Boolean
    private external fun nativeStop(enginePtr: Long)
    private external fun nativePause(enginePtr: Long)
    private external fun nativeResume(enginePtr: Long)
    private external fun nativeGetState(enginePtr: Long): Int
    private external fun nativeGetAudioLevel(enginePtr: Long): Float
    private external fun nativeGetSampleRate(enginePtr: Long): Int
    private external fun nativeIsReady(enginePtr: Long): Boolean
    private external fun nativeRelease(enginePtr: Long)

    // Native engine pointer
    private var nativeEnginePtr: Long = 0

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
        AUDIO_LEVEL_CHANGED(1),
        ERROR_OCCURRED(2)
    }

    // Audio event data
    data class AudioEvent(
        val type: AudioEventType,
        val confidence: Float,
        val amplitude: Float,
        val timestamp: Long
    )

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

    // Callback for audio events from native code
    fun onAudioEvent(type: Int, confidence: Int, amplitude: Float, timestamp: Long) {
        try {
            val eventType = AudioEventType.values().find { it.value == type }
            if (eventType != null) {
                val event = AudioEvent(
                    type = eventType,
                    confidence = confidence / 1000f, // Convert back from integer
                    amplitude = amplitude,
                    timestamp = timestamp
                )

                _lastAudioEvent.value = event

                when (eventType) {
                    AudioEventType.AUDIO_LEVEL_CHANGED -> {
                        _audioLevel.value = amplitude
                    }
                    AudioEventType.COUGH_DETECTED -> {
                        val currentTime = System.currentTimeMillis()
                        val timeStr = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())
                            .format(java.util.Date(currentTime))

                        Log.i(TAG, "🎯 咳嗽检测成功! 时间: $timeStr, 置信度: ${String.format("%.3f", event.confidence)}, " +
                                "振幅: ${String.format("%.3f", amplitude)}")

                        // 统计咳嗽检测频率
                        coughDetectionCount++
                        if (coughDetectionCount == 1) {
                            firstCoughTime = currentTime
                        }

                        if (coughDetectionCount % 5 == 0) {
                            val avgInterval = (currentTime - firstCoughTime) / (coughDetectionCount - 1)
                            Log.i(TAG, "咳嗽检测统计 - 总数: $coughDetectionCount, 平均间隔: ${avgInterval}ms")
                        }
                    }
                    AudioEventType.ERROR_OCCURRED -> {
                        _error.value = "引擎错误: 原生层发生错误"
                        Log.e(TAG, "❌ 原生引擎错误 - 时间戳: $timestamp, 振幅: $amplitude")
                    }
                }
            } else {
                Log.w(TAG, "未知的音频事件类型: $type")
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理音频事件时发生异常", e)
            _error.value = "事件处理异常: ${e.message}"
        }
    }

    // Initialize the engine
    fun initialize(modelPath: String? = null): Boolean {
        return try {
            val startTime = System.currentTimeMillis()
            Log.i(TAG, "🚀 开始初始化咳嗽检测引擎...")

            // 检查原生库是否已加载
            if (!isNativeLibraryLoaded()) {
                Log.e(TAG, "❌ 原生库未加载，无法初始化引擎")
                _error.value = "原生库未加载"
                return false
            }

            if (nativeEnginePtr == 0L) {
                nativeEnginePtr = nativeCreate()
                if (nativeEnginePtr == 0L) {
                    Log.e(TAG, "❌ 创建原生引擎失败")
                    _error.value = "创建原生引擎失败"
                    return false
                }
                Log.d(TAG, "✅ 原生引擎实例创建成功，指针: 0x${nativeEnginePtr.toString(16)}")
            }

            // Try to find model file if not provided
            val finalModelPath = modelPath ?: findModelFile()
            Log.i(TAG, "模型文件路径: ${finalModelPath ?: "未找到模型文件，将使用规则检测"}")

            val success = nativeInitialize(nativeEnginePtr, finalModelPath)
            val initTime = System.currentTimeMillis() - startTime

            if (success) {
                Log.i(TAG, "✅ 引擎初始化成功! 耗时: ${initTime}ms")
                updateEngineState()

                // 重置统计
                coughDetectionCount = 0
                firstCoughTime = 0L
            } else {
                Log.e(TAG, "❌ 引擎初始化失败，耗时: ${initTime}ms")
                _error.value = "引擎初始化失败"
            }
            success

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

            if (nativeEnginePtr == 0L) {
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

            val success = nativeStart(nativeEnginePtr)
            val startDuration = System.currentTimeMillis() - startTime

            if (success) {
                Log.i(TAG, "✅ 检测启动成功! 耗时: ${startDuration}ms")
                updateEngineState()
            } else {
                Log.e(TAG, "❌ 检测启动失败，耗时: ${startDuration}ms")
                _error.value = "启动检测失败"
            }
            success

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

            if (nativeEnginePtr != 0L) {
                val currentState = getState()
                Log.d(TAG, "停止前状态: ${currentState.name}")

                nativeStop(nativeEnginePtr)
                updateEngineState()

                Log.i(TAG, "✅ 检测已停止，新状态: ${getState().name}")

                // 输出检测统计
                if (coughDetectionCount > 0) {
                    val totalTime = System.currentTimeMillis() - firstCoughTime
                    val avgInterval = if (coughDetectionCount > 1) totalTime / (coughDetectionCount - 1) else 0
                    Log.i(TAG, "本次检测统计 - 总咳嗽数: $coughDetectionCount, 平均间隔: ${avgInterval}ms")
                }
            } else {
                Log.w(TAG, "⚠️ 引擎未初始化，无需停止")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 停止过程中发生异常", e)
            _error.value = "停止失败: ${e.message}"
        }
    }

    // Pause detection
    fun pause() {
        try {
            if (nativeEnginePtr != 0L) {
                nativePause(nativeEnginePtr)
                updateEngineState()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during pause", e)
            _error.value = "暂停失败: ${e.message}"
        }
    }

    // Resume detection
    fun resume() {
        try {
            if (nativeEnginePtr != 0L) {
                nativeResume(nativeEnginePtr)
                updateEngineState()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during resume", e)
            _error.value = "恢复失败: ${e.message}"
        }
    }

    // Get current engine state
    fun getState(): EngineState {
        return try {
            if (nativeEnginePtr != 0L) {
                val stateValue = nativeGetState(nativeEnginePtr)
                EngineState.values().find { it.value == stateValue } ?: EngineState.IDLE
            } else {
                EngineState.IDLE
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception getting state", e)
            EngineState.IDLE
        }
    }

    // Get current audio level
    fun getAudioLevel(): Float {
        return try {
            if (nativeEnginePtr != 0L) {
                nativeGetAudioLevel(nativeEnginePtr)
            } else {
                0f
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception getting audio level", e)
            0f
        }
    }

    // Get sample rate
    fun getSampleRate(): Int {
        return try {
            if (nativeEnginePtr != 0L) {
                nativeGetSampleRate(nativeEnginePtr)
            } else {
                16000
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception getting sample rate", e)
            16000
        }
    }

    // Check if engine is ready
    fun isReady(): Boolean {
        return try {
            nativeEnginePtr != 0L && nativeIsReady(nativeEnginePtr)
        } catch (e: Exception) {
            Log.e(TAG, "Exception checking ready state", e)
            false
        }
    }

    // Update engine state
    private fun updateEngineState() {
        _engineState.value = getState()
    }

    // Find model file in assets
    private fun findModelFile(): String? {
        return try {
            val modelFile = File(context.filesDir, "cough_detection_model.tflite")
            if (modelFile.exists()) {
                modelFile.absolutePath
            } else {
                // Try to copy from assets
                context.assets.open("cough_detection_model.tflite").use { input ->
                    modelFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                modelFile.absolutePath
            }
        } catch (e: Exception) {
            Log.w(TAG, "Model file not found in assets", e)
            null
        }
    }

    // Clear error
    fun clearError() {
        _error.value = null
    }

    // Release resources
    fun release() {
        try {
            if (nativeEnginePtr != 0L) {
                nativeRelease(nativeEnginePtr)
                nativeDestroy(nativeEnginePtr)
                nativeEnginePtr = 0L
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during release", e)
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
