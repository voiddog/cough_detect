#ifndef TENSORFLOW_WRAPPER_H
#define TENSORFLOW_WRAPPER_H

#include <vector>
#include <string>
#include <memory>

// Conditionally include TensorFlow Lite headers
#ifdef TENSORFLOW_LITE_AVAILABLE
#include "tensorflow/lite/interpreter.h"
#include "tensorflow/lite/kernels/register.h"
#include "tensorflow/lite/model.h"
#include "tensorflow/lite/optional_debug_tools.h"
#include "tensorflow/lite/delegates/gpu/delegate.h"
#endif

namespace coughdetect {

// Detection result
struct DetectionResult {
    bool isCough;
    float confidence;
    std::vector<float> features;
    std::vector<float> classProbabilities;
};

// YAMNet specific constants
struct YAMNetConfig {
    static constexpr int SAMPLE_RATE = 16000;
    static constexpr int HOP_LENGTH = 160;
    static constexpr int FFT_LENGTH = 400;
    static constexpr int MEL_BINS = 64;
    static constexpr int NUM_CLASSES = 521;
    static constexpr float MEL_MIN_FREQ = 125.0f;
    static constexpr float MEL_MAX_FREQ = 7500.0f;
};

// Cough-related class indices in YAMNet
struct CoughClasses {
    static constexpr int COUGH = 0;
    static constexpr int THROAT_CLEARING = 1;
    static constexpr int SNEEZE = 2;
    static constexpr int SNIFF = 3;
    static constexpr int BURP = 4;
    static constexpr int BELCH = 5;
};

class TensorFlowWrapper {
public:
    TensorFlowWrapper();
    ~TensorFlowWrapper();

    // Initialize TensorFlow Lite model
    bool initialize(const std::string& modelPath);
    
    // Detect cough in audio data
    DetectionResult detectCough(const std::vector<float>& audioData);
    
    // Check if model is loaded
    bool isModelLoaded() const;
    
    // Release resources
    void release();

private:
    class Impl;
    std::unique_ptr<Impl> pImpl;
};

} // namespace coughdetect

#endif // TENSORFLOW_WRAPPER_H