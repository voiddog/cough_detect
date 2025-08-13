#include "jni_interface.h"
#include "cough_detect_engine.h"
#include <android/log.h>
#include <jni.h>
#include <string>

#define LOG_TAG "JNIInterface"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using namespace coughdetect;

// Global reference to JavaVM
static JavaVM* g_vm = nullptr;

// Global reference to the Java object
static jobject g_java_object = nullptr;

// JNI function to create native engine
JNIEXPORT jlong JNICALL
Java_org_voiddog_coughdetect_engine_CoughDetectEngine_nativeCreate(JNIEnv *env, jobject thiz) {
    (void)env;
    (void)thiz;
    try {
        CoughDetectEngine* engine = new CoughDetectEngine();
        LOGI("Native engine created: %p", engine);
        return reinterpret_cast<jlong>(engine);
    } catch (const std::exception& e) {
        LOGE("Exception creating native engine: %s", e.what());
        return 0;
    }
}

// JNI function to destroy native engine
JNIEXPORT void JNICALL
Java_org_voiddog_coughdetect_engine_CoughDetectEngine_nativeDestroy(JNIEnv *env, jobject thiz, jlong engine_ptr) {
    (void)env;
    (void)thiz;
    try {
        if (engine_ptr != 0) {
            CoughDetectEngine* engine = reinterpret_cast<CoughDetectEngine*>(engine_ptr);
            delete engine;
            LOGI("Native engine destroyed: %p", engine);
        }
    } catch (const std::exception& e) {
        LOGE("Exception destroying native engine: %s", e.what());
    }
}

// JNI function to initialize engine
JNIEXPORT jboolean JNICALL
Java_org_voiddog_coughdetect_engine_CoughDetectEngine_nativeInitialize(JNIEnv *env, jobject thiz, jlong engine_ptr, jstring model_path) {
    try {
        if (engine_ptr == 0) {
            LOGE("Invalid engine pointer");
            return JNI_FALSE;
        }
        
        CoughDetectEngine* engine = reinterpret_cast<CoughDetectEngine*>(engine_ptr);
        
        // Convert Java string to C++ string
        const char* modelPathStr = nullptr;
        if (model_path != nullptr) {
            modelPathStr = env->GetStringUTFChars(model_path, nullptr);
        }
        
        std::string modelPath = modelPathStr ? modelPathStr : "";
        
        // Store global reference to Java object for callbacks
        if (g_java_object == nullptr) {
            g_java_object = env->NewGlobalRef(thiz);
            if (g_java_object == nullptr) {
                LOGE("Failed to create global reference to Java object");
                return JNI_FALSE;
            }
            LOGI("Global Java object reference created");
        }
        
        // Set up callback
        engine->setAudioEventCallback([](const AudioEvent& event) {
            // 详细的事件日志
            const char* eventTypeName = "";
            switch (event.type) {
                case AudioEventType::COUGH_DETECTED:
                    eventTypeName = "COUGH_DETECTED";
                    break;
                case AudioEventType::AUDIO_LEVEL_CHANGED:
                    eventTypeName = "AUDIO_LEVEL_CHANGED";
                    break;
                case AudioEventType::ERROR_OCCURRED:
                    eventTypeName = "ERROR_OCCURRED";
                    break;
            }
            
            if (event.type == AudioEventType::COUGH_DETECTED) {
                LOGI("🎯 JNI Event: %s - Confidence: %.3f, Amplitude: %.3f, Timestamp: %lld", 
                     eventTypeName, event.confidence, event.amplitude, (long long)event.timestamp);
            } else if (event.type == AudioEventType::ERROR_OCCURRED) {
                LOGE("❌ JNI Event: %s - Error: %s", eventTypeName, event.errorMessage.c_str());
            }
            
            // Call Java callback method
            JNIEnv* env;
            bool needDetach = false;
            
            if (g_vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
                if (g_vm->AttachCurrentThread(reinterpret_cast<JNIEnv**>(&env), nullptr) != JNI_OK) {
                    LOGE("Failed to attach thread for callback");
                    return;
                }
                needDetach = true;
                LOGI("Thread attached for JNI callback");
            }
            
            if (g_java_object != nullptr) {
                jclass clazz = env->GetObjectClass(g_java_object);
                if (clazz != nullptr) {
                    jmethodID methodId = env->GetMethodID(clazz, "onAudioEvent", "(IIFJ)V");
                    if (methodId != nullptr) {
                        env->CallVoidMethod(g_java_object, methodId, 
                                          static_cast<jint>(event.type),
                                          static_cast<jint>(event.confidence * 1000), // Convert to integer
                                          event.amplitude,
                                          static_cast<jlong>(event.timestamp));
                        
                        // 检查JNI异常
                        if (env->ExceptionCheck()) {
                            LOGE("JNI exception occurred during callback");
                            env->ExceptionDescribe();
                            env->ExceptionClear();
                        }
                    } else {
                        LOGE("Failed to find onAudioEvent method");
                    }
                    env->DeleteLocalRef(clazz);
                } else {
                    LOGE("Failed to get Java object class");
                }
            } else {
                LOGE("Global Java object reference is null");
            }
            
            if (needDetach) {
                g_vm->DetachCurrentThread();
                LOGI("Thread detached after JNI callback");
            }
        });
        
        bool result = engine->initialize(modelPath);
        
        if (modelPathStr != nullptr) {
            env->ReleaseStringUTFChars(model_path, modelPathStr);
        }
        
        LOGI("Engine initialization result: %s", result ? "success" : "failed");
        return result ? JNI_TRUE : JNI_FALSE;
        
    } catch (const std::exception& e) {
        LOGE("Exception during initialization: %s", e.what());
        return JNI_FALSE;
    }
}

// JNI function to start engine
JNIEXPORT jboolean JNICALL
Java_org_voiddog_coughdetect_engine_CoughDetectEngine_nativeStart(JNIEnv *env, jobject thiz, jlong engine_ptr) {
    (void)env;
    (void)thiz;
    try {
        if (engine_ptr == 0) {
            LOGE("Invalid engine pointer");
            return JNI_FALSE;
        }
        
        CoughDetectEngine* engine = reinterpret_cast<CoughDetectEngine*>(engine_ptr);
        bool result = engine->start();
        
        LOGI("Engine start result: %s", result ? "success" : "failed");
        return result ? JNI_TRUE : JNI_FALSE;
        
    } catch (const std::exception& e) {
        LOGE("Exception during start: %s", e.what());
        return JNI_FALSE;
    }
}

// JNI function to stop engine
JNIEXPORT void JNICALL
Java_org_voiddog_coughdetect_engine_CoughDetectEngine_nativeStop(JNIEnv *env, jobject thiz, jlong engine_ptr) {
    (void)env;
    (void)thiz;
    try {
        if (engine_ptr != 0) {
            CoughDetectEngine* engine = reinterpret_cast<CoughDetectEngine*>(engine_ptr);
            engine->stop();
            LOGI("Engine stopped");
        }
    } catch (const std::exception& e) {
        LOGE("Exception during stop: %s", e.what());
    }
}

// JNI function to pause engine
JNIEXPORT void JNICALL
Java_org_voiddog_coughdetect_engine_CoughDetectEngine_nativePause(JNIEnv *env, jobject thiz, jlong engine_ptr) {
    (void)env;
    (void)thiz;
    try {
        if (engine_ptr != 0) {
            CoughDetectEngine* engine = reinterpret_cast<CoughDetectEngine*>(engine_ptr);
            engine->pause();
            LOGI("Engine paused");
        }
    } catch (const std::exception& e) {
        LOGE("Exception during pause: %s", e.what());
    }
}

// JNI function to resume engine
JNIEXPORT void JNICALL
Java_org_voiddog_coughdetect_engine_CoughDetectEngine_nativeResume(JNIEnv *env, jobject thiz, jlong engine_ptr) {
    (void)env;
    (void)thiz;
    try {
        if (engine_ptr != 0) {
            CoughDetectEngine* engine = reinterpret_cast<CoughDetectEngine*>(engine_ptr);
            engine->resume();
            LOGI("Engine resumed");
        }
    } catch (const std::exception& e) {
        LOGE("Exception during resume: %s", e.what());
    }
}

// JNI function to get engine state
JNIEXPORT jint JNICALL
Java_org_voiddog_coughdetect_engine_CoughDetectEngine_nativeGetState(JNIEnv *env, jobject thiz, jlong engine_ptr) {
    (void)env;
    (void)thiz;
    try {
        if (engine_ptr == 0) {
            return static_cast<jint>(EngineState::IDLE);
        }
        
        CoughDetectEngine* engine = reinterpret_cast<CoughDetectEngine*>(engine_ptr);
        EngineState state = engine->getState();
        return static_cast<jint>(state);
        
    } catch (const std::exception& e) {
        LOGE("Exception getting state: %s", e.what());
        return static_cast<jint>(EngineState::IDLE);
    }
}

// JNI function to get audio level
JNIEXPORT jfloat JNICALL
Java_org_voiddog_coughdetect_engine_CoughDetectEngine_nativeGetAudioLevel(JNIEnv *env, jobject thiz, jlong engine_ptr) {
    (void)env;
    (void)thiz;
    try {
        if (engine_ptr == 0) {
            return 0.0f;
        }
        
        CoughDetectEngine* engine = reinterpret_cast<CoughDetectEngine*>(engine_ptr);
        return engine->getAudioLevel();
        
    } catch (const std::exception& e) {
        LOGE("Exception getting audio level: %s", e.what());
        return 0.0f;
    }
}

// JNI function to get sample rate
JNIEXPORT jint JNICALL
Java_org_voiddog_coughdetect_engine_CoughDetectEngine_nativeGetSampleRate(JNIEnv *env, jobject thiz, jlong engine_ptr) {
    (void)env;
    (void)thiz;
    try {
        if (engine_ptr == 0) {
            return 16000;
        }
        
        CoughDetectEngine* engine = reinterpret_cast<CoughDetectEngine*>(engine_ptr);
        return engine->getSampleRate();
        
    } catch (const std::exception& e) {
        LOGE("Exception getting sample rate: %s", e.what());
        return 16000;
    }
}

// JNI function to check if engine is ready
JNIEXPORT jboolean JNICALL
Java_org_voiddog_coughdetect_engine_CoughDetectEngine_nativeIsReady(JNIEnv *env, jobject thiz, jlong engine_ptr) {
    (void)env;
    (void)thiz;
    try {
        if (engine_ptr == 0) {
            return JNI_FALSE;
        }
        
        CoughDetectEngine* engine = reinterpret_cast<CoughDetectEngine*>(engine_ptr);
        return engine->isReady() ? JNI_TRUE : JNI_FALSE;
        
    } catch (const std::exception& e) {
        LOGE("Exception checking ready state: %s", e.what());
        return JNI_FALSE;
    }
}

// JNI function to release engine
JNIEXPORT void JNICALL
Java_org_voiddog_coughdetect_engine_CoughDetectEngine_nativeRelease(JNIEnv *env, jobject thiz, jlong engine_ptr) {
    (void)env;
    (void)thiz;
    try {
        if (engine_ptr != 0) {
            CoughDetectEngine* engine = reinterpret_cast<CoughDetectEngine*>(engine_ptr);
            engine->release();
            LOGI("Engine released");
        }
    } catch (const std::exception& e) {
        LOGE("Exception during release: %s", e.what());
    }
}

// JNI_OnLoad function
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    (void)reserved;
    g_vm = vm;
    LOGI("JNI_OnLoad called");
    return JNI_VERSION_1_6;
}

// JNI_OnUnload function
JNIEXPORT void JNICALL JNI_OnUnload(JavaVM* vm, void* reserved) {
    (void)reserved;
    LOGI("JNI_OnUnload called");
    g_vm = nullptr;
    if (g_java_object != nullptr) {
        JNIEnv* env;
        if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) == JNI_OK) {
            env->DeleteGlobalRef(g_java_object);
            g_java_object = nullptr;
        }
    }
} 