package com.tracediary.app.data.ai

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class AiModelManagerInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val testRoot = "ai-model-manager-test"

    @After
    fun tearDown() {
        File(context.filesDir, testRoot).deleteRecursively()
    }

    @Test
    fun download_primaryChecksumFailure_retriesCompleteBackupPair() = runBlocking {
        val model = "verified-model".toByteArray()
        val vocabulary = "verified-vocabulary".toByteArray()
        val badVocabulary = vocabulary.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
        val requests = mutableListOf<String>()
        val client = client(
            content = { host, path ->
                when (host to path) {
                    "primary.test" to "/model" -> model
                    "primary.test" to "/vocab" -> badVocabulary
                    "backup.test" to "/model" -> model
                    "backup.test" to "/vocab" -> vocabulary
                    else -> null
                }
            },
            onRequest = { host, path -> synchronized(requests) { requests += "$host$path" } },
        )
        val manager = manager("$testRoot/fallback", client, model, vocabulary)

        val files = manager.download().getOrThrow()

        assertArrayEquals(model, files.model.readBytes())
        assertArrayEquals(vocabulary, files.vocabulary.readBytes())
        assertEquals(AiModelState.Ready, manager.state.value)
        assertTrue(manager.verifyInstalled())
        assertEquals(
            listOf(
                "primary.test/model",
                "primary.test/vocab",
                "backup.test/model",
                "backup.test/vocab",
            ),
            requests,
        )
    }

    @Test
    fun download_allSourcesFail_preservesExistingVerifiedFiles() = runBlocking {
        val model = "existing-model".toByteArray()
        val vocabulary = "existing-vocabulary".toByteArray()
        val directory = "$testRoot/preserve"
        val seedManager = manager(
            directory,
            client(content = { _, path -> if (path == "/model") model else vocabulary }),
            model,
            vocabulary,
            sources = listOf(AiModelDownloadSource("https://seed.test/model", "https://seed.test/vocab")),
        )
        seedManager.download().getOrThrow()

        val failingManager = manager(
            directory,
            client(content = { _, _ -> null }),
            model,
            vocabulary,
            sources = listOf(AiModelDownloadSource("https://offline.test/model", "https://offline.test/vocab")),
        )
        assertTrue(failingManager.download().isFailure)

        val preserved = failingManager.filesOrNull()
        assertNotNull(preserved)
        assertArrayEquals(model, preserved?.model?.readBytes())
        assertArrayEquals(vocabulary, preserved?.vocabulary?.readBytes())
        assertEquals(AiModelState.Ready, failingManager.state.value)
    }

    private fun manager(
        directory: String,
        client: OkHttpClient,
        model: ByteArray,
        vocabulary: ByteArray,
        sources: List<AiModelDownloadSource> = listOf(
            AiModelDownloadSource("https://primary.test/model", "https://primary.test/vocab"),
            AiModelDownloadSource("https://backup.test/model", "https://backup.test/vocab"),
        ),
    ): AiModelManager = AiModelManager(
        context = context,
        client = client,
        downloadSources = sources,
        modelArtifact = AiModelArtifact(model.size.toLong(), model.sha256()),
        vocabularyArtifact = AiModelArtifact(vocabulary.size.toLong(), vocabulary.sha256()),
        modelDirectoryPath = directory,
    )

    private fun client(
        content: (host: String, path: String) -> ByteArray?,
        onRequest: (host: String, path: String) -> Unit = { _, _ -> },
    ): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val request = chain.request()
                onRequest(request.url.host, request.url.encodedPath)
                val bytes = content(request.url.host, request.url.encodedPath)
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(if (bytes == null) 503 else 200)
                    .message(if (bytes == null) "Unavailable" else "OK")
                    .body((bytes ?: ByteArray(0)).toResponseBody(null as MediaType?))
                    .build()
            })
            .build()

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { "%02x".format(it) }
}
