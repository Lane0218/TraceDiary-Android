package com.tracediary.app.data.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** 依赖 Android XmlPullParser 实现的 OSS 列表解析与分页集成测试。 */
@RunWith(AndroidJUnit4::class)
class AliyunOssRemoteStorageInstrumentedTest {

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

        assertTrue(result.toString(), result is RemoteResult.Ok)
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
        append("<ListBucketResult><Contents><Key>")
        append(key)
        append("</Key><LastModified>2026-05-27T08:00:00.000Z</LastModified>")
        append("<ETag>etag</ETag><Size>12</Size></Contents><IsTruncated>")
        append(isTruncated)
        append("</IsTruncated>")
        if (nextMarker != null) append("<NextMarker>").append(nextMarker).append("</NextMarker>")
        append("</ListBucketResult>")
    }
}
