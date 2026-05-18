package com.tracediary.app.data.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.URLEncoder
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 腾讯云 COS 实现，签名走官方 V5（q-sign-algorithm=sha1）：
 *
 *   Authorization = q-sign-algorithm=sha1
 *                   &q-ak={SecretId}
 *                   &q-sign-time={start};{end}
 *                   &q-key-time={start};{end}
 *                   &q-header-list={headerList}
 *                   &q-url-param-list={urlParamList}
 *                   &q-signature={Signature}
 *
 *   SignKey      = HMAC-SHA1(SecretKey,    q-key-time)        // hex
 *   HttpString   = method\npath\nurlParamList\nheaderList\n
 *   StringToSign = sha1\nq-sign-time\nsha1Hex(HttpString)\n
 *   Signature    = HMAC-SHA1(SignKey,      StringToSign)      // hex
 *
 * 凭证沿用 [RemoteCredential]：accessKeyId = SecretId，accessKeySecret = SecretKey。
 * Endpoint: https://{bucket}.cos.{region}.myqcloud.com，bucket 含 APPID 后缀。
 */
class TencentCosRemoteStorage(
    private val credential: RemoteCredential,
    private val client: OkHttpClient = defaultClient(),
) : RemoteStorage {

    init {
        require(credential.provider == SyncProvider.TENCENT_COS) {
            "credential.provider must be TENCENT_COS"
        }
    }

    private val host: String = "${credential.bucket}.cos.${credential.region}.myqcloud.com"

    override suspend fun put(
        key: String,
        body: ByteArray,
        contentType: String,
        ifMatchEtag: String?,
    ): RemoteResult<RemoteObject> = withContext(Dispatchers.IO) {
        val finalKey = applyPrefix(key)
        val path = "/$finalKey"
        val baseHeaders = mutableMapOf(
            "host" to host,
            "content-type" to contentType,
            "content-length" to body.size.toString(),
        )
        if (ifMatchEtag != null) baseHeaders["if-match"] = ifMatchEtag
        val auth = cosAuthorization("put", path, emptyMap(), baseHeaders)
        val builder = Request.Builder()
            .url("https://$host$path")
            .put(body.toRequestBody(contentType.toMediaTypeOrNull()))
            .header("Host", host)
            .header("Content-Type", contentType)
            .header("Authorization", auth)
        if (ifMatchEtag != null) builder.header("If-Match", ifMatchEtag)
        execute(builder.build()) { resp ->
            RemoteObject(
                key = finalKey,
                etag = resp.header("ETag")?.trim('"'),
                lastModified = parseHttpDate(resp.header("Date")),
                sizeBytes = body.size.toLong(),
            )
        }
    }

    override suspend fun get(key: String): RemoteResult<ByteArray> = withContext(Dispatchers.IO) {
        val finalKey = applyPrefix(key)
        val path = "/$finalKey"
        val headers = mapOf("host" to host)
        val auth = cosAuthorization("get", path, emptyMap(), headers)
        val request = Request.Builder()
            .url("https://$host$path")
            .get()
            .header("Host", host)
            .header("Authorization", auth)
            .build()
        runCatching { client.newCall(request).execute() }.fold(
            onSuccess = { resp ->
                resp.use {
                    if (it.isSuccessful) RemoteResult.Ok(it.body?.bytes() ?: ByteArray(0))
                    else mapHttpError(it)
                }
            },
            onFailure = { mapIoError(it) },
        )
    }

    override suspend fun head(key: String): RemoteResult<RemoteObject> = withContext(Dispatchers.IO) {
        val finalKey = applyPrefix(key)
        val path = "/$finalKey"
        val headers = mapOf("host" to host)
        val auth = cosAuthorization("head", path, emptyMap(), headers)
        val request = Request.Builder()
            .url("https://$host$path")
            .head()
            .header("Host", host)
            .header("Authorization", auth)
            .build()
        execute(request) { resp ->
            RemoteObject(
                key = finalKey,
                etag = resp.header("ETag")?.trim('"'),
                lastModified = parseHttpDate(resp.header("Last-Modified")),
                sizeBytes = resp.header("Content-Length")?.toLongOrNull() ?: 0L,
            )
        }
    }

    override suspend fun list(prefix: String): RemoteResult<List<RemoteObject>> = withContext(Dispatchers.IO) {
        val finalPrefix = applyPrefix(prefix)
        // url 参与签名时键值都用小写，且需要 RFC3986 编码
        val params = linkedMapOf("prefix" to finalPrefix)
        val headers = mapOf("host" to host)
        val auth = cosAuthorization("get", "/", params, headers)
        val query = params.entries.joinToString("&") { (k, v) ->
            "${urlEncodeRfc3986(k)}=${urlEncodeRfc3986(v)}"
        }
        val request = Request.Builder()
            .url("https://$host/?$query")
            .get()
            .header("Host", host)
            .header("Authorization", auth)
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
                    } else mapHttpError(it)
                }
            },
            onFailure = { mapIoError(it) },
        )
    }

    override suspend fun delete(key: String): RemoteResult<Unit> = withContext(Dispatchers.IO) {
        val finalKey = applyPrefix(key)
        val path = "/$finalKey"
        val headers = mapOf("host" to host)
        val auth = cosAuthorization("delete", path, emptyMap(), headers)
        val request = Request.Builder()
            .url("https://$host$path")
            .delete()
            .header("Host", host)
            .header("Authorization", auth)
            .build()
        runCatching { client.newCall(request).execute() }.fold(
            onSuccess = { resp ->
                resp.use {
                    if (it.isSuccessful || it.code == 404) RemoteResult.Ok(Unit)
                    else mapHttpError(it)
                }
            },
            onFailure = { mapIoError(it) },
        )
    }

    override suspend fun ping(): RemoteResult<Unit> = withContext(Dispatchers.IO) {
        val params = linkedMapOf("max-keys" to "1")
        val headers = mapOf("host" to host)
        val auth = cosAuthorization("get", "/", params, headers)
        val query = params.entries.joinToString("&") { (k, v) ->
            "${urlEncodeRfc3986(k)}=${urlEncodeRfc3986(v)}"
        }
        val request = Request.Builder()
            .url("https://$host/?$query")
            .get()
            .header("Host", host)
            .header("Authorization", auth)
            .build()
        runCatching { client.newCall(request).execute() }.fold(
            onSuccess = { resp ->
                resp.use { if (it.isSuccessful) RemoteResult.Ok(Unit) else mapHttpError(it) }
            },
            onFailure = { mapIoError(it) },
        )
    }

    private fun applyPrefix(key: String): String {
        val prefix = credential.pathPrefix.trim('/')
        val k = key.trimStart('/')
        return if (prefix.isEmpty()) k else "$prefix/$k"
    }

    private inline fun <T> execute(
        request: Request,
        crossinline onSuccess: (Response) -> T,
    ): RemoteResult<T> {
        return runCatching { client.newCall(request).execute() }.fold(
            onSuccess = { resp ->
                resp.use {
                    if (it.isSuccessful) RemoteResult.Ok(onSuccess(it)) else mapHttpError(it)
                }
            },
            onFailure = { mapIoError(it) },
        )
    }

    /**
     * 构造腾讯云 COS V5 Authorization 头。
     *
     * @param method HTTP 方法（小写）。
     * @param path 不含 query 的 URI path，需以 / 开头。
     * @param params 进入签名的 query 参数（键值均使用原值，函数内部负责 URL 编码与排序）。
     * @param headers 进入签名的请求头（键名小写，函数内部排序）。
     */
    private fun cosAuthorization(
        method: String,
        path: String,
        params: Map<String, String>,
        headers: Map<String, String>,
    ): String {
        val now = System.currentTimeMillis() / 1000
        val keyTime = "$now;${now + 3600}"

        val sortedParamKeys = params.keys.map { it.lowercase(Locale.US) }.sorted()
        val urlParamList = sortedParamKeys.joinToString(";")
        val paramString = sortedParamKeys.joinToString("&") { k ->
            val originalKey = params.keys.first { it.equals(k, ignoreCase = true) }
            "${urlEncodeRfc3986(k)}=${urlEncodeRfc3986(params[originalKey] ?: "")}"
        }

        val sortedHeaderKeys = headers.keys.map { it.lowercase(Locale.US) }.sorted()
        val headerList = sortedHeaderKeys.joinToString(";")
        val headerString = sortedHeaderKeys.joinToString("&") { k ->
            val originalKey = headers.keys.first { it.equals(k, ignoreCase = true) }
            "${urlEncodeRfc3986(k)}=${urlEncodeRfc3986(headers[originalKey] ?: "")}"
        }

        val httpString = "${method.lowercase(Locale.US)}\n$path\n$paramString\n$headerString\n"
        val stringToSign = "sha1\n$keyTime\n${sha1Hex(httpString)}\n"
        val signKey = hmacSha1Hex(credential.accessKeySecret, keyTime)
        val signature = hmacSha1Hex(signKey, stringToSign)

        return buildString {
            append("q-sign-algorithm=sha1")
            append("&q-ak=").append(credential.accessKeyId)
            append("&q-sign-time=").append(keyTime)
            append("&q-key-time=").append(keyTime)
            append("&q-header-list=").append(headerList)
            append("&q-url-param-list=").append(urlParamList)
            append("&q-signature=").append(signature)
        }
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

private fun hmacSha1Hex(key: String, data: String): String {
    val mac = Mac.getInstance("HmacSHA1")
    mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA1"))
    return hexEncode(mac.doFinal(data.toByteArray(Charsets.UTF_8)))
}

private fun sha1Hex(data: String): String {
    val md = MessageDigest.getInstance("SHA-1")
    return hexEncode(md.digest(data.toByteArray(Charsets.UTF_8)))
}

private fun hexEncode(bytes: ByteArray): String {
    val sb = StringBuilder(bytes.size * 2)
    for (b in bytes) {
        val v = b.toInt() and 0xFF
        if (v < 0x10) sb.append('0')
        sb.append(Integer.toHexString(v))
    }
    return sb.toString()
}

private fun urlEncodeRfc3986(value: String): String {
    val s = URLEncoder.encode(value, Charsets.UTF_8.name())
    return s.replace("+", "%20")
        .replace("*", "%2A")
        .replace("%7E", "~")
}

private fun parseHttpDate(value: String?): Instant? {
    if (value.isNullOrBlank()) return null
    return runCatching {
        val fmt = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("GMT")
        fmt.parse(value)?.toInstant()
    }.getOrNull()
}

private fun mapHttpError(resp: Response): RemoteResult.Err {
    val code = when (resp.code) {
        401, 403 -> ErrorCode.AUTH
        404 -> ErrorCode.NOT_FOUND
        412 -> ErrorCode.CONFLICT
        in 500..599 -> ErrorCode.NETWORK
        else -> ErrorCode.UNKNOWN
    }
    return RemoteResult.Err(code, "HTTP ${resp.code} ${resp.message}")
}

private fun mapIoError(t: Throwable): RemoteResult.Err = when (t) {
    is IOException -> RemoteResult.Err(ErrorCode.NETWORK, t.message ?: "IO error", t)
    else -> RemoteResult.Err(ErrorCode.UNKNOWN, t.message ?: "未知错误", t)
}
