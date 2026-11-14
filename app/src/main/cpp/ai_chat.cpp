#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include "llama.h"

#define LOG_TAG "MonolithNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static llama_model* model = nullptr;
static llama_context* ctx = nullptr;
static const llama_vocab* vocab = nullptr;

extern "C" JNIEXPORT jstring JNICALL
Java_com_monolith_ModelManager_nativeLoadModel(JNIEnv* env, jobject, jstring modelPath) {
    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("Loading model: %s", path);

    llama_backend_init();
    llama_model_params model_params = llama_model_default_params();
    model_params.use_mmap = true;

    model = llama_load_model_from_file(path, model_params);
    env->ReleaseStringUTFChars(modelPath, path);

    if (model == nullptr) {
        return env->NewStringUTF("فشل تحميل النموذج. تأكد من صيغة GGUF.");
    }

    // الحصول على المفردات (Vocab) من النموذج
    vocab = llama_model_get_vocab(model);

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = 2048;
    ctx_params.n_threads = 4;
    ctx = llama_new_context_with_model(model, ctx_params);

    if (ctx == nullptr) {
        llama_free_model(model);
        model = nullptr;
        return env->NewStringUTF("فشل إنشاء السياق.");
    }

    return env->NewStringUTF("تم تحميل النموذج بنجاح.");
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_monolith_ModelManager_nativeGenerate(JNIEnv* env, jobject, jstring prompt) {
    if (model == nullptr || ctx == nullptr || vocab == nullptr) {
        return env->NewStringUTF("النموذج غير محمل.");
    }

    const char* promptStr = env->GetStringUTFChars(prompt, nullptr);
    LOGI("Generating for: %s", promptStr);

    // 1. حساب عدد الرموز المطلوبة أولاً
    int n_prompt = -llama_tokenize(vocab, promptStr, strlen(promptStr), NULL, 0, true, true);
    if (n_prompt < 0) {
        env->ReleaseStringUTFChars(prompt, promptStr);
        return env->NewStringUTF("فشل حساب عدد الرموز.");
    }

    std::vector<llama_token> tokens(n_prompt);
    if (llama_tokenize(vocab, promptStr, strlen(promptStr), tokens.data(), tokens.size(), true, true) < 0) {
        env->ReleaseStringUTFChars(prompt, promptStr);
        return env->NewStringUTF("فشل تحويل النص إلى رموز.");
    }
    env->ReleaseStringUTFChars(prompt, promptStr);

    // 2. إدخال الرموز إلى النموذج (Prompt Evaluation)
    llama_batch batch = llama_batch_get_one(tokens.data(), tokens.size());
    if (llama_decode(ctx, batch) != 0) {
        return env->NewStringUTF("فشل في معالجة النص.");
    }

    // 3. حلقة التوليد
    std::string result = "";
    int n_predict = 256;
    int n_cur = tokens.size();

    llama_sampler* smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(smpl, llama_sampler_init_greedy());

    for (int i = 0; i < n_predict; i++) {
        llama_token id = llama_sampler_sample(smpl, ctx, -1);

        // التحقق من نهاية التوليد
        if (llama_vocab_is_eog(vocab, id)) {
            break;
        }

        char buf[128];
        int n = llama_token_to_piece(vocab, id, buf, sizeof(buf), 0, true);
        if (n > 0) {
            result.append(buf, n);
        }

        // إدخال الرمز الجديد باستخدام llama_batch_get_one
        llama_batch single_batch = llama_batch_get_one(&id, 1);
        if (llama_decode(ctx, single_batch) != 0) {
            break;
        }
        n_cur++;
    }

    llama_sampler_free(smpl);

    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_monolith_ModelManager_nativeUnloadModel(JNIEnv*, jobject) {
    if (ctx != nullptr) {
        llama_free(ctx);
        ctx = nullptr;
    }
    if (model != nullptr) {
        llama_free_model(model);
        model = nullptr;
    }
    vocab = nullptr;
    llama_backend_free();
}
