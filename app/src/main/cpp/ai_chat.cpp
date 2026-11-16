#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include "llama.h"

#define LOG_TAG "MonolithNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static llama_model* model = nullptr;
static std::vector<llama_context*> contexts;
static const llama_vocab* vocab = nullptr;

extern "C" JNIEXPORT jstring JNICALL
Java_com_monolith_ModelManager_nativeLoadModel(JNIEnv* env, jobject, jstring modelPath) {
    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("Loading model: %s", path);

    llama_backend_init();
    llama_model_params model_params = llama_model_default_params();
    
    model = llama_model_load_from_file(path, model_params);
    env->ReleaseStringUTFChars(modelPath, path);

    if (model == nullptr) {
        return env->NewStringUTF("فشل تحميل النموذج. تأكد من صيغة GGUF.");
    }

    vocab = llama_model_get_vocab(model);

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = 2048; 
    ctx_params.n_threads = 4;
    
    llama_context* main_ctx = llama_init_from_model(model, ctx_params);
    
    if (main_ctx == nullptr) {
        llama_model_free(model);
        model = nullptr;
        return env->NewStringUTF("فشل إنشاء السياق الرئيسي.");
    }
    
    contexts.clear();
    contexts.push_back(main_ctx);

    return env->NewStringUTF("تم تحميل النموذج بنجاح.");
}

extern "C" JNIEXPORT jint JNICALL
Java_com_monolith_ModelManager_nativeCreateSubAgent(JNIEnv* env, jobject) {
    if (model == nullptr) return -1;
    
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = 1024;
    ctx_params.n_threads = 2;
    
    llama_context* sub_ctx = llama_init_from_model(model, ctx_params);
    if (sub_ctx != nullptr) {
        contexts.push_back(sub_ctx);
        return contexts.size() - 1;
    }
    return -1;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_monolith_ModelManager_nativeGenerate(JNIEnv* env, jobject, jstring prompt, jint agent_id) {
    if (model == nullptr || vocab == nullptr || agent_id < 0 || agent_id >= contexts.size()) {
        return env->NewStringUTF("النموذج أو الوكيل غير محمل.");
    }

    llama_context* ctx = contexts[agent_id];
    
    // تصحيح: استخدام دالة مسح الذاكرة الصحيحة لهذا الإصدار
    llama_kv_cache_seq_rm(ctx, 0, -1, -1);

    const char* promptStr = env->GetStringUTFChars(prompt, nullptr);

    // إضافة قالب محادثة بسيط
    std::string formatted_prompt = "<|im_start|>user\n" + std::string(promptStr) + "<|im_end|>\n<|im_start|>assistant\n";
    
    int n_prompt = -llama_tokenize(vocab, formatted_prompt.c_str(), formatted_prompt.length(), NULL, 0, true, true);
    if (n_prompt < 0) {
        env->ReleaseStringUTFChars(prompt, promptStr);
        return env->NewStringUTF("فشل حساب عدد الرموز.");
    }

    int n_ctx = llama_n_ctx(ctx);
    if (n_prompt >= n_ctx - 4) {
        env->ReleaseStringUTFChars(prompt, promptStr);
        return env->NewStringUTF("النص طويل جداً بالنسبة لسعة النموذج.");
    }

    std::vector<llama_token> tokens(n_prompt);
    if (llama_tokenize(vocab, formatted_prompt.c_str(), formatted_prompt.length(), tokens.data(), tokens.size(), true, true) < 0) {
        env->ReleaseStringUTFChars(prompt, promptStr);
        return env->NewStringUTF("فشل تحويل النص إلى رموز.");
    }
    env->ReleaseStringUTFChars(prompt, promptStr);

    llama_batch prompt_batch = llama_batch_get_one(tokens.data(), tokens.size());
    if (llama_decode(ctx, prompt_batch) != 0) {
        return env->NewStringUTF("فشل في معالجة النص.");
    }

    std::string result = "";
    int n_predict = 512;
    int n_cur = tokens.size(); 

    llama_sampler* smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(smpl, llama_sampler_init_greedy());

    for (int i = 0; i < n_predict; i++) {
        if (n_cur >= n_ctx - 4) {
            LOGI("Context limit reached.");
            break; 
        }

        llama_token id = llama_sampler_sample(smpl, ctx, -1);

        if (llama_vocab_is_eog(vocab, id)) {
            break;
        }

        char buf[128];
        int n = llama_token_to_piece(vocab, id, buf, sizeof(buf), 0, true);
        if (n > 0) {
            result.append(buf, n);
        }

        llama_batch gen_batch = llama_batch_init(1, 0, 1);
        gen_batch.n_tokens = 1;
        gen_batch.token[0] = id;
        gen_batch.pos[0] = n_cur;       
        gen_batch.seq_id[0][0] = 0;
        gen_batch.n_seq_id[0] = 1;
        gen_batch.logits[0] = true;
        
        n_cur++;
        
        if (llama_decode(ctx, gen_batch) != 0) {
            llama_batch_free(gen_batch);
            break;
        }
        llama_batch_free(gen_batch); 
    }

    llama_sampler_free(smpl);

    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_monolith_ModelManager_nativeUnloadModel(JNIEnv*, jobject) {
    for (auto ctx : contexts) {
        if (ctx != nullptr) {
            llama_free(ctx);
        }
    }
    contexts.clear();

    if (model != nullptr) {
        llama_model_free(model);
        model = nullptr;
    }
    vocab = nullptr;
    llama_backend_free();
}
