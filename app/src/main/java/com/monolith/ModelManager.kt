package com.monolith

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

class ModelManager(context: Context) : AgentModel {

    companion object {
        private const val TAG = "ModelManager"

        private const val GGUF_VERSION_MIN = 2L
        private const val GGUF_VERSION_MAX = 3L

        private const val MAX_METADATA_ENTRIES = 100_000L
        private const val MAX_TENSORS = 1_000_000L
        private const val MAX_METADATA_KEY_BYTES = 4096L
        private const val MAX_METADATA_STRING_BYTES = 4L * 1024L * 1024L
        private const val MAX_ARRAY_ITEMS = 10_000_000L

        @Volatile
        private var sharedInstance: ModelManager? = null

        fun getInstance(context: Context): ModelManager {
            return sharedInstance ?: synchronized(this) {
                sharedInstance ?: ModelManager(
                    context.applicationContext
                ).also {
                    sharedInstance = it
                }
            }
        }
    }

    private val callbackScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO
    )

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

private external fun nativeGenerate(
    prompt: String,
    agentId: Int
): String

private external fun nativeGenerateConversation(
    roles: Array<String>,
    contents: Array<String>,
    agentId: Int
): String

private external fun nativeUnloadModel()

    private external fun nativeCreateSubAgent(): Int

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

    fun reopenEngine() {
        if (!nativeEngineReady) return
        closed = false
    }

    fun shutdown() {
        closed = true
        try {
            callbackScope.cancel()
        } catch (_: Exception) {
        }
    }

    // =========================================================
    // File validation
    // =========================================================

    private fun validateModelFile(path: String): File? {
        if (path.isBlank()) return null

        return try {
            File(path).canonicalFile.takeIf {
                it.exists() &&
                    it.isFile &&
                    it.canRead() &&
                    it.length() > 0L
            }
        } catch (e: Exception) {
            Log.e(TAG, "Model file validation failed", e)
            null
        }
    }

    // =========================================================
    // GGUF inspection
    // =========================================================

    private data class GgufInspection(
        val version: Long,
        val tensorCount: Long,
        val metadataCount: Long,
        val architecture: String,
        val modelName: String?,
        val contextLength: Long?,
        val embeddingLength: Long?,
        val blockCount: Long?,
        val chatTemplateAvailable: Boolean,
        val alignment: Long,
        val dataOffset: Long
    )

    private fun readU8(
        input: RandomAccessFile
    ): Int {
        val value = input.read()
        if (value < 0) {
            throw EOFException("Unexpected end of GGUF file")
        }
        return value
    }

    private fun readU16(
        input: RandomAccessFile
    ): Long {
        val a = readU8(input).toLong()
        val b = readU8(input).toLong()

        return a or (b shl 8)
    }

    private fun readU32(
        input: RandomAccessFile
    ): Long {
        var value = 0L

        repeat(4) { index ->
            value = value or (
                readU8(input).toLong() shl (index * 8)
            )
        }

        return value
    }

    private fun readI64(
        input: RandomAccessFile
    ): Long {
        var value = 0L

        repeat(8) { index ->
            value = value or (
                readU8(input).toLong() shl (index * 8)
            )
        }

        return value
    }

    private fun readU64(
        input: RandomAccessFile
    ): Long {
        val value = readI64(input)

        if (value < 0L) {
            throw IOException(
                "GGUF unsigned value exceeds supported range"
            )
        }

        return value
    }

    private fun ensureRemaining(
        input: RandomAccessFile,
        count: Long
    ) {
        if (count < 0L) {
            throw IOException("Invalid GGUF data length")
        }

        val position = input.filePointer
        val fileLength = input.length()

        if (
            position < 0L ||
            position > fileLength ||
            count > fileLength - position
        ) {
            throw EOFException(
                "GGUF file is truncated or contains invalid lengths"
            )
        }
    }

    private fun skipChecked(
        input: RandomAccessFile,
        count: Long
    ) {
        ensureRemaining(input, count)

        input.seek(
            input.filePointer + count
        )
    }

    private fun readUtf8(
        input: RandomAccessFile,
        maximumBytes: Long
    ): String {
        val length = readU64(input)

        if (length > maximumBytes) {
            throw IOException(
                "GGUF string exceeds allowed size"
            )
        }

        ensureRemaining(input, length)

        val bytes = ByteArray(length.toInt())
        input.readFully(bytes)

        return String(
            bytes,
            StandardCharsets.UTF_8
        )
    }

    private fun skipString(
        input: RandomAccessFile
    ) {
        val length = readU64(input)
        skipChecked(input, length)
    }

    private fun isRelevantMetadataKey(
        key: String
    ): Boolean {
        return key == "general.name" ||
            key == "general.architecture" ||
            key == "general.alignment" ||
            key == "tokenizer.chat_template" ||
            key == "tokenizer.ggml.model" ||
            key.endsWith(".context_length") ||
            key.endsWith(".embedding_length") ||
            key.endsWith(".block_count")
    }

    private fun readMetadataValue(
        input: RandomAccessFile,
        type: Long,
        key: String
    ): Any? {

        return when (type) {

            // UINT8
            0L -> readU8(input).toLong()

            // INT8
            1L -> {
                readU8(input).toByte().toLong()
            }

            // UINT16
            2L -> readU16(input)

            // INT16
            3L -> {
                readU16(input).toShort().toLong()
            }

            // UINT32
            4L -> readU32(input)

            // INT32
            5L -> {
                readU32(input).toInt().toLong()
            }

            // FLOAT32
            6L -> {
                val bits = readU32(input).toInt()
                java.lang.Float.intBitsToFloat(bits).toDouble()
            }

            // BOOL
            7L -> {
                val value = readU8(input)

                if (value != 0 && value != 1) {
                    throw IOException(
                        "Invalid GGUF boolean value"
                    )
                }

                value == 1
            }

            // STRING
            8L -> {
                val length = readU64(input)

                if (
                    key == "tokenizer.chat_template" &&
                    length > 0L
                ) {
                    skipChecked(input, length)
                    return ChatTemplateMarker
                }

                if (
                    key == "general.name" ||
                    key == "general.architecture" ||
                    key == "tokenizer.ggml.model"
                ) {
                    if (length > MAX_METADATA_STRING_BYTES) {
                        throw IOException(
                            "GGUF metadata string is too large: $key"
                        )
                    }

                    ensureRemaining(input, length)

                    val bytes = ByteArray(length.toInt())
                    input.readFully(bytes)

                    String(
                        bytes,
                        StandardCharsets.UTF_8
                    )
                } else {
                    skipChecked(input, length)
                    null
                }
            }

            // ARRAY
            9L -> {
                skipArray(input)
                null
            }

            // UINT64
            10L -> readU64(input)

            // INT64
            11L -> readI64(input)

            // FLOAT64
            12L -> {
                java.lang.Double.longBitsToDouble(
                    readI64(input)
                )
            }

            else -> {
                throw IOException(
                    "Unsupported GGUF metadata type: $type"
                )
            }
        }
    }

    private object ChatTemplateMarker

    private fun skipArray(
        input: RandomAccessFile
    ) {
        val elementType = readU32(input)
        val count = readU64(input)

        if (count > MAX_ARRAY_ITEMS) {
            throw IOException(
                "GGUF metadata array is too large"
            )
        }

        if (elementType == 9L) {
            throw IOException(
                "Nested GGUF arrays are invalid"
            )
        }

        val elementSize = when (elementType) {
            0L, 1L, 7L -> 1L
            2L, 3L -> 2L
            4L, 5L, 6L -> 4L
            10L, 11L, 12L -> 8L
            8L -> -1L
            else -> {
                throw IOException(
                    "Unsupported GGUF array element type"
                )
            }
        }

        if (elementSize >= 0L) {
            if (
                count > 0L &&
                elementSize > Long.MAX_VALUE / count
            ) {
                throw IOException(
                    "GGUF array size overflow"
                )
            }

            skipChecked(
                input,
                count * elementSize
            )
        } else {
            repeat(count.toInt()) {
                skipString(input)
            }
        }
    }

    private fun alignPosition(
        position: Long,
        alignment: Long
    ): Long {
        if (
            alignment <= 0L ||
            alignment > 4096L ||
            alignment and (alignment - 1L) != 0L
        ) {
            throw IOException(
                "Invalid GGUF alignment"
            )
        }

        val remainder = position % alignment

        if (remainder == 0L) {
            return position
        }

        val addition = alignment - remainder

        if (position > Long.MAX_VALUE - addition) {
            throw IOException(
                "GGUF alignment overflow"
            )
        }

        return position + addition
    }

    private fun inspectGgufFile(
        file: File
    ): GgufInspection {

        RandomAccessFile(file, "r").use { input ->

            val fileLength = input.length()

            if (fileLength < 24L) {
                throw IOException(
                    "GGUF file is too small"
                )
            }

            // -------------------------------------------------
            // Header
            // -------------------------------------------------

            val magic = ByteArray(4)
            input.readFully(magic)

            if (
                magic[0] != 'G'.code.toByte() ||
                magic[1] != 'G'.code.toByte() ||
                magic[2] != 'U'.code.toByte() ||
                magic[3] != 'F'.code.toByte()
            ) {
                throw IOException(
                    "Invalid GGUF magic header"
                )
            }

            val version = readU32(input)

            if (
                version < GGUF_VERSION_MIN ||
                version > GGUF_VERSION_MAX
            ) {
                throw IOException(
                    "Unsupported GGUF version: $version"
                )
            }

            val tensorCount = readU64(input)
            val metadataCount = readU64(input)

            if (
                tensorCount == 0L ||
                tensorCount > MAX_TENSORS
            ) {
                throw IOException(
                    "Invalid GGUF tensor count"
                )
            }

            if (
                metadataCount == 0L ||
                metadataCount > MAX_METADATA_ENTRIES
            ) {
                throw IOException(
                    "Invalid GGUF metadata count"
                )
            }

            // -------------------------------------------------
            // Metadata
            // -------------------------------------------------

            val metadata = HashMap<String, Any>()

            var chatTemplateAvailable = false

            repeat(metadataCount.toInt()) {

                val key = readUtf8(
                    input,
                    MAX_METADATA_KEY_BYTES
                )

                if (key.isBlank()) {
                    throw IOException(
                        "GGUF contains an empty metadata key"
                    )
                }

                if (metadata.containsKey(key)) {
                    throw IOException(
                        "Duplicate GGUF metadata key: $key"
                    )
                }

                val valueType = readU32(input)

                val value = readMetadataValue(
                    input,
                    valueType,
                    key
                )

                if (value === ChatTemplateMarker) {
                    chatTemplateAvailable = true
                } else if (
                    value != null &&
                    isRelevantMetadataKey(key)
                ) {
                    metadata[key] = value
                }
            }

            val architecture = (
                metadata["general.architecture"] as? String
            )?.trim().orEmpty()

            if (architecture.isBlank()) {
                throw IOException(
                    "GGUF architecture metadata is missing"
                )
            }

            val modelName = (
                metadata["general.name"] as? String
            )?.takeIf {
                it.isNotBlank()
            }

            val alignment = (
                metadata["general.alignment"] as? Number
            )?.toLong() ?: 32L

            // -------------------------------------------------
            // Tensor descriptors
            // -------------------------------------------------

            repeat(tensorCount.toInt()) {

                val tensorName = readUtf8(
                    input,
                    MAX_METADATA_KEY_BYTES
                )

                if (tensorName.isBlank()) {
                    throw IOException(
                        "GGUF contains an empty tensor name"
                    )
                }

                val dimensions = readU32(input)

                if (
                    dimensions < 1L ||
                    dimensions > 4L
                ) {
                    throw IOException(
                        "Invalid GGUF tensor dimensions"
                    )
                }

                repeat(dimensions.toInt()) {
                    val dimension = readU64(input)

                    if (dimension == 0L) {
                        throw IOException(
                            "Invalid GGUF tensor shape"
                        )
                    }
                }

                // Tensor data type.
                readU32(input)

                // Offset relative to the tensor data section.
                val tensorOffset = readU64(input)

                if (
                    tensorOffset >= fileLength
                ) {
                    throw IOException(
                        "GGUF tensor offset is outside the file"
                    )
                }

                if (tensorOffset % alignment != 0L) {
                    throw IOException(
                        "GGUF tensor offset is not aligned"
                    )
                }
            }

            // -------------------------------------------------
            // Tensor data section
            // -------------------------------------------------

            val dataOffset = alignPosition(
                input.filePointer,
                alignment
            )

            if (dataOffset >= fileLength) {
                throw IOException(
                    "GGUF tensor data section is missing"
                )
            }

            input.seek(dataOffset)

            // -------------------------------------------------
            // Extract architecture-specific metadata
            // -------------------------------------------------

            fun findArchitectureValue(
                suffix: String
            ): Long? {
                val key = "$architecture.$suffix"

                return (
                    metadata[key] as? Number
                )?.toLong()
            }

            val contextLength = findArchitectureValue(
                "context_length"
            )

            val embeddingLength = findArchitectureValue(
                "embedding_length"
            )

            val blockCount = findArchitectureValue(
                "block_count"
            )

            return GgufInspection(
                version = version,
                tensorCount = tensorCount,
                metadataCount = metadataCount,
                architecture = architecture,
                modelName = modelName,
                contextLength = contextLength,
                embeddingLength = embeddingLength,
                blockCount = blockCount,
                chatTemplateAvailable = chatTemplateAvailable,
                alignment = alignment,
                dataOffset = dataOffset
            )
        }
    }

    // =========================================================
    // Model classification
    // =========================================================

    private fun classifyModel(
        architecture: String
    ): String {

        val arch = architecture.lowercase()

        val multimodalArchitectures = setOf(
            "llava",
            "llava15",
            "llava16",
            "qwen2vl",
            "qwen2_vl",
            "qwen2.5vl",
            "qwen2_5_vl",
            "qwen3vl",
            "minicpmv",
            "pixtral",
            "gemma3",
            "mllama",
            "internvl",
            "smolvlm",
            "idefics",
            "idefics2",
            "llama4"
        )

        if (
            arch in multimodalArchitectures ||
            arch.contains("vision")
        ) {
            return "multimodal"
        }

        val textArchitectures = setOf(
            "llama",
            "llama2",
            "llama3",
            "qwen",
            "qwen2",
            "qwen3",
            "mistral",
            "mixtral",
            "gemma",
            "gemma2",
            "phi2",
            "phi3",
            "phi3small",
            "phi4",
            "deepseek",
            "deepseek2",
            "deepseek3",
            "command-r",
            "chatglm",
            "internlm2",
            "falcon",
            "baichuan",
            "starcoder2",
            "stablelm",
            "granite",
            "nemotron",
            "solar",
            "olmo",
            "mamba",
            "rwkv"
        )

        if (
            arch in textArchitectures ||
            arch.startsWith("llama")
        ) {
            return "text"
        }

        return "unknown"
    }

    // =========================================================
    // Text model loading
    // =========================================================

    suspend fun loadModelAsync(
        modelPath: String
    ): Result<Unit> {

        return withContext(Dispatchers.IO) {
            engineMutex.withLock {
                try {
                    if (!nativeEngineReady || closed) {
                        return@withLock Result.failure(
                            IllegalStateException(
                                "Native engine is unavailable"
                            )
                        )
                    }

                    val file = validateModelFile(modelPath)
                        ?: return@withLock Result.failure(
                            IllegalArgumentException(
                                "Invalid model file: $modelPath"
                            )
                        )

                    val inspection = try {
                        inspectGgufFile(file)
                    } catch (e: Exception) {
                        Log.e(
                            TAG,
                            "GGUF inspection failed",
                            e
                        )

                        return@withLock Result.failure(
                            IllegalArgumentException(
                                "GGUF validation failed: " +
                                    (e.message ?: "Unknown format error"),
                                e
                            )
                        )
                    }

                    Log.i(
                        TAG,
                        "GGUF validated: " +
                            "architecture=${inspection.architecture}, " +
                            "tensors=${inspection.tensorCount}, " +
                            "version=${inspection.version}"
                    )

                    textModelLoaded = false

                    val response = nativeLoadModel(
                        file.absolutePath
                    )

                    if (
                        response.contains(
                            "بنجاح",
                            ignoreCase = true
                        ) ||
                        response.contains(
                            "success",
                            ignoreCase = true
                        )
                    ) {
                        textModelLoaded = true
                        Result.success(Unit)
                    } else {
                        Log.e(
                            TAG,
                            "Model loading failed: $response"
                        )

                        Result.failure(
                            IllegalStateException(
                                response.ifBlank {
                                    "Unknown native loading error"
                                }
                            )
                        )
                    }

                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    textModelLoaded = false
                    Log.e(TAG, "Text model loading failed", e)
                    Result.failure(e)
                }
            }
        }
    }

    /**
     * Compatibility wrapper for older callback-based callers.
     */
    fun loadModel(
        modelPath: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        callbackScope.launch {
            val result = loadModelAsync(modelPath)

            withContext(Dispatchers.Main) {
                result.fold(
                    onSuccess = {
                        onSuccess()
                    },
                    onFailure = { error ->
                        onError(
                            error.message
                                ?: "Unknown model loading error"
                        )
                    }
                )
            }
        }
    }

    // =========================================================
    // Text generation
    // =========================================================

    override suspend fun generate(
        prompt: String,
        agentId: Int
    ): String {
        return generateResponse(prompt, agentId)
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

                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Text generation failed", e)
                    throw e
                }
            }
        }
    }

    // =========================================================
    // Multi-message conversation generation
    // =========================================================

    suspend fun generateConversation(
        roles: Array<String>,
        contents: Array<String>,
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

                    require(agentId >= 0) {
                        "Invalid agent ID"
                    }

                    require(roles.isNotEmpty()) {
                        "Conversation history is empty"
                    }

                    require(roles.size == contents.size) {
                        "Conversation roles and contents do not match"
                    }

                    require(
                        roles.all {
                            it == "system" ||
                            it == "user" ||
                            it == "assistant" ||
                            it == "tool"
                        }
                    ) {
                        "Invalid conversation role"
                    }

                    require(contents.all { it.isNotBlank() }) {
                        "Conversation contains an empty message"
                    }

                    nativeGenerateConversation(
                        roles,
                        contents,
                        agentId
                    )

                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(
                        TAG,
                        "Conversation generation failed",
                        e
                    )
                    throw e
                }
            }
        }
    }

    // =========================================================
    // Sub-agent management
    // =========================================================

    fun createSubAgent(): Int {
        if (
            !nativeEngineReady ||
            closed ||
            !textModelLoaded
        ) {
            return -1
        }

        return try {
            if (!engineMutex.tryLock()) {
                return -1
            }

            try {
                nativeCreateSubAgent()
            } finally {
                engineMutex.unlock()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Sub-agent creation failed", e)
            -1
        }
    }

    // =========================================================
    // Vision model management
    // =========================================================

    suspend fun loadVisionModelAsync(
        visionModelPath: String,
        mmprojPath: String
    ): Result<Unit> {

        return withContext(Dispatchers.IO) {
            engineMutex.withLock {
                try {
                    if (!nativeEngineReady || closed) {
                        return@withLock Result.failure(
                            IllegalStateException(
                                "Native engine unavailable"
                            )
                        )
                    }

                    val modelFile = validateModelFile(
                        visionModelPath
                    ) ?: return@withLock Result.failure(
                        IllegalArgumentException(
                            "Invalid vision model"
                        )
                    )

                    val projectorFile = validateModelFile(
                        mmprojPath
                    ) ?: return@withLock Result.failure(
                        IllegalArgumentException(
                            "Invalid projector model"
                        )
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
                            IllegalStateException(
                                "Vision initialization failed"
                            )
                        )
                    }

                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    visionModelLoaded = false
                    Log.e(TAG, "Vision loading failed", e)
                    Result.failure(e)
                }
            }
        }
    }

    /**
     * Compatibility wrapper for older vision callers.
     */
    fun loadVisionModel(
        visionModelPath: String,
        mmprojPath: String,
        onResult: (Boolean) -> Unit
    ) {
        callbackScope.launch {
            val result = loadVisionModelAsync(
                visionModelPath,
                mmprojPath
            )

            withContext(Dispatchers.Main) {
                onResult(result.isSuccess)
            }
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

                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Frame analysis failed", e)

                    JSONObject()
                        .put(
                            "error",
                            e.message ?: "Unknown vision error"
                        )
                        .toString()
                }
            }
        }
    }

    // =========================================================
    // Unloading
    // =========================================================

    fun unloadModel() {
        if (!nativeEngineReady || closed) return
        if (!engineMutex.tryLock()) return

        try {
            if (textModelLoaded) {
                nativeUnloadModel()
                textModelLoaded = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Text model unloading failed", e)
        } finally {
            engineMutex.unlock()
        }
    }

    fun unloadVisionModel() {
        if (!nativeEngineReady || closed) return
        if (!engineMutex.tryLock()) return

        try {
            if (visionModelLoaded) {
                VisionProcessor.nativeFreeVision()
                visionModelLoaded = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Vision model unloading failed", e)
        } finally {
            engineMutex.unlock()
        }
    }

    suspend fun unloadAllAsync() {
        withContext(Dispatchers.IO) {
            engineMutex.withLock {
                if (closed) return@withLock

                try {
                    if (textModelLoaded) {
                        nativeUnloadModel()
                    }

                    if (visionModelLoaded) {
                        VisionProcessor.nativeFreeVision()
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

    // =========================================================
    // Public model information
    // =========================================================

    fun getModelDetails(modelPath: String): String {
        return try {
            val file = validateModelFile(modelPath)
                ?: return "الملف غير موجود أو غير صالح"

            val inspection = inspectGgufFile(file)

            val sizeBytes = file.length()
            val sizeMB = sizeBytes.toDouble() /
                (1024.0 * 1024.0)

            val modelType = classifyModel(
                inspection.architecture
            )

            JSONObject()
                .put("name", inspection.modelName ?: file.name)
                .put("fileName", file.name)
                .put("sizeMB", sizeMB)
                .put("sizeBytes", sizeBytes)
                .put("readable", file.canRead())
                .put("format", "GGUF")
                .put("ggufVersion", inspection.version)
                .put("tensorCount", inspection.tensorCount)
                .put("metadataCount", inspection.metadataCount)
                .put("architecture", inspection.architecture)
                .put("modelType", modelType)
                .put("contextLength", inspection.contextLength ?: JSONObject.NULL)
                .put("embeddingLength", inspection.embeddingLength ?: JSONObject.NULL)
                .put("blockCount", inspection.blockCount ?: JSONObject.NULL)
                .put("alignment", inspection.alignment)
                .put("dataOffset", inspection.dataOffset)
                .put("chatTemplateAvailable", inspection.chatTemplateAvailable)
                .put("ggufStructureValid", true)
                .put("runtimeTested", false)
                .toString()

        } catch (e: Exception) {
            Log.e(TAG, "Model details failed", e)

            JSONObject()
                .put("ggufStructureValid", false)
                .put(
                    "error",
                    e.message ?: "GGUF inspection failed"
                )
                .toString()
        }
    }
}
