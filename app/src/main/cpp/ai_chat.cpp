
#include <jni.h>
#include <android/log.h>

#include <string>
#include <vector>
#include <mutex>
#include <memory>
#include <cstdint>
#include <climits>
#include <algorithm>
#include <limits>

#include "llama.h"

#define LOG_TAG "MonolithNative"

#define LOGI(...) \
    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

#define LOGE(...) \
    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace Monolith {

// =========================================================
// Engine State
// =========================================================

static std::mutex engine_mutex;

static llama_model* model = nullptr;
static const llama_vocab* vocab = nullptr;

static std::vector<llama_context*> contexts;

static bool backend_initialized = false;

// =========================================================
// Configuration
// =========================================================

static constexpr uint32_t MAIN_CONTEXT_SIZE = 2048;
static constexpr uint32_t AGENT_CONTEXT_SIZE = 1024;

static constexpr uint32_t MAX_BATCH_SIZE = 512;

static constexpr int MAIN_THREADS = 4;
static constexpr int AGENT_THREADS = 2;

static constexpr int MAX_GENERATION_TOKENS = 512;

// =========================================================
// JNI Helpers
// =========================================================

static jstring makeResult(
    JNIEnv* env,
    const char* message
) {
    return env->NewStringUTF(message);
}

// Convert standard UTF-8 into UTF-16.
// JNI NewString expects UTF-16, avoiding problems with
// supplementary Unicode characters and emoji.

static void appendUtf16(
    std::u16string& output,
    uint32_t codepoint
) {
    if (codepoint <= 0xFFFF) {
        output.push_back(
            static_cast<char16_t>(codepoint)
        );
    } else {
        codepoint -= 0x10000;

        output.push_back(
            static_cast<char16_t>(
                0xD800 + (codepoint >> 10)
            )
        );

        output.push_back(
            static_cast<char16_t>(
                0xDC00 + (codepoint & 0x3FF)
            )
        );
    }
}

static std::u16string utf8ToUtf16(
    const std::string& input
) {
    std::u16string output;

    size_t i = 0;

    while (i < input.size()) {

        const uint8_t first =
            static_cast<uint8_t>(input[i]);

        uint32_t codepoint = 0;
        size_t count = 0;

        if (first <= 0x7F) {
            codepoint = first;
            count = 1;
        } else if (
            first >= 0xC2 &&
            first <= 0xDF
        ) {
            codepoint = first & 0x1F;
            count = 2;
        } else if (
            first >= 0xE0 &&
            first <= 0xEF
        ) {
            codepoint = first & 0x0F;
            count = 3;
        } else if (
            first >= 0xF0 &&
            first <= 0xF4
        ) {
            codepoint = first & 0x07;
            count = 4;
        } else {
            appendUtf16(output, 0xFFFD);
            ++i;
            continue;
        }

        if (i + count > input.size()) {
            appendUtf16(output, 0xFFFD);
            ++i;
            continue;
        }

        bool valid = true;

        for (size_t j = 1; j < count; ++j) {

            const uint8_t next =
                static_cast<uint8_t>(input[i + j]);

            if ((next & 0xC0) != 0x80) {
                valid = false;
                break;
            }

            codepoint =
                (codepoint << 6) |
                (next & 0x3F);
        }

        if (
            !valid ||
            (count == 2 && codepoint < 0x80) ||
            (count == 3 && codepoint < 0x800) ||
            (count == 4 && codepoint < 0x10000) ||
            codepoint > 0x10FFFF ||
            (codepoint >= 0xD800 &&
             codepoint <= 0xDFFF)
        ) {
            appendUtf16(output, 0xFFFD);
            ++i;
            continue;
        }

        appendUtf16(output, codepoint);
        i += count;
    }

    return output;
}

static jstring makeUtf8Result(
    JNIEnv* env,
    const std::string& message
) {
    const std::u16string converted =
        utf8ToUtf16(message);

    return env->NewString(
        reinterpret_cast<const jchar*>(
            converted.data()
        ),
        static_cast<jsize>(converted.size())
    );
}

// =========================================================
// Resource Management
// =========================================================

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

struct SamplerDeleter {
    void operator()(llama_sampler* sampler) const {
        if (sampler != nullptr) {
            llama_sampler_free(sampler);
        }
    }
};

using SamplerPtr =
    std::unique_ptr<llama_sampler, SamplerDeleter>;

// =========================================================
// Context Management
// =========================================================

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

    params.n_batch = context_size;

    params.n_ubatch = std::min(
        context_size,
        MAX_BATCH_SIZE
    );

    params.n_threads = threads;
    params.n_threads_batch = threads;

    return llama_init_from_model(
        source_model,
        params
    );
}

static bool resetContext(size_t agent_id) {

    if (
        agent_id >= contexts.size() ||
        model == nullptr
    ) {
        return false;
    }

    llama_context* old_context =
        contexts[agent_id];

    if (old_context != nullptr) {

        llama_free(old_context);

        contexts[agent_id] = nullptr;
    }

    const uint32_t size =
        agent_id == 0
        ? MAIN_CONTEXT_SIZE
        : AGENT_CONTEXT_SIZE;

    const int threads =
        agent_id == 0
        ? MAIN_THREADS
        : AGENT_THREADS;

    llama_context* new_context =
        createContext(
            model,
            size,
            threads
        );

    if (new_context == nullptr) {

        LOGE(
            "Failed to recreate context for agent %zu",
            agent_id
        );

        return false;
    }

    contexts[agent_id] = new_context;

    return true;
}

// =========================================================
// Model-Specific Prompt Formatting
// =========================================================

static bool formatPrompt(
    const llama_model* source_model,
    const std::string& user_prompt,
    std::string& formatted_prompt
) {

    if (source_model == nullptr) {
        return false;
    }

    // Ask llama.cpp for the model's own chat template.
    const char* template_text =
        llama_model_chat_template(
            source_model,
            nullptr
        );

    // Some completion models do not provide a chat template.
    // In that case, preserve the user's original prompt
    // instead of forcing an unrelated ChatML format.

    if (
        template_text == nullptr ||
        template_text[0] == '\0'
    ) {
        formatted_prompt = user_prompt;
        return !formatted_prompt.empty();
    }

    llama_chat_message message;

    message.role = "user";
    message.content = user_prompt.c_str();

    const size_t initial_size =
        user_prompt.size() * 2 + 1024;

    if (
        initial_size >
        static_cast<size_t>(
            std::numeric_limits<int32_t>::max()
        )
    ) {
        return false;
    }

    std::vector<char> buffer(
        initial_size,
        '\0'
    );

    int32_t result =
        llama_chat_apply_template(
            template_text,
            &message,
            1,
            true,
            buffer.data(),
            static_cast<int32_t>(buffer.size())
        );

    // The template may require a larger output buffer.
    if (result > static_cast<int32_t>(buffer.size())) {

        if (
            result >=
            std::numeric_limits<int32_t>::max()
        ) {
            return false;
        }

        buffer.assign(
            static_cast<size_t>(result) + 1,
            '\0'
        );

        result = llama_chat_apply_template(
            template_text,
            &message,
            1,
            true,
            buffer.data(),
            static_cast<int32_t>(buffer.size())
        );
    }

    if (result <= 0) {

        LOGE(
            "Chat template application failed: %d",
            result
        );

        return false;
    }

    if (
        static_cast<size_t>(result) >=
        buffer.size()
    ) {
        return false;
    }

    formatted_prompt.assign(
        buffer.data(),
        static_cast<size_t>(result)
    );

    return !formatted_prompt.empty();
}

// =========================================================
// Tokenization
// =========================================================

static bool tokenizePrompt(
    const std::string& prompt,
    std::vector<llama_token>& tokens
) {

    if (vocab == nullptr || prompt.empty()) {
        return false;
    }

    if (
        prompt.size() >
        static_cast<size_t>(
            std::numeric_limits<int32_t>::max()
        )
    ) {
        return false;
    }

    const int32_t prompt_length =
        static_cast<int32_t>(prompt.size());

    const int32_t required =
        llama_tokenize(
            vocab,
            prompt.c_str(),
            prompt_length,
            nullptr,
            0,
            true,
            true
        );

    if (
        required >= 0 ||
        required == INT32_MIN
    ) {
        LOGE(
            "Token count calculation failed: %d",
            required
        );

        return false;
    }

    const int32_t token_count = -required;

    if (token_count <= 0) {
        return false;
    }

    tokens.resize(
        static_cast<size_t>(token_count)
    );

    const int32_t actual =
        llama_tokenize(
            vocab,
            prompt.c_str(),
            prompt_length,
            tokens.data(),
            token_count,
            true,
            true
        );

    if (
        actual <= 0 ||
        actual > token_count
    ) {
        LOGE(
            "Tokenization failed: %d",
            actual
        );

        tokens.clear();
        return false;
    }

    tokens.resize(
        static_cast<size_t>(actual)
    );

    return true;
}

// =========================================================
// Model Loading
// =========================================================

extern "C" JNIEXPORT jstring JNICALL

Java_com_monolith_ModelManager_nativeLoadModel(
    JNIEnv* env,
    jobject,
    jstring modelPath
) {

    if (modelPath == nullptr) {
        return makeResult(
            env,
            "مسار النموذج فارغ."
        );
    }

    const char* path =
        env->GetStringUTFChars(
            modelPath,
            nullptr
        );

    if (path == nullptr) {
        return makeResult(
            env,
            "تعذر قراءة مسار النموذج."
        );
    }

    std::string native_path(path);

    env->ReleaseStringUTFChars(
        modelPath,
        path
    );

    std::lock_guard<std::mutex> lock(
        engine_mutex
    );

    try {

        LOGI(
            "Loading model: %s",
            native_path.c_str()
        );

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
                "فشل تحميل النموذج. تحقق من ملف GGUF والذاكرة المتاحة."
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

        const char* chat_template =
            llama_model_chat_template(
                model,
                nullptr
            );

        LOGI(
            "Model metadata: context=%d embedding=%d layers=%d",
            llama_model_n_ctx_train(model),
            llama_model_n_embd(model),
            llama_model_n_layer(model)
        );

        LOGI(
            "Chat template: %s",
            chat_template != nullptr
                ? "available"
                : "not available"
        );

        LOGI("Model loaded successfully.");

        return makeResult(
            env,
            "تم تحميل النموذج بنجاح."
        );

    } catch (...) {

        LOGE(
            "Unexpected error during model loading."
        );

        releaseModel();

        return makeResult(
            env,
            "حدث خطأ غير متوقع أثناء تحميل النموذج."
        );
    }
}

// =========================================================
// Sub-Agent Creation
// =========================================================

extern "C" JNIEXPORT jint JNICALL

Java_com_monolith_ModelManager_nativeCreateSubAgent(
    JNIEnv*,
    jobject
) {

    std::lock_guard<std::mutex> lock(
        engine_mutex
    );

    if (
        model == nullptr ||
        vocab == nullptr
    ) {
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

            LOGE(
                "Sub-agent context creation failed."
            );

            return -1;
        }

        contexts.push_back(sub_context);

        const int agent_id =
            static_cast<int>(
                contexts.size() - 1
            );

        LOGI(
            "Sub-agent created: %d",
            agent_id
        );

        return agent_id;

    } catch (...) {

        LOGE(
            "Sub-agent creation exception."
        );

        return -1;
    }
}

// =========================================================
// Token Decoding Helper
// =========================================================

static bool decodeGeneratedToken(
    llama_context* ctx,
    llama_token token,
    int current_position
) {

    llama_batch batch =
        llama_batch_init(
            1,
            0,
            1
        );

    if (
        batch.token == nullptr ||
        batch.pos == nullptr ||
        batch.n_seq_id == nullptr ||
        batch.seq_id == nullptr ||
        batch.logits == nullptr
    ) {
        llama_batch_free(batch);
        return false;
    }

    batch.n_tokens = 1;

    batch.token[0] = token;
    batch.pos[0] = current_position;

    batch.n_seq_id[0] = 1;
    batch.seq_id[0][0] = 0;

    batch.logits[0] = true;

    const int result =
        llama_decode(
            ctx,
            batch
        );

    llama_batch_free(batch);

    return result == 0;
}

// =========================================================
// Token To Text
// =========================================================

static bool appendTokenPiece(
    const llama_vocab* source_vocab,
    llama_token token,
    std::string& output
) {

    char buffer[256];

    int32_t piece_size =
        llama_token_to_piece(
            source_vocab,
            token,
            buffer,
            sizeof(buffer),
            0,
            true
        );

    if (piece_size > 0) {

        output.append(
            buffer,
            static_cast<size_t>(piece_size)
        );

        return true;
    }

    if (piece_size == 0) {
        return true;
    }

    if (piece_size == INT32_MIN) {
        return false;
    }

    const size_t required =
        static_cast<size_t>(-piece_size);

    // Prevent an unreasonable allocation.
    if (
        required == 0 ||
        required > 1024 * 1024
    ) {
        return false;
    }

    std::vector<char> large_buffer(
        required
    );

    piece_size =
        llama_token_to_piece(
            source_vocab,
            token,
            large_buffer.data(),
            static_cast<int32_t>(
                large_buffer.size()
            ),
            0,
            true
        );

    if (
        piece_size <= 0 ||
        static_cast<size_t>(piece_size) >
            large_buffer.size()
    ) {
        return false;
    }

    output.append(
        large_buffer.data(),
        static_cast<size_t>(piece_size)
    );

    return true;
}

// =========================================================
// Text Generation
// =========================================================

extern "C" JNIEXPORT jstring JNICALL

Java_com_monolith_ModelManager_nativeGenerate(
    JNIEnv* env,
    jobject,
    jstring prompt,
    jint agent_id
) {

    if (prompt == nullptr) {
        return makeResult(
            env,
            "النص المدخل فارغ."
        );
    }

    const char* prompt_chars =
        env->GetStringUTFChars(
            prompt,
            nullptr
        );

    if (prompt_chars == nullptr) {
        return makeResult(
            env,
            "تعذر قراءة النص."
        );
    }

    std::string user_prompt(prompt_chars);

    env->ReleaseStringUTFChars(
        prompt,
        prompt_chars
    );

    if (user_prompt.empty()) {
        return makeResult(
            env,
            "لا يمكن توليد إجابة لنص فارغ."
        );
    }

    std::lock_guard<std::mutex> lock(
        engine_mutex
    );

    if (
        model == nullptr ||
        vocab == nullptr
    ) {
        return makeResult(
            env,
            "النموذج غير محمل."
        );
    }

    if (
        agent_id < 0 ||
        static_cast<size_t>(agent_id) >=
            contexts.size()
    ) {
        return makeResult(
            env,
            "معرف الوكيل غير صالح."
        );
    }

    try {

        // -------------------------------------------------
        // Reset Context
        // -------------------------------------------------

        if (
            !resetContext(
                static_cast<size_t>(agent_id)
            )
        ) {
            return makeResult(
                env,
                "فشل إعادة تهيئة سياق الوكيل."
            );
        }

        llama_context* ctx =
            contexts[static_cast<size_t>(agent_id)];

        if (ctx == nullptr) {
            return makeResult(
                env,
                "سياق النموذج غير متاح."
            );
        }

        const uint32_t n_ctx =
            llama_n_ctx(ctx);

        if (n_ctx < 2) {
            return makeResult(
                env,
                "سعة سياق النموذج غير صالحة."
            );
        }

        // -------------------------------------------------
        // Model-Specific Prompt
        // -------------------------------------------------

        std::string formatted_prompt;

        if (
            !formatPrompt(
                model,
                user_prompt,
                formatted_prompt
            )
        ) {
            return makeResult(
                env,
                "تعذر تجهيز قالب المحادثة الخاص بالنموذج."
            );
        }

        // -------------------------------------------------
        // Tokenization
        // -------------------------------------------------

        std::vector<llama_token> tokens;

        if (
            !tokenizePrompt(
                formatted_prompt,
                tokens
            )
        ) {
            return makeResult(
                env,
                "فشل تحويل النص إلى رموز."
            );
        }

        if (
            tokens.size() >=
            static_cast<size_t>(n_ctx - 1)
        ) {
            return makeResult(
                env,
                "النص أطول من سعة سياق النموذج."
            );
        }

        // -------------------------------------------------
        // Prompt Evaluation
        // -------------------------------------------------

        llama_batch prompt_batch =
            llama_batch_get_one(
                tokens.data(),
                static_cast<int32_t>(
                    tokens.size()
                )
            );

        const int prompt_result =
            llama_decode(
                ctx,
                prompt_batch
            );

        if (prompt_result != 0) {

            LOGE(
                "Prompt decoding failed: %d",
                prompt_result
            );

            return makeResult(
                env,
                "فشل معالجة النص داخل النموذج."
            );
        }

        // -------------------------------------------------
        // Sampler Initialization
        // -------------------------------------------------

        SamplerPtr sampler(
            llama_sampler_chain_init(
                llama_sampler_chain_default_params()
            )
        );

        if (!sampler) {
            return makeResult(
                env,
                "فشل إنشاء نظام التوليد."
            );
        }

        llama_sampler* greedy =
            llama_sampler_init_greedy();

        if (greedy == nullptr) {
            return makeResult(
                env,
                "فشل تهيئة خوارزمية التوليد."
            );
        }

        // The chain takes ownership of the sampler.
        llama_sampler_chain_add(
            sampler.get(),
            greedy
        );

        // -------------------------------------------------
        // Generation
        // -------------------------------------------------

        std::string output;

        int current_position =
            static_cast<int>(
                tokens.size()
            );

        int generated = 0;

        while (
            generated < MAX_GENERATION_TOKENS &&
            current_position <
                static_cast<int>(n_ctx)
        ) {

            const llama_token token =
                llama_sampler_sample(
                    sampler.get(),
                    ctx,
                    -1
                );

            if (
                llama_vocab_is_eog(
                    vocab,
                    token
                )
            ) {
                break;
            }

            // ---------------------------------------------
            // Convert token into UTF-8 text
            // ---------------------------------------------

            if (
                !appendTokenPiece(
                    vocab,
                    token,
                    output
                )
            ) {
                LOGE(
                    "Token-to-piece conversion failed."
                );

                break;
            }

            // ---------------------------------------------
            // Decode token to obtain next logits
            // ---------------------------------------------

            if (
                !decodeGeneratedToken(
                    ctx,
                    token,
                    current_position
                )
            ) {

                LOGE(
                    "Generated token decoding failed."
                );

                break;
            }

            ++current_position;
            ++generated;
        }

        LOGI(
            "Generation completed. Agent=%d Tokens=%d OutputBytes=%zu",
            agent_id,
            generated,
            output.size()
        );

        if (output.empty()) {

            return makeResult(
                env,
                "لم ينتج النموذج إجابة. تحقق من توافق قالب المحادثة والنموذج."
            );
        }

        return makeUtf8Result(
            env,
            output
        );

    } catch (...) {

        LOGE(
            "Unexpected generation exception."
        );

        return makeResult(
            env,
            "حدث خطأ داخلي أثناء التوليد."
        );
    }
}

// =========================================================
// Model Unloading
// =========================================================

extern "C" JNIEXPORT void JNICALL

Java_com_monolith_ModelManager_nativeUnloadModel(
    JNIEnv*,
    jobject
) {

    std::lock_guard<std::mutex> lock(
        engine_mutex
    );

    LOGI(
        "Unloading native model."
    );

    releaseModel();

    if (backend_initialized) {

        llama_backend_free();

        backend_initialized = false;
    }

    LOGI(
        "Native model unloaded."
    );
}

} // namespace Monolith
