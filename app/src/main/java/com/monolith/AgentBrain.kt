package com.monolith

import android.util.Log
import kotlinx.coroutines.delay
import org.json.JSONObject

class AgentBrain(private val modelManager: ModelManager) {

    private val TAG = "MonolithAgent"

    // الدستور الجديد: يسمح بالدردشة والتحكم في نفس الوقت
    private val systemPrompt = """
        أنت Monolith، وكيل ذكاء اصطناعي سيادي يعمل على هاتف أندرويد.
        مهمتك التحدث مع المستخدم بلغة طبيعية أو إنجاز طلباته بالتحكم في الشاشة.

        يجب أن ترد دائماً بصيغة JSON فقط، بدون أي كلام إضافي أو علامات Markdown.
        اختر الإجراء المناسب من القائمة التالية:
        1. للدردشة العادية أو الإجابة على الأسئلة: {"action": "chat", "message": "نص الرد هنا"}
        2. للنقر على زر أو عنصر: {"action": "click", "target": "نص الزر الموجود على الشاشة"}
        3. لكتابة نص في حقل إدخال: {"action": "type", "text": "النص المراد كتابته"}
        4. للتمرير في الشاشة: {"action": "scroll", "direction": "down"} (أو up)
        5. للانتظار (مثلاً انتظار تحميل تطبيق): {"action": "wait"}
        6. لإنهاء المهمة وإخبار المستخدم بالنتيجة: {"action": "finish", "message": "رسالة النجاح النهائية"}

        قواعد صارمة:
        - إذا كان طلب المستخدم يحتاج إلى التحكم في الهاتف، استخدم الأدوات (click, type, scroll).
        - إذا كان طلب المستخدم مجرد سؤال أو دردشة، استخدم (chat) فوراً.
        - لا ترد بأي كلام خارج الـ JSON.
        - إذا لم تجد العنصر المطلوب، استخدم أداة التمرير (scroll) للبحث عنه.
    """.trimIndent()

    suspend fun executeTask(userGoal: String, maxSteps: Int = 10, agentId: Int = 0): String {
        val a11yService = MonolithAccessibilityService.instance
            ?: return "خطأ: خدمة إمكانية الوصول (Accessibility) غير مفعلة. يرجى تفعيلها من إعدادات الهاتف."

        var currentStep = 0
        var finalMessage = "لم يتم إنجاز المهمة."

        while (currentStep < maxSteps) {
            currentStep++
            Log.d(TAG, "--- Agent $agentId - Step $currentStep ---")

            // 1. قراءة الشاشة الحالية
            val screenJson = a11yService.getScreenTree()
            
            // 2. بناء الطلب للنموذج
            val prompt = """
                $systemPrompt
                
                هدف المستخدم: $userGoal
                حالة الشاشة الحالية (JSON): $screenJson
                
                ما هي خطوتك التالية (JSON فقط)؟
            """.trimIndent()

            // 3. توليد الرد
            val response = modelManager.generateResponse(prompt, agentId)
            Log.d(TAG, "Model Response: $response")

            // 4. تحليل الرد وتنفيذ الإجراء
            try {
                val jsonStart = response.indexOf("{")
                val jsonEnd = response.lastIndexOf("}") + 1
                
                // حماية: إذا لم يخرج النموذج JSON، نعتبره رداً نصياً عادياً
                if (jsonStart == -1 || jsonEnd == 0) {
                    return response.trim()
                }

                val jsonString = response.substring(jsonStart, jsonEnd)
                val jsonAction = JSONObject(jsonString)
                val action = jsonAction.optString("action")

                when (action) {
                    "chat" -> {
                        // إذا كان رداً نصياً، نعيده فوراً للواجهة
                        return jsonAction.optString("message", "...")
                    }
                    "click" -> {
                        val target = jsonAction.optString("target")
                        Log.d(TAG, "Executing: Click on $target")
                        
                        val coords = a11yService.getCoordinatesByText(target)
                        if (coords != null) {
                            AgentOverlayService.instance?.showClick(coords.first, coords.second, currentStep)
                            delay(500)
                            a11yService.clickByCoordinates(coords.first, coords.second)
                            delay(1500)
                        } else {
                            Log.d(TAG, "Coordinates not found, trying programmatic click...")
                            if (!a11yService.clickNodeByText(target)) {
                                Log.d(TAG, "Element not found, trying scroll...")
                                a11yService.scroll(true)
                                delay(1000)
                            } else {
                                delay(1500)
                            }
                        }
                    }
                    "type" -> {
                        val text = jsonAction.optString("text")
                        Log.d(TAG, "Executing: Type '$text'")
                        a11yService.typeText(text)
                        delay(1000)
                    }
                    "scroll" -> {
                        val dir = jsonAction.optString("direction")
                        Log.d(TAG, "Executing: Scroll $dir")
                        a11yService.scroll(dir == "down")
                        delay(1000)
                    }
                    "wait" -> {
                        Log.d(TAG, "Executing: Wait...")
                        delay(2000)
                    }
                    "finish" -> {
                        finalMessage = jsonAction.optString("message", "تمت المهمة بنجاح.")
                        Log.d(TAG, "Task Finished: $finalMessage")
                        return finalMessage
                    }
                    else -> {
                        Log.d(TAG, "Unknown action: $action")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing action JSON", e)
                // إذا فشل التحليل، نعيد النص كما هو كإجابة
                return response.trim()
            }
        }
        return finalMessage
    }
}
