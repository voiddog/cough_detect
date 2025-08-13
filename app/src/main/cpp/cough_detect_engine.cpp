#include "cough_detect_engine.h"
#include "audio_recorder.h"
#include "tensorflow_wrapper.h"
#include <android/log.h>
#include <chrono>
#include <thread>

#define LOG_TAG "CoughDetectEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace coughdetect {

class CoughDetectEngine::Impl {
public:
    Impl() : state_(EngineState::IDLE), audioLevel_(0.0f), isReady_(false) {
        LOGI("CoughDetectEngine::Impl created");
    }
    
    ~Impl() {
        release();
        LOGI("CoughDetectEngine::Impl destroyed");
    }
    
    bool initialize(const std::string& modelPath) {
        try {
            // Initialize audio recorder
            if (!audioRecorder_.initialize()) {
                LOGE("Failed to initialize audio recorder");
                return false;
            }
            
            // Initialize TensorFlow wrapper
            if (!tensorFlowWrapper_.initialize(modelPath)) {
                LOGE("Failed to initialize TensorFlow wrapper");
                return false;
            }
            
            // Set up audio data callback
            audioRecorder_.setAudioDataCallback([this](const std::vector<float>& audioData, float amplitude) {
                processAudioData(audioData, amplitude);
            });
            
            isReady_ = true;
            state_ = EngineState::IDLE;
            LOGI("CoughDetectEngine initialized successfully");
            return true;
            
        } catch (const std::exception& e) {
            LOGE("Exception during initialization: %s", e.what());
            return false;
        }
    }
    
    bool start() {
        if (!isReady_) {
            LOGE("Engine not ready");
            return false;
        }
        
        if (state_ == EngineState::RECORDING) {
            LOGI("Already recording");
            return true;
        }
        
        try {
            if (audioRecorder_.start()) {
                state_ = EngineState::RECORDING;
                LOGI("Recording started");
                return true;
            } else {
                LOGE("Failed to start recording");
                return false;
            }
        } catch (const std::exception& e) {
            LOGE("Exception during start: %s", e.what());
            return false;
        }
    }
    
    void stop() {
        if (state_ == EngineState::IDLE) {
            return;
        }
        
        try {
            audioRecorder_.stop();
            state_ = EngineState::IDLE;
            audioLevel_ = 0.0f;
            LOGI("Recording stopped");
        } catch (const std::exception& e) {
            LOGE("Exception during stop: %s", e.what());
        }
    }
    
    void pause() {
        if (state_ == EngineState::RECORDING) {
            try {
                audioRecorder_.pause();
                state_ = EngineState::PAUSED;
                LOGI("Recording paused");
            } catch (const std::exception& e) {
                LOGE("Exception during pause: %s", e.what());
            }
        }
    }
    
    void resume() {
        if (state_ == EngineState::PAUSED) {
            try {
                audioRecorder_.resume();
                state_ = EngineState::RECORDING;
                LOGI("Recording resumed");
            } catch (const std::exception& e) {
                LOGE("Exception during resume: %s", e.what());
            }
        }
    }
    
    void setAudioEventCallback(AudioEventCallback callback) {
        audioEventCallback_ = callback;
    }
    
    EngineState getState() const {
        return state_;
    }
    
    float getAudioLevel() const {
        return audioLevel_;
    }
    
    int getSampleRate() const {
        return audioRecorder_.getSampleRate();
    }
    
    bool isReady() const {
        return isReady_;
    }
    
    void release() {
        stop();
        audioRecorder_.release();
        tensorFlowWrapper_.release();
        isReady_ = false;
        state_ = EngineState::IDLE;
        audioLevel_ = 0.0f;
        LOGI("Engine released");
    }
    
private:
    void processAudioData(const std::vector<float>& audioData, float amplitude) {
        try {
            // Update audio level
            audioLevel_ = amplitude;
            
            // Send audio level event
            if (audioEventCallback_) {
                AudioEvent event;
                event.type = AudioEventType::AUDIO_LEVEL_CHANGED;
                event.amplitude = amplitude;
                event.timestamp = std::chrono::duration_cast<std::chrono::milliseconds>(
                    std::chrono::system_clock::now().time_since_epoch()).count();
                audioEventCallback_(event);
            }
            
            // Process for cough detection
            if (state_ == EngineState::RECORDING) {
                state_ = EngineState::PROCESSING;
                
                // Detect cough using TensorFlow
                DetectionResult result = tensorFlowWrapper_.detectCough(audioData);
                
                if (result.isCough) {
                    LOGI("Cough detected with confidence: %.3f", result.confidence);
                    
                    // Send cough event
                    if (audioEventCallback_) {
                        AudioEvent event;
                        event.type = AudioEventType::COUGH_DETECTED;
                        event.confidence = result.confidence;
                        event.amplitude = amplitude;
                        event.audioData = audioData;
                        event.timestamp = std::chrono::duration_cast<std::chrono::milliseconds>(
                            std::chrono::system_clock::now().time_since_epoch()).count();
                        audioEventCallback_(event);
                    }
                }
                
                state_ = EngineState::RECORDING;
            }
            
        } catch (const std::exception& e) {
            LOGE("Exception during audio processing: %s", e.what());
            
            // Send error event
            if (audioEventCallback_) {
                AudioEvent event;
                event.type = AudioEventType::ERROR_OCCURRED;
                event.errorMessage = e.what();
                event.timestamp = std::chrono::duration_cast<std::chrono::milliseconds>(
                    std::chrono::system_clock::now().time_since_epoch()).count();
                audioEventCallback_(event);
            }
            
            state_ = EngineState::RECORDING;
        }
    }
    
    AudioRecorder audioRecorder_;
    TensorFlowWrapper tensorFlowWrapper_;
    AudioEventCallback audioEventCallback_;
    
    std::atomic<EngineState> state_;
    std::atomic<float> audioLevel_;
    std::atomic<bool> isReady_;
};

// CoughDetectEngine implementation
CoughDetectEngine::CoughDetectEngine() : pImpl(std::make_unique<Impl>()) {
    LOGI("CoughDetectEngine created");
}

CoughDetectEngine::~CoughDetectEngine() {
    LOGI("CoughDetectEngine destroyed");
}

bool CoughDetectEngine::initialize(const std::string& modelPath) {
    return pImpl->initialize(modelPath);
}

bool CoughDetectEngine::start() {
    return pImpl->start();
}

void CoughDetectEngine::stop() {
    pImpl->stop();
}

void CoughDetectEngine::pause() {
    pImpl->pause();
}

void CoughDetectEngine::resume() {
    pImpl->resume();
}

void CoughDetectEngine::setAudioEventCallback(AudioEventCallback callback) {
    pImpl->setAudioEventCallback(callback);
}

EngineState CoughDetectEngine::getState() const {
    return pImpl->getState();
}

float CoughDetectEngine::getAudioLevel() const {
    return pImpl->getAudioLevel();
}

int CoughDetectEngine::getSampleRate() const {
    return pImpl->getSampleRate();
}

bool CoughDetectEngine::isReady() const {
    return pImpl->isReady();
}

void CoughDetectEngine::release() {
    pImpl->release();
}

} // namespace coughdetect 