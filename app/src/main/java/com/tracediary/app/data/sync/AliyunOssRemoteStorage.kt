package com.tracediary.app.data.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 阿里云 OSS 实现，签名算法走 V1：
 *   Authorization: OSS {AccessKeyId}:{Signature}
 *   Signature = Base64(HMAC-SHA1(AccessKeySecret,
 *       VERB\nContent-MD5\nContent-Type\nDate\nCanonicalizedOSSHeaders+CanonicalizedResource))
 *
 * Endpoint: https://{bucket}.{region}.aliyuncs.com，例如 region = oss-cn-hangzhou。
 *
 * XML 列表解析使用 Android 内置的 [XmlPullParserFactory]，避免引入额外依赖。
 */
class AliyunOssRemoteStorage(
    private val credential: RemoteCredential,
    private val client: OkHttpClient = defaultClient(),
) : RemoteStorage {

    init {
        require(credential.provider == SyncProvider.ALIYUN_OSS) {
            "credential.provider must be ALIYUN_OSS"
        }
    }

    private val host: String = "${credential.bucket}.${credential.region}.aliyuncs.com"

    override suspend fun put(
        key: String,
        body: ByteArray,
        contentType: String,
        ifMatchEtag: String?,
    ): RemoteResult<RemoteObject> = withContext(Dispatchers.IO) {
        val finalKey = applyPrefix(key)
        val date = ossHttpDate()
        val canonicalizedResource = "/${credential.bucket}/$finalKey"
        val stringToSign = buildString {
            append("PUT\n")
            append("\n") // Content-MD5
            append(contentType).append("\n")
            append(date).append("\n")
            // CanonicalizedOSSHeaders 留空
            append(canonicalizedResource)
        }
        val auth = ossAuthorization(stringToSign)
        val mediaType = contentType.toMediaTypeOrNull()
        val builder = Request.Builder()
            .url("https://$host/$finalKey")
            .put(body.toRequestBody(mediaType))
            .header("Host", host)
            .header("Date", date)
            .header("Content-Type", contentType)
            .header("Authorization", auth)
        if (ifMatchEtag != null) builder.header("If-Match", ifMatchEtag)

        runCatching { client.newCall(builder.build()).execute() }
            .fold(
                onSuccess = { resp ->
                    resp.use {
                        if (it.isSuccessful) {
                            RemoteResult.Ok(
                                RemoteObject(
                                    key = finalKey,
                                    etag = it.header("ETag")?.trim('"'),
                                    lastModified = parseHttpDate(it.header("Date")),
                                    sizeBytes = body.size.toLong(),
                                )
                            )
                        } else {
                            mapHttpError(it)
                        }
                    }
                },
                onFailure = { mapIoError(it) },
            )
    }

    override suspend fun get(key: String): RemoteResult<ByteArray> = withContext(Dispatchers.IO) {
        val finalKey = applyPrefix(key)
        val date = ossHttpDate()
        val resource = "/${credential.bucket}/$finalKey"
        val stringToSign = "GET\n\n\n$date\n$resource"
        val request = Request.Builder()
            .url("https://$host/$finalKey")
            .get()
            .header("Host", host)
            .header("Date", date)
            .header("Authorization", ossAuthorization(stringToSign))
            .build()
        runCatching { client.newCall(request).execute() }.fold(
            onSuccess = { resp ->
                resp.use {
                    if (it.isSuccessful) {
                        RemoteResult.Ok(it.body?.bytes() ?: ByteArray(0))
                    } else {
                        mapHttpError(it)
                    }
                }
            },
            onFailure = { mapIoError(it) },
        )
    }

    override suspend fun head(key: String): RemoteResult<RemoteObject> = withContext(Dispatchers.IO) {
        val finalKey = applyPrefix(key)
        val date = ossHttpDate()
        val resource = "/${credential.bucket}/$finalKey"
        val stringToSign = "HEAD\n\n\n$date\n$resource"
        val request = Request.Builder()
            .url("https://$host/$finalKey")
            .head()
            .header("Host", host)
            .header("Date", date)
            .header("Authorization", ossAuthorization(stringToSign))
            .build()
        runCatching { client.newCall(request).execute() }.fold(
            onSuccess = { resp ->
                resp.use {
                    if (it.isSuccessful) {
                        RemoteResult.Ok(
                            RemoteObject(
                                key = finalKey,
                                etag = it.header("ETag")?.trim('"'),
                                lastModified = parseHttpDate(it.header("Last-Modified")),
                                sizeBytes = it.header("Content-Length")?.toLongOrNull() ?: 0L,
                            )
                        )
                    } else {
                        mapHttpError(it)
                    }
                }
            },
            onFailure = { mapIoError(it) },
        )
    }

    override suspend fun list(prefix: String): RemoteResult<List<RemoteObject>> = withContext(Dispatchers.IO) {
        val finalPrefix = applyPrefix(prefix)
        val encoded = urlEncodeRfc3986(finalPrefix)
        val date = ossHttpDate()
        // OSS V1 签名规则：max-keys/prefix/marker/delimiter 等普通列表参数不参与 CanonicalizedResource，
        // 只有 acl/uploads/partNumber 等子资源才参与。
        val resource = "/${credential.bucket}/"
        val stringToSign = "GET\n\n\n$date\n$resource"
        val url = "https://$host/?prefix=$encoded"
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Host", host)
            .header("Date", date)
            .header("Authorization", ossAuthorization(stringToSign))
            .build()
        runCatching { client.newCall(request).execute() }.fold(
            onSuccess = { resp ->
                resp.use {
                    if (it.isSuccessful) {
                        val xml = it.body?.bytes() ?: ByteArray(0)
                        runCatching { parseListBucketResult(xml) }.fold(
                            onSuccess = { list -> RemoteResult.Ok(list) },
                            onFailure = { e ->
                                RemoteResult.Err(ErrorCode.UNKNOWN, "解析 ListBucketResult 失败", e)
                            },
                        )
                    } else {
                        mapHttpError(it)
                    }
                }
            },
            onFailure = { mapIoError(it) },
        )
    }

    override suspend fun delete(key: String): RemoteResult<Unit> = withContext(Dispatchers.IO) {
        val finalKey = applyPrefix(key)
        val date = ossHttpDate()
        val resource = "/${credential.bucket}/$finalKey"
        val stringToSign = "DELETE\n\n\n$date\n$resource"
        val request = Request.Builder()
            .url("https://$host/$finalKey")
            .delete()
            .header("Host", host)
            .header("Date", date)
            .header("Authorization", ossAuthorization(stringToSign))
            .build()
        runCatching { client.newCall(request).execute() }.fold(
            onSuccess = { resp ->
                resp.use {
                    if (it.isSuccessful || it.code == 404) {
                        RemoteResult.Ok(Unit)
                    } else {
                        mapHttpError(it)
                    }
                }
            },
            onFailure = { mapIoError(it) },
        )
    }

    override suspend fun ping(): RemoteResult<Unit> = withContext(Dispatchers.IO) {
        val date = ossHttpDate()
        // 同 list()：max-keys 不是子资源，不参与 CanonicalizedResource。
        val resource = "/${credential.bucket}/"
        val stringToSign = "GET\n\n\n$date\n$resource"
        val request = Request.Builder()
            .url("https://$host/?max-keys=1")
            .get()
            .header("Host", host)
            .header("Date", date)
            .header("Authorization", ossAuthorization(stringToSign))
            .build()
        runCatching { client.newCall(request).execute() }.fold(
            onSuccess = { resp ->
                resp.use {
                    if (it.isSuccessful) RemoteResult.Ok(Unit) else mapHttpError(it)
                }
            },
            onFailure = { mapIoError(it) },
        )
    }

    private fun applyPrefix(key: String): String {
        val prefix = credential.pathPrefix.trim('/')
        val k = key.trimStart('/')
        return if (prefix.isEmpty()) k else "$prefix/$k"
    }

    private fun ossAuthorization(stringToSign: String): String {
        val sig = base64HmacSha1(credential.accessKeySecret, stringToSign)
        return "OSS ${credential.accessKeyId}:$sig"
    }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .callTimeout(15, TimeUnit.SECONDS)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}

// =================== 共用工具（file private） ===================

private fun base64HmacSha1(key: String, data: String): String {
    val mac = Mac.getInstance("HmacSHA1")
    mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA1"))
    val raw = mac.doFinal(data.toByteArray(Charsets.UTF_8))
    return android.util.Base64.encodeToString(raw, android.util.Base64.NO_WRAP)
}

/** RFC 1123 GMT 时间字符串，例如 Sun, 06 Nov 1994 08:49:37 GMT。 */
private fun ossHttpDate(): String {
    val fmt = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
    fmt.timeZone = TimeZone.getTimeZone("GMT")
    return fmt.format(Date())
}

private fun parseHttpDate(value: String?): Instant? {
    if (value.isNullOrBlank()) return null
    return runCatching {
        val fmt = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("GMT")
        fmt.parse(value)?.toInstant()
    }.getOrNull()
}

private fun urlEncodeRfc3986(value: String): String {
    val s = URLEncoder.encode(value, Charsets.UTF_8.name())
    return s.replace("+", "%20")
        .replace("*", "%2A")
        .replace("%7E", "~")
        .replace("%2F", "/") // 保留 path 中的 /
}

private fun mapHttpError(resp: Response): RemoteResult.Err {
    val code = when (resp.code) {
        401, 403 -> ErrorCode.AUTH
        404 -> ErrorCode.NOT_FOUND
        412 -> ErrorCode.CONFLICT
        in 500..599 -> ErrorCode.NETWORK
        else -> ErrorCode.UNKNOWN
    }
    return RemoteResult.Err(code, buildRemoteErrorMessage(resp))
}

private fun mapIoError(t: Throwable): RemoteResult.Err = when (t) {
    is IOException -> RemoteResult.Err(ErrorCode.NETWORK, t.message ?: "IO error", t)
    else -> RemoteResult.Err(ErrorCode.UNKNOWN, t.message ?: "未知错误", t)
}

internal fun buildRemoteErrorMessage(resp: Response): String {
    val head = "HTTP ${resp.code} ${resp.message}"
    val body = runCatching { resp.peekBody(MAX_ERROR_BODY).string() }.getOrNull().orEmpty()
    if (body.isBlank()) return head
    val errCode = extractXmlTag(body, "Code")
    val errMsg = extractXmlTag(body, "Message")
    val reqId = extractXmlTag(body, "RequestId")
    return buildString {
        append(head)
        if (!errCode.isNullOrBlank()) append(" · ").append(errCode)
        if (!errMsg.isNullOrBlank()) append("：").append(errMsg)
        if (!reqId.isNullOrBlank()) append("（RequestId ").append(reqId).append("）")
    }
}

private const val MAX_ERROR_BODY = 4096L

private fun extractXmlTag(xml: String, tag: String): String? {
    val regex = Regex("<$tag>([\\s\\S]*?)</$tag>")
    return regex.find(xml)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
}

/**
 * 解析 OSS / COS 通用的 ListBucketResult XML。两家的字段结构基本一致：
 *   <ListBucketResult>
 *     <Contents>
 *       <Key/><LastModified/><ETag/><Size/>
 *     </Contents>
 *   </ListBucketResult>
 */
internal fun parseListBucketResult(xml: ByteArray): List<RemoteObject> {
    val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
    val parser = factory.newPullParser()
    parser.setInput(xml.inputStream(), "UTF-8")
    val result = mutableListOf<RemoteObject>()
    var key: String? = null
    var etag: String? = null
    var lastModified: String? = null
    var size: Long = 0L
    var inContents = false
    var current: String? = null
    var ev = parser.eventType
    while (ev != XmlPullParser.END_DOCUMENT) {
        when (ev) {
            XmlPullParser.START_TAG -> {
                current = parser.name
                if (current == "Contents") {
                    inContents = true
                    key = null; etag = null; lastModified = null; size = 0L
                }
            }
            XmlPullParser.TEXT -> if (inContents) {
                val text = parser.text ?: ""
                when (current) {
                    "Key" -> key = (key ?: "") + text
                    "ETag" -> etag = (etag ?: "") + text
                    "LastModified" -> lastModified = (lastModified ?: "") + text
                    "Size" -> size = text.trim().toLongOrNull() ?: size
                }
            }
            XmlPullParser.END_TAG -> {
                if (parser.name == "Contents") {
                    val k = key
                    if (k != null) {
                        val instant = lastModified?.let {
                            runCatching { Instant.from(DateTimeFormatter.ISO_INSTANT.parse(it)) }.getOrNull()
                        }
                        result.add(
                            RemoteObject(
                                key = k,
                                etag = etag?.trim('"'),
                                lastModified = instant,
                                sizeBytes = size,
                            )
                        )
                    }
                    inContents = false
                }
                current = null
            }
        }
        ev = parser.next()
    }
    return result
}
