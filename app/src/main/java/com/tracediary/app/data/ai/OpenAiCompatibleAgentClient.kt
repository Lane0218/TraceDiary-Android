package com.tracediary.app.data.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
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
        val endpoint = resolveEndpoint(request.baseUrl)
        val payload = buildJsonObject {
            put("model", JsonPrimitive(request.model))
            put("stream", JsonPrimitive(false))
            put("messages", buildJsonArray {
                request.messages.forEach { message -> add(message.toJson()) }
            })
            if (request.tools.isNotEmpty()) {
                put("tools", request.tools)
                put("tool_choice", JsonPrimitive("auto"))
            }
        }.toString()
        val httpRequest = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer ${request.apiKey}")
            .header("Content-Type", JSON_MEDIA_TYPE.toString())
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        client.newCall(httpRequest).await().use { response ->
            val raw = response.body?.string().orEmpty()
            if (raw.length > MAX_RESPONSE_CHARS) throw AiAgentException("AI 响应过大")
            if (!response.isSuccessful) {
                throw AiAgentException(response.code.toUserMessage())
            }
            parseTurn(raw)
        }
    }

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
                toolCalls = calls,
            ),
            finishReason = choice["finish_reason"]?.jsonPrimitive?.contentOrNull,
        )
    }

    private fun AiAgentMessage.toJson(): JsonObject = buildJsonObject {
        put("role", JsonPrimitive(role))
        content?.let { put("content", JsonPrimitive(it)) }
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
