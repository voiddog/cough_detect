package org.voiddog.coughdetect.ml

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.support.common.FileUtil
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp

class TensorFlowLiteDetector(private val context: Context) {
    
    companion object {
        private const val TAG = "TFLiteDetector"
        private const val MODEL_FILENAME = "cough_detection_model.tflite"
        private const val INPUT_SIZE = 16000 // 1 second at 16kHz
        private const val OUTPUT_SIZE = 2 // [non-cough, cough]
        private const val COUGH_THRESHOLD = 0.5f
    }
    
    private var interpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null
    private var isModelLoaded = false
    
    data class DetectionResult(
        val isCough: Boolean,
        val confidence: Float,
        val coughProbability: Float,
        val nonCoughProbability: Float
    )
    
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "🚀 开始初始化TensorFlow Lite检测器...")
            
            val modelFile = loadModelFile()
            if (modelFile == null) {
                Log.w(TAG, "⚠️ 未找到模型文件，将使用规则检测")
                return@withContext false
            }
            
            val modelBuffer = FileUtil.loadMappedFile(context, modelFile.absolutePath)
            
            // Configure interpreter options
            val options = Interpreter.Options().apply {
                setNumThreads(4)
                // setUseXNNPack is not available in this version
            }
            
            // Try to use GPU if available
            val compatibilityList = CompatibilityList()
            if (compatibilityList.isDelegateSupportedOnThisDevice) {
                try {
                    gpuDelegate = GpuDelegate()
                    options.addDelegate(gpuDelegate)
                    Log.i(TAG, "✅ 启用GPU加速")
                } catch (e: Exception) {
                    Log.w(TAG, "GPU初始化失败，使用CPU: ${e.message}")
                }
            } else {
                Log.i(TAG, "ℹ️ GPU不支持，使用CPU")
            }
            
            interpreter = Interpreter(modelBuffer, options)
            isModelLoaded = true
            
            Log.i(TAG, "✅ TensorFlow Lite检测器初始化成功")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 初始化TensorFlow Lite检测器失败", e)
            cleanup()
            false
        }
    }
    
    suspend fun detectCough(audioData: FloatArray): DetectionResult = withContext(Dispatchers.Default) {
        if (!isModelLoaded || interpreter == null) {
            Log.w(TAG, "模型未加载，使用规则检测")
            return@withContext performRuleBasedDetection(audioData)
        }
        
        try {
            // Prepare input buffer
            val inputBuffer = ByteBuffer.allocateDirect(INPUT_SIZE * 4).apply {
                order(ByteOrder.nativeOrder())
                rewind()
            }
            
            // Normalize and pad/truncate audio data to INPUT_SIZE
            val processedData = preprocessAudioData(audioData)
            for (value in processedData) {
                inputBuffer.putFloat(value)
            }
            inputBuffer.rewind()
            
            // Prepare output buffer
            val outputBuffer = ByteBuffer.allocateDirect(OUTPUT_SIZE * 4).apply {
                order(ByteOrder.nativeOrder())
            }
            
            // Run inference
            interpreter?.run(inputBuffer, outputBuffer)
            
            // Parse output
            outputBuffer.rewind()
            val nonCoughProb = outputBuffer.float
            val coughProb = outputBuffer.float
            
            // Apply softmax normalization
            val expNonCough = exp(nonCoughProb)
            val expCough = exp(coughProb)
            val sumExp = expNonCough + expCough
            
            val normalizedNonCough = expNonCough / sumExp
            val normalizedCough = expCough / sumExp
            
            val isCough = normalizedCough > COUGH_THRESHOLD
            val confidence = if (isCough) normalizedCough else normalizedNonCough
            
            Log.d(TAG, "TFLite检测结果 - 咳嗽概率: %.3f, 非咳嗽概率: %.3f, 判断: %s".format(
                normalizedCough, normalizedNonCough, if (isCough) "咳嗽" else "非咳嗽"
            ))
            
            DetectionResult(
                isCough = isCough,
                confidence = confidence,
                coughProbability = normalizedCough,
                nonCoughProbability = normalizedNonCough
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ TensorFlow Lite推理失败，回退到规则检测", e)
            performRuleBasedDetection(audioData)
        }
    }
    
    private fun preprocessAudioData(audioData: FloatArray): FloatArray {
        val processedData = FloatArray(INPUT_SIZE)
        
        when {
            audioData.size == INPUT_SIZE -> {
                // Perfect size, just copy
                audioData.copyInto(processedData)
            }
            audioData.size > INPUT_SIZE -> {
                // Truncate to INPUT_SIZE
                audioData.copyInto(processedData, 0, 0, INPUT_SIZE)
            }
            else -> {
                // Pad with zeros
                audioData.copyInto(processedData, 0, 0, audioData.size)
                // Remaining elements are already 0.0f
            }
        }
        
        // Normalize to [-1, 1] range if needed
        val maxAbs = processedData.maxOfOrNull { kotlin.math.abs(it) } ?: 1.0f
        if (maxAbs > 1.0f) {
            for (i in processedData.indices) {
                processedData[i] /= maxAbs
            }
        }
        
        return processedData
    }
    
    private fun performRuleBasedDetection(audioData: FloatArray): DetectionResult {
        // Simple rule-based detection based on audio characteristics
        val rms = calculateRMS(audioData)
        val zeroCrossingRate = calculateZeroCrossingRate(audioData)
        val spectralCentroid = calculateSpectralCentroid(audioData)
        
        // Cough detection heuristics
        val isCough = when {
            rms > 0.1f && zeroCrossingRate > 0.05f -> true // High energy + rapid changes
            rms > 0.05f && spectralCentroid > 2000f -> true // Moderate energy + high frequency content
            else -> false
        }
        
        val confidence = when {
            isCough -> kotlin.math.min(rms * 2f + zeroCrossingRate * 5f, 1.0f)
            else -> kotlin.math.max(1.0f - rms * 2f, 0.0f)
        }
        
        Log.d(TAG, "规则检测结果 - RMS: %.3f, ZCR: %.3f, SC: %.1f, 判断: %s".format(
            rms, zeroCrossingRate, spectralCentroid, if (isCough) "咳嗽" else "非咳嗽"
        ))
        
        return DetectionResult(
            isCough = isCough,
            confidence = confidence,
            coughProbability = if (isCough) confidence else 1.0f - confidence,
            nonCoughProbability = if (isCough) 1.0f - confidence else confidence
        )
    }
    
    private fun calculateRMS(data: FloatArray): Float {
        var sum = 0.0
        for (value in data) {
            sum += value * value
        }
        return kotlin.math.sqrt(sum / data.size).toFloat()
    }
    
    private fun calculateZeroCrossingRate(data: FloatArray): Float {
        var crossings = 0
        for (i in 1 until data.size) {
            if ((data[i] >= 0) != (data[i - 1] >= 0)) {
                crossings++
            }
        }
        return crossings.toFloat() / data.size
    }
    
    private fun calculateSpectralCentroid(data: FloatArray): Float {
        // Simplified spectral centroid calculation
        // In a real implementation, you would use FFT
        var weightedSum = 0.0
        var magnitudeSum = 0.0
        
        for (i in data.indices) {
            val magnitude = kotlin.math.abs(data[i]).toDouble()
            weightedSum += i * magnitude
            magnitudeSum += magnitude
        }
        
        return if (magnitudeSum > 0) {
            (weightedSum / magnitudeSum).toFloat()
        } else {
            0f
        }
    }
    
    private fun loadModelFile(): File? {
        return try {
            // First try to load from internal storage
            val internalFile = File(context.filesDir, MODEL_FILENAME)
            if (internalFile.exists()) {
                Log.i(TAG, "找到内部存储中的模型文件: ${internalFile.absolutePath}")
                return internalFile
            }
            
            // Try to copy from assets
            context.assets.open(MODEL_FILENAME).use { inputStream ->
                internalFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
                Log.i(TAG, "从assets复制模型文件到: ${internalFile.absolutePath}")
                internalFile
            }
            
        } catch (e: Exception) {
            Log.w(TAG, "无法加载模型文件: ${e.message}")
            null
        }
    }
    
    fun isModelLoaded(): Boolean = isModelLoaded
    
    fun cleanup() {
        try {
            interpreter?.close()
            interpreter = null
            
            gpuDelegate?.close()
            gpuDelegate = null
            
            isModelLoaded = false
            Log.i(TAG, "✅ TensorFlow Lite资源已清理")
            
        } catch (e: Exception) {
            Log.e(TAG, "清理资源时发生异常", e)
        }
    }
}
