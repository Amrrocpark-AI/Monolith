#include <jni.h>
#include <string>
#include <vector>
#include <mutex>
#include <memory>
#include <cstdint>
#include <cstddef>
#include <cstdlib>
#include <limits>
#include <new>
#include <algorithm>
#include <stdexcept>

#include <android/log.h>

#include "llama.h"
#include "mtmd.h"
#include "mtmd-helper.h"

#define LOG_TAG "MonolithVision"

#define LOGI(...) \
    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

#define LOGE(...) \
    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace MonolithVision {

// ==========================================
// Engine State
// ==========================================

static std::mutex vision_mutex;

static llama_model* vision_model = nullptr;
static llama_context* vision_ctx = nullptr;
static mtmd_context* mtmd_ctx = nullptr;

static const llama_vocab* vision_vocab = nullptr;

static constexpr uint32_t VISION_CONTEXT_SIZE = 4096;
static constexpr int VISION_THREADS = 4;

static constexpr int MAX_OUTPUT_TOKENS = 256;
static constexpr uint64_t MAX_IMAGE_PIXELS = 16ULL * 1024ULL * 1024ULL;

// ==========================================
// Motion Detection State (Protected by motion_mutex)
// ==========================================

static std::mutex motion_mutex;
static std::vector<uint8_t> previousFrame;
static int prevWidth = 0;
static int prevHeight = 0;

// ==========================================
// Error Helpers
// ==========================================

static jstring makeError(
    JNIEnv* env,
    const char* message
) {
    std::string json =
        "{\"error\":\"";

    for (const char* p = message; *p; ++p) {
        if (*p == '"' || *p == '\\') {
            json += '\\';
        }

        if (*p == '\n') {
            json += "\\n";
        } else if (*p == '\r') {
            json += "\\r";
        } else {
            json += *p;
        }
    }

    json += "\"}";

    return env->NewStringUTF(json.c_str());
}

// ==========================================
// Resource Management
// ==========================================

static void releaseVisionResources() {

    if (mtmd_ctx != nullptr) {
        mtmd_free(mtmd_ctx);
        mtmd_ctx = nullptr;
    }

    if (vision_ctx != nullptr) {
        llama_free(vision_ctx);
        vision_ctx = nullptr;
    }

    if (vision_model != nullptr) {
        llama_model_free(vision_model);
        vision_model = nullptr;
    }

    vision_vocab = nullptr;
}

static llama_context* createVisionContext() {

    if (vision_model == nullptr) {
        return nullptr;
    }

    llama_context_params params =
        llama_context_default_params();

    params.n_ctx = VISION_CONTEXT_SIZE;
    params.n_threads = VISION_THREADS;

    return llama_init_from_model(
        vision_model,
        params
    );
}

static bool resetVisionContext() {

    if (vision_ctx != nullptr) {
        llama_free(vision_ctx);
        vision_ctx = nullptr;
    }

    vision_ctx = createVisionContext();

    return vision_ctx != nullptr;
}

// ==========================================
// Vision Initialization
// ==========================================

extern "C" JNIEXPORT jboolean JNICALL

Java_com_monolith_VisionProcessor_nativeInitVision(
    JNIEnv* env,
    jobject,
    jstring textModelPath,
    jstring mmprojPath
) {

    if (textModelPath == nullptr || mmprojPath == nullptr) {
        return JNI_FALSE;
    }

    const char* model_chars =
        env->GetStringUTFChars(textModelPath, nullptr);

    if (model_chars == nullptr) {
        return JNI_FALSE;
    }

    std::string model_path(model_chars);

    env->ReleaseStringUTFChars(
        textModelPath,
        model_chars
    );

    const char* projector_chars =
        env->GetStringUTFChars(mmprojPath, nullptr);

    if (projector_chars == nullptr) {
        return JNI_FALSE;
    }

    std::string projector_path(projector_chars);

    env->ReleaseStringUTFChars(
        mmprojPath,
        projector_chars
    );

    if (model_path.empty() || projector_path.empty()) {
        return JNI_FALSE;
    }

    std::lock_guard<std::mutex> lock(vision_mutex);

    if (vision_model != nullptr ||
        vision_ctx != nullptr ||
        mtmd_ctx != nullptr) {

        LOGI("Releasing previous vision instance.");

        releaseVisionResources();
    }

    try {

        LOGI("Loading vision language model.");

        llama_model_params model_params =
            llama_model_default_params();

        vision_model = llama_model_load_from_file(
            model_path.c_str(),
            model_params
        );

        if (vision_model == nullptr) {
            LOGE("Vision model loading failed.");
            releaseVisionResources();
            return JNI_FALSE;
        }

        vision_vocab =
            llama_model_get_vocab(vision_model);

        if (vision_vocab == nullptr) {
            LOGE("Vision vocabulary unavailable.");
            releaseVisionResources();
            return JNI_FALSE;
        }

        vision_ctx = createVisionContext();

        if (vision_ctx == nullptr) {
            LOGE("Vision context creation failed.");
            releaseVisionResources();
            return JNI_FALSE;
        }

        mtmd_context_params mtmd_params =
            mtmd_context_params_default();

        mtmd_ctx = mtmd_init_from_file(
            projector_path.c_str(),
            vision_model,
            mtmd_params
        );

        if (mtmd_ctx == nullptr) {
            LOGE("Multimodal projector initialization failed.");
            releaseVisionResources();
            return JNI_FALSE;
        }

        LOGI("Vision engine initialized successfully.");

        return JNI_TRUE;

    } catch (const std::exception& e) {

        LOGE("Vision initialization exception: %s", e.what());

        releaseVisionResources();

        return JNI_FALSE;

    } catch (...) {

        LOGE("Unknown vision initialization exception.");

        releaseVisionResources();

        return JNI_FALSE;
    }
}

// ==========================================
// Peripheral Change Detection (processFrame)
// ==========================================

extern "C" JNIEXPORT jstring JNICALL

Java_com_monolith_VisionProcessor_processFrame(
    JNIEnv* env,
    jobject,
    jobject byteBuffer,
    jint width,
    jint height
) {
    if (env == nullptr) {
        return nullptr;
    }

    if (byteBuffer == nullptr) {
        return env->NewStringUTF("[]");
    }

    if (width <= 0 || height <= 0) {
        return env->NewStringUTF("[]");
    }

    const uint64_t w64 = static_cast<uint64_t>(width);
    const uint64_t h64 = static_cast<uint64_t>(height);
    const uint64_t bytesPerPixel = 4ULL;

    if (w64 > std::numeric_limits<uint64_t>::max() / h64) {
        return env->NewStringUTF("[]");
    }

    const uint64_t pixelCount64 = w64 * h64;
    if (pixelCount64 > std::numeric_limits<uint64_t>::max() / bytesPerPixel) {
        return env->NewStringUTF("[]");
    }

    const uint64_t requiredBytes64 = pixelCount64 * bytesPerPixel;
    if (requiredBytes64 > static_cast<uint64_t>(std::numeric_limits<size_t>::max())) {
        return env->NewStringUTF("[]");
    }

    void* bufferAddress = env->GetDirectBufferAddress(byteBuffer);
    const jlong bufferCapacity = env->GetDirectBufferCapacity(byteBuffer);

    if (bufferAddress == nullptr ||
        bufferCapacity < 0 ||
        static_cast<uint64_t>(bufferCapacity) < requiredBytes64) {
        return env->NewStringUTF("[]");
    }

    const size_t frameSize = static_cast<size_t>(requiredBytes64);
    const uint8_t* currentPixels = static_cast<const uint8_t*>(bufferAddress);
    const int frameW = static_cast<int>(width);
    const int frameH = static_cast<int>(height);

    constexpr int blockSize = 16;
    constexpr int diffThreshold = 30;
    constexpr size_t maxRegions = 10;

    std::lock_guard<std::mutex> lock(motion_mutex);

    try {
        std::vector<uint8_t> nextFrame(frameSize);
        std::copy_n(currentPixels, frameSize, nextFrame.begin());

        const bool dimensionsChanged =
            (prevWidth != frameW) ||
            (prevHeight != frameH) ||
            (previousFrame.size() != frameSize);

        if (dimensionsChanged) {
            previousFrame = std::move(nextFrame);
            prevWidth = frameW;
            prevHeight = frameH;
            return env->NewStringUTF("[]");
        }

        std::string json = "[";
        size_t regionCount = 0;

        for (int by = 0; by < frameH && regionCount < maxRegions; by += blockSize) {
            const int cellH = std::min(blockSize, frameH - by);

            for (int bx = 0; bx < frameW && regionCount < maxRegions; bx += blockSize) {
                const int cellW = std::min(blockSize, frameW - bx);

                uint64_t diffSum = 0;
                uint64_t cellPixels = 0;

                for (int y = by; y < by + cellH; ++y) {
                    const size_t rowOffset =
                        static_cast<size_t>(y) * static_cast<size_t>(frameW) * 4ULL;

                    for (int x = bx; x < bx + cellW; ++x) {
                        const size_t idx = rowOffset + static_cast<size_t>(x) * 4ULL;
                        const int currVal = static_cast<int>(nextFrame[idx]);
                        const int prevVal = static_cast<int>(previousFrame[idx]);
                        diffSum += static_cast<uint64_t>(std::abs(currVal - prevVal));
                        ++cellPixels;
                    }
                }

                if (cellPixels > 0 &&
                    (diffSum / cellPixels) > static_cast<uint64_t>(diffThreshold)) {
                    if (regionCount > 0) {
                        json += ",";
                    }
                    json += "{\"x\":" + std::to_string(bx) +
                            ",\"y\":" + std::to_string(by) +
                            ",\"w\":" + std::to_string(cellW) +
                            ",\"h\":" + std::to_string(cellH) + "}";
                    ++regionCount;
                }
            }
        }

        json += "]";

        previousFrame = std::move(nextFrame);
        prevWidth = frameW;
        prevHeight = frameH;

        return env->NewStringUTF(json.c_str());

    } catch (...) {
        previousFrame.clear();
        prevWidth = 0;
        prevHeight = 0;
        return env->NewStringUTF("[]");
    }
}

// ==========================================
// Frame Analysis
// ==========================================

extern "C" JNIEXPORT jstring JNICALL

Java_com_monolith_VisionProcessor_nativeAnalyzeFrame(
    JNIEnv* env,
    jobject,
    jobject byteBuffer,
    jint width,
    jint height,
    jstring prompt
) {

    if (byteBuffer == nullptr || prompt == nullptr) {
        return makeError(env, "Invalid input.");
    }

    if (width <= 0 || height <= 0) {
        return makeError(env, "Invalid image dimensions.");
    }

    const uint64_t pixels =
        static_cast<uint64_t>(width) *
        static_cast<uint64_t>(height);

    if (pixels > MAX_IMAGE_PIXELS ||
        pixels > std::numeric_limits<uint64_t>::max() / 4) {

        return makeError(env, "Image exceeds supported dimensions.");
    }

    const uint64_t required_bytes = pixels * 4;

    void* buffer_address =
        env->GetDirectBufferAddress(byteBuffer);

    jlong buffer_capacity =
        env->GetDirectBufferCapacity(byteBuffer);

    if (buffer_address == nullptr ||
        buffer_capacity < 0 ||
        static_cast<uint64_t>(buffer_capacity) < required_bytes) {

        return makeError(env, "Invalid or insufficient image buffer.");
    }

    const char* prompt_chars =
        env->GetStringUTFChars(prompt, nullptr);

    if (prompt_chars == nullptr) {
        return makeError(env, "Unable to read prompt.");
    }

    std::string prompt_text(prompt_chars);

    env->ReleaseStringUTFChars(prompt, prompt_chars);

    if (prompt_text.empty()) {
        return makeError(env, "Prompt cannot be empty.");
    }

    std::lock_guard<std::mutex> lock(vision_mutex);

    if (vision_model == nullptr ||
        vision_ctx == nullptr ||
        mtmd_ctx == nullptr ||
        vision_vocab == nullptr) {

        return makeError(env, "Vision engine is not initialized.");
    }

    mtmd_input_chunks* chunks = nullptr;
    mtmd_bitmap* bitmap = nullptr;
    llama_sampler* sampler = nullptr;

    try {

        // ----------------------------------
        // Reset Context
        // ----------------------------------

        if (!resetVisionContext()) {
            return makeError(env, "Vision context reset failed.");
        }

        // ----------------------------------
        // RGBA -> RGB
        // ----------------------------------

        const uint8_t* rgba =
            static_cast<const uint8_t*>(buffer_address);

        std::vector<uint8_t> rgb(
            static_cast<size_t>(pixels) * 3
        );

        for (uint64_t i = 0; i < pixels; ++i) {

            rgb[i * 3]     = rgba[i * 4];
            rgb[i * 3 + 1] = rgba[i * 4 + 1];
            rgb[i * 3 + 2] = rgba[i * 4 + 2];
        }

        bitmap = mtmd_bitmap_init(
            width,
            height,
            rgb.data()
        );

        if (bitmap == nullptr) {
            throw std::runtime_error("Bitmap creation failed.");
        }

        // ----------------------------------
        // Prepare Multimodal Input
        // ----------------------------------

        mtmd_input_text text;

        text.text = prompt_text.c_str();
        text.add_special = true;
        text.parse_special = true;

        chunks = mtmd_input_chunks_init();

        if (chunks == nullptr) {
            throw std::runtime_error("Chunk allocation failed.");
        }

        const mtmd_bitmap* bitmaps[] = {
            bitmap
        };

        int32_t tokenization_result =
            mtmd_tokenize(
                mtmd_ctx,
                chunks,
                &text,
                bitmaps,
                1
            );

        mtmd_bitmap_free(bitmap);
        bitmap = nullptr;

        if (tokenization_result != 0) {
            throw std::runtime_error("Multimodal tokenization failed.");
        }

        // ----------------------------------
        // Evaluate Image And Prompt
        // ----------------------------------

        llama_pos new_n_past = 0;

        int eval_result =
            mtmd_helper_eval_chunks(
                mtmd_ctx,
                vision_ctx,
                chunks,
                0,
                0,
                512,
                true,
                &new_n_past
            );

        if (eval_result != 0) {
            throw std::runtime_error("Multimodal evaluation failed.");
        }

        // ----------------------------------
        // Initialize Sampler
        // ----------------------------------

        sampler = llama_sampler_chain_init(
            llama_sampler_chain_default_params()
        );

        if (sampler == nullptr) {
            throw std::runtime_error("Sampler initialization failed.");
        }

        llama_sampler_chain_add(
            sampler,
            llama_sampler_init_greedy()
        );

        // ----------------------------------
        // Text Generation
        // ----------------------------------

        std::string output;

        llama_pos current_position = new_n_past;

        const llama_pos context_limit =
            static_cast<llama_pos>(
                llama_n_ctx(vision_ctx)
            );

        for (int i = 0;
             i < MAX_OUTPUT_TOKENS &&
             current_position < context_limit - 1;
             ++i) {

            llama_token token =
                llama_sampler_sample(
                    sampler,
                    vision_ctx,
                    -1
                );

            if (llama_vocab_is_eog(vision_vocab, token)) {
                break;
            }

            char token_buffer[256];

            int token_size =
                llama_token_to_piece(
                    vision_vocab,
                    token,
                    token_buffer,
                    sizeof(token_buffer),
                    0,
                    true
                );

            if (token_size < 0) {

                std::vector<char> large_buffer(
                    static_cast<size_t>(-token_size)
                );

                token_size =
                    llama_token_to_piece(
                        vision_vocab,
                        token,
                        large_buffer.data(),
                        static_cast<int32_t>(large_buffer.size()),
                        0,
                        true
                    );

                if (token_size > 0) {
                    output.append(
                        large_buffer.data(),
                        token_size
                    );
                }

            } else if (token_size > 0) {

                output.append(
                    token_buffer,
                    token_size
                );
            }

            // ----------------------------------
            // Decode Next Token At Correct Position
            // ----------------------------------

            llama_batch batch =
                llama_batch_init(1, 0, 1);

            if (batch.token == nullptr) {
                llama_batch_free(batch);
                throw std::runtime_error("Batch allocation failed.");
            }

            batch.n_tokens = 1;

            batch.token[0] = token;
            batch.pos[0] = current_position;

            batch.n_seq_id[0] = 1;
            batch.seq_id[0][0] = 0;

            batch.logits[0] = true;

            int decode_result =
                llama_decode(vision_ctx, batch);

            llama_batch_free(batch);

            if (decode_result != 0) {
                LOGE("Token decoding failed: %d", decode_result);
                break;
            }

            current_position++;
        }

        // ----------------------------------
        // Cleanup
        // ----------------------------------

        llama_sampler_free(sampler);
        sampler = nullptr;

        mtmd_input_chunks_free(chunks);
        chunks = nullptr;

        LOGI("Frame analysis completed.");

        return env->NewStringUTF(output.c_str());

    } catch (const std::exception& e) {

        LOGE("Frame analysis error: %s", e.what());

        if (sampler != nullptr) {
            llama_sampler_free(sampler);
        }

        if (chunks != nullptr) {
            mtmd_input_chunks_free(chunks);
        }

        if (bitmap != nullptr) {
            mtmd_bitmap_free(bitmap);
        }

        return makeError(env, e.what());

    } catch (...) {

        LOGE("Unknown frame analysis exception.");

        if (sampler != nullptr) {
            llama_sampler_free(sampler);
        }

        if (chunks != nullptr) {
            mtmd_input_chunks_free(chunks);
        }

        if (bitmap != nullptr) {
            mtmd_bitmap_free(bitmap);
        }

        return makeError(env, "Unknown vision processing error.");
    }
}

// ==========================================
// Vision Shutdown
// ==========================================

extern "C" JNIEXPORT void JNICALL

Java_com_monolith_VisionProcessor_nativeFreeVision(
    JNIEnv*,
    jobject
) {

    std::lock_guard<std::mutex> lock(vision_mutex);

    LOGI("Releasing vision engine.");

    releaseVisionResources();

    LOGI("Vision engine released.");
}

} // namespace MonolithVision
