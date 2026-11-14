#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include <cstring>

#define LOG_TAG "MonolithVision"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

// ذاكرة لتخزين الإطار السابق
static std::vector<uint8_t> previousFrame;
static int prevWidth = 0;
static int prevHeight = 0;

extern "C" JNIEXPORT jstring JNICALL
Java_com_monolith_VisionProcessor_processFrame(JNIEnv* env, jobject, jobject byteBuffer, jint width, jint height) {
    // 1. الحصول على مؤشر مباشر للبيانات (Zero-Copy)
    uint8_t* currentFrame = (uint8_t*)env->GetDirectBufferAddress(byteBuffer);
    if (currentFrame == nullptr) return env->NewStringUTF("[]");

    // 2. إذا تغير حجم الشاشة، نعيد ضبط الذاكرة
    int frameSize = width * height * 4; // RGBA_8888
    if (prevWidth != width || prevHeight != height || previousFrame.empty()) {
        previousFrame.resize(frameSize);
        memcpy(previousFrame.data(), currentFrame, frameSize);
        prevWidth = width;
        prevHeight = height;
        return env->NewStringUTF("[]");
    }

    // 3. خوارزمية كشف التغييرات (Event Detection) - رياضيات سريعة
    // نقسم الشاشة إلى شبكة (مثلاً 16x16 بكسل لكل خلية) لتسريع المعالجة
    int blockSize = 16; 
    int changedBlocks = 0;
    std::string changedRegions = "[";

    for (int y = 0; y < height; y += blockSize) {
        for (int x = 0; x < width; x += blockSize) {
            long diff = 0;
            int pixels = 0;
            
            // مقارنة البكسلات داخل الخلية
            for (int by = 0; by < blockSize && (y + by) < height; by++) {
                for (int bx = 0; bx < blockSize && (x + bx) < width; bx++) {
                    int idx = ((y + by) * width + (x + bx)) * 4;
                    // حساب الفرق في قناة اللون (مبسط)
                    diff += abs(currentFrame[idx] - previousFrame[idx]);
                    pixels++;
                }
            }

            // إذا كان متوسط التغير كبيراً (عتبة)، نعتبره "حدثاً"
            if (pixels > 0 && (diff / pixels) > 30) {
                if (changedBlocks > 0) changedRegions += ",";
                changedRegions += "{\"x\":" + std::to_string(x) + 
                                 ",\"y\":" + std::to_string(y) + 
                                 ",\"w\":" + std::to_string(blockSize) + 
                                 ",\"h\":" + std::to_string(blockSize) + "}";
                changedBlocks++;
                // لحماية الذاكرة، نتوقف بعد 10 تغييرات كبيرة
                if (changedBlocks >= 10) break;
            }
        }
        if (changedBlocks >= 10) break;
    }
    changedRegions += "]";

    // 4. تحديث الإطار السابق للإطار التالي
    memcpy(previousFrame.data(), currentFrame, frameSize);

    return env->NewStringUTF(changedRegions.c_str());
}
