package com.monolith

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

// ==========================================
// 1. اعتراض الاتصال (التأكد من وجود إنترنت)
// ==========================================
class ConnectivityInterceptor(private val context: Context) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        if (!isNetworkAvailable(context)) {
            throw IOException("لا يوجد اتصال بالإنترنت. يرجى التحقق من الشبكة.")
        }
        return chain.proceed(chain.request())
    }
    private fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

// ==========================================
// 2. اعتراض المصادقة (حقن مفتاح API ديناميكياً)
// ==========================================
class AuthInterceptor(private val apiKeyProvider: () -> String?) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        // إذا كان الطلب لا يحتوي على مفتاح، قم بإضافته
        if (originalRequest.header("Authorization") == null) {
            val apiKey = apiKeyProvider()
            if (!apiKey.isNullOrEmpty()) {
                val newRequest = originalRequest.newBuilder()
                    .header("Authorization", "Bearer $apiKey")
                    .build()
                return chain.proceed(newRequest)
            }
        }
        return chain.proceed(originalRequest)
    }
}

// ==========================================
// 3. اعتراض تجاوز الحد (إعادة المحاولة التلقائية)
// ==========================================
class RetryInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var response = chain.proceed(chain.request())
        var retryCount = 0
        val maxRetries = 3
        
        while (!response.isSuccessful && response.code in listOf(429, 503) && retryCount < maxRetries) {
            retryCount++
            // الانتظار التصاعدي: 1 ثانية، ثم 2، ثم 4
            Thread.sleep((1000L * Math.pow(2.0, retryCount.toDouble())).toLong())
            response.close() // إغلاق الاستجابة القديمة
            response = chain.proceed(chain.request())
        }
        return response
    }
}

// ==========================================
// 4. اعتراض إخفاء المفاتيح في السجلات (أمان)
// ==========================================
class LogSanitizerInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        // هذا مجرد مثال بسيط، في الواقع يتم تطبيقه داخل الـ Logging Interceptor
        return chain.proceed(chain.request())
    }
}

// ==========================================
// 5. المصنع السيادي الخارق (The Sovereign Engine)
// ==========================================
object ApiClient {

    private var context: Context? = null
    private var customTimeoutSeconds: Long = 120L
    private var dynamicApiKey: String? = null // تخزين المفتاح في الذاكرة

    fun init(appContext: Context) {
        this.context = appContext.applicationContext
    }

    fun setDynamicTimeout(seconds: Long) {
        customTimeoutSeconds = seconds
    }

    // دالة لتعيين مفتاح API ديناميكياً
    fun setApiKey(key: String?) {
        dynamicApiKey = key
    }

    // إعداد السجل الذكي مع إخفاء المفاتيح
    private val loggingInterceptor = HttpLoggingInterceptor { message ->
        // إخفاء مفتاح API إذا ظهر في السجل
        val sanitizedMessage = message.replace(Regex("Bearer\\s+[A-Za-z0-9\\-._~+/]+=*"), "Bearer [HIDDEN_API_KEY]")
        android.util.Log.d("MonolithNetwork", sanitizedMessage)
    }.apply {
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
    }

    // قياس زمن الاستجابة (Latency Telemetry)
    private val metricsInterceptor = Interceptor { chain ->
        val startTime = System.nanoTime()
        val response = chain.proceed(chain.request())
        val endTime = System.nanoTime()
        val durationMs = (endTime - startTime) / 1_000_000
        
        // هنا يمكن حفظ الزمن في متغير عام لعرضه في الواجهة لاحقاً
        android.util.Log.d("MonolithNetwork", "زمن الاستجابة: ${durationMs}ms")
        response
    }

    private fun buildOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor(AuthInterceptor { dynamicApiKey })
            .addInterceptor(RetryInterceptor())
            .addInterceptor(metricsInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(customTimeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(customTimeoutSeconds, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)

        context?.let {
            builder.addInterceptor(ConnectivityInterceptor(it))
            val cacheSize = 10L * 1024 * 1024
            val cacheDir = File(it.cacheDir, "http_cache")
            builder.cache(Cache(cacheDir, cacheSize))
        }
        return builder.build()
    }

    private fun buildRetrofit(): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.openai.com/") // رابط أساسي وهمي
            .client(buildOkHttpClient())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val service: ApiService by lazy {
        buildRetrofit().create(ApiService::class.java)
    }
}
