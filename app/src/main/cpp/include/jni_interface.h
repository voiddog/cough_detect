#ifndef JNI_INTERFACE_H
#define JNI_INTERFACE_H

#include <jni.h>
#include "cough_detect_engine.h"

#ifdef __cplusplus
extern "C" {
#endif

// JNI function declarations
JNIEXPORT jlong JNICALL
Java_org_voiddog_coughdetect_engine_CoughDetectEngine_nativeCreate(JNIEnv *env, jobject thiz);

JNIEXPORT void JNICALL
Java_org_voiddog_coughdetect_engine_CoughDetectEngine_nativeDestroy(JNIEnv *env, jobject thiz, jlong engine_ptr);

JNIEXPORT jboolean JNICALL
Java_org_voiddog_coughdetect_engine_CoughDetectEngine_nativeInitialize(JNIEnv *env, jobject thiz, jlong engine_ptr, jstring model_path);

JNIEXPORT jboolean JNICALL
Java_org_voiddog_coughdetect_engine_CoughDetectEngine_nativeStart(JNIEnv *env, jobject thiz, jlong engine_ptr);

JNIEXPORT void JNICALL
Java_org_voiddog_coughdetect_engine_CoughDetectEngine_nativeStop(JNIEnv *env, jobject thiz, jlong engine_ptr);

JNIEXPORT void JNICALL
Java_org_voiddog_coughdetect_engine_CoughDetectEngine_nativePause(JNIEnv *env, jobject thiz, jlong engine_ptr);

JNIEXPORT void JNICALL
Java_org_voiddog_coughdetect_engine_CoughDetectEngine_nativeResume(JNIEnv *env, jobject thiz, jlong engine_ptr);

JNIEXPORT jint JNICALL
Java_org_voiddog_coughdetect_engine_CoughDetectEngine_nativeGetState(JNIEnv *env, jobject thiz, jlong engine_ptr);

JNIEXPORT jfloat JNICALL
Java_org_voiddog_coughdetect_engine_CoughDetectEngine_nativeGetAudioLevel(JNIEnv *env, jobject thiz, jlong engine_ptr);

JNIEXPORT jint JNICALL
Java_org_voiddog_coughdetect_engine_CoughDetectEngine_nativeGetSampleRate(JNIEnv *env, jobject thiz, jlong engine_ptr);

JNIEXPORT jboolean JNICALL
Java_org_voiddog_coughdetect_engine_CoughDetectEngine_nativeIsReady(JNIEnv *env, jobject thiz, jlong engine_ptr);

JNIEXPORT void JNICALL
Java_org_voiddog_coughdetect_engine_CoughDetectEngine_nativeRelease(JNIEnv *env, jobject thiz, jlong engine_ptr);

#ifdef __cplusplus
}
#endif

#endif // JNI_INTERFACE_H
