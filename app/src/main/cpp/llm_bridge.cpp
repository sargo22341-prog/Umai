// JNI bridge between LlamaNative (Kotlin) and llama.cpp.
//
// One model is loaded at a time, and every generation is a single turn: the
// context memory is cleared before each prompt. Strings cross the bridge as
// UTF-8 byte arrays, since JNI's own string functions use modified UTF-8,
// which mangles characters outside the BMP.

#include <jni.h>
#include <android/log.h>

#include <algorithm>
#include <exception>
#include <string>
#include <vector>

#include "chat.h"
#include "common.h"
#include "json-schema-to-grammar.h"
#include "llama.h"
#include "sampling.h"

#define LOG_TAG "UmaiLlm"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

struct LoadedModel {
    llama_model * model = nullptr;
    llama_context * context = nullptr;
    common_chat_templates_ptr templates;
    int batch = 0;
};

std::string to_string(JNIEnv * env, jbyteArray bytes) {
    if (bytes == nullptr) return {};
    const jsize length = env->GetArrayLength(bytes);
    std::string value(static_cast<size_t>(length), '\0');
    env->GetByteArrayRegion(bytes, 0, length, reinterpret_cast<jbyte *>(value.data()));
    return value;
}

jbyteArray to_bytes(JNIEnv * env, const std::string & value) {
    jbyteArray bytes = env->NewByteArray(static_cast<jsize>(value.size()));
    env->SetByteArrayRegion(bytes, 0, static_cast<jsize>(value.size()),
                            reinterpret_cast<const jbyte *>(value.data()));
    return bytes;
}

LoadedModel * from_handle(jlong handle) {
    return reinterpret_cast<LoadedModel *>(handle);
}

void log_to_android(ggml_log_level level, const char * text, void *) {
    if (level == GGML_LOG_LEVEL_ERROR) {
        __android_log_write(ANDROID_LOG_ERROR, LOG_TAG, text);
    } else if (level == GGML_LOG_LEVEL_WARN) {
        __android_log_write(ANDROID_LOG_WARN, LOG_TAG, text);
    } else if (level == GGML_LOG_LEVEL_INFO) {
        __android_log_write(ANDROID_LOG_INFO, LOG_TAG, text);
    }
}

// Returns false when the listener asks to stop.
bool report(JNIEnv * env, jobject listener, jmethodID method, jint prompt_done, jint prompt_total, jint generated) {
    const jboolean keep_going = env->CallBooleanMethod(listener, method, prompt_done, prompt_total, generated);
    if (env->ExceptionCheck()) return false;
    return keep_going == JNI_TRUE;
}

}  // namespace

extern "C" JNIEXPORT void JNICALL
Java_org_opensources_umai_llm_data_LlamaNative_nativeInit(JNIEnv * env, jobject, jstring native_lib_dir) {
    llama_log_set(log_to_android, nullptr);
    const char * dir = env->GetStringUTFChars(native_lib_dir, nullptr);
    ggml_backend_load_all_from_path(dir);
    env->ReleaseStringUTFChars(native_lib_dir, dir);
    llama_backend_init();
    LOGI("%s", llama_print_system_info());
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_opensources_umai_llm_data_LlamaNative_nativeSystemInfo(JNIEnv * env, jobject) {
    return env->NewStringUTF(llama_print_system_info());
}

extern "C" JNIEXPORT jlong JNICALL
Java_org_opensources_umai_llm_data_LlamaNative_nativeLoad(
        JNIEnv * env, jobject, jstring jpath, jint context_size, jint threads) {
    const char * path = env->GetStringUTFChars(jpath, nullptr);
    llama_model_params model_params = llama_model_default_params();
    // The CPU backend rearranges the weights for its matrix instructions into
    // memory of its own: a mapped file would stay resident next to that copy.
    model_params.load_mode = LLAMA_LOAD_MODE_NONE;
    llama_model * model = llama_model_load_from_file(path, model_params);
    env->ReleaseStringUTFChars(jpath, path);
    if (model == nullptr) {
        LOGE("model could not be loaded");
        return 0;
    }

    llama_context_params context_params = llama_context_default_params();
    context_params.n_ctx = static_cast<uint32_t>(context_size);
    context_params.n_batch = 512;
    context_params.n_ubatch = 512;
    context_params.n_threads = threads;
    context_params.n_threads_batch = threads;
    llama_context * context = llama_init_from_model(model, context_params);
    if (context == nullptr) {
        LOGE("context could not be created");
        llama_model_free(model);
        return 0;
    }

    auto * loaded = new LoadedModel();
    loaded->model = model;
    loaded->context = context;
    loaded->batch = static_cast<int>(context_params.n_batch);
    try {
        loaded->templates = common_chat_templates_init(model, "");
    } catch (const std::exception & e) {
        LOGE("chat template: %s", e.what());
        llama_free(context);
        llama_model_free(model);
        delete loaded;
        return 0;
    }
    return reinterpret_cast<jlong>(loaded);
}

extern "C" JNIEXPORT void JNICALL
Java_org_opensources_umai_llm_data_LlamaNative_nativeFree(JNIEnv *, jobject, jlong handle) {
    LoadedModel * loaded = from_handle(handle);
    if (loaded == nullptr) return;
    loaded->templates.reset();
    llama_free(loaded->context);
    llama_model_free(loaded->model);
    delete loaded;
}

extern "C" JNIEXPORT jint JNICALL
Java_org_opensources_umai_llm_data_LlamaNative_nativeContextSize(JNIEnv *, jobject, jlong handle) {
    return static_cast<jint>(llama_n_ctx(from_handle(handle)->context));
}

// The system and user messages laid out with the model's own chat template,
// its thinking phase turned off: the answers are short structured data.
extern "C" JNIEXPORT jbyteArray JNICALL
Java_org_opensources_umai_llm_data_LlamaNative_nativeFormat(
        JNIEnv * env, jobject, jlong handle, jbyteArray jsystem, jbyteArray juser) {
    LoadedModel * loaded = from_handle(handle);
    try {
        common_chat_msg system;
        system.role = "system";
        system.content = to_string(env, jsystem);
        common_chat_msg user;
        user.role = "user";
        user.content = to_string(env, juser);

        common_chat_templates_inputs inputs;
        inputs.messages = {system, user};
        inputs.add_generation_prompt = true;
        inputs.use_jinja = true;
        inputs.enable_thinking = false;
        const common_chat_params params = common_chat_templates_apply(loaded->templates.get(), inputs);
        return to_bytes(env, params.prompt);
    } catch (const std::exception & e) {
        LOGE("chat format: %s", e.what());
        return nullptr;
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_org_opensources_umai_llm_data_LlamaNative_nativeTokenCount(
        JNIEnv * env, jobject, jlong handle, jbyteArray jtext) {
    const std::vector<llama_token> tokens =
            common_tokenize(from_handle(handle)->context, to_string(env, jtext), true, true);
    return static_cast<jint>(tokens.size());
}

// A GBNF grammar that only lets the model write JSON matching the schema.
extern "C" JNIEXPORT jbyteArray JNICALL
Java_org_opensources_umai_llm_data_LlamaNative_nativeSchemaToGrammar(
        JNIEnv * env, jobject, jbyteArray jschema) {
    try {
        const common_json schema = common_json::parse(to_string(env, jschema));
        return to_bytes(env, json_schema_to_grammar(schema));
    } catch (const std::exception & e) {
        LOGE("schema: %s", e.what());
        return nullptr;
    }
}

// Runs [prompt] and returns what the model wrote, or null when it failed or
// the listener stopped it. The listener hears about the prompt as it is read,
// then about each generated token.
extern "C" JNIEXPORT jbyteArray JNICALL
Java_org_opensources_umai_llm_data_LlamaNative_nativeGenerate(
        JNIEnv * env, jobject, jlong handle, jbyteArray jprompt, jbyteArray jgrammar,
        jint max_tokens, jfloat temperature, jint seed, jobject listener) {
    LoadedModel * loaded = from_handle(handle);
    jclass listener_class = env->GetObjectClass(listener);
    jmethodID on_progress = env->GetMethodID(listener_class, "onProgress", "(III)Z");

    common_sampler * sampler = nullptr;
    try {
        llama_context * context = loaded->context;
        const llama_vocab * vocab = llama_model_get_vocab(loaded->model);
        llama_memory_clear(llama_get_memory(context), true);

        const std::vector<llama_token> prompt = common_tokenize(context, to_string(env, jprompt), true, true);
        const int context_size = static_cast<int>(llama_n_ctx(context));
        if (prompt.empty() || static_cast<int>(prompt.size()) + max_tokens > context_size) {
            LOGE("prompt of %d tokens does not fit a context of %d", static_cast<int>(prompt.size()), context_size);
            return nullptr;
        }

        const int total = static_cast<int>(prompt.size());
        for (int start = 0; start < total; start += loaded->batch) {
            const int count = std::min(loaded->batch, total - start);
            llama_batch batch = llama_batch_get_one(const_cast<llama_token *>(prompt.data()) + start, count);
            if (llama_decode(context, batch) != 0) {
                LOGE("prompt decoding failed");
                return nullptr;
            }
            if (!report(env, listener, on_progress, start + count, total, 0)) return nullptr;
        }

        common_params_sampling params;
        params.temp = temperature;
        params.seed = static_cast<uint32_t>(seed);
        params.top_k = 40;
        params.top_p = 0.9f;
        params.min_p = 0.05f;
        const std::string grammar = to_string(env, jgrammar);
        if (!grammar.empty()) {
            params.grammar = common_grammar(COMMON_GRAMMAR_TYPE_USER, grammar);
        }
        sampler = common_sampler_init(loaded->model, params);
        if (sampler == nullptr) {
            LOGE("sampler could not be created");
            return nullptr;
        }

        std::string output;
        for (int generated = 0; generated < max_tokens; generated++) {
            llama_token token = common_sampler_sample(sampler, context, -1);
            common_sampler_accept(sampler, token, true);
            if (llama_vocab_is_eog(vocab, token)) break;
            output += common_token_to_piece(context, token, false);
            if (!report(env, listener, on_progress, total, total, generated + 1)) {
                common_sampler_free(sampler);
                return nullptr;
            }
            llama_batch next = llama_batch_get_one(&token, 1);
            if (llama_decode(context, next) != 0) {
                LOGE("generation decoding failed");
                common_sampler_free(sampler);
                return nullptr;
            }
        }
        common_sampler_free(sampler);
        return to_bytes(env, output);
    } catch (const std::exception & e) {
        LOGE("generation: %s", e.what());
        if (sampler != nullptr) common_sampler_free(sampler);
        return nullptr;
    }
}
