// The calls WhisperNative.kt makes into whisper.cpp: load a model, transcribe
// a piece of sound, read the timed segments, free the model.

#include <cstring>

#include <jni.h>
#include <android/log.h>

#include "whisper.h"

namespace {

// whisper.cpp writes to stderr, which Android drops: warnings and errors go to logcat.
void log_to_logcat(enum ggml_log_level level, const char * text, void *) {
    if (level == GGML_LOG_LEVEL_WARN || level == GGML_LOG_LEVEL_ERROR) {
        __android_log_write(level == GGML_LOG_LEVEL_ERROR ? ANDROID_LOG_ERROR : ANDROID_LOG_WARN, "UmaiWhisper", text);
    }
}

whisper_context * context_of(jlong handle) {
    return reinterpret_cast<whisper_context *>(handle);
}

}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_org_opensources_umai_speech_data_WhisperNative_load(JNIEnv * env, jobject, jstring path) {
    whisper_log_set(log_to_logcat, nullptr);
    // Which instructions of the CPU ggml was built to use: the first thing to check when it is slow.
    __android_log_write(ANDROID_LOG_INFO, "UmaiWhisper", whisper_print_system_info());
    const char * model = env->GetStringUTFChars(path, nullptr);
    whisper_context_params params = whisper_context_default_params();
    params.use_gpu = false;
    // Measured on a Pixel 10: without it, a token is written about a fifth faster on the CPU.
    params.flash_attn = false;
    whisper_context * context = whisper_init_from_file_with_params(model, params);
    env->ReleaseStringUTFChars(path, model);
    return reinterpret_cast<jlong>(context);
}

JNIEXPORT jint JNICALL
Java_org_opensources_umai_speech_data_WhisperNative_transcribe(
    JNIEnv * env, jobject, jlong handle, jfloatArray samples, jstring language, jint threads) {
    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_special = false;
    params.print_progress = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.translate = false;
    // Each piece is heard on its own: text carried over from the previous one
    // is where a small model starts repeating itself.
    params.no_context = true;
    params.suppress_blank = true;
    // Music and noises are not written as "[Musique]".
    params.suppress_nst = true;
    // A piece that must be decoded again, at a higher temperature, is decoded
    // once rather than five times: on a phone's CPU, the five took as long as
    // the video itself.
    params.greedy.best_of = 1;
    params.n_threads = threads;

    const char * spoken = env->GetStringUTFChars(language, nullptr);
    // A language Whisper does not know is detected instead of failing the piece.
    params.language = whisper_lang_id(spoken) >= 0 ? spoken : "auto";
    jfloat * data = env->GetFloatArrayElements(samples, nullptr);
    const int result = whisper_full(context_of(handle), params, data, env->GetArrayLength(samples));
    env->ReleaseFloatArrayElements(samples, data, JNI_ABORT);
    env->ReleaseStringUTFChars(language, spoken);
    return result == 0 ? whisper_full_n_segments(context_of(handle)) : -1;
}

// As bytes: a segment is UTF-8, which the JNI's modified UTF-8 does not read for every character.
JNIEXPORT jbyteArray JNICALL
Java_org_opensources_umai_speech_data_WhisperNative_segmentText(JNIEnv * env, jobject, jlong handle, jint index) {
    const char * text = whisper_full_get_segment_text(context_of(handle), index);
    const jsize length = static_cast<jsize>(strlen(text));
    jbyteArray bytes = env->NewByteArray(length);
    env->SetByteArrayRegion(bytes, 0, length, reinterpret_cast<const jbyte *>(text));
    return bytes;
}

// Times in hundredths of a second from the start of the piece.
JNIEXPORT jlong JNICALL
Java_org_opensources_umai_speech_data_WhisperNative_segmentStart(JNIEnv *, jobject, jlong handle, jint index) {
    return whisper_full_get_segment_t0(context_of(handle), index);
}

JNIEXPORT jlong JNICALL
Java_org_opensources_umai_speech_data_WhisperNative_segmentEnd(JNIEnv *, jobject, jlong handle, jint index) {
    return whisper_full_get_segment_t1(context_of(handle), index);
}

JNIEXPORT jfloat JNICALL
Java_org_opensources_umai_speech_data_WhisperNative_segmentNoSpeech(JNIEnv *, jobject, jlong handle, jint index) {
    return whisper_full_get_segment_no_speech_prob(context_of(handle), index);
}

JNIEXPORT void JNICALL
Java_org_opensources_umai_speech_data_WhisperNative_free(JNIEnv *, jobject, jlong handle) {
    whisper_free(context_of(handle));
}

}  // extern "C"
