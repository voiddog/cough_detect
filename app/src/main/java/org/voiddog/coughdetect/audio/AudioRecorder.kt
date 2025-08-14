package org.voiddog.coughdetect.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

class AudioRecorder(private val context: Context) {
    
    companion object {
        private const val TAG = "AudioRecorder"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE_MULTIPLIER = 4
    }
    
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val isRecording = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)
    
    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()
    
    private val _isRecordingState = MutableStateFlow(false)
    val isRecordingState: StateFlow<Boolean> = _isRecordingState.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    private var audioDataCallback: ((FloatArray, Float) -> Unit)? = null
    private val bufferSize: Int
    
    init {
        bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT) * BUFFER_SIZE_MULTIPLIER
        Log.d(TAG, "初始化AudioRecorder，缓冲区大小: $bufferSize")
    }
    
    fun setAudioDataCallback(callback: (FloatArray, Float) -> Unit) {
        audioDataCallback = callback
    }
    
    fun initialize(): Boolean {
        return try {
            if (!checkAudioPermission()) {
                _error.value = "缺少音频录制权限"
                return false
            }
            
            if (audioRecord != null) {
                Log.w(TAG, "AudioRecord已经初始化")
                return true
            }
            
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )
            
            val state = audioRecord?.state
            if (state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord初始化失败，状态: $state")
                _error.value = "AudioRecord初始化失败"
                audioRecord?.release()
                audioRecord = null
                return false
            }
            
            Log.i(TAG, "✅ AudioRecorder初始化成功")
            true
        } catch (e: Exception) {
            Log.e(TAG, "初始化AudioRecord时发生异常", e)
            _error.value = "初始化失败: ${e.message}"
            false
        }
    }
    
    fun start(): Boolean {
        return try {
            if (isRecording.get()) {
                Log.w(TAG, "录制已在进行中")
                return true
            }
            
            if (audioRecord == null && !initialize()) {
                return false
            }
            
            audioRecord?.let { record ->
                record.startRecording()
                val recordingState = record.recordingState
                
                if (recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    Log.e(TAG, "开始录制失败，状态: $recordingState")
                    _error.value = "开始录制失败"
                    return false
                }
                
                isRecording.set(true)
                isPaused.set(false)
                _isRecordingState.value = true
                
                startRecordingLoop()
                Log.i(TAG, "✅ 开始音频录制")
                true
            } ?: false
            
        } catch (e: Exception) {
            Log.e(TAG, "开始录制时发生异常", e)
            _error.value = "开始录制失败: ${e.message}"
            false
        }
    }
    
    fun stop() {
        try {
            if (!isRecording.get()) {
                Log.w(TAG, "录制未在进行中")
                return
            }
            
            isRecording.set(false)
            isPaused.set(false)
            _isRecordingState.value = false
            
            recordingJob?.cancel()
            recordingJob = null
            
            audioRecord?.let { record ->
                try {
                    if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        record.stop()
                    } else {
                        Log.d(TAG, "AudioRecord 不在录制状态: ${record.recordingState}")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "停止录制时发生异常", e)
                }
            }
            
            _audioLevel.value = 0f
            Log.i(TAG, "✅ 音频录制已停止")
            
        } catch (e: Exception) {
            Log.e(TAG, "停止录制时发生异常", e)
            _error.value = "停止录制失败: ${e.message}"
        }
    }
    
    fun pause() {
        if (isRecording.get()) {
            isPaused.set(true)
            Log.i(TAG, "⏸️ 音频录制已暂停")
        }
    }
    
    fun resume() {
        if (isRecording.get() && isPaused.get()) {
            isPaused.set(false)
            Log.i(TAG, "▶️ 音频录制已恢复")
        }
    }
    
    fun release() {
        try {
            stop()
            
            audioRecord?.let { record ->
                try {
                    record.release()
                } catch (e: Exception) {
                    Log.w(TAG, "释放AudioRecord时发生异常", e)
                }
            }
            audioRecord = null
            
            Log.i(TAG, "✅ AudioRecorder资源已释放")
            
        } catch (e: Exception) {
            Log.e(TAG, "释放资源时发生异常", e)
        }
    }
    
    fun getSampleRate(): Int = SAMPLE_RATE
    
    fun isRecording(): Boolean = isRecording.get()
    
    fun isPaused(): Boolean = isPaused.get()
    
    fun clearError() {
        _error.value = null
    }
    
    private fun startRecordingLoop() {
        recordingJob = CoroutineScope(Dispatchers.IO).launch {
            val buffer = ShortArray(bufferSize / 2) // 16-bit samples
            val floatBuffer = FloatArray(bufferSize / 2)
            
            while (isRecording.get() && isActive) {
                try {
                    if (isPaused.get()) {
                        delay(10)
                        continue
                    }
                    
                    val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    
                    if (bytesRead > 0) {
                        // Convert short to float and normalize
                        for (i in 0 until bytesRead) {
                            floatBuffer[i] = buffer[i] / 32768.0f
                        }
                        
                        // Calculate audio level (RMS)
                        val audioLevel = calculateRMS(floatBuffer, bytesRead)
                        _audioLevel.value = audioLevel
                        
                        // Callback with audio data
                        if (!isPaused.get()) {
                            audioDataCallback?.invoke(floatBuffer.copyOf(bytesRead), audioLevel)
                        }
                    } else if (bytesRead < 0) {
                        Log.e(TAG, "读取音频数据错误: $bytesRead")
                        _error.value = "读取音频数据失败"
                        break
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "录制循环中发生异常", e)
                    _error.value = "录制异常: ${e.message}"
                    break
                }
            }
        }
    }
    
    private fun calculateRMS(data: FloatArray, length: Int): Float {
        var sum = 0.0
        for (i in 0 until length) {
            sum += data[i] * data[i]
        }
        return sqrt(sum / length).toFloat()
    }
    
    private fun checkAudioPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
}
