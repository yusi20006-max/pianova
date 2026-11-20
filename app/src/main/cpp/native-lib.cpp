//
// Pianova — Phase 0 native smoke test.
//
// Links Google Oboe through Prefab and exposes a single JNI entry point so the
// Kotlin side can prove that libpianova_audio.so was built, packaged, and
// loaded, and that it really links against liboboe.so.
//

#include <jni.h>

#include <oboe/Oboe.h>
#include <android/log.h>

namespace {

constexpr const char *kLogTag = "PianovaNative";
constexpr const char *kEngineStatus = "Oboe Native Engine Ready";

} // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_ir_pianova_app_core_audio_NativeAudioBridge_getEngineStatus(
        JNIEnv *env,
        jobject /* this */) {
    // oboe::getVersionText() is defined inside liboboe.so, so resolving this
    // symbol proves the Oboe prefab package is genuinely linked (the build
    // would fail at link time otherwise).
    const char *oboeVersion = oboe::getVersionText();

    __android_log_print(
            ANDROID_LOG_INFO,
            kLogTag,
            "%s (built against Oboe %s, default sample rate %d Hz)",
            kEngineStatus,
            oboeVersion,
            oboe::DefaultStreamValues::SampleRate);

    return env->NewStringUTF(kEngineStatus);
}
