// JNI-обёртка llama.cpp для приложения: загрузка GGUF, генерация с потоковой отдачей
// токенов и контролем памяти. Модель грузится через mmap, поэтому страницы файла
// остаются вытесняемыми — это главная защита от OOM на телефоне.
#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>

#include "llama.h"

#define LOG_TAG "OgeLlm"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

JavaVM *g_vm = nullptr;

struct Handle {
    llama_model *model = nullptr;
    llama_context *ctx = nullptr;
    const llama_vocab *vocab = nullptr;
    int n_ctx = 0;
    int n_past = 0;
};

struct Callback {
    JNIEnv *env = nullptr;
    jobject obj = nullptr;
    jmethodID onToken = nullptr;
    jmethodID allow = nullptr;
};

std::string to_string(JNIEnv *env, jstring value) {
    if (value == nullptr) return {};
    const char *chars = env->GetStringUTFChars(value, nullptr);
    std::string result(chars == nullptr ? "" : chars);
    if (chars != nullptr) env->ReleaseStringUTFChars(value, chars);
    return result;
}

}  // namespace

extern "C" JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *) {
    g_vm = vm;
    llama_backend_init();
    LOGI("llama.cpp backend initialized");
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT jlong JNICALL
Java_ru_tolmachev_ogetrainer_data_llm_LlmEngine_nativeLoad(
        JNIEnv *env, jclass, jstring path, jint n_ctx, jint n_threads, jboolean use_mmap) {
    const std::string file = to_string(env, path);
    if (file.empty()) return 0;

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0;
    // В новых версиях llama.cpp режим загрузки задаётся через load_mode:
    // mmap оставляет страницы файла вытесняемыми — это и есть защита от OOM.
    mparams.load_mode = use_mmap == JNI_TRUE ? LLAMA_LOAD_MODE_MMAP : LLAMA_LOAD_MODE_NONE;

    llama_model *model = llama_model_load_from_file(file.c_str(), mparams);
    if (model == nullptr) {
        LOGE("не удалось загрузить модель %s", file.c_str());
        return 0;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = static_cast<uint32_t>(n_ctx);
    cparams.n_batch = 128;
    cparams.n_ubatch = 128;
    cparams.n_threads = n_threads;
    cparams.n_threads_batch = n_threads;
    cparams.no_perf = true;

    llama_context *ctx = llama_init_from_model(model, cparams);
    if (ctx == nullptr) {
        LOGE("не удалось создать контекст (n_ctx=%d)", n_ctx);
        llama_model_free(model);
        return 0;
    }

    auto *handle = new Handle();
    handle->model = model;
    handle->ctx = ctx;
    handle->vocab = llama_model_get_vocab(model);
    handle->n_ctx = n_ctx;
    handle->n_past = 0;
    LOGI("модель загружена: n_ctx=%d threads=%d mmap=%d", n_ctx, n_threads, use_mmap == JNI_TRUE);
    return reinterpret_cast<jlong>(handle);
}

extern "C" JNIEXPORT jint JNICALL
Java_ru_tolmachev_ogetrainer_data_llm_LlmEngine_nativeContextSize(JNIEnv *, jclass, jlong ptr) {
    auto *handle = reinterpret_cast<Handle *>(ptr);
    return handle == nullptr ? 0 : handle->n_ctx;
}

extern "C" JNIEXPORT jlong JNICALL
Java_ru_tolmachev_ogetrainer_data_llm_LlmEngine_nativeModelBytes(JNIEnv *, jclass, jlong ptr) {
    auto *handle = reinterpret_cast<Handle *>(ptr);
    if (handle == nullptr) return 0;
    return static_cast<jlong>(llama_model_size(handle->model));
}

/**
 * Генерация. Возвращает текст ответа и отдаёт токены в колбэк по мере появления.
 * Каждые 16 токенов спрашивает у Kotlin `allow()`: если память на исходе —
 * генерация останавливается, не доводя процесс до OOM.
 */
extern "C" JNIEXPORT jstring JNICALL
Java_ru_tolmachev_ogetrainer_data_llm_LlmEngine_nativeGenerate(
        JNIEnv *env, jclass, jlong ptr, jstring prompt, jint max_tokens, jfloat temperature,
        jint top_k, jfloat top_p, jobject callback) {
    auto *handle = reinterpret_cast<Handle *>(ptr);
    if (handle == nullptr || handle->ctx == nullptr) {
        return env->NewStringUTF("");
    }

    Callback cb;
    cb.env = env;
    cb.obj = callback;
    if (callback != nullptr) {
        jclass cls = env->GetObjectClass(callback);
        cb.onToken = env->GetMethodID(cls, "onToken", "(Ljava/lang/String;)V");
        cb.allow = env->GetMethodID(cls, "allow", "()Z");
    }

    const std::string text = to_string(env, prompt);
    std::vector<llama_token> tokens(text.size() + 32);
    int n_tokens = llama_tokenize(handle->vocab, text.c_str(), static_cast<int32_t>(text.size()),
                                  tokens.data(), static_cast<int32_t>(tokens.size()), true, true);
    if (n_tokens < 0) {
        tokens.resize(-n_tokens);
        n_tokens = llama_tokenize(handle->vocab, text.c_str(), static_cast<int32_t>(text.size()),
                                  tokens.data(), static_cast<int32_t>(tokens.size()), true, true);
    }
    if (n_tokens <= 0) {
        return env->NewStringUTF("");
    }

    // Оставляем место под ответ: слишком длинный промпт обрезаем слева.
    const int room = handle->n_ctx - max_tokens - 8;
    if (room <= 0) {
        return env->NewStringUTF("");
    }
    if (n_tokens > room) {
        tokens.erase(tokens.begin(), tokens.begin() + (n_tokens - room));
        n_tokens = room;
    }

    llama_memory_clear(llama_get_memory(handle->ctx), true);

    // Промпт подаём кусками по n_batch, иначе decode отвергнет большой батч.
    const int batch = 128;
    for (int start = 0; start < n_tokens; start += batch) {
        const int count = std::min(batch, n_tokens - start);
        llama_batch chunk = llama_batch_get_one(tokens.data() + start, count);
        if (llama_decode(handle->ctx, chunk) != 0) {
            LOGE("decode промпта не удался на позиции %d", start);
            return env->NewStringUTF("");
        }
    }
    handle->n_past = n_tokens;

    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    sparams.no_perf = true;
    llama_sampler *sampler = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(top_k));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(top_p, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    std::string output;
    std::vector<char> piece(256);
    for (int produced = 0; produced < max_tokens; ++produced) {
        if (produced > 0 && produced % 16 == 0 && cb.allow != nullptr) {
            const jboolean ok = env->CallBooleanMethod(cb.obj, cb.allow);
            if (env->ExceptionCheck()) {
                env->ExceptionClear();
            } else if (ok != JNI_TRUE) {
                LOGI("генерация остановлена контролем памяти на %d токенах", produced);
                break;
            }
        }

        const llama_token id = llama_sampler_sample(sampler, handle->ctx, -1);
        if (llama_vocab_is_eog(handle->vocab, id)) {
            break;
        }

        int len = llama_token_to_piece(handle->vocab, id, piece.data(),
                                       static_cast<int32_t>(piece.size()), 0, true);
        if (len < 0) {
            piece.resize(-len);
            len = llama_token_to_piece(handle->vocab, id, piece.data(),
                                       static_cast<int32_t>(piece.size()), 0, true);
        }
        if (len <= 0) continue;

        output.append(piece.data(), static_cast<size_t>(len));
        if (cb.onToken != nullptr) {
            jstring chunk = env->NewStringUTF(std::string(piece.data(), len).c_str());
            env->CallVoidMethod(cb.obj, cb.onToken, chunk);
            env->DeleteLocalRef(chunk);
            if (env->ExceptionCheck()) env->ExceptionClear();
        }

        llama_batch next = llama_batch_get_one(const_cast<llama_token *>(&id), 1);
        if (llama_decode(handle->ctx, next) != 0) {
            LOGE("decode ответа не удался на токене %d", produced);
            break;
        }
        handle->n_past += 1;

        if (handle->n_past >= handle->n_ctx - 4) {
            LOGI("контекст исчерпан на %d токенах", produced);
            break;
        }
    }

    llama_sampler_free(sampler);
    return env->NewStringUTF(output.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_ru_tolmachev_ogetrainer_data_llm_LlmEngine_nativeFree(JNIEnv *, jclass, jlong ptr) {
    auto *handle = reinterpret_cast<Handle *>(ptr);
    if (handle == nullptr) return;
    if (handle->ctx != nullptr) llama_free(handle->ctx);
    if (handle->model != nullptr) llama_model_free(handle->model);
    delete handle;
    LOGI("модель выгружена");
}
