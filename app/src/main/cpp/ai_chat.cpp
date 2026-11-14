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

extern "C" JNIEXPORT jstring JNICALL
Java_com_monolith_ModelManager_nativeLoadModel(JNIEnv* env, jobject, jstring modelPath) {
    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("Loading model: %s", path);

    llama_backend_init();
    llama_model_params model_params = llama_model_default_params();
    
    // ميزة توفير الذاكرة (mmap)
    model_params.use_mmap = true; 
    model_params.n_gpu_layers = 0;

    model = llama_load_model_from_file(path, model_params);
    env->ReleaseStringUTFChars(modelPath, path);

    if (model == nullptr) {
        return env->NewStringUTF("فشل تحميل النموذج. تأكد من صيغة GGUF.");
    }

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
    if (model == nullptr || ctx == nullptr) {
        return env->NewStringUTF("النموذج غير محمل.");
    }

    const char* promptStr = env->GetStringUTFChars(prompt, nullptr);
    LOGI("Generating for: %s", promptStr);

    // 1. تحويل النص إلى Tokens
    std::vector<llama_token> tokens(2048);
    int n_tokens = llama_tokenize(model, promptStr, strlen(promptStr), tokens.data(), tokens.size(), true, false);
    env->ReleaseStringUTFChars(prompt, promptStr);

    if (n_tokens < 0) {
        return env->NewStringUTF("فشل تحويل النص إلى رموز.");
    }
    tokens.resize(n_tokens);

    // 2. إدخال الرموز إلى النموذج
    llama_batch batch = llama_batch_init(512, 0, 1);
    for (int i = 0; i < n_tokens; i++) {
        llama_batch_add(batch, tokens[i], i, {0}, false);
    }
    
    if (llama_decode(ctx, batch) != 0) {
        llama_batch_free(batch);
        return env->NewStringUTF("فشل في معالجة النص.");
    }

    // 3. حلقة التوليد (Generation Loop)
    std::string result = "";
    int n_predict = 256; // الحد الأقصى لعدد الكلمات المولدة
    int n_cur = batch.n_tokens;
    
    // استخدام أبسط طريقة اختيار (Greedy)
    llama_sampler* smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(smpl, llama_sampler_init_greedy());

    for (int i = 0; i < n_predict; i++) {
        llama_token id = llama_sampler_sample(smpl, ctx, -1);
        
        if (id == llama_token_eos(model)) {
            break;
        }

        char buf[128];
        int n = llama_token_to_piece(model, id, buf, sizeof(buf), 0, true);
        if (n > 0) {
            result.append(buf, n);
        }

        llama_batch_add(batch, id, n_cur, {0}, true);
        n_cur++;
        
        if (llama_decode(ctx, batch) != 0) {
            break;
        }
    }

    // تنظيف الذاكرة
    llama_sampler_free(smpl);
    llama_batch_free(batch);

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
    llama_backend_free();
}
