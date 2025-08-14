package org.voiddog.coughdetect.repository

import android.content.Context
import android.util.Log
import org.voiddog.coughdetect.data.CoughRecord
import org.voiddog.coughdetect.data.CoughDetectDatabase
import org.voiddog.coughdetect.data.CoughRecordDao
import org.voiddog.coughdetect.engine.CoughDetectEngine
import org.voiddog.coughdetect.data.SettingsManager
import org.voiddog.coughdetect.plugin.AudioEventRecordPlugin
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
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
    private val settingsManager = SettingsManager.getInstance(context)
    
    // 插件列表
    private val plugins = mutableListOf<AudioEventRecordPlugin>()
    
    private val _detectionState = MutableStateFlow(DetectionState.IDLE)
    val detectionState: StateFlow<DetectionState> = _detectionState.asStateFlow()
    
    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    private val _lastDetectionResult = MutableStateFlow<CoughDetectionResult?>(null)
    val lastDetectionResult: StateFlow<CoughDetectionResult?> = _lastDetectionResult.asStateFlow()
    
    private var detectionJob: Job? = null
    private var currentAudioBuffer = mutableListOf<Float>()
    private var coughStartTime = 0L

    enum class DetectionState {
        IDLE, RECORDING, PAUSED, PROCESSING
    }

    data class CoughDetectionResult(
        val isCough: Boolean,
        val confidence: Float,
        val timestamp: Long = System.currentTimeMillis()
    )

    /**
     * 注册插件
     * @param plugin 要注册的插件
     */
    fun registerPlugin(plugin: AudioEventRecordPlugin) {
        plugins.add(plugin)
        plugin.initialize(context)
        Log.d(TAG, "插件已注册: ${plugin.name}")
    }
    
    /**
     * 初始化仓库
     */
    suspend fun initialize(): Boolean {
        return try {
            val startTime = System.currentTimeMillis()
            Log.i(TAG, "🚀 开始初始化咳嗽检测仓库...")

            // 检查数据库连接
            withContext(Dispatchers.IO) {
                try {
                    val recordCount = coughRecordDao.getRecordCount()
                    Log.d(TAG, "数据库连接正常，现有记录数: $recordCount")
                } catch (e: Exception) {
                    Log.w(TAG, "数据库连接检查失败", e)
                }
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
            withContext(Dispatchers.IO) {
                val audioDir = java.io.File(context.getExternalFilesDir(null), "cough_audio")
                if (!audioDir.exists()) {
                    val created = audioDir.mkdirs()
                    Log.d(TAG, "音频目录创建${if (created) "成功" else "失败"}: ${audioDir.absolutePath}")
                }
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
                event?.let {
                    // Store the audio data when a cough is detected
                    if (it.type == CoughDetectEngine.AudioEventType.COUGH_DETECTED && it.audioData != null) {
                        // We could store the audio data here if needed
                        // For now, we're just handling the event normally
                    }
                    handleAudioEvent(it)
                }
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

                // Use the audio data from the event for accurate processing
                if (event.audioData != null) {
                    Log.d(TAG, "🎤 收到包含音频数据的咳嗽事件，数据长度: ${event.audioData.size}")
                    handleAudioDetectionWithData(event.confidence, event.amplitude, event.timestamp, org.voiddog.coughdetect.data.AudioEventType.COUGH, event.audioData)
                } else {
                    Log.w(TAG, "⚠️ 咳嗽事件没有音频数据，使用振幅数据")
                    handleAudioDetection(event.confidence, event.amplitude, event.timestamp, org.voiddog.coughdetect.data.AudioEventType.COUGH)
                }
            }
            CoughDetectEngine.AudioEventType.SNORING_DETECTED -> {
                val timeStr = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())
                    .format(java.util.Date(event.timestamp))
                Log.i(TAG, "😴 Repository收到打鼾事件 - 时间: $timeStr, 置信度: ${String.format("%.3f", event.confidence)}, 振幅: ${String.format("%.3f", event.amplitude)}")

                // Use the audio data from the event for accurate processing
                if (event.audioData != null) {
                    Log.d(TAG, "😴 收到包含音频数据的打鼾事件，数据长度: ${event.audioData.size}")
                    handleAudioDetectionWithData(event.confidence, event.amplitude, event.timestamp, org.voiddog.coughdetect.data.AudioEventType.SNORING, event.audioData)
                } else {
                    Log.w(TAG, "⚠️ 打鼾事件没有音频数据，使用振幅数据")
                    handleAudioDetection(event.confidence, event.amplitude, event.timestamp, org.voiddog.coughdetect.data.AudioEventType.SNORING)
                }
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
            if (currentAudioBuffer.isNotEmpty()) {
                processPendingAudio()
            }

            Log.d(TAG, "Cough detection stopped")
        }
    }

    // 处理带有真实音频数据的事件 (调用合并后的函数)
    private suspend fun handleAudioDetectionWithData(
        confidence: Float,
        amplitude: Float,
        timestamp: Long,
        eventType: org.voiddog.coughdetect.data.AudioEventType,
        audioData: FloatArray
    ) {
        Log.d(TAG, "📊 处理${eventType.displayName}事件，音频数据长度: ${audioData.size}, 置信度: ${String.format("%.3f", confidence)}")

        // 直接使用引擎提供的音频数据保存记录
        saveAudioEventRecord(confidence, amplitude, timestamp, eventType, audioData)

        // Update the last detection result on the main thread
        withContext(Dispatchers.Main) {
            _lastDetectionResult.value = CoughDetectionResult(
                isCough = eventType == org.voiddog.coughdetect.data.AudioEventType.COUGH,
                confidence = confidence,
                timestamp = timestamp
            )
        }
    }

    // 处理不包含音频数据的事件 (调用合并后的函数)
    private suspend fun handleAudioDetection(
        confidence: Float,
        amplitude: Float,
        timestamp: Long,
        eventType: org.voiddog.coughdetect.data.AudioEventType
    ) {
        // Start new audio buffer if empty
        if (currentAudioBuffer.isEmpty()) {
            coughStartTime = timestamp
        }

        // Add amplitude to buffer (fallback when no real audio data)
        currentAudioBuffer.add(amplitude)

        // Check if audio event is too long
        val currentDuration = currentAudioBuffer.size * 100L // Assuming 100ms chunks

        if (currentDuration > MAX_COUGH_DURATION_MS) {
            // Save the audio event record (without audio data)
            saveAudioEventRecord(confidence, amplitude, timestamp, eventType)

            // Clear buffer for next event
            clearAudioBuffer()
        }

        // Update the last detection result on the main thread
        withContext(Dispatchers.Main) {
            _lastDetectionResult.value = CoughDetectionResult(
                isCough = eventType == org.voiddog.coughdetect.data.AudioEventType.COUGH,
                confidence = confidence,
                timestamp = timestamp
            )
        }
    }

    private suspend fun processPendingAudio() {
        // 在IO线程中处理 pending audio
        withContext(Dispatchers.IO) {
            if (currentAudioBuffer.isNotEmpty()) {
                val amplitude = currentAudioBuffer.maxOrNull() ?: 0.0f // 使用最大振幅而不是平均值
                val timestamp = coughStartTime

                // 使用最后一个检测结果的置信度，如果没有则使用默认值
                val confidence = _lastDetectionResult.value?.confidence ?: 0.7f
                val isCough = _lastDetectionResult.value?.isCough ?: true
                val eventType = if (isCough) org.voiddog.coughdetect.data.AudioEventType.COUGH else org.voiddog.coughdetect.data.AudioEventType.SNORING

                // 保存记录（不包含音频数据）
                // 注意：这里我们直接调用保存逻辑，而不是通过 withContext(Dispatchers.IO)，因为已经在IO线程中了
                try {
                    saveAudioEventRecordInternal(confidence, amplitude, timestamp, eventType)

                    // Update the last detection result on the main thread
                    withContext(Dispatchers.Main) {
                        _lastDetectionResult.value = CoughDetectionResult(
                            isCough = eventType == org.voiddog.coughdetect.data.AudioEventType.COUGH,
                            confidence = confidence,
                            timestamp = timestamp
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ 保存pending audio记录失败", e)
                    withContext(Dispatchers.Main) {
                        _error.value = "保存pending audio记录失败: ${e.message}"
                    }
                }
                clearAudioBuffer()
            }
        }
    }

    // 内部函数，用于在已知处于IO线程时保存记录
    private suspend fun saveAudioEventRecordInternal(
        confidence: Float,
        amplitude: Float,
        timestamp: Long,
        eventType: org.voiddog.coughdetect.data.AudioEventType,
        audioData: FloatArray? = null // 可空参数，如果为null则不保存音频文件
    ) {
        try {
            val saveStartTime = System.currentTimeMillis()
            var audioFilePath = ""
            var duration = 0L
            var audioSamples = 0

            // 如果提供了音频数据，则保存音频文件
            if (audioData != null) {
                // Generate filename with timestamp
                val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault())
                val eventTypeName = eventType.name.lowercase()
                val filename = "${eventTypeName}_${dateFormat.format(Date(timestamp))}"

                // Create audio file path using internal storage for better compatibility
                val audioDir = File(context.filesDir, "audio_events")
                if (!audioDir.exists()) {
                    audioDir.mkdirs()
                }
                audioFilePath = "${audioDir.absolutePath}/$filename.wav"

                // 在保存新文件之前检查磁盘空间并清理旧文件
                manageDiskSpace(audioDir)

                // Save real audio data to file
                val saved = saveAudioDataToFile(audioFilePath, audioData)
                if (!saved) {
                    Log.e(TAG, "❌ 保存音频文件失败: $audioFilePath")
                    // 注意：这里不能直接更新 _error.value，因为可能不在主线程
                    throw Exception("保存音频文件失败")
                }

                // Calculate duration based on sample rate (assuming 16kHz)
                val sampleRate = 16000 // Hz
                duration = (audioData.size * 1000L) / sampleRate // 转换为毫秒
                audioSamples = audioData.size

                Log.d(TAG, "✅ 音频文件保存成功: $filename, 样本数: $audioSamples, 时长: ${duration}ms")
            } else {
                // 如果没有音频数据，使用缓冲区大小估算时长（后备方案）
                duration = currentAudioBuffer.size * 100L // Assuming 100ms chunks
                Log.w(TAG, "⚠️ ${eventType.displayName}事件没有音频数据，只保存数据库记录，不创建音频文件")
            }

            // 创建基本的数据库记录
            val baseRecord = CoughRecord(
                timestamp = timestamp,
                audioFilePath = audioFilePath, // 可能为空字符串
                duration = duration,
                confidence = confidence,
                amplitude = amplitude,
                eventType = eventType.name,
                createdAt = Date(timestamp)
            )

            // 使用插件处理记录，获取扩展数据
            val extensionData = processRecordWithPlugins(baseRecord)

            // 创建包含扩展数据的最终记录
            val audioRecord = baseRecord.copy(extension = extensionData)

            // Save to database
            val recordId = coughRecordDao.insertRecord(audioRecord)
            val saveTime = System.currentTimeMillis() - saveStartTime

            // 获取当前总记录数
            val totalRecords = coughRecordDao.getRecordCount()

            if (audioData != null) {
                Log.i(TAG, "💾 ${eventType.displayName}记录已保存 - ID: $recordId, 文件: ${audioFilePath.substringAfterLast("/")}, 置信度: ${String.format("%.3f", confidence)}, " +
                        "音频样本: $audioSamples, 时长: ${duration}ms, 保存耗时: ${saveTime}ms, 总记录数: $totalRecords")
            } else {
                Log.i(TAG, "💾 ${eventType.displayName}记录已保存(仅数据库) - ID: $recordId, 置信度: ${String.format("%.3f", confidence)}, " +
                        "时长: ${duration}ms(估算), 保存耗时: ${saveTime}ms, 总记录数: $totalRecords")
            }

            // 检查存储空间 (仅在保存了音频文件时检查)
            if (audioData != null) {
                val audioDir = File(context.filesDir, "audio_events")
                val freeSpace = audioDir.freeSpace / 1024 / 1024
                if (freeSpace < 100) {  // 小于100MB时警告
                    Log.w(TAG, "⚠️ 存储空间不足: ${freeSpace}MB")
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ 保存${eventType.displayName}记录失败", e)
            // 注意：这里不能直接更新 _error.value，因为可能不在主线程
            throw e
        }
    }

    /**
     * 使用插件处理记录，获取扩展数据
     * @param record 基本的音频事件记录
     * @return 包含扩展数据的 JSON 字符串
     */
    private fun processRecordWithPlugins(record: CoughRecord): String {
        try {
            // 如果没有插件，返回默认的空 JSON 对象
            if (plugins.isEmpty()) {
                return "{}"
            }

            // 创建一个 JSON 对象来存储所有插件的数据
            val extensionJson = mutableMapOf<String, Any>()

            // 遍历所有插件，收集它们的扩展数据
            for (plugin in plugins) {
                try {
                    val pluginData = plugin.processRecord(record)
                    // 将插件数据解析为 JSON 对象并添加到主对象中
                    // 这里为了简化，我们假设插件返回的是一个简单的 JSON 对象字符串
                    // 在实际应用中，你可能需要使用一个 JSON 解析库来合并对象
                    extensionJson[plugin.name] = pluginData
                } catch (e: Exception) {
                    Log.e(TAG, "插件 ${plugin.name} 处理记录时出错", e)
                }
            }

            // 将 map 转换为 JSON 字符串
            // 这里我们使用一个简单的实现，实际应用中应该使用专门的 JSON 库
            return convertMapToJson(extensionJson)
        } catch (e: Exception) {
            Log.e(TAG, "处理插件数据时出错", e)
            return "{}" // 返回默认的空 JSON 对象
        }
    }

    /**
     * 将 Map 转换为 JSON 字符串
     * 注意：这是一个简化的实现，实际应用中应该使用专门的 JSON 库如 Gson 或 kotlinx.serialization
     * @param map 要转换的 Map
     * @return JSON 字符串
     */
    private fun convertMapToJson(map: Map<String, Any>): String {
        if (map.isEmpty()) return "{}"

        val json = StringBuilder()
        json.append("{")

        val entries = map.entries
        for ((index, entry) in entries.withIndex()) {
            // 简化处理：假设所有值都是字符串
            json.append("\"${entry.key}\":\"${entry.value}\"")
            if (index < entries.size - 1) {
                json.append(",")
            }
        }

        json.append("}")
        return json.toString()
    }

    /**
     * 管理磁盘空间，确保音频文件的总大小不超过设定的限制
     */
    private suspend fun manageDiskSpace(audioDir: File) {
        try {
            val settings = settingsManager.getSettings()
            val maxSizeBytes = settings.maxAudioCacheSizeMB.toLong() * 1024 * 1024

            // 获取目录下所有wav文件
            val wavFiles = audioDir.listFiles { file -> file.extension.equals("wav", ignoreCase = true) }

            if (wavFiles != null) {
                // 计算当前总大小
                var currentSize = 0L
                val fileWithLastModified = mutableListOf<Pair<File, Long>>()

                for (file in wavFiles) {
                    currentSize += file.length()
                    fileWithLastModified.add(Pair(file, file.lastModified()))
                }

                Log.d(TAG, "当前音频缓存大小: ${currentSize / 1024 / 1024}MB, 限制大小: ${settings.maxAudioCacheSizeMB}MB")

                // 如果当前大小超过了限制，则删除最旧的文件
                if (currentSize > maxSizeBytes) {
                    // 按最后修改时间排序，最旧的在前面
                    fileWithLastModified.sortBy { it.second }

                    // 多删除一些空间
                    var sizeToDelete = currentSize - maxSizeBytes / 2
                    var deletedSize = 0L
                    val filesToDelete = mutableListOf<File>()

                    // 收集需要删除的文件
                    for ((file, _) in fileWithLastModified) {
                        if (deletedSize < sizeToDelete) {
                            filesToDelete.add(file)
                            deletedSize += file.length()
                        } else {
                            break
                        }
                    }

                    // 删除文件
                    var actuallyDeletedSize = 0L
                    val deletedFilePaths = mutableListOf<String>()
                    for (file in filesToDelete) {
                        val fileSize = file.length()
                        if (file.delete()) {
                            actuallyDeletedSize += fileSize
                            deletedFilePaths.add(file.absolutePath)
                            Log.d(TAG, "已删除旧音频文件: ${file.name}, 大小: ${fileSize / 1024}KB")
                        } else {
                            Log.w(TAG, "删除音频文件失败: ${file.absolutePath}")
                        }
                    }

                    // 更新数据库中对应记录的audioFilePath字段为空
                    if (deletedFilePaths.isNotEmpty()) {
                        val updatedCount = coughRecordDao.clearAudioFilePaths(deletedFilePaths)
                        Log.d(TAG, "已更新数据库中" + updatedCount + "条记录的audioFilePath字段为空")
                    }

                    Log.i(TAG, "磁盘空间管理完成: 计划删除${sizeToDelete / 1024 / 1024}MB, 实际删除${actuallyDeletedSize / 1024 / 1024}MB")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "磁盘空间管理失败", e)
        }
    }

    // 处理音频检测事件并保存记录（合并了原有的两个函数）
    private suspend fun saveAudioEventRecord(
        confidence: Float,
        amplitude: Float,
        timestamp: Long,
        eventType: org.voiddog.coughdetect.data.AudioEventType,
        audioData: FloatArray? = null // 可空参数，如果为null则不保存音频文件
    ) {
        // 在IO线程中执行耗时的文件和数据库操作
        withContext(Dispatchers.IO) {
            try {
                saveAudioEventRecordInternal(confidence, amplitude, timestamp, eventType, audioData)
            } catch (e: Exception) {
                Log.e(TAG, "❌ 保存${eventType.displayName}记录失败", e)
                withContext(Dispatchers.Main) {
                    _error.value = "保存${eventType.displayName}记录失败: ${e.message}"
                }
            }
        }
    }

    private fun saveAudioDataToFile(filePath: String, audioData: FloatArray): Boolean {
        return try {
            java.io.FileOutputStream(filePath).use { fos ->
                // Convert float array to 16-bit PCM
                val shortBuffer = ShortArray(audioData.size)
                for (i in audioData.indices) {
                    // Clamp the value between -1.0 and 1.0
                    val clampedValue = audioData[i].coerceIn(-1.0f, 1.0f)
                    // Convert to 16-bit PCM (-32768 to 32767)
                    shortBuffer[i] = (clampedValue * 32767).toInt().toShort()
                }

                // Write WAV header and audio data
                writeWavHeader(fos, shortBuffer.size * 2, 16000, 1, 16)

                // Write audio data
                for (sample in shortBuffer) {
                    fos.write(sample.toInt() and 0xFF)
                    fos.write((sample.toInt() ushr 8) and 0xFF)
                }
            }

            Log.d(TAG, "✅ 音频文件保存成功: $filePath, 大小: ${java.io.File(filePath).length()} bytes")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ 保存音频文件失败: $filePath", e)
            false
        }
    }

    private fun writeWavHeader(
        out: java.io.OutputStream,
        audioLength: Int,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ) {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8

        // RIFF header
        out.write("RIFF".toByteArray())
        writeInt(out, 36 + audioLength) // Chunk size
        out.write("WAVE".toByteArray())

        // Format chunk
        out.write("fmt ".toByteArray())
        writeInt(out, 16) // Subchunk1 size
        writeShort(out, 1.toShort()) // Audio format (1 = PCM)
        writeShort(out, channels.toShort()) // Number of channels
        writeInt(out, sampleRate) // Sample rate
        writeInt(out, byteRate) // Byte rate
        writeShort(out, blockAlign.toShort()) // Block align
        writeShort(out, bitsPerSample.toShort()) // Bits per sample

        // Data chunk
        out.write("data".toByteArray())
        writeInt(out, audioLength) // Data chunk size
    }

    private fun writeInt(out: java.io.OutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value ushr 8) and 0xFF)
        out.write((value ushr 16) and 0xFF)
        out.write((value ushr 24) and 0xFF)
    }

    private fun writeShort(out: java.io.OutputStream, value: Short) {
        out.write(value.toInt() and 0xFF)
        out.write((value.toInt() ushr 8) and 0xFF)
    }

    private fun clearAudioBuffer() {
        currentAudioBuffer.clear()
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
        // 在IO线程中执行耗时的文件和数据库操作
        withContext(Dispatchers.IO) {
            try {
                // Delete audio file only if path is not empty
                if (record.audioFilePath.isNotEmpty()) {
                    val audioFile = java.io.File(record.audioFilePath)
                    Log.d(TAG, "删除音频文件: ${record.audioFilePath}, 文件存在: ${audioFile.exists()}")
                    if (audioFile.exists()) {
                        val deleted = audioFile.delete()
                        Log.d(TAG, "音频文件删除${if (deleted) "成功" else "失败"}: ${record.audioFilePath}")
                    }
                } else {
                    Log.d(TAG, "记录 ${record.id} 没有关联的音频文件")
                }

                // Delete database record
                coughRecordDao.deleteRecord(record)

                Log.d(TAG, "Cough record deleted: ${record.id}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete cough record", e)
                withContext(Dispatchers.Main) {
                    _error.value = "删除记录失败: ${e.message}"
                }
            }
        }
    }

    suspend fun clearAllCoughRecords() {
        // 在IO线程中执行耗时的文件和数据库操作
        withContext(Dispatchers.IO) {
            try {
                // Get all records first
                val records = coughRecordDao.getAllRecords().first()

                // Delete audio files only if they exist
                records.forEach { record ->
                    if (record.audioFilePath.isNotEmpty()) {
                        val audioFile = java.io.File(record.audioFilePath)
                        Log.d(TAG, "删除音频文件: ${record.audioFilePath}, 文件存在: ${audioFile.exists()}")
                        if (audioFile.exists()) {
                            val deleted = audioFile.delete()
                            Log.d(TAG, "音频文件删除${if (deleted) "成功" else "失败"}: ${record.audioFilePath}")
                        }
                    } else {
                        Log.d(TAG, "记录 ${record.id} 没有关联的音频文件，跳过")
                    }
                }

                // Clear database
                coughRecordDao.deleteAllRecords()

                Log.d(TAG, "All cough records cleared")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear all records", e)
                withContext(Dispatchers.Main) {
                    _error.value = "清空记录失败: ${e.message}"
                }
            }
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
        clearAudioBuffer()
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
