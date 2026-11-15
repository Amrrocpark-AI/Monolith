#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include <cstring>
#include <mutex>

#include "llama.h"
#include "mtmd.h"
#include "mtmd-helper.h"

#define LOG_TAG "MonolithVision"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static llama_model* vision_model = nullptr;
static llama_context* vision_ctx = nullptr;
static mtmd_context* mtmd_ctx = nullptr;
static std::mutex vision_mutex;

extern "C" JNIEXPORT jboolean JNICALL
Java_com_monolith_VisionProcessor_nativeInitVision(JNIEnv* env, jobject, jstring textModelPath, jstring mmprojPath) {
    std::lock_guard<std::mutex> lock(vision_mutex);

    if (vision_model != nullptr || mtmd_ctx != nullptr) {
        LOGI("Vision model already initialized");
        return JNI_TRUE;
    }

    const char* modelPath = env->GetStringUTFChars(textModelPath, nullptr);
    const char* projPath = env->GetStringUTFChars(mmprojPath, nullptr);

    LOGI("Loading text model: %s", modelPath);
    llama_model_params model_params = llama_model_default_params();
    vision_model = llama_model_load_from_file(modelPath, model_params);
    env->ReleaseStringUTFChars(textModelPath, modelPath);

    if (vision_model == nullptr) {
        LOGE("Failed to load text model");
        env->ReleaseStringUTFChars(mmprojPath, projPath);
        return JNI_FALSE;
    }

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = 4096;
    ctx_params.n_threads = 4;
    vision_ctx = llama_init_from_model(vision_model, ctx_params);

    if (vision_ctx == nullptr) {
        LOGE("Failed to create vision context");
        llama_model_free(vision_model);
        vision_model = nullptr;
        env->ReleaseStringUTFChars(mmprojPath, projPath);
        return JNI_FALSE;
    }

    LOGI("Loading mmproj: %s", projPath);
    mtmd_context_params mtmd_params = mtmd_context_params_default();
    mtmd_ctx = mtmd_init_from_file(projPath, vision_model, mtmd_params);
    env->ReleaseStringUTFChars(mmprojPath, projPath);

    if (mtmd_ctx == nullptr) {
        LOGE("Failed to load mmproj");
        llama_free(vision_ctx);
        llama_model_free(vision_model);
        vision_ctx = nullptr;
        vision_model = nullptr;
        return JNI_FALSE;
    }

    LOGI("Vision engine initialized successfully");
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_monolith_VisionProcessor_nativeAnalyzeFrame(JNIEnv* env, jobject, jobject byteBuffer, jint width, jint height, jstring prompt) {
    std::lock_guard<std::mutex> lock(vision_mutex);

    if (mtmd_ctx == nullptr || vision_ctx == nullptr || vision_model == nullptr) {
        return env->NewStringUTF("{\"error\":\"Vision engine not initialized\"}");
    }

    uint8_t* rgba = (uint8_t*)env->GetDirectBufferAddress(byteBuffer);
    if (rgba == nullptr) {
        return env->NewStringUTF("{\"error\":\"Invalid buffer\"}");
    }

    const char* promptStr = env->GetStringUTFChars(prompt, nullptr);

    // 1. إنشاء bitmap من RGBA
    mtmd_bitmap* bitmap = mtmd_bitmap_init(width, height, rgba);
    if (bitmap == nullptr) {
        env->ReleaseStringUTFChars(prompt, promptStr);
        return env->NewStringUTF("{\"error\":\"Failed to create bitmap\"}");
    }

    // 2. تحضير النص والصورة للترميز
    mtmd_input_text text;
    text.text = promptStr;
    text.add_special = true;
    text.parse_special = true;

    mtmd_input_chunks* chunks = mtmd_input_chunks_init();
    int32_t res = mtmd_tokenize(mtmd_ctx, chunks, &text, (const mtmd_bitmap**)&bitmap, 1);
    
    mtmd_bitmap_free(bitmap);
    env->ReleaseStringUTFChars(prompt, promptStr);

    if (res != 0) {
        mtmd_input_chunks_free(chunks);
        return env->NewStringUTF("{\"error\":\"Tokenization failed\"}");
    }

    // 3. تقييم الـ chunks (توليد الرد)
    llama_pos new_n_past = 0;
    if (mtmd_helper_eval_chunks(mtmd_ctx, vision_ctx, chunks, 0, 0, 512, true, &new_n_past) != 0) {
        mtmd_input_chunks_free(chunks);
        return env->NewStringUTF("{\"error\":\"Evaluation failed\"}");
    }

    // 4. توليد النص
    std::string result;
    const llama_vocab* vocab = llama_model_get_vocab(vision_model);
    llama_sampler* smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(smpl, llama_sampler_init_greedy());

    for (int i = 0; i < 256; i++) {
        llama_token id = llama_sampler_sample(smpl, vision_ctx, -1);
        if (llama_vocab_is_eog(vocab, id)) break;

        char buf[128];
        int n = llama_token_to_piece(vocab, id, buf, sizeof(buf), 0, true);
        if (n > 0) result.append(buf, n);

        llama_batch single = llama_batch_get_one(&id, 1);
        if (llama_decode(vision_ctx, single) != 0) break;
    }

    llama_sampler_free(smpl);
    mtmd_input_chunks_free(chunks);

    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_monolith_VisionProcessor_nativeFreeVision(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(vision_mutex);
    if (mtmd_ctx != nullptr) { mtmd_free(mtmd_ctx); mtmd_ctx = nullptr; }
    if (vision_ctx != nullptr) { llama_free(vision_ctx); vision_ctx = nullptr; }
    if (vision_model != nullptr) { llama_model_free(vision_model); vision_model = nullptr; }
}
