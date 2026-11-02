package com.monolith

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.FieldMap
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.HeaderMap
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.PartMap
import retrofit2.http.QueryMap
import retrofit2.http.Streaming
import retrofit2.http.Url

// ==========================================
// 1. واجهة الجسر الكوني والوكيل الذكي (Agentic Gateway V3)
// ==========================================
interface ApiService {

    // ==========================================
    // 1.1 الدردشة والذكاء الاصطناعي (JSON)
    // ==========================================
    @POST
    fun sendMessage(
        @Url url: String,
        @HeaderMap headers: Map<String, String>,
        @QueryMap queries: Map<String, String> = emptyMap(),
        @Body request: ChatRequest
    ): Call<ChatResponse>

    @Streaming
    @POST
    fun sendMessageStream(
        @Url url: String,
        @HeaderMap headers: Map<String, String>,
        @QueryMap queries: Map<String, String> = emptyMap(),
        @Body request: ChatRequest
    ): Call<ResponseBody>

    @GET
    fun getModels(
        @Url url: String,
        @HeaderMap headers: Map<String, String>
    ): Call<ResponseBody>

    // ==========================================
    // 1.2 الأبعاد الجديدة: الوعي المتجسد (Context Injection)
    // ==========================================
    @POST
    fun syncDeviceState(
        @Url url: String,
        @HeaderMap headers: Map<String, String>,
        @Body deviceState: Any 
    ): Call<ResponseBody>

    // ==========================================
    // 1.3 الأبعاد الجديدة: سير العمل والإضافات
    // ==========================================
    @POST
    fun triggerExternalPlugin(
        @Url url: String,
        @HeaderMap headers: Map<String, String>,
        @Body payload: Any
    ): Call<ResponseBody>

    // ==========================================
    // 1.4 الأبعاد الجديدة: الحواس المتعددة والذاكرة
    // ==========================================
    @Multipart
    @POST
    fun transcribeAudio(
        @Url url: String,
        @HeaderMap headers: Map<String, String>,
        @Part file: MultipartBody.Part,
        @PartMap parts: Map<String, @JvmSuppressWildcards RequestBody> = emptyMap()
    ): Call<ResponseBody>

    @POST
    fun generateSpeech(
        @Url url: String,
        @HeaderMap headers: Map<String, String>,
        @Body body: Any
    ): Call<ResponseBody>

    @POST
    fun generateImage(
        @Url url: String,
        @HeaderMap headers: Map<String, String>,
        @Body body: Any
    ): Call<ResponseBody>

    @POST
    fun createEmbeddings(
        @Url url: String,
        @HeaderMap headers: Map<String, String>,
        @Body body: Any
    ): Call<ResponseBody>

    // ==========================================
    // 1.5 حلقة الوكيل وفحص الصحة
    // ==========================================
    @POST
    fun sendToolResult(
        @Url url: String,
        @HeaderMap headers: Map<String, String>,
        @Body body: Any
    ): Call<ChatResponse>

    @GET
    fun pingServer(
        @Url url: String,
        @HeaderMap headers: Map<String, String> = emptyMap()
    ): Call<ResponseBody>

    // ==========================================
    // 1.6 المرونة القصوى والبروتوكولات القديمة
    // ==========================================
    @Multipart
    @POST
    fun uploadMedia(
        @Url url: String,
        @HeaderMap headers: Map<String, String>,
        @PartMap parts: Map<String, @JvmSuppressWildcards RequestBody>,
        @Part file: MultipartBody.Part
    ): Call<ResponseBody>

    @FormUrlEncoded
    @POST
    fun postFormData(
        @Url url: String,
        @HeaderMap headers: Map<String, String>,
        @FieldMap fields: Map<String, String>
    ): Call<ResponseBody>

    @HTTP(method = "GET", hasBody = false)
    fun genericGet(
        @Url url: String,
        @HeaderMap headers: Map<String, String>,
        @QueryMap queries: Map<String, String> = emptyMap()
    ): Call<ResponseBody>

    @HTTP(method = "POST", hasBody = true)
    fun genericPost(
        @Url url: String,
        @HeaderMap headers: Map<String, String>,
        @Body body: Any
    ): Call<ResponseBody>

    @PUT
    fun updateResource(
        @Url url: String,
        @HeaderMap headers: Map<String, String>,
        @Body body: Any
    ): Call<ResponseBody>

    @DELETE
    fun deleteResource(
        @Url url: String,
        @HeaderMap headers: Map<String, String>
    ): Call<ResponseBody>
}

// ==========================================
// 2. حل المشاكل المحتملة (Problem Solvers)
// ==========================================
data class ApiError(
    val error: ErrorDetails? = null,
    val message: String? = null
)

data class ErrorDetails(
    val message: String,
    val type: String? = null,
    val param: String? = null,
    val code: String? = null
)

data class DeviceContext(
    val currentApp: String,
    val batteryLevel: Int,
    val isScreenOn: Boolean,
    val networkType: String,
    val timestamp: Long = System.currentTimeMillis()
)
