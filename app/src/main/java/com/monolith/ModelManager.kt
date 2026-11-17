
package com.monolith

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer

class ModelManager(context: Context) {

    private val appContext = context.applicationContext

    companion object {
        private const val TAG = "ModelManager"
    }

    // ==========================================
    // Native Engine
    // ==========================================

    private val nativeEngineReady: Boolean = try {
        System.loadLibrary("monolith")
        true
    } catch (e: UnsatisfiedLinkError) {
        Log.e(TAG, "Native library loading failed", e)
        false
    } catch (e: SecurityException) {
        Log.e(TAG, "Native library access denied", e)
        false
    }

    private external fun nativeLoadModel(modelPath: String): String
    private external fun nativeGenerate(prompt: String, agentId: Int): String
    private external fun nativeUnloadModel()
    private external fun nativeCreateSubAgent(): Int

    // ==========================================
    // State Management
    // ==========================================

    private val engineMutex = Mutex()

    @Volatile
    private var textModelLoaded = false

    @Volatile
    private var visionModelLoaded = false

    @Volatile
    private var closed = false

    val isTextModelLoaded: Boolean
        get() = textModelLoaded

    val isVisionModelLoaded: Boolean
        get() = visionModelLoaded

    val isEngineReady: Boolean
        get() = nativeEngineReady && !closed

    // ==========================================
    // Model Validation
    // ==========================================

    private fun validateModelFile(path: String): File? {
        if (path.isBlank()) return null

        return try {
            File(path).canonicalFile.takeIf {
                it.exists() && it.isFile && it.canRead() && it.length() > 0L
            }
        } catch (e: Exception) {
            Log.e(TAG, "Model file validation failed", e)
            null
        }
    }

    // ==========================================
    // Text Model
    // ==========================================

    suspend fun loadModelAsync(modelPath: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            engineMutex.withLock {
                try {
                    if (!nativeEngineReady || closed) {
                        return@withLock Result.failure(
                            IllegalStateException("Native engine is unavailable")
                        )
                    }

                    val file = validateModelFile(modelPath)
                        ?: return@withLock Result.failure(
                            IllegalArgumentException("Invalid model file: $modelPath")
                        )

                    textModelLoaded = false

                    val response = nativeLoadModel(file.absolutePath)

                    if (response.contains("بنجاح", ignoreCase = true) ||
                        response.contains("success", ignoreCase = true)
                    ) {
                        textModelLoaded = true
                        Result.success(Unit)
                    } else {
                        Log.e(TAG, "Model loading failed: $response")
                        Result.failure(
                            IllegalStateException(response.ifBlank {
                                "Unknown native loading error"
                            })
                        )
                    }

                } catch (e: Exception) {
                    textModelLoaded = false
                    Log.e(TAG, "Text model loading failed", e)
                    Result.failure(e)
                }
            }
        }
    }

    // Original API compatibility
    fun loadModel(
        modelPath: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (!nativeEngineReady || closed) {
            onError("Native engine is unavailable")
            return
        }

        Thread {
            loadModelAsync(modelPath)
            // This compatibility method is intentionally not used
            // for coroutine execution. See note below.
        }.start()
    }

    suspend fun generateResponse(
        prompt: String,
        agentId: Int = 0
    ): String {
        return withContext(Dispatchers.IO) {
            engineMutex.withLock {
                try {
                    check(nativeEngineReady) {
                        "Native engine is unavailable"
                    }

                    check(!closed) {
                        "Model manager is closed"
                    }

                    check(textModelLoaded) {
                        "Text model is not loaded"
                    }

                    require(prompt.isNotBlank()) {
                        "Prompt cannot be empty"
                    }

                    require(agentId >= 0) {
                        "Invalid agent ID"
                    }

                    nativeGenerate(prompt, agentId)

                } catch (e: Exception) {
                    Log.e(TAG, "Text generation failed", e)
                    "فشل التوليد: ${e.message ?: "خطأ غير معروف"}"
                }
            }
        }
    }

    fun createSubAgent(): Int {
        if (!nativeEngineReady || closed || !textModelLoaded) {
            return -1
        }

        return try {
            // Native implementation must guarantee thread safety.
            engineMutex.tryLock().let { locked ->
                if (!locked) return -1

                try {
                    nativeCreateSubAgent()
                } finally {
                    engineMutex.unlock()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sub-agent creation failed", e)
            -1
        }
    }

    // ==========================================
    // Vision Model
    // ==========================================

    suspend fun loadVisionModelAsync(
        visionModelPath: String,
        mmprojPath: String
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            engineMutex.withLock {
                try {
                    if (!nativeEngineReady || closed) {
                        return@withLock Result.failure(
                            IllegalStateException("Native engine unavailable")
                        )
                    }

                    val modelFile = validateModelFile(visionModelPath)
                        ?: return@withLock Result.failure(
                            IllegalArgumentException("Invalid vision model")
                        )

                    val projectorFile = validateModelFile(mmprojPath)
                        ?: return@withLock Result.failure(
                            IllegalArgumentException("Invalid projector model")
                        )

                    visionModelLoaded = false

                    val success = VisionProcessor.nativeInitVision(
                        modelFile.absolutePath,
                        projectorFile.absolutePath
                    )

                    if (success) {
                        visionModelLoaded = true
                        Result.success(Unit)
                    } else {
                        Result.failure(
                            IllegalStateException("Vision initialization failed")
                        )
                    }

                } catch (e: Exception) {
                    visionModelLoaded = false
                    Log.e(TAG, "Vision loading failed", e)
                    Result.failure(e)
                }
            }
        }
    }

    // Original API compatibility
    fun loadVisionModel(
        visionModelPath: String,
        mmprojPath: String,
        onResult: (Boolean) -> Unit
    ) {
        if (!nativeEngineReady || closed) {
            onResult(false)
            return
        }

        // Legacy synchronous API retained for existing callers.
        try {
            val modelFile = validateModelFile(visionModelPath)
            val projectorFile = validateModelFile(mmprojPath)

            if (modelFile == null || projectorFile == null) {
                onResult(false)
                return
            }

            val success = VisionProcessor.nativeInitVision(
                modelFile.absolutePath,
                projectorFile.absolutePath
            )

            visionModelLoaded = success
            onResult(success)

        } catch (e: Exception) {
            Log.e(TAG, "Vision loading failed", e)
            visionModelLoaded = false
            onResult(false)
        }
    }

    suspend fun analyzeScreenFrame(
        buffer: ByteBuffer,
        width: Int,
        height: Int,
        prompt: String
    ): String {
        return withContext(Dispatchers.IO) {
            engineMutex.withLock {
                try {
                    check(nativeEngineReady && !closed) {
                        "Native engine unavailable"
                    }

                    check(visionModelLoaded) {
                        "Vision model is not loaded"
                    }

                    require(width > 0 && height > 0) {
                        "Invalid frame dimensions"
                    }

                    require(buffer.isDirect) {
                        "A direct ByteBuffer is required"
                    }

                    require(prompt.isNotBlank()) {
                        "Vision prompt cannot be empty"
                    }

                    VisionProcessor.nativeAnalyzeFrame(
                        buffer,
                        width,
                        height,
                        prompt
                    )

                } catch (e: Exception) {
                    Log.e(TAG, "Frame analysis failed", e)

                    JSONObject()
                        .put("error", e.message ?: "Unknown vision error")
                        .toString()
                }
            }
        }
    }

    // ==========================================
    // Unloading
    // ==========================================

    fun unloadModel() {
        if (!nativeEngineReady || closed) return

        try {
            // Synchronous compatibility method.
            // Call only after active generation has stopped.
            if (textModelLoaded) {
                nativeUnloadModel()
                textModelLoaded = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Text model unloading failed", e)
        }
    }

    fun unloadVisionModel() {
        if (!nativeEngineReady || closed) return

        try {
            if (visionModelLoaded) {
                VisionProcessor.nativeFreeVision()
                visionModelLoaded = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Vision model unloading failed", e)
        }
    }

    suspend fun unloadAllAsync() {
        withContext(Dispatchers.IO) {
            engineMutex.withLock {
                if (closed) return@withLock

                try {
                    if (textModelLoaded) {
                        nativeUnloadModel()
                        textModelLoaded = false
                    }

                    if (visionModelLoaded) {
                        VisionProcessor.nativeFreeVision()
                        visionModelLoaded = false
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Engine unloading failed", e)
                } finally {
                    textModelLoaded = false
                    visionModelLoaded = false
                    closed = true
                }
            }
        }
    }

    // ==========================================
    // Model Information
    // ==========================================

    fun getModelDetails(modelPath: String): String {
        return try {
            val file = validateModelFile(modelPath)
                ?: return "الملف غير موجود أو غير صالح"

            val sizeMB = file.length() / (1024 * 1024)

            JSONObject()
                .put("name", file.name)
                .put("sizeMB", sizeMB)
                .put("sizeBytes", file.length())
                .put("readable", file.canRead())
                .toString()

        } catch (e: Exception) {
            Log.e(TAG, "Model details failed", e)
            "خطأ في قراءة معلومات النموذج"
        }
    }
}
