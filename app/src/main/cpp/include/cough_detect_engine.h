#ifndef COUGH_DETECT_ENGINE_H
#define COUGH_DETECT_ENGINE_H

#include <functional>
#include <memory>
#include <vector>
#include <string>

namespace coughdetect {

// Audio event types
enum class AudioEventType {
    COUGH_DETECTED,
    AUDIO_LEVEL_CHANGED,
    ERROR_OCCURRED
};

// Audio event data
struct AudioEvent {
    AudioEventType type;
    float confidence;
    float amplitude;
    int64_t timestamp;
    std::vector<float> audioData;
    std::string errorMessage;
};

// Callback function type for audio events
using AudioEventCallback = std::function<void(const AudioEvent&)>;

// Engine states
enum class EngineState {
    IDLE,
    RECORDING,
    PAUSED,
    PROCESSING
};

class CoughDetectEngine {
public:
    CoughDetectEngine();
    ~CoughDetectEngine();

    // Initialize the engine
    bool initialize(const std::string& modelPath = "");
    
    // Start audio recording and detection
    bool start();
    
    // Stop audio recording and detection
    void stop();
    
    // Pause recording
    void pause();
    
    // Resume recording
    void resume();
    
    // Set callback for audio events
    void setAudioEventCallback(AudioEventCallback callback);
    
    // Get current engine state
    EngineState getState() const;
    
    // Get current audio level (0.0 to 1.0)
    float getAudioLevel() const;
    
    // Get sample rate
    int getSampleRate() const;
    
    // Check if engine is ready
    bool isReady() const;
    
    // Release resources
    void release();

private:
    class Impl;
    std::unique_ptr<Impl> pImpl;
};

} // namespace coughdetect

#endif // COUGH_DETECT_ENGINE_H 