
#include <jni.h>
#include <string>
#include <vector>
#include <mutex>
#include <memory>
#include <cstdint>
#include <climits>
#include <algorithm>

#include <android/log.h>
#include "llama.h"

#define LOG_TAG "MonolithNative"

#define LOGI(...) \
    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

#define LOGE(...) \
    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace Monolith {

// ==========================================
// Engine State
// ==========================================

static std::mutex engine_mutex;

static llama_model* model = nullptr;
static const llama_vocab* vocab = nullptr;

static std::vector<llama_context*> contexts;

static llama_context_params context_params;

static bool backend_initialized = false;

// ==========================================
// Configuration
// ==========================================

static constexpr uint32_t MAIN_CONTEXT_SIZE = 2048;
static constexpr uint32_t AGENT_CONTEXT_SIZE = 1024;

static constexpr int MAIN_THREADS = 4;
static constexpr int AGENT_THREADS = 2;

static constexpr int MAX_GENERATION_TOKENS = 512;

// ==========================================
// JNI Helpers
// ==========================================

static jstring makeResult(
    JNIEnv* env,
    const char* message
) {
    return env->NewStringUTF(message);
}

static void releaseContexts() {

    for (llama_context* ctx : contexts) {

        if (ctx != nullptr) {
            llama_free(ctx);
        }
    }

    contexts.clear();
}

static void releaseModel() {

    releaseContexts();

    vocab = nullptr;

    if (model != nullptr) {

        llama_model_free(model);

        model = nullptr;
    }
}

// ==========================================
// Context Management
// ==========================================

static llama_context* createContext(
    llama_model* source_model,
    uint32_t context_size,
    int threads
) {

    if (source_model == nullptr) {
        return nullptr;
    }

    llama_context_params params =
        llama_context_default_params();

    params.n_ctx = context_size;
    params.n_threads = threads;

    return llama_init_from_model(
        source_model,
        params
    );
}

static bool resetContext(size_t agent_id) {

    if (agent_id >= contexts.size()) {
        return false;
    }

    llama_context* old_context = contexts[agent_id];

    if (old_context != nullptr) {

        llama_free(old_context);

        contexts[agent_id] = nullptr;
    }

    uint32_t size =
        agent_id == 0
        ? MAIN_CONTEXT_SIZE
        : AGENT_CONTEXT_SIZE;

    int threads =
        agent_id == 0
        ? MAIN_THREADS
        : AGENT_THREADS;

    llama_context* new_context =
        createContext(model, size, threads);

    if (new_context == nullptr) {

        LOGE("Failed to recreate context for agent %zu",
             agent_id);

        return false;
    }

    contexts[agent_id] = new_context;

    return true;
}

// ==========================================
// Model Loading
// ==========================================

extern "C" JNIEXPORT jstring JNICALL

Java_com_monolith_ModelManager_nativeLoadModel(
    JNIEnv* env,
    jobject,
    jstring modelPath
) {

    if (modelPath == nullptr) {
        return makeResult(env, "مسار النموذج فارغ.");
    }

    const char* path =
        env->GetStringUTFChars(modelPath, nullptr);

    if (path == nullptr) {
        return makeResult(env, "تعذر قراءة مسار النموذج.");
    }

    std::string native_path(path);

    env->ReleaseStringUTFChars(modelPath, path);

    std::lock_guard<std::mutex> lock(engine_mutex);

    try {

        LOGI("Loading model: %s", native_path.c_str());

        releaseModel();

        if (!backend_initialized) {

            llama_backend_init();

            backend_initialized = true;
        }

        llama_model_params params =
            llama_model_default_params();

        model = llama_model_load_from_file(
            native_path.c_str(),
            params
        );

        if (model == nullptr) {

            LOGE("Model loading failed.");

            return makeResult(
                env,
                "فشل تحميل النموذج. تحقق من ملف GGUF."
            );
        }

        vocab = llama_model_get_vocab(model);

        if (vocab == nullptr) {

            releaseModel();

            return makeResult(
                env,
                "تعذر الحصول على مفردات النموذج."
            );
        }

        llama_context* main_context =
            createContext(
                model,
                MAIN_CONTEXT_SIZE,
                MAIN_THREADS
            );

        if (main_context == nullptr) {

            releaseModel();

            return makeResult(
                env,
                "فشل إنشاء السياق الرئيسي."
            );
        }

        contexts.push_back(main_context);

        LOGI("Model loaded successfully.");

        return makeResult(
            env,
            "تم تحميل النموذج بنجاح."
        );

    } catch (...) {

        LOGE("Unexpected error during model loading.");

        releaseModel();

        return makeResult(
            env,
            "حدث خطأ غير متوقع أثناء تحميل النموذج."
        );
    }
}

// ==========================================
// Sub-Agent Creation
// ==========================================

extern "C" JNIEXPORT jint JNICALL

Java_com_monolith_ModelManager_nativeCreateSubAgent(
    JNIEnv*,
    jobject
) {

    std::lock_guard<std::mutex> lock(engine_mutex);

    if (model == nullptr || vocab == nullptr) {
        return -1;
    }

    try {

        llama_context* sub_context =
            createContext(
                model,
                AGENT_CONTEXT_SIZE,
                AGENT_THREADS
            );

        if (sub_context == nullptr) {

            LOGE("Sub-agent context creation failed.");

            return -1;
        }

        contexts.push_back(sub_context);

        int agent_id =
            static_cast<int>(contexts.size() - 1);

        LOGI("Sub-agent created: %d", agent_id);

        return agent_id;

    } catch (...) {

        LOGE("Sub-agent creation exception.");

        return -1;
    }
}

// ==========================================
// Text Generation
// ==========================================

extern "C" JNIEXPORT jstring JNICALL

Java_com_monolith_ModelManager_nativeGenerate(
    JNIEnv* env,
    jobject,
    jstring prompt,
    jint agent_id
) {

    if (prompt == nullptr) {
        return makeResult(env, "النص المدخل فارغ.");
    }

    const char* prompt_chars =
        env->GetStringUTFChars(prompt, nullptr);

    if (prompt_chars == nullptr) {
        return makeResult(env, "تعذر قراءة النص.");
    }

    std::string user_prompt(prompt_chars);

    env->ReleaseStringUTFChars(prompt, prompt_chars);

    if (user_prompt.empty()) {
        return makeResult(env, "لا يمكن توليد إجابة لنص فارغ.");
    }

    std::lock_guard<std::mutex> lock(engine_mutex);

    if (model == nullptr || vocab == nullptr) {

        return makeResult(
            env,
            "النموذج غير محمل."
        );
    }

    if (agent_id < 0 ||
        static_cast<size_t>(agent_id) >= contexts.size()) {

        return makeResult(
            env,
            "معرف الوكيل غير صالح."
        );
    }

    try {

        // ----------------------------------
        // Reset Agent Context
        // ----------------------------------

        if (!resetContext(static_cast<size_t>(agent_id))) {

            return makeResult(
                env,
                "فشل إعادة تهيئة سياق الوكيل."
            );
        }

        llama_context* ctx =
            contexts[agent_id];

        const int n_ctx =
            static_cast<int>(llama_n_ctx(ctx));

        // ----------------------------------
        // Prompt Formatting
        // ----------------------------------

        std::string formatted_prompt =
            "<|im_start|>user\n" +
            user_prompt +
            "<|im_end|>\n"
            "<|im_start|>assistant\n";

        if (formatted_prompt.size() > INT_MAX) {

            return makeResult(
                env,
                "حجم النص المدخل كبير جداً."
            );
        }

        // ----------------------------------
        // Tokenization
        // ----------------------------------

        int32_t prompt_length =
            static_cast<int32_t>(formatted_prompt.size());

        int n_tokens = -llama_tokenize(
            vocab,
            formatted_prompt.c_str(),
            prompt_length,
            nullptr,
            0,
            true,
            true
        );

        if (n_tokens <= 0) {

            return makeResult(
                env,
                "فشل حساب عدد الرموز."
            );
        }

        if (n_tokens >= n_ctx - 1) {

            return makeResult(
                env,
                "النص أطول من سعة سياق النموذج."
            );
        }

        std::vector<llama_token> tokens(n_tokens);

        int actual_tokens = llama_tokenize(
            vocab,
            formatted_prompt.c_str(),
            prompt_length,
            tokens.data(),
            static_cast<int32_t>(tokens.size()),
            true,
            true
        );

        if (actual_tokens <= 0 ||
            actual_tokens > n_tokens) {

            return makeResult(
                env,
                "فشل تحويل النص إلى رموز."
            );
        }

        tokens.resize(actual_tokens);

        // ----------------------------------
        // Prompt Evaluation
        // ----------------------------------

        llama_batch prompt_batch =
            llama_batch_get_one(
                tokens.data(),
                static_cast<int32_t>(tokens.size())
            );

        int decode_result =
            llama_decode(ctx, prompt_batch);

        if (decode_result != 0) {

            LOGE("Prompt decoding failed: %d",
                 decode_result);

            return makeResult(
                env,
                "فشل معالجة النص المدخل."
            );
        }

        // ----------------------------------
        // Sampler Initialization
        // ----------------------------------

        llama_sampler* sampler =
            llama_sampler_chain_init(
                llama_sampler_chain_default_params()
            );

        if (sampler == nullptr) {

            return makeResult(
                env,
                "فشل إنشاء نظام التوليد."
            );
        }

        llama_sampler_chain_add(
            sampler,
            llama_sampler_init_greedy()
        );

        // ----------------------------------
        // Generation
        // ----------------------------------

        std::string output;

        int current_position =
            static_cast<int>(tokens.size());

        int generated = 0;

        while (generated < MAX_GENERATION_TOKENS &&
               current_position < n_ctx - 1) {

            llama_token token =
                llama_sampler_sample(
                    sampler,
                    ctx,
                    -1
                );

            if (llama_vocab_is_eog(vocab, token)) {
                break;
            }

            // ------------------------------
            // Token To Text
            // ------------------------------

            char buffer[256];

            int piece_size =
                llama_token_to_piece(
                    vocab,
                    token,
                    buffer,
                    sizeof(buffer),
                    0,
                    true
                );

            if (piece_size < 0) {

                std::vector<char> large_buffer(
                    static_cast<size_t>(-piece_size)
                );

                piece_size =
                    llama_token_to_piece(
                        vocab,
                        token,
                        large_buffer.data(),
                        static_cast<int32_t>(
                            large_buffer.size()
                        ),
                        0,
                        true
                    );

                if (piece_size > 0) {

                    output.append(
                        large_buffer.data(),
                        piece_size
                    );
                }

            } else if (piece_size > 0) {

                output.append(
                    buffer,
                    piece_size
                );
            }

            // ------------------------------
            // Decode Generated Token
            // ------------------------------

            llama_batch batch =
                llama_batch_init(1, 0, 1);

            batch.n_tokens = 1;

            batch.token[0] = token;
            batch.pos[0] = current_position;

            batch.n_seq_id[0] = 1;
            batch.seq_id[0][0] = 0;

            batch.logits[0] = true;

            int result =
                llama_decode(ctx, batch);

            llama_batch_free(batch);

            if (result != 0) {

                LOGE("Generation decoding failed: %d",
                     result);

                break;
            }

            current_position++;
            generated++;
        }

        // ----------------------------------
        // Cleanup
        // ----------------------------------

        llama_sampler_free(sampler);

        LOGI(
            "Generation completed. Agent=%d Tokens=%d",
            agent_id,
            generated
        );

        return env->NewStringUTF(output.c_str());

    } catch (...) {

        LOGE("Unexpected generation exception.");

        return makeResult(
            env,
            "حدث خطأ داخلي أثناء التوليد."
        );
    }
}

// ==========================================
// Model Unloading
// ==========================================

extern "C" JNIEXPORT void JNICALL

Java_com_monolith_ModelManager_nativeUnloadModel(
    JNIEnv*,
    jobject
) {

    std::lock_guard<std::mutex> lock(engine_mutex);

    LOGI("Unloading native model.");

    releaseModel();

    if (backend_initialized) {

        llama_backend_free();

        backend_initialized = false;
    }

    LOGI("Native model unloaded.");
}

} // namespace Monolith
