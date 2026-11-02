package com.monolith

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
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
// 2. اعتراض المصادقة (حقن المفتاح المشفر تلقائياً)
// ==========================================
class AuthInterceptor(private val context: Context) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        
        if (originalRequest.header("Authorization") == null) {
            // جلب المفتاح من التخزين المشفر
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val securePrefs = EncryptedSharedPreferences.create(
                context,
                "SecureSettings",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            
            val apiKey = securePrefs.getString("api_key", null)
            
            if (!apiKey.isNullOrEmpty()) {
                val newRequest = originalRequest.newBuilder()
                    .header("Authorization", "Bearer $apiKey")
                    .build()
                return chain.proceed(newRequest)
            } else {
                throw IOException("المحرك غير مهيأ. يرجى إدخال مفتاح API من الإعدادات.")
            }
        }
        return chain.proceed(originalRequest)
    }
}

// ==========================================
// 3. اعتراض تجاوز الحد (إعادة المحاولة التلقائية) - ميزتك العبقرية
// ==========================================
class RetryInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var response = chain.proceed(chain.request())
        var retryCount = 0
        val maxRetries = 3
        
        while (!response.isSuccessful && response.code in listOf(429, 503) && retryCount < maxRetries) {
            retryCount++
            Thread.sleep((1000L * Math.pow(2.0, retryCount.toDouble())).toLong())
            response.close()
            response = chain.proceed(chain.request())
        }
        return response
    }
}

// ==========================================
// 4. اعتراض الأخطاء (منع ظهور 401 في الواجهة)
// ==========================================
class ErrorInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        when (response.code) {
            401 -> throw IOException("فشل المصادقة: مفتاح API غير صحيح أو منتهي الصلاحية.")
            429 -> throw IOException("تم تجاوز الحد المسموح به. يرجى الانتظار قليلاً.")
            500, 502, 503, 504 -> throw IOException("الخادم يواجه مشكلة حالياً. يرجى المحاولة لاحقاً.")
        }
        return response
    }
}

// ==========================================
// 5. المصنع السيادي الخارق (The Sovereign Engine)
// ==========================================
object ApiClient {

    private var context: Context? = null
    private var customTimeoutSeconds: Long = 120L

    fun init(appContext: Context) {
        this.context = appContext.applicationContext
    }

    fun setDynamicTimeout(seconds: Long) {
        customTimeoutSeconds = seconds
    }

    private val loggingInterceptor = HttpLoggingInterceptor { message ->
        val sanitizedMessage = message.replace(Regex("Bearer\\s+[A-Za-z0-9\\-._~+/]+=*"), "Bearer [HIDDEN_API_KEY]")
        android.util.Log.d("MonolithNetwork", sanitizedMessage)
    }.apply {
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
    }

    private val metricsInterceptor = Interceptor { chain ->
        val startTime = System.nanoTime()
        val response = chain.proceed(chain.request())
        val endTime = System.nanoTime()
        val durationMs = (endTime - startTime) / 1_000_000
        android.util.Log.d("MonolithNetwork", "زمن الاستجابة: ${durationMs}ms")
        response
    }

    private fun buildOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor(ErrorInterceptor()) // يعترض 401 قبل أن يصل للواجهة
            .addInterceptor(RetryInterceptor()) // يعيد المحاولة
            .addInterceptor(metricsInterceptor) // يقيس الزمن
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(customTimeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(customTimeoutSeconds, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)

        context?.let {
            builder.addInterceptor(ConnectivityInterceptor(it))
            builder.addInterceptor(AuthInterceptor(it)) // هنا يتم حقن المفتاح المشفر
            
            val cacheSize = 10L * 1024 * 1024
            val cacheDir = File(it.cacheDir, "http_cache")
            builder.cache(Cache(cacheDir, cacheSize))
        }
        return builder.build()
    }

    private fun buildRetrofit(): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.groq.com/openai/") 
            .client(buildOkHttpClient())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val service: ApiService by lazy {
        buildRetrofit().create(ApiService::class.java)
    }
}
