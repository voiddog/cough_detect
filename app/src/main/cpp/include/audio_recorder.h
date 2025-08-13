#ifndef AUDIO_RECORDER_H
#define AUDIO_RECORDER_H

#include <oboe/Oboe.h>
#include <functional>
#include <vector>
#include <memory>
#include <atomic>
#include <mutex>

namespace coughdetect {

// Audio data callback
using AudioDataCallback = std::function<void(const std::vector<float>&, float)>;

class AudioRecorder : public oboe::AudioStreamDataCallback, public oboe::AudioStreamErrorCallback {
public:
    AudioRecorder();
    ~AudioRecorder();

    // Initialize the recorder
    bool initialize(int sampleRate = 16000, int channelCount = 1);
    
    // Start recording
    bool start();
    
    // Stop recording
    void stop();
    
    // Pause recording
    void pause();
    
    // Resume recording
    void resume();
    
    // Set callback for audio data
    void setAudioDataCallback(AudioDataCallback callback);
    
    // Get current audio level
    float getAudioLevel() const;
    
    // Get sample rate
    int getSampleRate() const;
    
    // Check if recording
    bool isRecording() const;
    
    // Release resources
    void release();

    // Oboe callback implementation
    oboe::DataCallbackResult onAudioReady(
        oboe::AudioStream *audioStream,
        void *audioData,
        int32_t numFrames) override;

    // Error callback
    void onErrorAfterClose(oboe::AudioStream *audioStream, oboe::Result error) override;

private:
    std::unique_ptr<oboe::AudioStream> audioStream_;
    AudioDataCallback audioDataCallback_;
    
    std::atomic<float> currentAudioLevel_;
    std::atomic<bool> isRecording_;
    
    int sampleRate_;
    int channelCount_;
    int framesPerCallback_;
    
    std::vector<float> audioBuffer_;
    std::mutex bufferMutex_;
    
    // Audio processing
    void processAudioData(const float* data, int32_t numFrames);
    float calculateRMS(const float* data, int32_t numFrames);
    void updateAudioLevel(float level);
};

} // namespace coughdetect

#endif // AUDIO_RECORDER_H 