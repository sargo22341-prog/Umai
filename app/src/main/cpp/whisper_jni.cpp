// The calls WhisperNative.kt makes into whisper.cpp: load a model, transcribe
// a piece of sound, read the timed segments, free the model.
//
// Every JNI call that can fail is checked. A handle or a segment index the
// Kotlin side should never pass throws IllegalArgumentException: it is a
// programming error, reported where it happens rather than read as memory.

#include <cstring>

#include <jni.h>
#include <android/log.h>

#include "whisper.h"

namespace {

constexpr const char * kTag = "UmaiWhisper";

// whisper.cpp writes to stderr, which Android drops: warnings and errors go to logcat.
void log_to_logcat(enum ggml_log_level level, const char * text, void *) {
    if (level != GGML_LOG_LEVEL_WARN && level != GGML_LOG_LEVEL_ERROR) {
        return;
    }
    const int priority = level == GGML_LOG_LEVEL_ERROR ? ANDROID_LOG_ERROR : ANDROID_LOG_WARN;
    // A line logcat did not take is not worth failing a transcription for.
    static_cast<void>(__android_log_write(priority, kTag, text));
}

whisper_context * context_of(jlong handle) {
    return reinterpret_cast<whisper_context *>(handle);
}

// False, with IllegalArgumentException pending, when the handle or the index is not one of a transcription.
bool check_segment(JNIEnv * env, jlong handle, jint index) {
    if (handle != 0 && index >= 0 && index < whisper_full_n_segments(context_of(handle))) {
        return true;
    }
    jclass illegal = env->FindClass("java/lang/IllegalArgumentException");
    if (illegal != nullptr) {
        static_cast<void>(env->ThrowNew(illegal, "No such whisper segment"));
    }
    // Without the class, FindClass left NoClassDefFoundError pending: the call fails either way.
    return false;
}

}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_org_opensources_umai_speech_data_WhisperNative_load(JNIEnv * env, jobject, jstring path) {
    whisper_log_set(log_to_logcat, nullptr);
    // Which instructions of the CPU ggml was built to use: the first thing to check when it is slow.
    static_cast<void>(__android_log_write(ANDROID_LOG_INFO, kTag, whisper_print_system_info()));
    const char * model = env->GetStringUTFChars(path, nullptr);
    if (model == nullptr) {
        // Out of memory, with the error pending in Java.
        return 0;
    }
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
    if (handle == 0 || threads <= 0) {
        return -1;
    }
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
    if (spoken == nullptr) {
        return -1;
    }
    // A language Whisper does not know is detected instead of failing the piece.
    params.language = whisper_lang_id(spoken) >= 0 ? spoken : "auto";
    jfloat * data = env->GetFloatArrayElements(samples, nullptr);
    int result = -1;
    if (data != nullptr) {
        result = whisper_full(context_of(handle), params, data, env->GetArrayLength(samples));
        env->ReleaseFloatArrayElements(samples, data, JNI_ABORT);
    }
    env->ReleaseStringUTFChars(language, spoken);
    return result == 0 ? whisper_full_n_segments(context_of(handle)) : -1;
}

// As bytes: a segment is UTF-8, which the JNI's modified UTF-8 does not read for every character.
JNIEXPORT jbyteArray JNICALL
Java_org_opensources_umai_speech_data_WhisperNative_segmentText(JNIEnv * env, jobject, jlong handle, jint index) {
    if (!check_segment(env, handle, index)) {
        return nullptr;
    }
    const char * text = whisper_full_get_segment_text(context_of(handle), index);
    const jsize length = text == nullptr ? 0 : static_cast<jsize>(strlen(text));
    jbyteArray bytes = env->NewByteArray(length);
    if (bytes != nullptr && length > 0) {
        env->SetByteArrayRegion(bytes, 0, length, reinterpret_cast<const jbyte *>(text));
    }
    // Null only when the array could not be made, with OutOfMemoryError pending.
    return bytes;
}

// Times in hundredths of a second from the start of the piece.
JNIEXPORT jlong JNICALL
Java_org_opensources_umai_speech_data_WhisperNative_segmentStart(JNIEnv * env, jobject, jlong handle, jint index) {
    return check_segment(env, handle, index) ? whisper_full_get_segment_t0(context_of(handle), index) : 0;
}

JNIEXPORT jlong JNICALL
Java_org_opensources_umai_speech_data_WhisperNative_segmentEnd(JNIEnv * env, jobject, jlong handle, jint index) {
    return check_segment(env, handle, index) ? whisper_full_get_segment_t1(context_of(handle), index) : 0;
}

JNIEXPORT jfloat JNICALL
Java_org_opensources_umai_speech_data_WhisperNative_segmentNoSpeech(JNIEnv * env, jobject, jlong handle, jint index) {
    return check_segment(env, handle, index) ? whisper_full_get_segment_no_speech_prob(context_of(handle), index) : 1.0f;
}

JNIEXPORT void JNICALL
Java_org_opensources_umai_speech_data_WhisperNative_free(JNIEnv *, jobject, jlong handle) {
    if (handle != 0) {
        whisper_free(context_of(handle));
    }
}

}  // extern "C"
