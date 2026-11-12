#include <jni.h>
#include <string>
#include <android/log.h>
#include "llama.h"

#define LOG_TAG "MonolithNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

static llama_model* model = nullptr;
static llama_context* ctx = nullptr;

extern "C" JNIEXPORT jstring JNICALL
Java_com_monolith_ModelManager_nativeLoadModel(JNIEnv* env, jobject, jstring modelPath) {
    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("Loading model: %s", path);

    llama_backend_init();
    llama_model_params model_params = llama_model_default_params();
    model = llama_load_model_from_file(path, model_params);
    env->ReleaseStringUTFChars(modelPath, path);

    if (model == nullptr) {
        return env->NewStringUTF("فشل تحميل النموذج.");
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

    // توليد بسيط (بدون معالجة متقدمة)
    std::string result = "رد تجريبي من llama.cpp: ";
    result += promptStr;

    env->ReleaseStringUTFChars(prompt, promptStr);
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
