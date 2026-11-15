package com.monolith

import android.util.Log
import kotlinx.coroutines.delay
import org.json.JSONObject

class AgentBrain(private val modelManager: ModelManager) {

    private val TAG = "MonolithAgent"

    // التوجيه الأساسي (System Prompt) الذي يعطي النموذج هويته وأدواته
    private val systemPrompt = """
        أنت Monolith، وكيل ذكاء اصطناعي سيادي تعمل على هاتف أندرويد.
        مهمتك إنجاز طلبات المستخدم بالتحكم في الشاشة.
        
        لديك الأدوات التالية (يجب أن ترد بصيغة JSON فقط):
        1. {"action": "click", "target": "نص الزر أو الإحداثيات"}
        2. {"action": "type", "text": "النص المراد كتابته"}
        3. {"action": "scroll", "direction": "down"} (أو up)
        4. {"action": "wait"} (انتظار تحميل)
        5. {"action": "finish", "message": "رسالة النجاح"}
        
        قاعدة صارمة: لا ترد بأي كلام آخر غير JSON. إذا لم تجد العنصر، جرب التمرير.
    """.trimIndent()

    // الحلقة الرئيسية للوكيل
    suspend fun executeTask(userGoal: String, maxSteps: Int = 5): String {
        val a11yService = MonolithAccessibilityService.instance
            ?: return "خطأ: خدمة إمكانية الوصول (Accessibility) غير مفعلة. يرجى تفعيلها من إعدادات الهاتف."

        var currentStep = 0
        var finalMessage = "لم يتم إنجاز المهمة."

        while (currentStep < maxSteps) {
            currentStep++
            Log.d(TAG, "--- Step $currentStep ---")

            // 1. قراءة الشاشة الحالية (العيون)
            val screenJson = a11yService.getScreenTree()
            
            // 2. بناء الطلب للنموذج
            val prompt = """
                $systemPrompt
                
                هدف المستخدم: $userGoal
                حالة الشاشة الحالية (JSON): $screenJson
                
                ما هي خطوتك التالية (JSON فقط)؟
            """.trimIndent()

            // 3. التفكير (توليد الرد من النموذج المحلي)
            val response = modelManager.generateResponse(prompt)
            Log.d(TAG, "Model Response: $response")

            // 4. تحليل الرد وتنفيذ الإجراء
            try {
                // استخراج JSON من الرد (حماية من النصوص الزائدة)
                val jsonStart = response.indexOf("{")
                val jsonEnd = response.lastIndexOf("}") + 1
                if (jsonStart == -1 || jsonEnd == 0) {
                    return "فشل النموذج في إخراج صيغة JSON صحيحة."
                }

                val jsonString = response.substring(jsonStart, jsonEnd)
                val jsonAction = JSONObject(jsonString)
                val action = jsonAction.optString("action")

                when (action) {
                    "click" -> {
                        val target = jsonAction.optString("target")
                        Log.d(TAG, "Executing: Click on $target")
                        
                        // جلب إحداثيات العنصر
                        val coords = a11yService.getCoordinatesByText(target)
                        
                        if (coords != null) {
                            // رسم المؤشر وتأثير النقر قبل التنفيذ
                            AgentOverlayService.instance?.showClick(coords.first, coords.second, currentStep)
                            delay(500) // انتظار بسيط ليرى المستخدم المؤشر (نصف ثانية)
                            
                            // النقر على الإحداثيات
                            a11yService.clickByCoordinates(coords.first, coords.second)
                            delay(1500) // انتظار انتقال الشاشة
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
                return "حدث خطأ في فهم استجابة النموذج."
            }
        }
        return finalMessage
    }
}
