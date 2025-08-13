#include "audio_recorder.h"
#include <android/log.h>
#include <cmath>
#include <algorithm>
#include <chrono>
#include <jni.h>

#define LOG_TAG "AudioRecorder"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace coughdetect {

AudioRecorder::AudioRecorder()
    : currentAudioLevel_(0.0f), isRecording_(false), sampleRate_(16000),
      channelCount_(1), framesPerCallback_(480) {
    LOGI("AudioRecorder created");
}

AudioRecorder::~AudioRecorder() {
    release();
    LOGI("AudioRecorder destroyed");
}

bool AudioRecorder::initialize(int sampleRate, int channelCount) {
    try {
        sampleRate_ = sampleRate;
        channelCount_ = channelCount;

        // Calculate frames per callback (100ms chunks)
        framesPerCallback_ = sampleRate_ / 10;

        // Create audio stream builder
        oboe::AudioStreamBuilder builder;
        builder.setDirection(oboe::Direction::Input)
               ->setChannelCount(channelCount_)
               ->setFormat(oboe::AudioFormat::Float)
               ->setSampleRate(sampleRate_)
               ->setFramesPerCallback(framesPerCallback_)
               ->setDataCallback(this)
               ->setErrorCallback(this);

        // Open the stream
        oboe::AudioStream* stream = nullptr;
        oboe::Result result = builder.openStream(&stream);
        if (result == oboe::Result::OK && stream != nullptr) {
            audioStream_.reset(stream);
        }
        if (result != oboe::Result::OK) {
            LOGE("Failed to open audio stream: %s", oboe::convertToText(result));
            return false;
        }

        LOGI("AudioRecorder initialized - SampleRate: %d, Channels: %d",
             sampleRate_, channelCount_);
        return true;

    } catch (const std::exception& e) {
        LOGE("Exception during initialization: %s", e.what());
        return false;
    }
}

bool AudioRecorder::start() {
    if (isRecording_) {
        LOGI("Already recording");
        return true;
    }

    try {
        if (audioStream_) {
            oboe::Result result = audioStream_->requestStart();
            if (result != oboe::Result::OK) {
                LOGE("Failed to start audio stream: %s", oboe::convertToText(result));
                return false;
            }
            isRecording_ = true;
            LOGI("Audio recording started");
            return true;
        } else {
            LOGE("Audio stream not initialized");
            return false;
        }

    } catch (const std::exception& e) {
        LOGE("Exception during start: %s", e.what());
        return false;
    }
}

void AudioRecorder::stop() {
    if (!isRecording_) {
        return;
    }

    try {
        if (audioStream_) {
            audioStream_->requestStop();
        }
        isRecording_ = false;
        currentAudioLevel_ = 0.0f;
        LOGI("Audio recording stopped");
    } catch (const std::exception& e) {
        LOGE("Exception during stop: %s", e.what());
    }
}

void AudioRecorder::pause() {
    if (isRecording_) {
        try {
            if (audioStream_) {
                audioStream_->requestPause();
            }
            isRecording_ = false;
            LOGI("Audio recording paused");
        } catch (const std::exception& e) {
            LOGE("Exception during pause: %s", e.what());
        }
    }
}

void AudioRecorder::resume() {
    if (!isRecording_) {
        try {
            if (audioStream_) {
                audioStream_->requestStart();
            }
            isRecording_ = true;
            LOGI("Audio recording resumed");
        } catch (const std::exception& e) {
            LOGE("Exception during resume: %s", e.what());
        }
    }
}

void AudioRecorder::setAudioDataCallback(AudioDataCallback callback) {
    audioDataCallback_ = callback;
    LOGI("Audio data callback set");
}

float AudioRecorder::getAudioLevel() const {
    return currentAudioLevel_;
}

int AudioRecorder::getSampleRate() const {
    return sampleRate_;
}

bool AudioRecorder::isRecording() const {
    return isRecording_;
}

void AudioRecorder::release() {
    stop();
    if (audioStream_) {
        audioStream_->close();
        audioStream_.reset();
    }
    audioDataCallback_ = nullptr;
    LOGI("AudioRecorder released");
}

// Oboe callback implementation
oboe::DataCallbackResult AudioRecorder::onAudioReady(
    oboe::AudioStream *audioStream,
    void *audioData,
    int32_t numFrames) {
    (void)audioStream;

    if (audioData && numFrames > 0) {
        float* floatData = static_cast<float*>(audioData);
        processAudioData(floatData, numFrames);
    }

    return oboe::DataCallbackResult::Continue;
}

void AudioRecorder::onErrorAfterClose(oboe::AudioStream *audioStream, oboe::Result error) {
    (void)audioStream;
    LOGE("Audio stream error: %s", oboe::convertToText(error));
    isRecording_ = false;
}

void AudioRecorder::processAudioData(const float* data, int32_t numFrames) {
    if (audioDataCallback_) {
        std::vector<float> audioData(data, data + numFrames);
        float level = calculateRMS(data, numFrames);

        // 更新当前音频电平
        updateAudioLevel(level);

        // 详细日志：每10秒输出一次音频处理统计
        static int frameCount = 0;
        static auto lastLogTime = std::chrono::steady_clock::now();
        frameCount++;

        auto now = std::chrono::steady_clock::now();
        auto elapsed = std::chrono::duration_cast<std::chrono::seconds>(now - lastLogTime);

        if (elapsed.count() >= 10) {
            LOGI("Audio processing stats - Frames processed: %d, Current level: %.3f, Avg frames/sec: %.1f",
                 frameCount, level, frameCount / 10.0f);
            frameCount = 0;
            lastLogTime = now;
        }

        // 检测音频异常
        if (level > 0.95f) {
            LOGI("High audio level detected: %.3f (possible clipping)", level);
        } else if (level < 0.001f && isRecording_) {
            static int silenceCount = 0;
            silenceCount++;
            if (silenceCount % 100 == 0) {  // 每100次静音时记录一次
                LOGI("Extended silence detected - count: %d, level: %.6f", silenceCount, level);
            }
        }

        audioDataCallback_(audioData, level);
    } else {
        LOGE("Audio data callback not set, dropping %d frames", numFrames);
    }
}

float AudioRecorder::calculateRMS(const float* data, int32_t numFrames) {
    if (numFrames <= 0) return 0.0f;

    double sum = 0.0;
    for (int32_t i = 0; i < numFrames; ++i) {
        sum += data[i] * data[i];
    }

    float rms = static_cast<float>(std::sqrt(sum / numFrames));

    // Convert to dB and normalize to 0-1 range
    float db = (rms > 0) ? 20.0f * std::log10(rms) : -100.0f;
    float normalizedLevel = std::max(0.0f, std::min(1.0f, (db + 40.0f) / 40.0f));

    return normalizedLevel;
}

void AudioRecorder::updateAudioLevel(float level) {
    // Smooth the audio level to avoid rapid fluctuations
    const float smoothingFactor = 0.1f;
    currentAudioLevel_ = smoothingFactor * level + (1.0f - smoothingFactor) * currentAudioLevel_;
}

} // namespace coughdetect
