#include "AudioEngine.h"

#include <jni.h>
#include <android/asset_manager_jni.h>
#include <android/log.h>

#include <cstdint>
#include <memory>

namespace {
constexpr char kTag[] = "PianovaJNI";

pianova::AudioEngine* fromHandle(jlong handle) {
    return reinterpret_cast<pianova::AudioEngine*>(
        static_cast<intptr_t>(handle));
}
}

extern "C" JNIEXPORT jlong JNICALL
Java_ir_pianova_app_core_audio_NativeAudioBridge_nativeCreate(
    JNIEnv* env, jobject /* thiz */, jobject assetManager) {
    AAssetManager* assets = AAssetManager_fromJava(env, assetManager);
    if (assets == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, kTag,
                            "Unable to obtain Android AssetManager");
        return 0;
    }

    auto engine = std::make_unique<pianova::AudioEngine>();
    if (!engine->loadSoundFont(assets, "soundfonts/pianova.sf2")) {
        return 0;
    }

    return static_cast<jlong>(
        reinterpret_cast<intptr_t>(engine.release()));
}

extern "C" JNIEXPORT jboolean JNICALL
Java_ir_pianova_app_core_audio_NativeAudioBridge_nativeStart(
    JNIEnv*, jobject, jlong handle) {
    auto* engine = fromHandle(handle);
    return engine != nullptr && engine->start() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_ir_pianova_app_core_audio_NativeAudioBridge_nativeStop(
    JNIEnv*, jobject, jlong handle) {
    if (auto* engine = fromHandle(handle)) engine->stop();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_ir_pianova_app_core_audio_NativeAudioBridge_nativeNoteOn(
    JNIEnv*, jobject, jlong handle, jint note, jfloat velocity) {
    auto* engine = fromHandle(handle);
    return engine != nullptr && engine->noteOn(note, velocity)
        ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_ir_pianova_app_core_audio_NativeAudioBridge_nativeNoteOff(
    JNIEnv*, jobject, jlong handle, jint note) {
    auto* engine = fromHandle(handle);
    return engine != nullptr && engine->noteOff(note)
        ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_ir_pianova_app_core_audio_NativeAudioBridge_nativeSetSustain(
    JNIEnv*, jobject, jlong handle, jboolean enabled) {
    auto* engine = fromHandle(handle);
    return engine != nullptr &&
        engine->setSustain(enabled == JNI_TRUE) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_ir_pianova_app_core_audio_NativeAudioBridge_nativeSetVolume(
    JNIEnv*, jobject, jlong handle, jfloat volume) {
    auto* engine = fromHandle(handle);
    return engine != nullptr && engine->setVolume(volume)
        ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_ir_pianova_app_core_audio_NativeAudioBridge_nativeStopAllNotes(
    JNIEnv*, jobject, jlong handle) {
    auto* engine = fromHandle(handle);
    return engine != nullptr && engine->stopAllNotes()
        ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_ir_pianova_app_core_audio_NativeAudioBridge_nativeRelease(
    JNIEnv*, jobject, jlong handle) {
    delete fromHandle(handle);
}
