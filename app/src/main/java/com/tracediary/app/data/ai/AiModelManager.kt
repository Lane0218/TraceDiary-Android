package com.tracediary.app.data.ai

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed interface AiModelState {
    data object Missing : AiModelState
    data class Downloading(val progress: Float) : AiModelState
    data object Ready : AiModelState
    data class Failed(val message: String) : AiModelState
}

data class AiModelFiles(val model: File, val vocabulary: File)

internal data class AiModelDownloadSource(
    val modelUrl: String,
    val vocabularyUrl: String,
)

internal data class AiModelArtifact(
    val sizeBytes: Long,
    val sha256: String,
)

class AiModelManager internal constructor(
    context: Context,
    private val client: OkHttpClient,
    private val downloadSources: List<AiModelDownloadSource>,
    private val modelArtifact: AiModelArtifact,
    private val vocabularyArtifact: AiModelArtifact,
    modelDirectoryPath: String,
) {
    constructor(
        context: Context,
        client: OkHttpClient = OkHttpClient(),
    ) : this(
        context = context,
        client = client,
        downloadSources = DEFAULT_DOWNLOAD_SOURCES,
        modelArtifact = DEFAULT_MODEL_ARTIFACT,
        vocabularyArtifact = DEFAULT_VOCABULARY_ARTIFACT,
        modelDirectoryPath = MODEL_DIRECTORY,
    )

    private val filesDirectory = context.applicationContext.filesDir
    private val modelDirectory = File(filesDirectory, modelDirectoryPath)
    private val stagingDirectory = File(filesDirectory, "$modelDirectoryPath.staging")
    private val backupDirectory = File(filesDirectory, "$modelDirectoryPath.backup")
    private val modelFile = File(modelDirectory, MODEL_FILE)
    private val vocabularyFile = File(modelDirectory, VOCABULARY_FILE)
    private val markerFile = File(modelDirectory, VERIFIED_MARKER_FILE)
    private val verifiedMarkerContent = "${modelArtifact.sha256}:${vocabularyArtifact.sha256}"
    private val totalDownloadSize = modelArtifact.sizeBytes + vocabularyArtifact.sizeBytes
    private val _state = MutableStateFlow<AiModelState>(detectState())
    val state: StateFlow<AiModelState> = _state.asStateFlow()

    fun filesOrNull(): AiModelFiles? = if (
        _state.value == AiModelState.Ready &&
        modelFile.length() == modelArtifact.sizeBytes &&
        vocabularyFile.length() == vocabularyArtifact.sizeBytes
    ) {
        AiModelFiles(modelFile, vocabularyFile)
    } else null

    suspend fun download(): Result<AiModelFiles> = withContext(Dispatchers.IO) {
        try {
            check(downloadSources.isNotEmpty()) { "未配置模型下载源" }
            var lastFailure: Throwable? = null
            for (source in downloadSources) {
                currentCoroutineContext().ensureActive()
                stagingDirectory.deleteRecursively()
                check(stagingDirectory.mkdirs()) { "无法创建模型临时目录" }
                _state.value = AiModelState.Downloading(0f)
                try {
                    downloadArtifact(
                        url = source.modelUrl,
                        destination = File(stagingDirectory, MODEL_FILE),
                        artifact = modelArtifact,
                        completedBefore = 0L,
                    )
                    downloadArtifact(
                        url = source.vocabularyUrl,
                        destination = File(stagingDirectory, VOCABULARY_FILE),
                        artifact = vocabularyArtifact,
                        completedBefore = modelArtifact.sizeBytes,
                    )
                    File(stagingDirectory, VERIFIED_MARKER_FILE)
                        .writeText(verifiedMarkerContent, Charsets.US_ASCII)
                    installStagedFiles()
                    _state.value = AiModelState.Ready
                    return@withContext Result.success(AiModelFiles(modelFile, vocabularyFile))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    lastFailure = error
                    stagingDirectory.deleteRecursively()
                }
            }
            throw IllegalStateException(
                "模型下载失败，请检查网络后重试",
                lastFailure,
            )
        } catch (cancelled: CancellationException) {
            stagingDirectory.deleteRecursively()
            _state.value = detectState()
            throw cancelled
        } catch (error: Throwable) {
            stagingDirectory.deleteRecursively()
            _state.value = if (detectState() == AiModelState.Ready) {
                AiModelState.Ready
            } else {
                AiModelState.Failed(error.userMessage())
            }
            Result.failure(error)
        }
    }

    fun delete() {
        modelDirectory.deleteRecursively()
        stagingDirectory.deleteRecursively()
        backupDirectory.deleteRecursively()
        _state.value = AiModelState.Missing
    }

    suspend fun verifyInstalled(): Boolean = withContext(Dispatchers.IO) {
        if (_state.value != AiModelState.Ready) return@withContext false
        val valid = modelFile.length() == modelArtifact.sizeBytes &&
            vocabularyFile.length() == vocabularyArtifact.sizeBytes &&
            sha256(modelFile) == modelArtifact.sha256 &&
            sha256(vocabularyFile) == vocabularyArtifact.sha256
        if (valid) {
            markerFile.writeText(verifiedMarkerContent, Charsets.US_ASCII)
        } else {
            delete()
        }
        valid
    }

    private suspend fun downloadArtifact(
        url: String,
        destination: File,
        artifact: AiModelArtifact,
        completedBefore: Long,
    ) {
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).await().use { response ->
            if (!response.isSuccessful) error("模型下载失败（HTTP ${response.code}）")
            val body = response.body ?: error("模型下载响应为空")
            val digest = MessageDigest.getInstance("SHA-256")
            var copied = 0L
            body.byteStream().use { input ->
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        copied += count
                        check(copied <= artifact.sizeBytes) { "模型文件大小不符" }
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        _state.value = AiModelState.Downloading(
                            ((completedBefore + copied).toFloat() / totalDownloadSize)
                                .coerceIn(0f, 1f),
                        )
                    }
                }
            }
            check(copied == artifact.sizeBytes) { "模型文件大小不符" }
            check(digest.digest().toHex() == artifact.sha256) { "模型校验失败" }
        }
    }

    private fun installStagedFiles() {
        backupDirectory.deleteRecursively()
        val hadExisting = modelDirectory.exists()
        if (hadExisting) {
            check(modelDirectory.renameTo(backupDirectory)) { "无法备份已有模型" }
        }
        try {
            check(stagingDirectory.renameTo(modelDirectory)) { "无法启用已下载模型" }
            backupDirectory.deleteRecursively()
        } catch (error: Throwable) {
            modelDirectory.deleteRecursively()
            if (hadExisting) {
                check(backupDirectory.renameTo(modelDirectory)) { "无法恢复已有模型" }
            }
            throw error
        }
    }

    private fun detectState(): AiModelState {
        val marker = markerFile.readTextOrNull()
        val markerMatches = marker == verifiedMarkerContent ||
            (usesDefaultArtifacts() && marker == LEGACY_ARCHIVE_SHA256)
        return if (
            modelFile.isFile && vocabularyFile.isFile &&
            modelFile.length() == modelArtifact.sizeBytes &&
            vocabularyFile.length() == vocabularyArtifact.sizeBytes &&
            markerMatches
        ) {
            AiModelState.Ready
        } else {
            AiModelState.Missing
        }
    }

    private fun usesDefaultArtifacts(): Boolean =
        modelArtifact == DEFAULT_MODEL_ARTIFACT && vocabularyArtifact == DEFAULT_VOCABULARY_ARTIFACT

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().toHex()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
    private fun File.readTextOrNull(): String? = runCatching { readText(Charsets.US_ASCII) }.getOrNull()
    private fun Throwable.userMessage(): String = message?.takeIf(String::isNotBlank) ?: "模型下载失败"

    companion object {
        const val MODEL_ID = "BAAI/bge-small-zh-v1.5-int8-v1"
        const val MODEL_SHA256 = "15b717c382bcb518ba457b93ea6850ede7f4f1cd8937454aa06972366cd19bcc"
        const val VOCABULARY_SHA256 = "45bbac6b341c319adc98a532532882e91a9cefc0329aa57bac9ae761c27b291c"
        const val MODEL_SIZE_BYTES = 24_010_842L
        const val VOCABULARY_SIZE_BYTES = 109_540L
        const val TOTAL_DOWNLOAD_SIZE_BYTES = MODEL_SIZE_BYTES + VOCABULARY_SIZE_BYTES

        private const val MODELSCOPE_REVISION = "08d7186b7de51be7c12444137221ad96825593d6"
        private const val HUGGING_FACE_REVISION = "75c43b069aac4d136ba6bc1122f995fedcfd2781"
        private const val MODELSCOPE_BASE =
            "https://www.modelscope.cn/models/Xenova/bge-small-zh-v1.5/resolve/$MODELSCOPE_REVISION"
        private const val HUGGING_FACE_BASE =
            "https://huggingface.co/Xenova/bge-small-zh-v1.5/resolve/$HUGGING_FACE_REVISION"
        private const val MODEL_DIRECTORY = "ai-model/bge-small-zh-v1.5-int8-v1"
        private const val MODEL_FILE = "model.onnx"
        private const val VOCABULARY_FILE = "vocab.txt"
        private const val VERIFIED_MARKER_FILE = ".verified-sha256"
        private const val LEGACY_ARCHIVE_SHA256 =
            "62a7917624b8fb583e416b8c80b6541aa8927a0327b73e95467aaef682a7a1cf"

        private val DEFAULT_MODEL_ARTIFACT = AiModelArtifact(MODEL_SIZE_BYTES, MODEL_SHA256)
        private val DEFAULT_VOCABULARY_ARTIFACT =
            AiModelArtifact(VOCABULARY_SIZE_BYTES, VOCABULARY_SHA256)
        private val DEFAULT_DOWNLOAD_SOURCES = listOf(
            AiModelDownloadSource(
                modelUrl = "$MODELSCOPE_BASE/onnx/model_quantized.onnx",
                vocabularyUrl = "$MODELSCOPE_BASE/vocab.txt",
            ),
            AiModelDownloadSource(
                modelUrl = "$HUGGING_FACE_BASE/onnx/model_quantized.onnx?download=true",
                vocabularyUrl = "$HUGGING_FACE_BASE/vocab.txt?download=true",
            ),
        )
    }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isActive) continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            if (continuation.isActive) continuation.resume(response) else response.close()
        }
    })
}
