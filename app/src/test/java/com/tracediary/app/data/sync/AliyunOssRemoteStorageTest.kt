package com.tracediary.app.data.sync

import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
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

    @Test
    fun list_fetchesAllPagesUntilNotTruncated() = runTest {
        val requestedMarkers = mutableListOf<String?>()
        val client = OkHttpClient.Builder()
            .addInterceptor { chain: Interceptor.Chain ->
                val request = chain.request()
                val marker = request.url.queryParameter("marker")
                requestedMarkers += marker
                val body = if (marker == null) {
                    listBucketXml(
                        key = "TraceDiary/diary/2026-01-01.json",
                        isTruncated = true,
                        nextMarker = "TraceDiary/diary/2026-01-01.json",
                    )
                } else {
                    listBucketXml(
                        key = "TraceDiary/diary/2026-01-02.json",
                        isTruncated = false,
                    )
                }
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body.toResponseBody(null))
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
                pathPrefix = "TraceDiary",
            ),
            client = client,
        )

        val result = storage.list("diary/")

        assertTrue(result is RemoteResult.Ok)
        val objects = (result as RemoteResult.Ok).value
        assertEquals(2, objects.size)
        assertEquals("TraceDiary/diary/2026-01-01.json", objects[0].key)
        assertEquals("TraceDiary/diary/2026-01-02.json", objects[1].key)
        assertEquals(listOf(null, "TraceDiary/diary/2026-01-01.json"), requestedMarkers)
    }

    @Test
    fun parseListBucketPage_readsPaginationFields() {
        val page = parseListBucketPage(
            listBucketXml(
                key = "TraceDiary/diary/2026-01-01.json",
                isTruncated = true,
                nextMarker = "TraceDiary/diary/2026-01-01.json",
            ).toByteArray(),
        )

        assertEquals(1, page.objects.size)
        assertTrue(page.isTruncated)
        assertEquals("TraceDiary/diary/2026-01-01.json", page.nextMarker)
    }

    private fun listBucketXml(
        key: String,
        isTruncated: Boolean,
        nextMarker: String? = null,
    ): String = buildString {
        append("<ListBucketResult>")
        append("<Contents>")
        append("<Key>").append(key).append("</Key>")
        append("<LastModified>2026-05-27T08:00:00.000Z</LastModified>")
        append("<ETag>etag</ETag>")
        append("<Size>12</Size>")
        append("</Contents>")
        append("<IsTruncated>").append(isTruncated).append("</IsTruncated>")
        if (nextMarker != null) {
            append("<NextMarker>").append(nextMarker).append("</NextMarker>")
        }
        append("</ListBucketResult>")
    }
}
