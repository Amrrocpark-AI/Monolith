#include <jni.h>
#include <string>
#include <android/log.h>

#define LOG_TAG "MonolithNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

extern "C" JNIEXPORT jstring JNICALL
Java_com_monolith_ModelManager_nativeLoadModel(JNIEnv* env, jobject /* this */, jstring modelPath) {
    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("Loading model from: %s", path);
    env->ReleaseStringUTFChars(modelPath, path);
    
    // هنا سيتم استدعاء llama.cpp لاحقاً
    return env->NewStringUTF("تم تحميل النموذج (محاكاة)");
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_monolith_ModelManager_nativeGenerate(JNIEnv* env, jobject /* this */, jstring prompt) {
    const char* promptStr = env->GetStringUTFChars(prompt, nullptr);
    LOGI("Generating response for: %s", promptStr);
    env->ReleaseStringUTFChars(prompt, promptStr);
    
    // هنا سيتم استدعاء llama.cpp لاحقاً
    return env->NewStringUTF("هذا رد تجريبي من المحرك المحلي.");
}
