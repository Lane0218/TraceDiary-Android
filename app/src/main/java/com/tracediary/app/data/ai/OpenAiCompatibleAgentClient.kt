package com.tracediary.app.data.ai

import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.job
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class OpenAiCompatibleAgentClient(
    private val client: OkHttpClient = defaultClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    suspend fun complete(request: AiAgentRequest): AiAgentTurn = withContext(Dispatchers.IO) {
        val startedAt = SystemClock.elapsedRealtime()
        var headersAt: Long? = null
        var bodyFinishedAt: Long? = null
        var httpCode = -1
        var responseChars = 0
        var turn: AiAgentTurn? = null
        var outcome = "failure"
        var failure = "none"
        try {
            val httpRequest = Request.Builder()
                .url(resolveEndpoint(request.baseUrl))
                .header("Authorization", "Bearer ${request.apiKey}")
                .header("Content-Type", JSON_MEDIA_TYPE.toString())
                .post(buildRequestPayload(request, stream = false).toRequestBody(JSON_MEDIA_TYPE))
                .build()

            client.newCall(httpRequest).await().use { response ->
                headersAt = SystemClock.elapsedRealtime()
                httpCode = response.code
                val raw = response.body?.string().orEmpty()
                bodyFinishedAt = SystemClock.elapsedRealtime()
                responseChars = raw.length
                if (raw.length > MAX_RESPONSE_CHARS) throw AiAgentException("AI 响应过大")
                if (!response.isSuccessful) {
                    throw AiAgentException(response.code.toUserMessage())
                }
                parseTurn(raw).also {
                    turn = it
                    outcome = "success"
                }
            }
        } catch (cancelled: CancellationException) {
            outcome = "cancelled"
            failure = "cancelled"
            throw cancelled
        } catch (error: Throwable) {
            failure = error.performanceCategory(httpCode)
            throw error
        } finally {
            val finishedAt = SystemClock.elapsedRealtime()
            AiPerformanceLog.debug {
                "event=cloud_complete trace=${request.traceId()} stage=${request.stage()} " +
                    "outcome=$outcome failure=$failure totalMs=${finishedAt - startedAt} " +
                    "headersMs=${headersAt?.minus(startedAt) ?: -1} " +
                    "bodyMs=${headersAt?.let { bodyFinishedAt?.minus(it) } ?: -1} http=$httpCode " +
                    "requestMessages=${request.messages.size} requestChars=${request.approximateChars()} " +
                    "toolDefinitions=${request.tools.size} responseChars=$responseChars " +
                    "answerChars=${turn?.message?.content?.length ?: 0} " +
                    "toolCalls=${turn?.message?.toolCalls?.size ?: 0} finish=${turn?.finishReason.safeFinishReason()}"
            }
        }
    }

    suspend fun stream(
        request: AiAgentRequest,
        onEvent: suspend (AiAgentStreamEvent) -> Unit,
    ): Unit = withContext(Dispatchers.IO) {
        val endpoint = resolveEndpoint(request.baseUrl)
        val httpRequest = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer ${request.apiKey}")
            .header("Content-Type", JSON_MEDIA_TYPE.toString())
            .header("Accept", "text/event-stream")
            .post(buildRequestPayload(request, stream = true).toRequestBody(JSON_MEDIA_TYPE))
            .build()
        val call = client.newCall(httpRequest)
        val cancellationHandle = currentCoroutineContext().job.invokeOnCompletion { call.cancel() }
        val startedAt = SystemClock.elapsedRealtime()
        var headersAt: Long? = null
        var httpCode = -1
        var outcome = "failure"
        var failure = "none"
        var eventCount = 0
        var contentEventCount = 0
        var contentChars = 0
        var reasoningEventCount = 0
        var reasoningChars = 0
        var toolDeltaCount = 0
        var otherEventCount = 0
        var firstDataAt: Long? = null
        var firstReasoningAt: Long? = null
        var firstContentAt: Long? = null
        var lastContentAt: Long? = null
        var firstToolAt: Long? = null
        var previousDataAt: Long? = null
        var maxDataGapMs = 0L
        try {
            call.execute().use { response ->
                headersAt = SystemClock.elapsedRealtime()
                httpCode = response.code
                if (!response.isSuccessful) {
                    throw AiAgentException(response.code.toUserMessage())
                }
                val source = response.body?.source() ?: throw AiAgentStreamException("AI 流式响应为空")
                var sawData = false
                var responseChars = 0
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (line.isBlank() || line.startsWith(":")) continue
                    if (!line.startsWith("data:")) {
                        if (!sawData) throw AiAgentStreamException("AI 服务不支持流式输出")
                        continue
                    }
                    sawData = true
                    val dataAt = SystemClock.elapsedRealtime()
                    eventCount += 1
                    if (firstDataAt == null) firstDataAt = dataAt
                    val gapMs = previousDataAt?.let { dataAt - it } ?: 0L
                    maxDataGapMs = maxOf(maxDataGapMs, gapMs)
                    previousDataAt = dataAt
                    val data = line.removePrefix("data:").trim()
                    if (data == "[DONE]") {
                        onEvent(AiAgentStreamEvent(done = true))
                        break
                    }
                    val event = parseStreamEvent(data)
                    if (event.reasoningDeltaChars > 0) {
                        if (firstReasoningAt == null) firstReasoningAt = dataAt
                        reasoningEventCount += 1
                        reasoningChars += event.reasoningDeltaChars
                    }
                    event.contentDelta?.takeIf { it.isNotEmpty() }?.let { delta ->
                        if (firstContentAt == null) firstContentAt = dataAt
                        lastContentAt = dataAt
                        contentEventCount += 1
                        contentChars += delta.length
                        responseChars += delta.length
                    }
                    if (event.toolCallDeltas.isNotEmpty()) {
                        if (firstToolAt == null) firstToolAt = dataAt
                        toolDeltaCount += event.toolCallDeltas.size
                    }
                    if (
                        event.contentDelta.isNullOrEmpty() &&
                        event.reasoningDeltaChars == 0 &&
                        event.toolCallDeltas.isEmpty() &&
                        event.finishReason == null
                    ) {
                        otherEventCount += 1
                    }
                    if (responseChars > MAX_RESPONSE_CHARS) throw AiAgentException("AI 响应过大")
                    onEvent(event)
                }
                if (!sawData) throw AiAgentStreamException("AI 服务未返回流式数据")
                outcome = "success"
            }
        } catch (cancelled: CancellationException) {
            outcome = "cancelled"
            failure = "cancelled"
            throw cancelled
        } catch (error: IOException) {
            if (currentCoroutineContext().job.isCancelled) {
                outcome = "cancelled"
                failure = "cancelled"
                throw CancellationException("AI 流式请求已取消", error)
            }
            failure = error.performanceCategory(httpCode)
            throw AiAgentStreamException("AI 流式连接失败", error)
        } catch (error: Throwable) {
            failure = error.performanceCategory(httpCode)
            throw error
        } finally {
            AiPerformanceLog.debug {
                "event=cloud_stream trace=${request.traceId()} stage=${request.stage()} " +
                    "outcome=$outcome failure=$failure totalMs=${SystemClock.elapsedRealtime() - startedAt} " +
                    "headersMs=${headersAt?.minus(startedAt) ?: -1} http=$httpCode " +
                    "firstDataMs=${firstDataAt?.minus(startedAt) ?: -1} " +
                    "firstReasoningMs=${firstReasoningAt?.minus(startedAt) ?: -1} " +
                    "firstContentMs=${firstContentAt?.minus(startedAt) ?: -1} " +
                    "firstToolMs=${firstToolAt?.minus(startedAt) ?: -1} " +
                    "contentSpanMs=${firstContentAt?.let { first -> lastContentAt?.minus(first) } ?: -1} " +
                    "requestMessages=${request.messages.size} requestChars=${request.approximateChars()} " +
                    "events=$eventCount reasoningEvents=$reasoningEventCount reasoningChars=$reasoningChars " +
                    "contentEvents=$contentEventCount contentChars=$contentChars toolDeltas=$toolDeltaCount " +
                    "otherEvents=$otherEventCount maxGapMs=$maxDataGapMs"
            }
            cancellationHandle.dispose()
        }
    }

    internal fun buildRequestPayload(request: AiAgentRequest, stream: Boolean): String = buildJsonObject {
        put("model", JsonPrimitive(request.model))
        put("stream", JsonPrimitive(stream))
        put("messages", buildJsonArray {
            request.messages.forEach { message -> add(message.toJson()) }
        })
        if (request.tools.isNotEmpty()) {
            put("tools", request.tools)
            put("tool_choice", JsonPrimitive("auto"))
        }
        when (request.thinking) {
            AiAgentThinking.Default -> Unit
            AiAgentThinking.Low -> {
                put("thinking", buildJsonObject { put("type", JsonPrimitive("enabled")) })
                put("reasoning_effort", JsonPrimitive("low"))
            }
            AiAgentThinking.Disabled -> {
                put("thinking", buildJsonObject { put("type", JsonPrimitive("disabled")) })
            }
        }
    }.toString()

    suspend fun testConnection(
        baseUrl: String,
        apiKey: String,
        model: String,
    ): AiAgentConnectionResult = runCatching {
        val turn = complete(
            AiAgentRequest(
                baseUrl = baseUrl,
                apiKey = apiKey,
                model = model,
                messages = listOf(
                    AiAgentMessage(role = "system", content = "你正在执行连接能力测试。必须调用 ping 工具，不要输出用户内容。"),
                    AiAgentMessage(role = "user", content = "请调用 ping。"),
                ),
                tools = buildJsonArray { add(pingTool()) },
            ),
        )
        check(turn.message.toolCalls.any { it.name == "ping" }) { "当前模型未返回 Tool Call" }
    }.fold(
        onSuccess = { AiAgentConnectionResult.Success },
        onFailure = { AiAgentConnectionResult.Failed(it.toSafeMessage()) },
    )

    internal fun parseTurn(raw: String): AiAgentTurn {
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }
            .getOrElse { throw AiAgentException("AI 返回了无法解析的响应") }
        val choice = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: throw AiAgentException("AI 响应缺少结果")
        val message = choice["message"]?.jsonObject
            ?: throw AiAgentException("AI 响应缺少消息")
        val calls = message["tool_calls"]?.jsonArray.orEmpty().map { element ->
            val call = element.jsonObject
            val function = call["function"]?.jsonObject ?: throw AiAgentException("Tool Call 格式错误")
            AiAgentToolCall(
                id = call["id"]?.jsonPrimitive?.content ?: throw AiAgentException("Tool Call 缺少 id"),
                name = function["name"]?.jsonPrimitive?.content ?: throw AiAgentException("Tool Call 缺少名称"),
                arguments = function["arguments"]?.jsonPrimitive?.content ?: "{}",
            )
        }
        return AiAgentTurn(
            message = AiAgentMessage(
                role = "assistant",
                content = message["content"]?.jsonPrimitive?.contentOrNull,
                reasoningContent = message["reasoning_content"]?.jsonPrimitive?.contentOrNull,
                toolCalls = calls,
            ),
            finishReason = choice["finish_reason"]?.jsonPrimitive?.contentOrNull,
        )
    }

    internal fun parseStreamEvent(raw: String): AiAgentStreamEvent {
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }
            .getOrElse { throw AiAgentStreamException("AI 流式事件无法解析") }
        val choice = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: return AiAgentStreamEvent()
        val delta = choice["delta"]?.jsonObject
        val toolCallDeltas = delta?.get("tool_calls")?.jsonArray.orEmpty().map { element ->
            val call = element.jsonObject
            val function = call["function"]?.jsonObject
            AiAgentToolCallDelta(
                index = call["index"]?.jsonPrimitive?.intOrNull ?: 0,
                id = call["id"]?.jsonPrimitive?.contentOrNull,
                name = function?.get("name")?.jsonPrimitive?.contentOrNull,
                argumentsDelta = function?.get("arguments")?.jsonPrimitive?.contentOrNull,
            )
        }
        return AiAgentStreamEvent(
            contentDelta = delta?.get("content")?.jsonPrimitive?.contentOrNull,
            reasoningDelta = delta?.get("reasoning_content")?.jsonPrimitive?.contentOrNull,
            toolCallDeltas = toolCallDeltas,
            finishReason = choice["finish_reason"]?.jsonPrimitive?.contentOrNull,
        )
    }

    private fun AiAgentMessage.toJson(): JsonObject = buildJsonObject {
        put("role", JsonPrimitive(role))
        content?.let { put("content", JsonPrimitive(it)) }
        reasoningContent?.let { put("reasoning_content", JsonPrimitive(it)) }
        toolCallId?.let { put("tool_call_id", JsonPrimitive(it)) }
        if (toolCalls.isNotEmpty()) {
            put("tool_calls", buildJsonArray {
                toolCalls.forEach { call ->
                    add(buildJsonObject {
                        put("id", JsonPrimitive(call.id))
                        put("type", JsonPrimitive("function"))
                        put("function", buildJsonObject {
                            put("name", JsonPrimitive(call.name))
                            put("arguments", JsonPrimitive(call.arguments))
                        })
                    })
                }
            })
        }
    }

    internal fun resolveEndpoint(baseUrl: String): String {
        val normalized = baseUrl.trim().trimEnd('/')
        require(normalized.startsWith("https://", ignoreCase = true)) { "Base URL 必须使用 HTTPS" }
        require(normalized.isNotBlank()) { "Base URL 不能为空" }
        return if (normalized.endsWith("/chat/completions")) normalized else "$normalized/chat/completions"
    }

    private fun pingTool(): JsonObject = buildJsonObject {
        put("type", JsonPrimitive("function"))
        put("function", buildJsonObject {
            put("name", JsonPrimitive("ping"))
            put("description", JsonPrimitive("连接测试工具"))
            put("parameters", buildJsonObject {
                put("type", JsonPrimitive("object"))
                put("properties", JsonObject(emptyMap()))
                put("additionalProperties", JsonPrimitive(false))
            })
        })
    }

    private fun Int.toUserMessage(): String = when (this) {
        401, 403 -> "API Key 或模型权限无效"
        408 -> "AI 服务请求超时"
        429 -> "AI 服务请求过于频繁，请稍后重试"
        in 500..599 -> "AI 服务暂时不可用"
        else -> "AI 服务请求失败（HTTP $this）"
    }

    private fun Throwable.toSafeMessage(): String =
        (this as? AiAgentException)?.message ?: when (this) {
            is IllegalArgumentException -> message ?: "AI 配置无效"
            else -> "无法连接 AI 服务"
        }

    private fun AiAgentRequest.traceId(): String = diagnostics?.traceId ?: "none"
    private fun AiAgentRequest.stage(): String = diagnostics?.stage ?: "untracked"
    private fun AiAgentRequest.approximateChars(): Int =
        messages.sumOf { message ->
            (message.content?.length ?: 0) +
                (message.reasoningContent?.length ?: 0) +
                message.toolCalls.sumOf { it.name.length + it.arguments.length }
        } + tools.toString().length

    private fun Throwable.performanceCategory(httpCode: Int): String = when {
        this is CancellationException -> "cancelled"
        httpCode >= 400 -> "http"
        this is IOException -> "network"
        this is AiAgentStreamException -> "stream"
        this is AiAgentException -> "agent"
        this is IllegalArgumentException -> "configuration"
        else -> "unexpected"
    }

    private fun String?.safeFinishReason(): String = when (this) {
        "stop", "length", "tool_calls", "content_filter" -> this
        null -> "none"
        else -> "other"
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val MAX_RESPONSE_CHARS = 1_000_000

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}

class AiAgentException(message: String) : Exception(message)

class AiAgentStreamException(message: String, cause: Throwable? = null) : Exception(message, cause)

private fun JsonArray?.orEmpty(): JsonArray = this ?: JsonArray(emptyList())

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
