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
        6. للرؤية الحاسوبية (تحليل صورة الشاشة): {"action": "see_screen", "prompt": "سؤالك عن الشاشة"}
        7. لإنهاء المهمة وإخبار المستخدم بالنتيجة: {"action": "finish", "message": "رسالة النجاح النهائية"}

        قواعد صارمة:
        - إذا كان طلب المستخدم يحتاج إلى التحكم في الهاتف، استخدم الأدوات (click, type, scroll).
        - إذا لم تجد العنصر في شجرة النصوص، استخدم (see_screen) لفهم ما يحدث بصرياً.
        - إذا كان طلب المستخدم مجرد سؤال أو دردشة، استخدم (chat) فوراً.
        - لا ترد بأي كلام خارج الـ JSON.
    """.trimIndent()

    suspend fun executeTask(userGoal: String, maxSteps: Int = 10, agentId: Int = 0): String {
        // لم نعد نجبر المستخدم على تفعيل إمكانية الوصول لمجرد الدردشة
        val a11yService = MonolithAccessibilityService.instance
        
        var currentGoal = userGoal
        var startStep = 0
        var taskId = ""
        var visionContext = ""

        try {
            val pendingTask = appDatabase.getPendingTask()
            if (pendingTask != null && pendingTask["task_goal"] == userGoal) {
                Log.d(TAG, "استئناف المهمة من الخطوة ${pendingTask["current_step"]}")
                startStep = pendingTask["current_step"]?.toIntOrNull() ?: 0
                taskId = pendingTask["id"] ?: ""
            } else {
                appDatabase.insertTaskToQueue(userGoal)
                val newTask = appDatabase.getPendingTask()
                taskId = newTask?.get("id") ?: ""
                Log.d(TAG, "مهمة جديدة تم حفظها في قائمة الانتظار بالمعرف: $taskId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "خطأ في قائمة انتظار المهام، سنكمل بدونها", e)
        }

        val startTime = System.currentTimeMillis()
        var currentStep = startStep
        var finalMessage = "لم يتم إنجاز المهمة."
        val actionHistory = StringBuilder()

        while (currentStep < maxSteps) {
            currentStep++
            Log.d(TAG, "--- Agent $agentId - Step $currentStep ---")

            // جلب شجرة الشاشة فقط إذا كانت الخدمة متاحة
            val screenJson = a11yService?.getScreenTree() ?: "غير متاح (خدمة إمكانية الوصول معطلة)"
            
            val prompt = """
                $systemPrompt
                
                هدف المستخدم: $currentGoal
                حالة الشاشة الحالية (JSON): $screenJson
                ${if (visionContext.isNotEmpty()) "معلومات بصرية إضافية: $visionContext\n" else ""}
                
                ما هي خطوتك التالية (JSON فقط)؟
            """.trimIndent()

            val response = modelManager.generateResponse(prompt, agentId)
            Log.d(TAG, "Model Response: $response")

            try {
                val jsonStart = response.indexOf("{")
                val jsonEnd = response.lastIndexOf("}") + 1
                
                if (jsonStart == -1 || jsonEnd == 0) {
                    return response.trim()
                }

                val jsonString = response.substring(jsonStart, jsonEnd)
                val jsonAction = JSONObject(jsonString)
                val action = jsonAction.optString("action")
                
                if (action != "chat") {
                    actionHistory.append("-> $action: ${jsonAction.toString()}\n")
                }

                when (action) {
                    "chat" -> {
                        // الدردشة تعمل بدون الحاجة لإمكانية الوصول
                        return jsonAction.optString("message", "...")
                    }
                    "click", "type", "scroll" -> {
                        if (a11yService == null) {
                            visionContext = "فشل التنفيذ: خدمة إمكانية الوصول معطلة. لا يمكن التحكم في الشاشة."
                        } else {
                            if (action == "click") {
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
                            } else if (action == "type") {
                                val text = jsonAction.optString("text")
                                a11yService.typeText(text)
                                delay(1000)
                            } else if (action == "scroll") {
                                val dir = jsonAction.optString("direction")
                                a11yService.scroll(dir == "down")
                                delay(1000)
                            }
                        }
                    }
                    "wait" -> {
                        delay(2000)
                    }
                    "see_screen" -> {
                        val visionPrompt = jsonAction.optString("prompt", "ماذا يظهر على الشاشة؟")
                        Log.d(TAG, "Executing Vision: $visionPrompt")
                        
                        val screenCapture = ScreenCaptureService.instance
                        if (screenCapture == null) {
                            visionContext = "فشلت الرؤية: خدمة التقاط الشاشة غير مفعلة."
                        } else {
                            val frame = screenCapture.getLatestFrameForVision()
                            if (frame != null) {
                                val buffer = frame.first
                                val width = frame.second
                                val height = frame.third
                                val visualDescription = modelManager.analyzeScreenFrame(buffer, width, height, visionPrompt)
                                visionContext = "نتيجة الرؤية: $visualDescription"
                                Log.d(TAG, visionContext)
                            } else {
                                visionContext = "فشلت الرؤية: لا يوجد إطار متاح."
                            }
                        }
                        delay(1000)
                    }
                    "finish" -> {
                        finalMessage = jsonAction.optString("message", "تمت المهمة بنجاح.")
                        
                        val latency = System.currentTimeMillis() - startTime
                        performSelfReflection(currentGoal, finalMessage, actionHistory.toString(), "SUCCESS", currentStep, latency)
                        
                        if (taskId.isNotEmpty()) {
                            try {
                                appDatabase.updateTaskStatus(taskId, "COMPLETED")
                            } catch (e: Exception) {
                                Log.e(TAG, "فشل تحديث حالة المهمة", e)
                            }
                        }
                        
                        return finalMessage
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing action JSON", e)
                return response.trim()
            }
        }
        
        val latency = System.currentTimeMillis() - startTime
        performSelfReflection(currentGoal, "فشل في إنجاز المهمة خلال $maxSteps خطوات.", actionHistory.toString(), "FAILED", currentStep, latency)
        
        if (taskId.isNotEmpty()) {
            try {
                appDatabase.updateTaskStatus(taskId, "FAILED")
            } catch (e: Exception) {
                Log.e(TAG, "فشل تحديث حالة المهمة", e)
            }
        }
        
        return finalMessage
    }

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
