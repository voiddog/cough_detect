#include "include/tensorflow_wrapper.h"
#include <android/log.h>
#include <cmath>
#include <algorithm>
#include <complex>
#include <numeric>
#include <chrono>
#include <string>

#define LOG_TAG "TensorFlowWrapper"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace coughdetect {

class TensorFlowWrapper::Impl {
public:
    Impl() : isModelLoaded_(false), useGpuDelegate_(false) {
#ifdef TENSORFLOW_LITE_AVAILABLE
        model_ = nullptr;
        interpreter_ = nullptr;
        gpuDelegate_ = nullptr;
#endif
        LOGI("TensorFlowWrapper::Impl created");
    }
    
    ~Impl() {
        release();
        LOGI("TensorFlowWrapper::Impl destroyed");
    }
    
    bool initialize(const std::string& modelPath) {
        try {
            if (modelPath.empty()) {
                LOGI("No model path provided, using rule-based detection");
                isModelLoaded_ = false;
                return true;
            }
            
#ifdef TENSORFLOW_LITE_AVAILABLE
            // Load TensorFlow Lite model
            LOGI("Loading TensorFlow Lite model from: %s", modelPath.c_str());
            
            // 加载模型
            model_ = tflite::FlatBufferModel::BuildFromFile(modelPath.c_str());
            if (!model_) {
                LOGE("Failed to load model from: %s", modelPath.c_str());
                // 回退到基于规则的检测
                isModelLoaded_ = false;
                return true;
            }
            
            // 创建解释器
            tflite::ops::builtin::BuiltinOpResolver resolver;
            tflite::InterpreterBuilder builder(*model_, resolver);
            builder(&interpreter_);
            
            if (!interpreter_) {
                LOGE("Failed to create interpreter");
                model_.reset();
                isModelLoaded_ = false;
                return true;
            }
            
            // 设置线程数
            interpreter_->SetNumThreads(4);
            
            // 尝试使用GPU委托（可选）
            if (initializeGpuDelegate()) {
                LOGI("GPU delegate initialized successfully");
                useGpuDelegate_ = true;
            } else {
                LOGI("GPU delegate not available, using CPU");
                useGpuDelegate_ = false;
            }
            
            // 分配张量
            TfLiteStatus status = interpreter_->AllocateTensors();
            if (status != kTfLiteOk) {
                LOGE("Failed to allocate tensors");
                release();
                isModelLoaded_ = false;
                return true;
            }
            
            // 获取输入和输出张量信息
            printModelInfo();
            
            isModelLoaded_ = true;
            LOGI("TensorFlow Lite model loaded successfully");
            return true;
#else
            // TensorFlow Lite not available, use rule-based detection
            LOGI("TensorFlow Lite not available, using rule-based detection");
            isModelLoaded_ = false;
            return true;
#endif
        } catch (const std::exception& e) {
            LOGE("Exception during TensorFlow initialization: %s", e.what());
            // Fallback to rule-based detection
            isModelLoaded_ = false;
            return true;
        }
    }
    
    DetectionResult detectCough(const std::vector<float>& audioData) {
#ifdef TENSORFLOW_LITE_AVAILABLE
        if (isModelLoaded_ && model_ && interpreter_) {
            return detectCoughWithModel(audioData);
        } else {
            // Use rule-based detection as fallback
            return detectCoughRuleBased(audioData);
        }
#else
        // TensorFlow Lite not available, use rule-based detection
        return detectCoughRuleBased(audioData);
#endif
    }
    
    bool isModelLoaded() const {
        return isModelLoaded_;
    }
    
    void release() {
#ifdef TENSORFLOW_LITE_AVAILABLE
        if (gpuDelegate_) {
            TfLiteGpuDelegateV2Delete(gpuDelegate_);
            gpuDelegate_ = nullptr;
        }
        
        interpreter_.reset();
        model_.reset();
#endif
        isModelLoaded_ = false;
        useGpuDelegate_ = false;
        LOGI("TensorFlow wrapper released");
    }
    
private:
#ifdef TENSORFLOW_LITE_AVAILABLE
    bool initializeGpuDelegate() {
        // 创建GPU委托选项
        TfLiteGpuDelegateOptionsV2 options = TfLiteGpuDelegateOptionsV2Default();
        options.inference_preference = TFLITE_GPU_INFERENCE_PREFERENCE_FAST_SINGLE_ANSWER;
        options.inference_priority1 = TFLITE_GPU_INFERENCE_PRIORITY_MIN_LATENCY;
        options.inference_priority2 = TFLITE_GPU_INFERENCE_PRIORITY_AUTO;
        options.inference_priority3 = TFLITE_GPU_INFERENCE_PRIORITY_AUTO;
        
        gpuDelegate_ = TfLiteGpuDelegateV2Create(&options);
        if (!gpuDelegate_) {
            return false;
        }
        
        // 应用GPU委托
        TfLiteStatus status = interpreter_->ModifyGraphWithDelegate(gpuDelegate_);
        if (status != kTfLiteOk) {
            TfLiteGpuDelegateV2Delete(gpuDelegate_);
            gpuDelegate_ = nullptr;
            return false;
        }
        
        return true;
    }
    
    void printModelInfo() {
        if (!interpreter_) return;
        
        // 输入张量信息
        const std::vector<int>& inputs = interpreter_->inputs();
        LOGI("Model has %zu input(s):", inputs.size());
        
        for (size_t i = 0; i < inputs.size(); ++i) {
            TfLiteTensor* tensor = interpreter_->tensor(inputs[i]);
            LOGI("  Input %zu: %s", i, tensor->name ? tensor->name : "unnamed");
            LOGI("    Type: %s", TfLiteTypeGetName(tensor->type));
            LOGI("    Shape: [");
            for (int j = 0; j < tensor->dims->size; ++j) {
                LOGI("      %d", tensor->dims->data[j]);
            }
            LOGI("    ]");
        }
        
        // 输出张量信息
        const std::vector<int>& outputs = interpreter_->outputs();
        LOGI("Model has %zu output(s):", outputs.size());
        
        for (size_t i = 0; i < outputs.size(); ++i) {
            TfLiteTensor* tensor = interpreter_->tensor(outputs[i]);
            LOGI("  Output %zu: %s", i, tensor->name ? tensor->name : "unnamed");
            LOGI("    Type: %s", TfLiteTypeGetName(tensor->type));
            LOGI("    Shape: [");
            for (int j = 0; j < tensor->dims->size; ++j) {
                LOGI("      %d", tensor->dims->data[j]);
            }
            LOGI("    ]");
        }
    }
    
    DetectionResult detectCoughWithModel(const std::vector<float>& audioData) {
        auto startTime = std::chrono::high_resolution_clock::now();
        
        try {
            // 预处理音频数据
            std::vector<float> processedData = preprocessAudioForModel(audioData);
            
            // 获取输入张量
            int input_tensor_index = interpreter_->inputs()[0];
            TfLiteTensor* input_tensor = interpreter_->tensor(input_tensor_index);
            
            if (!input_tensor) {
                LOGE("Failed to get input tensor");
                return detectCoughRuleBased(audioData);
            }
            
            // 复制数据到输入张量
            if (input_tensor->type == kTfLiteFloat32) {
                float* input_data = interpreter_->typed_tensor<float>(input_tensor_index);
                if (!input_data) {
                    LOGE("Failed to get input tensor data pointer");
                    return detectCoughRuleBased(audioData);
                }
                
                // 确保数据大小匹配
                size_t expected_size = 1;
                for (int i = 0; i < input_tensor->dims->size; ++i) {
                    expected_size *= input_tensor->dims->data[i];
                }
                
                if (processedData.size() != expected_size) {
                    LOGE("Input data size mismatch: expected %zu, got %zu", 
                         expected_size, processedData.size());
                    return detectCoughRuleBased(audioData);
                }
                
                std::copy(processedData.begin(), processedData.end(), input_data);
            } else {
                LOGE("Unsupported input tensor type: %s", TfLiteTypeGetName(input_tensor->type));
                return detectCoughRuleBased(audioData);
            }
            
            // 运行推理
            TfLiteStatus status = interpreter_->Invoke();
            if (status != kTfLiteOk) {
                LOGE("Failed to invoke interpreter, status: %d", status);
                return detectCoughRuleBased(audioData);
            }
            
            // 获取输出张量
            int output_tensor_index = interpreter_->outputs()[0];
            TfLiteTensor* output_tensor = interpreter_->tensor(output_tensor_index);
            
            if (!output_tensor) {
                LOGE("Failed to get output tensor");
                return detectCoughRuleBased(audioData);
            }
            
            // 后处理结果
            DetectionResult result = postprocessModelOutput(output_tensor);
            
            auto endTime = std::chrono::high_resolution_clock::now();
            auto duration = std::chrono::duration_cast<std::chrono::microseconds>(endTime - startTime);
            
            LOGI("TensorFlow inference completed in %.2fms - Cough: %s, Confidence: %.3f", 
                 duration.count() / 1000.0f, result.isCough ? "YES" : "NO", result.confidence);
            
            return result;
            
        } catch (const std::exception& e) {
            LOGE("Exception during TensorFlow inference: %s", e.what());
            return detectCoughRuleBased(audioData);
        }
    }
    
    std::vector<float> preprocessAudioForModel(const std::vector<float>& audioData) {
        // 获取输入张量的形状
        int input_tensor_index = interpreter_->inputs()[0];
        TfLiteTensor* input_tensor = interpreter_->tensor(input_tensor_index);
        
        if (!input_tensor) {
            // 默认使用16000样本（1秒16kHz）
            const int targetSize = 16000;
            std::vector<float> processedData(targetSize, 0.0f);
            
            size_t copySize = std::min(audioData.size(), static_cast<size_t>(targetSize));
            for (size_t i = 0; i < copySize; ++i) {
                processedData[i] = std::clamp(audioData[i], -1.0f, 1.0f);
            }
            
            return processedData;
        }
        
        // 计算预期的输入大小
        size_t expectedSize = 1;
        for (int i = 0; i < input_tensor->dims->size; ++i) {
            expectedSize *= input_tensor->dims->data[i];
        }
        
        std::vector<float> processedData(expectedSize, 0.0f);
        
        // 复制和归一化音频数据
        size_t copySize = std::min(audioData.size(), expectedSize);
        for (size_t i = 0; i < copySize; ++i) {
            // 归一化到[-1, 1]范围
            processedData[i] = std::clamp(audioData[i], -1.0f, 1.0f);
        }
        
        return processedData;
    }
    
    DetectionResult postprocessModelOutput(TfLiteTensor* output_tensor) {
        if (!output_tensor) {
            return DetectionResult{false, 0.0f, {}, {}};
        }
        
        if (output_tensor->type != kTfLiteFloat32) {
            LOGE("Unsupported output tensor type: %s", TfLiteTypeGetName(output_tensor->type));
            return DetectionResult{false, 0.0f, {}, {}};
        }
        
        float* output_data = reinterpret_cast<float*>(output_tensor->data.raw);
        size_t output_size = 1;
        
        for (int i = 0; i < output_tensor->dims->size; ++i) {
            output_size *= output_tensor->dims->data[i];
        }
        
        // 对于YAMNet模型（521个类别）
        if (output_size == 521) {
            return postprocessYAMNetOutput(output_data, output_size);
        }
        // 对于二分类模型（2个类别：非咳嗽，咳嗽）
        else if (output_size == 2) {
            return postprocessBinaryOutput(output_data, output_size);
        }
        // 其他情况，尝试通用处理
        else {
            return postprocessGenericOutput(output_data, output_size);
        }
    }
    
    DetectionResult postprocessYAMNetOutput(const float* output_data, size_t output_size) {
        // YAMNet咳嗽相关类别索引（这些需要根据实际的类别映射调整）
        std::vector<int> coughClassIndices = {
            CoughClasses::COUGH,
            CoughClasses::THROAT_CLEARING,
            CoughClasses::SNEEZE,
            CoughClasses::SNIFF,
            CoughClasses::BURP,
            CoughClasses::BELCH
        };
        
        float maxCoughProbability = 0.0f;
        
        // 找到咳嗽相关类别中的最大概率
        for (int classIdx : coughClassIndices) {
            if (classIdx < static_cast<int>(output_size)) {
                maxCoughProbability = std::max(maxCoughProbability, output_data[classIdx]);
            }
        }
        
        // 创建类别概率向量（显示前10个类别）
        std::vector<float> classProbs(std::min(10, static_cast<int>(output_size)));
        for (size_t i = 0; i < classProbs.size(); ++i) {
            classProbs[i] = output_data[i];
        }
        
        // 咳嗽检测阈值
        bool isCough = maxCoughProbability > 0.3f; // 较低的阈值以获得更好的灵敏度
        
        return DetectionResult{isCough, maxCoughProbability, {}, classProbs};
    }
    
    DetectionResult postprocessBinaryOutput(const float* output_data, size_t output_size) {
        // 对于二分类：[非咳嗽概率, 咳嗽概率]
        float coughProbability = output_data[1];
        bool isCough = coughProbability > 0.5f;
        
        std::vector<float> classProbs(output_size);
        for (size_t i = 0; i < output_size; ++i) {
            classProbs[i] = output_data[i];
        }
        
        return DetectionResult{isCough, coughProbability, {}, classProbs};
    }
    
    DetectionResult postprocessGenericOutput(const float* output_data, size_t output_size) {
        // 通用处理：找到最大概率
        float maxProbability = *std::max_element(output_data, output_data + output_size);
        bool isCough = maxProbability > 0.5f;
        
        std::vector<float> classProbs(std::min(10, static_cast<int>(output_size)));
        for (size_t i = 0; i < classProbs.size(); ++i) {
            classProbs[i] = output_data[i];
        }
        
        return DetectionResult{isCough, maxProbability, {}, classProbs};
    }
#endif
    
    DetectionResult detectCoughRuleBased(const std::vector<float>& audioData) {
        // Enhanced rule-based cough detection based on YAMNet principles
        auto startTime = std::chrono::high_resolution_clock::now();
        
        // 检查输入数据有效性
        if (audioData.empty()) {
            LOGE("Empty audio data received for cough detection");
            return DetectionResult{false, 0.0f, {}, {}};
        }
        
        if (audioData.size() < 100) {  // 最少100个样本
            LOGI("Audio data too short for reliable detection: %zu samples", audioData.size());
        }
        
        float amplitude = calculateAmplitude(audioData);
        float zeroCrossingRate = calculateZeroCrossingRate(audioData);
        float spectralCentroid = calculateSpectralCentroid(audioData);
        float spectralRolloff = calculateSpectralRolloff(audioData);
        float mfcc = calculateMFCC(audioData);
        
        // 计算处理时间
        auto featureTime = std::chrono::high_resolution_clock::now();
        auto featureDuration = std::chrono::duration_cast<std::chrono::microseconds>(featureTime - startTime);
        
        // Cough characteristics based on YAMNet analysis
        bool hasHighAmplitude = amplitude > 0.08f;
        bool hasModerateZCR = zeroCrossingRate > 0.03f && zeroCrossingRate < 0.25f;
        bool hasHighFrequencyContent = spectralCentroid > 800.0f && spectralCentroid < 3000.0f;
        bool hasAppropriateRolloff = spectralRolloff > 2000.0f && spectralRolloff < 6000.0f;
        bool hasCoughMFCC = mfcc > 0.1f;
        
        // 详细特征日志
        LOGI("Feature extraction (%.2fms) - Amp: %.4f [%s], ZCR: %.4f [%s], "
             "Centroid: %.1fHz [%s], Rolloff: %.1fHz [%s], MFCC: %.4f [%s]", 
             featureDuration.count() / 1000.0f,
             amplitude, hasHighAmplitude ? "✓" : "✗",
             zeroCrossingRate, hasModerateZCR ? "✓" : "✗",
             spectralCentroid, hasHighFrequencyContent ? "✓" : "✗",
             spectralRolloff, hasAppropriateRolloff ? "✓" : "✗",
             mfcc, hasCoughMFCC ? "✓" : "✗");
        
        // Calculate confidence using weighted scoring
        float confidence = 0.0f;
        int score = 0;
        std::string scoreBreakdown = "Score breakdown: ";
        
        if (hasHighAmplitude) {
            confidence += 0.25f;
            score++;
            scoreBreakdown += "Amp(+0.25) ";
        }
        if (hasModerateZCR) {
            confidence += 0.20f;
            score++;
            scoreBreakdown += "ZCR(+0.20) ";
        }
        if (hasHighFrequencyContent) {
            confidence += 0.25f;
            score++;
            scoreBreakdown += "Freq(+0.25) ";
        }
        if (hasAppropriateRolloff) {
            confidence += 0.15f;
            score++;
            scoreBreakdown += "Rolloff(+0.15) ";
        }
        if (hasCoughMFCC) {
            confidence += 0.15f;
            score++;
            scoreBreakdown += "MFCC(+0.15) ";
        }
        
        // Bonus for multiple indicators
        if (score >= 3) {
            confidence += 0.1f;
            scoreBreakdown += "Multi3(+0.1) ";
        }
        if (score >= 4) {
            confidence += 0.1f;
            scoreBreakdown += "Multi4(+0.1) ";
        }
        
        confidence = std::min(1.0f, confidence);
        bool isCough = confidence > 0.6f;
        
        // Create mock class probabilities for compatibility
        std::vector<float> classProbs(6, 0.0f);
        if (isCough) {
            classProbs[CoughClasses::COUGH] = confidence;
            classProbs[CoughClasses::THROAT_CLEARING] = confidence * 0.7f;
            classProbs[CoughClasses::SNEEZE] = confidence * 0.3f;
        }
        
        auto endTime = std::chrono::high_resolution_clock::now();
        auto totalDuration = std::chrono::duration_cast<std::chrono::microseconds>(endTime - startTime);
        
        // 输出检测结果和性能统计
        if (isCough) {
            LOGI("🎯 COUGH DETECTED! Confidence: %.3f, Score: %d/5, Processing: %.2fms", 
                 confidence, score, totalDuration.count() / 1000.0f);
            LOGI("%s", scoreBreakdown.c_str());
        } else {
            // 只在调试模式下输出非咳嗽检测结果
            static int nonCoughCount = 0;
            nonCoughCount++;
            if (nonCoughCount % 50 == 0) {  // 每50次记录一次
                LOGI("No cough detected (count: %d) - Confidence: %.3f, Score: %d/5", 
                     nonCoughCount, confidence, score);
            }
        }
        
        return DetectionResult{isCough, confidence, {}, classProbs};
    }
    
    float calculateAmplitude(const std::vector<float>& audioData) {
        if (audioData.empty()) return 0.0f;
        
        float sum = 0.0f;
        for (float sample : audioData) {
            sum += std::abs(sample);
        }
        return sum / audioData.size();
    }
    
    float calculateZeroCrossingRate(const std::vector<float>& audioData) {
        if (audioData.size() < 2) return 0.0f;
        
        int crossings = 0;
        for (size_t i = 1; i < audioData.size(); ++i) {
            if ((audioData[i] >= 0) != (audioData[i - 1] >= 0)) {
                crossings++;
            }
        }
        return static_cast<float>(crossings) / audioData.size();
    }
    
    float calculateSpectralCentroid(const std::vector<float>& audioData) {
        if (audioData.empty()) return 0.0f;
        
        std::vector<std::complex<float>> fft = performSimpleFFT(audioData);
        
        double weightedSum = 0.0;
        double magnitudeSum = 0.0;
        
        for (size_t i = 0; i < fft.size(); ++i) {
            float magnitude = std::abs(fft[i]);
            float frequency = static_cast<float>(i) * YAMNetConfig::SAMPLE_RATE / static_cast<float>(fft.size());
            weightedSum += frequency * magnitude;
            magnitudeSum += magnitude;
        }
        
        return (magnitudeSum > 0) ? static_cast<float>(weightedSum / magnitudeSum) : 0.0f;
    }
    
    float calculateSpectralRolloff(const std::vector<float>& audioData) {
        if (audioData.empty()) return 0.0f;
        
        std::vector<std::complex<float>> fft = performSimpleFFT(audioData);
        std::vector<float> magnitudes(fft.size());
        
        for (size_t i = 0; i < fft.size(); ++i) {
            magnitudes[i] = std::abs(fft[i]);
        }
        
        float totalEnergy = std::accumulate(magnitudes.begin(), magnitudes.end(), 0.0f);
        float threshold = totalEnergy * 0.85f; // 85% energy threshold
        
        float cumulativeEnergy = 0.0f;
        for (size_t i = 0; i < magnitudes.size(); ++i) {
            cumulativeEnergy += magnitudes[i];
            if (cumulativeEnergy >= threshold) {
                return static_cast<float>(i) * YAMNetConfig::SAMPLE_RATE / static_cast<float>(magnitudes.size());
            }
        }
        
        return YAMNetConfig::SAMPLE_RATE / 2.0f; // Nyquist frequency
    }
    
    float calculateMFCC(const std::vector<float>& audioData) {
        if (audioData.empty()) return 0.0f;
        
        // Simplified MFCC calculation (first coefficient)
        std::vector<std::complex<float>> fft = performSimpleFFT(audioData);
        
        // Apply mel filterbank (simplified)
        float melEnergy = 0.0f;
        for (size_t i = 0; i < fft.size(); ++i) {
            float frequency = static_cast<float>(i) * YAMNetConfig::SAMPLE_RATE / static_cast<float>(fft.size());
            if (frequency >= YAMNetConfig::MEL_MIN_FREQ && frequency <= YAMNetConfig::MEL_MAX_FREQ) {
                melEnergy += std::abs(fft[i]);
            }
        }
        
        // Log and DCT (simplified)
        return melEnergy > 0 ? std::log(melEnergy) : 0.0f;
    }
    
    std::vector<std::complex<float>> performSimpleFFT(const std::vector<float>& audioData) {
        size_t n = audioData.size();
        std::vector<std::complex<float>> result(n);
        
        for (size_t k = 0; k < n; ++k) {
            std::complex<float> sum(0.0f, 0.0f);
            
            for (size_t t = 0; t < n; ++t) {
                float angle = -2.0f * M_PI * k * t / n;
                std::complex<float> phase(std::cos(angle), std::sin(angle));
                sum += audioData[t] * phase;
            }
            
            result[k] = sum;
        }
        
        return result;
    }
    
    bool isModelLoaded_;
    bool useGpuDelegate_;
#ifdef TENSORFLOW_LITE_AVAILABLE
    std::unique_ptr<tflite::FlatBufferModel> model_;
    std::unique_ptr<tflite::Interpreter> interpreter_;
    TfLiteDelegate* gpuDelegate_;
#endif
};

// TensorFlowWrapper implementation
TensorFlowWrapper::TensorFlowWrapper() : pImpl(std::make_unique<Impl>()) {
    LOGI("TensorFlowWrapper created");
}

TensorFlowWrapper::~TensorFlowWrapper() {
    LOGI("TensorFlowWrapper destroyed");
}

bool TensorFlowWrapper::initialize(const std::string& modelPath) {
    return pImpl->initialize(modelPath);
}

DetectionResult TensorFlowWrapper::detectCough(const std::vector<float>& audioData) {
    return pImpl->detectCough(audioData);
}

bool TensorFlowWrapper::isModelLoaded() const {
    return pImpl->isModelLoaded();
}

void TensorFlowWrapper::release() {
    pImpl->release();
}

} // namespace coughdetect