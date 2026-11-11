package com.monolith

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Response

class AgentManager {

    suspend fun createPlan(userRequest: String, model: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val planningPrompt = "أنت الآن في وضع التخطيط. قم بتقسيم طلب المستخدم إلى خطوات. اكتب الخطوات فقط، بدون تنفيذ."
                val messages = listOf(
                    Message(role = "system", content = planningPrompt),
                    Message(role = "user", content = userRequest)
                )
                val request = ChatRequest(model = model, messages = messages, temperature = 0.7f, maxTokens = 1024)
                val response = ApiClient.service.sendMessage(url = "v1/chat/completions", headers = emptyMap(), request = request).execute()
                if (response.isSuccessful) {
                    response.body()?.choices?.firstOrNull()?.message?.content ?: "فشل التخطيط."
                } else {
                    "فشل التخطيط: ${response.code()}"
                }
            } catch (e: Exception) {
                "فشل التخطيط: ${e.message}"
            }
        }
    }

    suspend fun executeStep(step: String, model: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val executePrompt = "أنت الآن في وضع التنفيذ. نفذ الخطوة التالية: $step"
                val messages = listOf(Message(role = "system", content = executePrompt))
                val request = ChatRequest(model = model, messages = messages, temperature = 0.5f, maxTokens = 1024)
                val response = ApiClient.service.sendMessage(url = "v1/chat/completions", headers = emptyMap(), request = request).execute()
                if (response.isSuccessful) {
                    response.body()?.choices?.firstOrNull()?.message?.content ?: "فشل التنفيذ."
                } else {
                    "فشل التنفيذ: ${response.code()}"
                }
            } catch (e: Exception) {
                "فشل التنفيذ: ${e.message}"
            }
        }
    }
}
