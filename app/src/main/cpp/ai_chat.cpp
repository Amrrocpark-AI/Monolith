
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

static void appendUtf8(std::string& out, uint32_t cp) {
    if (cp <= 0x7F) {
        out.push_back(static_cast<char>(cp));
    } else if (cp <= 0x7FF) {
        out.push_back(static_cast<char>(0xC0 | (cp >> 6)));
        out.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
    } else if (cp <= 0xFFFF) {
        out.push_back(static_cast<char>(0xE0 | (cp >> 12)));
        out.push_back(static_cast<char>(0x80 | ((cp >> 6) & 0x3F)));
        out.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
    } else {
        out.push_back(static_cast<char>(0xF0 | (cp >> 18)));
        out.push_back(static_cast<char>(0x80 | ((cp >> 12) & 0x3F)));
        out.push_back(static_cast<char>(0x80 | ((cp >> 6) & 0x3F)));
        out.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
    }
}

static std::string jstringToUtf8(JNIEnv* env, jstring value) {
    std::string out;

    if (value == nullptr) {
        return out;
    }

    const jsize length = env->GetStringLength(value);
    const jchar* chars = env->GetStringChars(value, nullptr);

    if (chars == nullptr) {
        return out;
    }

    out.reserve(static_cast<size_t>(length) * 3);

    for (jsize i = 0; i < length; ++i) {
        uint32_t cp = chars[i];

        if (cp >= 0xD800 && cp <= 0xDBFF && i + 1 < length) {
            const uint32_t low = chars[i + 1];

            if (low >= 0xDC00 && low <= 0xDFFF) {
                cp = 0x10000 +
                    ((cp - 0xD800) << 10) +
                    (low - 0xDC00);

                ++i;
            } else {
                cp = 0xFFFD;
            }
        } else if (cp >= 0xD800 && cp <= 0xDFFF) {
            cp = 0xFFFD;
        }

        appendUtf8(out, cp);
    }

    env->ReleaseStringChars(value, chars);

    return out;
}

static jstring makeResult(
    JNIEnv* env,
    const char* message
) {
    return env->NewStringUTF(message);
}

// =========================================================
// UTF-8 To UTF-16
// =========================================================

static void appendUtf16(
    std::u16string& output,
    uint32_t codepoint
) {
    if (codepoint <= 0xFFFF) {
        output.push_back(static_cast<char16_t>(codepoint));
    } else {
        codepoint -= 0x10000;

        output.push_back(
            static_cast<char16_t>(0xD800 + (codepoint >> 10))
        );

        output.push_back(
            static_cast<char16_t>(0xDC00 + (codepoint & 0x3FF))
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
        } else if (first >= 0xC2 && first <= 0xDF) {
            codepoint = first & 0x1F;
            count = 2;
        } else if (first >= 0xE0 && first <= 0xEF) {
            codepoint = first & 0x0F;
            count = 3;
        } else if (first >= 0xF0 && first <= 0xF4) {
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
            (codepoint >= 0xD800 && codepoint <= 0xDFFF)
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

    if (
        converted.size() >
        static_cast<size_t>(std::numeric_limits<jsize>::max())
    ) {
        return makeResult(
            env,
            "حجم الإجابة أكبر من الحد المسموح."
        );
    }

    return env->NewString(
        reinterpret_cast<const jchar*>(converted.data()),
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

    // Adapt the requested context to the model's training limit.
    const uint32_t trained_context =
        llama_model_n_ctx_train(source_model);

    if (trained_context > 0) {
        context_size = std::min(
            context_size,
            trained_context
        );
    }

    if (context_size < 2) {
        return nullptr;
    }

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

    llama_context* old_context = contexts[agent_id];

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
        createContext(model, size, threads);

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


struct ChatPromptMessage {
    std::string role;
    std::string content;
};

static bool formatPrompt(
    const llama_model* source_model,
    const std::vector<ChatPromptMessage>& messages,
    std::string& formatted_prompt
) {
    if (source_model == nullptr || messages.empty()) {
        return false;
    }

    std::vector<llama_chat_message> llama_messages;
    llama_messages.reserve(messages.size());

    size_t total_size = 0;

    for (const auto& message : messages) {
        if (
            message.role.empty() ||
            message.content.empty()
        ) {
            return false;
        }

        if (
            message.role != "system" &&
            message.role != "user" &&
            message.role != "assistant" &&
            message.role != "tool"
        ) {
            return false;
        }

        if (
            message.content.size() >
            static_cast<size_t>(
                std::numeric_limits<int32_t>::max()
            ) - total_size
        ) {
            return false;
        }

        total_size += message.content.size();

        llama_chat_message item;
        item.role = message.role.c_str();
        item.content = message.content.c_str();

        llama_messages.push_back(item);
    }

    const char* tmpl =
        llama_model_chat_template(source_model, nullptr);

    if (tmpl == nullptr || tmpl[0] == '\0') {
        formatted_prompt.clear();

        for (const auto& message : messages) {
            formatted_prompt += message.role;
            formatted_prompt += ":\n";
            formatted_prompt += message.content;
            formatted_prompt += "\n\n";
        }

        return !formatted_prompt.empty();
    }

    if (
        total_size >
        static_cast<size_t>(
            (std::numeric_limits<int32_t>::max() - 1024) / 2
        )
    ) {
        return false;
    }

    size_t capacity = total_size * 2 + 1024;

    std::vector<char> buffer(capacity, '\0');

    int32_t written = llama_chat_apply_template(
        tmpl,
        llama_messages.data(),
        static_cast<int32_t>(llama_messages.size()),
        true,
        buffer.data(),
        static_cast<int32_t>(buffer.size())
    );

    if (written > static_cast<int32_t>(buffer.size())) {
        if (written == INT32_MAX) {
            return false;
        }

        buffer.assign(
            static_cast<size_t>(written) + 1,
            '\0'
        );

        written = llama_chat_apply_template(
            tmpl,
            llama_messages.data(),
            static_cast<int32_t>(llama_messages.size()),
            true,
            buffer.data(),
            static_cast<int32_t>(buffer.size())
        );
    }

    if (
        written > 0 &&
        static_cast<size_t>(written) < buffer.size()
    ) {
        formatted_prompt.assign(
            buffer.data(),
            static_cast<size_t>(written)
        );

        return !formatted_prompt.empty();
    }

    LOGE(
        "Multi-message chat template failed (%d).",
        written
    );

    return false;
}

// Backward-compatible single-user-message interface.
static bool formatPrompt(
    const llama_model* source_model,
    const std::string& user_prompt,
    std::string& formatted_prompt
) {
    if (user_prompt.empty()) {
        return false;
    }

    const std::vector<ChatPromptMessage> messages = {
        {"user", user_prompt}
    };

    return formatPrompt(
        source_model,
        messages,
        formatted_prompt
    );
}
    /*
    if (tmpl == nullptr || tmpl[0] == '\0') {
        
        LOGI("No model chat template; using raw prompt.");

        return true;
    }

    if (
        user_prompt.size() >
        static_cast<size_t>(
            (std::numeric_limits<int32_t>::max() - 1024) / 2
        )
    ) {
        return false;
    }

    llama_chat_message message;

    message.role = "user";
    message.content = user_prompt.c_str();

    size_t capacity =
        user_prompt.size() * 2 + 1024;

    std::vector<char> buffer(
        capacity,
        '\0'
    );

    int32_t written =
        llama_chat_apply_template(
            tmpl,
            &message,
            1,
            true,
            buffer.data(),
            static_cast<int32_t>(buffer.size())
        );

    if (written > static_cast<int32_t>(buffer.size())) {
        if (written == INT32_MAX) {
            return false;
        }

        buffer.assign(
            static_cast<size_t>(written) + 1,
            '\0'
        );

        written = llama_chat_apply_template(
            tmpl,
            &message,
            1,
            true,
            buffer.data(),
            static_cast<int32_t>(buffer.size())
        );
    }

    if (
        written > 0 &&
        static_cast<size_t>(written) < buffer.size()
    ) {
        formatted_prompt.assign(
            buffer.data(),
            static_cast<size_t>(written)
        );

        if (!formatted_prompt.empty()) {
            LOGI("Model chat template applied.");
            return true;
        }
    }

    // This llama.cpp build supports a defined subset of templates.
    // If the model's template is unsupported, raw prompt is safer
    // than guessing a different format.
    LOGE(
        "Chat template failed or is unsupported (%d); using raw prompt.",
        written
    );

        formatted_prompt = user_prompt;
*/

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

    if (required >= 0 || required == INT32_MIN) {
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

    std::string native_path =
        jstringToUtf8(env, modelPath);

    if (native_path.empty()) {
        return makeResult(
            env,
            "تعذر قراءة مسار النموذج."
        );
    }

    std::lock_guard<std::mutex> lock(engine_mutex);

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

        try {
            contexts.push_back(main_context);
        } catch (...) {
            llama_free(main_context);
            throw;
        }

        const char* chat_template =
            llama_model_chat_template(
                model,
                nullptr
            );

        LOGI(
            "Model metadata: context=%u embedding=%d layers=%d",
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
        LOGE("Unexpected error during model loading.");

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

        try {
            contexts.push_back(sub_context);
        } catch (...) {
            llama_free(sub_context);
            throw;
        }

        const int agent_id =
            static_cast<int>(contexts.size() - 1);

        LOGI(
            "Sub-agent created: %d",
            agent_id
        );

        return agent_id;

    } catch (...) {
        LOGE("Sub-agent creation exception.");
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
        llama_batch_init(1, 0, 1);

    if (
        batch.token == nullptr ||
        batch.pos == nullptr ||
        batch.n_seq_id == nullptr ||
        batch.seq_id == nullptr ||
        batch.seq_id[0] == nullptr ||
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
        llama_decode(ctx, batch);

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

    if (
        required == 0 ||
        required > 1024 * 1024
    ) {
        return false;
    }

    std::vector<char> large_buffer(required);

    piece_size =
        llama_token_to_piece(
            source_vocab,
            token,
            large_buffer.data(),
            static_cast<int32_t>(large_buffer.size()),
            0,
            true
        );

    if (
        piece_size <= 0 ||
        static_cast<size_t>(piece_size) > large_buffer.size()
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
// JNI Chat Messages Bridge
// =========================================================

static bool readChatMessages(
    JNIEnv* env,
    jobjectArray roles,
    jobjectArray contents,
    std::vector<ChatPromptMessage>& messages
) {
    if (
        env == nullptr ||
        roles == nullptr ||
        contents == nullptr
    ) {
        return false;
    }

    const jsize role_count =
        env->GetArrayLength(roles);

    const jsize content_count =
        env->GetArrayLength(contents);

    if (
        role_count <= 0 ||
        role_count != content_count
    ) {
        return false;
    }

    messages.clear();
    messages.reserve(
        static_cast<size_t>(role_count)
    );

    for (jsize i = 0; i < role_count; ++i) {
        jstring java_role =
            static_cast<jstring>(
                env->GetObjectArrayElement(roles, i)
            );

        jstring java_content =
            static_cast<jstring>(
                env->GetObjectArrayElement(contents, i)
            );

        if (
            java_role == nullptr ||
            java_content == nullptr
        ) {
            if (java_role != nullptr) {
                env->DeleteLocalRef(java_role);
            }

            if (java_content != nullptr) {
                env->DeleteLocalRef(java_content);
            }

            return false;
        }

        ChatPromptMessage message;

        message.role = jstringToUtf8(
            env,
            java_role
        );

        message.content = jstringToUtf8(
            env,
            java_content
        );

        env->DeleteLocalRef(java_role);
        env->DeleteLocalRef(java_content);

        if (
            message.role.empty() ||
            message.content.empty()
        ) {
            return false;
        }

        if (
            message.role != "system" &&
            message.role != "user" &&
            message.role != "assistant" &&
            message.role != "tool"
        ) {
            return false;
        }

        messages.push_back(
            std::move(message)
        );
    }

    return !messages.empty();
}

// =========================================================
// Shared Text Generation Engine
// =========================================================

// The caller must hold engine_mutex before calling this function.
static jstring generateFromFormattedPrompt(
    JNIEnv* env,
    const std::string& formatted_prompt,
    jint agent_id
) {
    if (model == nullptr || vocab == nullptr) {
        return makeResult(
            env,
            "النموذج غير محمل."
        );
    }

    if (
        agent_id < 0 ||
        static_cast<size_t>(agent_id) >= contexts.size()
    ) {
        return makeResult(
            env,
            "معرف الوكيل غير صالح."
        );
    }

    if (formatted_prompt.empty()) {
        return makeResult(
            env,
            "النص المجهز للتوليد فارغ."
        );
    }

    try {
        if (!resetContext(static_cast<size_t>(agent_id))) {
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

        const uint32_t n_ctx = llama_n_ctx(ctx);

        if (n_ctx < 2) {
            return makeResult(
                env,
                "سعة سياق النموذج غير صالحة."
            );
        }

        std::vector<llama_token> tokens;

        if (!tokenizePrompt(formatted_prompt, tokens)) {
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

        llama_batch prompt_batch =
            llama_batch_get_one(
                tokens.data(),
                static_cast<int32_t>(tokens.size())
            );

        const int prompt_result =
            llama_decode(ctx, prompt_batch);

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

        llama_sampler_chain_add(
            sampler.get(),
            greedy
        );

        std::string output;

        int current_position =
            static_cast<int>(tokens.size());

        int generated = 0;

        while (
            generated < MAX_GENERATION_TOKENS &&
            current_position < static_cast<int>(n_ctx)
        ) {
            const llama_token token =
                llama_sampler_sample(
                    sampler.get(),
                    ctx,
                    -1
                );

            if (llama_vocab_is_eog(vocab, token)) {
                break;
            }

            if (!appendTokenPiece(vocab, token, output)) {
                LOGE("Token-to-piece conversion failed.");
                break;
            }

            if (
                !decodeGeneratedToken(
                    ctx,
                    token,
                    current_position
                )
            ) {
                LOGE("Generated token decoding failed.");
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

        return makeUtf8Result(env, output);

    } catch (...) {
        LOGE("Unexpected generation exception.");

        return makeResult(
            env,
            "حدث خطأ داخلي أثناء التوليد."
        );
    }
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

    std::string user_prompt =
        jstringToUtf8(env, prompt);

    if (user_prompt.empty()) {
        return makeResult(
            env,
            "لا يمكن توليد إجابة لنص فارغ."
        );
    }

    std::lock_guard<std::mutex> lock(engine_mutex);

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

    return generateFromFormattedPrompt(
        env,
        formatted_prompt,
        agent_id
    );
}
    /*
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

    std::string user_prompt =
        jstringToUtf8(env, prompt);

    if (user_prompt.empty()) {
        return makeResult(
            env,
            "لا يمكن توليد إجابة لنص فارغ."
        );
    }

    std::lock_guard<std::mutex> lock(engine_mutex);

    if (model == nullptr || vocab == nullptr) {
        return makeResult(
            env,
            "النموذج غير محمل."
        );
    }

    if (
        agent_id < 0 ||
        static_cast<size_t>(agent_id) >= contexts.size()
    ) {
        return makeResult(
            env,
            "معرف الوكيل غير صالح."
        );
    }

    try {
        // Reset context for a clean generation.
        if (!resetContext(static_cast<size_t>(agent_id))) {
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

        const uint32_t n_ctx = llama_n_ctx(ctx);

        if (n_ctx < 2) {
            return makeResult(
                env,
                "سعة سياق النموذج غير صالحة."
            );
        }

        // Model-specific prompt formatting.
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

        // Tokenization.
        std::vector<llama_token> tokens;

        if (!tokenizePrompt(formatted_prompt, tokens)) {
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

        // Evaluate prompt.
        llama_batch prompt_batch =
            llama_batch_get_one(
                tokens.data(),
                static_cast<int32_t>(tokens.size())
            );

        const int prompt_result =
            llama_decode(ctx, prompt_batch);

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

        // Initialize sampler.
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

        // Sampler chain takes ownership.
        llama_sampler_chain_add(
            sampler.get(),
            greedy
        );

        // Generation loop.
        std::string output;

        int current_position =
            static_cast<int>(tokens.size());

        int generated = 0;

        while (
            generated < MAX_GENERATION_TOKENS &&
            current_position < static_cast<int>(n_ctx)
        ) {
            const llama_token token =
                llama_sampler_sample(
                    sampler.get(),
                    ctx,
                    -1
                );

            if (llama_vocab_is_eog(vocab, token)) {
                break;
            }

            if (!appendTokenPiece(vocab, token, output)) {
                LOGE("Token-to-piece conversion failed.");
                break;
            }

            if (
                !decodeGeneratedToken(
                    ctx,
                    token,
                    current_position
                )
            ) {
                LOGE("Generated token decoding failed.");
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

        return makeUtf8Result(env, output);

    } catch (...) {
        LOGE("Unexpected generation exception.");

        return makeResult(
            env,
        );
    }
}
*/

// =========================================================
// Multi-Message Conversation Generation
// =========================================================

extern "C" JNIEXPORT jstring JNICALL

Java_com_monolith_ModelManager_nativeGenerateConversation(
    JNIEnv* env,
    jobject,
    jobjectArray roles,
    jobjectArray contents,
    jint agent_id
) {
    if (roles == nullptr || contents == nullptr) {
        return makeResult(
            env,
            "بيانات المحادثة غير متاحة."
        );
    }

    std::lock_guard<std::mutex> lock(engine_mutex);

    if (model == nullptr || vocab == nullptr) {
        return makeResult(
            env,
            "النموذج غير محمل."
        );
    }

    try {
        std::vector<ChatPromptMessage> messages;

        if (
            !readChatMessages(
                env,
                roles,
                contents,
                messages
            )
        ) {
            return makeResult(
                env,
                "تعذر قراءة رسائل المحادثة."
            );
        }

        std::string formatted_prompt;

        if (
            !formatPrompt(
                model,
                messages,
                formatted_prompt
            )
        ) {
            return makeResult(
                env,
                "تعذر تجهيز قالب المحادثة."
            );
        }

        return generateFromFormattedPrompt(
            env,
            formatted_prompt,
            agent_id
        );

    } catch (...) {
        LOGE("Conversation generation exception.");

        return makeResult(
            env,
            "حدث خطأ أثناء معالجة المحادثة."
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
