package com.tracediary.app.data.sync

import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AliyunOssRemoteStorageTest {

    @Test
    fun put_doesNotSendIfMatchHeaderEvenWhenEtagProvided() = runTest {
        var observedIfMatch: String? = "not-called"
        val client = OkHttpClient.Builder()
            .addInterceptor { chain: Interceptor.Chain ->
                val request = chain.request()
                observedIfMatch = request.header("If-Match")
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .header("Date", "Sun, 18 May 2025 10:00:00 GMT")
                    .header("ETag", "\"etag-after-upload\"")
                    .body(ByteArray(0).toResponseBody(null))
                    .build()
            }
            .build()
        val storage = AliyunOssRemoteStorage(
            credential = RemoteCredential(
                provider = SyncProvider.ALIYUN_OSS,
                region = "oss-cn-hangzhou",
                bucket = "trace-diary",
                accessKeyId = "LTAI-test",
                accessKeySecret = "secret",
                pathPrefix = "",
            ),
            client = client,
        )

        val result = storage.put(
            key = "diary/2026-05-18.json",
            body = """{"content":"test"}""".toByteArray(),
            contentType = "application/json",
            ifMatchEtag = "etag-before-upload",
        )

        assertTrue(result is RemoteResult.Ok)
        assertNull(observedIfMatch)
    }

}
