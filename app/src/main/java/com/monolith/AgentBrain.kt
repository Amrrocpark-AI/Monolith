package com.monolith

import android.content.Context
import android.util.Log
import kotlinx.coroutines.delay
import org.json.JSONObject

class AgentBrain(private val modelManager: ModelManager, private val context: Context) {

    private val TAG = "MonolithAgent"
    private val appDatabase = AppDatabase(context)

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

        val startTime = System.currentTimeMillis()
        var currentStep = 0
        var finalMessage = "لم يتم إنجاز المهمة."
        val actionHistory = StringBuilder()

        while (currentStep < maxSteps) {
            currentStep++
            Log.d(TAG, "--- Agent $agentId - Step $currentStep ---")

            val screenJson = a11yService.getScreenTree()
            
            val prompt = """
                $systemPrompt
                
                هدف المستخدم: $userGoal
                حالة الشاشة الحالية (JSON): $screenJson
                
                ما هي خطوتك التالية (JSON فقط)؟
            """.trimIndent()

            val response = modelManager.generateResponse(prompt, agentId)
            Log.d(TAG, "Model Response: $response")

            try {
                val jsonStart = response.indexOf("{")
                val jsonEnd = response.lastIndexOf("}") + 1
                
                if (jsonStart == -1 || jsonEnd == 0) {
                    // إذا لم يخرج النموذج JSON، نعتبره رداً نصياً عادياً ولا نسجل له أداء (لأنه مجرد دردشة)
                    return response.trim()
                }

                val jsonString = response.substring(jsonStart, jsonEnd)
                val jsonAction = JSONObject(jsonString)
                val action = jsonAction.optString("action")
                
                // تسجيل الإجراء في السجل
                if (action != "chat") {
                    actionHistory.append("-> $action: ${jsonAction.toString()}\n")
                }

                when (action) {
                    "chat" -> {
                        return jsonAction.optString("message", "...")
                    }
                    "click" -> {
                        val target = jsonAction.optString("target")
                        val coords = a11yService.getCoordinatesByText(target)
                        if (coords != null) {
                            AgentOverlayService.instance?.showClick(coords.first, coords.second, currentStep)
                            delay(500)
                            a11yService.clickByCoordinates(coords.first, coords.second)
                            delay(1500)
                        } else {
                            if (!a11yService.clickNodeByText(target)) {
                                a11yService.scroll(true)
                                delay(1000)
                            } else {
                                delay(1500)
                            }
                        }
                    }
                    "type" -> {
                        val text = jsonAction.optString("text")
                        a11yService.typeText(text)
                        delay(1000)
                    }
                    "scroll" -> {
                        val dir = jsonAction.optString("direction")
                        a11yService.scroll(dir == "down")
                        delay(1000)
                    }
                    "wait" -> {
                        delay(2000)
                    }
                    "finish" -> {
                        finalMessage = jsonAction.optString("message", "تمت المهمة بنجاح.")
                        
                        // المهمة انتهت بنجاح: نقوم بتقييم الأداء
                        val latency = System.currentTimeMillis() - startTime
                        performSelfReflection(userGoal, finalMessage, actionHistory.toString(), "SUCCESS", currentStep, latency)
                        
                        return finalMessage
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing action JSON", e)
                return response.trim()
            }
        }
        
        // انتهت الخطوات دون إنجاز: نقوم بتقييم الفشل
        val latency = System.currentTimeMillis() - startTime
        performSelfReflection(userGoal, "فشل في إنجاز المهمة خلال $maxSteps خطوات.", actionHistory.toString(), "FAILED", currentStep, latency)
        
        return finalMessage
    }

    // دالة التأمل الذاتي (Self-Reflection)
    private suspend fun performSelfReflection(goal: String, result: String, history: String, status: String, steps: Int, latency: Long) {
        try {
            val reflectionPrompt = """
                لقد انتهيت للتو من محاولة إنجاز المهمة التالية: "$goal"
                النتيجة: $result
                الإجراءات التي قمت بها:
                $history
                
                قم بتقييم أدائك بموضوعية وصرامة.
                أجب بصيغة JSON فقط:
                {"rating": 1-5, "reflection": "ملاحظة قصيرة عن سبب النجاح أو الفشل", "solution": "حل مقترح لتجنب الفشل في المرة القادمة (أو اكتب 'لا يوجد' إذا نجحت)"}
            """.trimIndent()

            val response = modelManager.generateResponse(reflectionPrompt, 0)
            Log.d(TAG, "Reflection Response: $response")

            val jsonStart = response.indexOf("{")
            val jsonEnd = response.lastIndexOf("}") + 1
            
            if (jsonStart != -1 && jsonEnd > 0) {
                val jsonString = response.substring(jsonStart, jsonEnd)
                val json = JSONObject(jsonString)
                
                val rating = json.optInt("rating", 3)
                val reflectionNote = json.optString("reflection", "لا توجد ملاحظات.")
                val solution = json.optString("solution", "لا يوجد حل.")

                // حفظ التقييم في قاعدة البيانات
                appDatabase.insertPerformanceLog(
                    goal = goal,
                    status = status,
                    steps = steps,
                    latencyMs = latency,
                    selfRating = rating,
                    reflection = reflectionNote,
                    solution = solution
                )
                Log.d(TAG, "Performance logged successfully.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to perform self-reflection", e)
        }
    }
}
