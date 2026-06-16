package com.mimochat.data

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

// --- MiMo API 数据模型 (OpenAI 兼容格式) ---

data class MiMoConfig(
    val baseUrl: String,
    val apiKey: String
)

data class ChatMessage(
    val role: String,       // "system", "user", "assistant"
    val content: Any        // String 或 List<ContentPart> (多模态)
)

data class ContentPart(
    val type: String,                    // "text" 或 "image_url"
    val text: String? = null,
    @SerializedName("image_url")
    val imageUrl: ImageUrl? = null
)

data class ImageUrl(
    val url: String   // "data:image/jpeg;base64,..."
)

data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean = false,
    val temperature: Double? = null,
    @SerializedName("max_tokens")
    val maxTokens: Int? = null
)

data class ChatCompletionResponse(
    val id: String?,
    val choices: List<Choice>?,
    val usage: Usage?
)

data class Choice(
    val index: Int,
    val message: ChatMessage,
    @SerializedName("finish_reason")
    val finishReason: String?
)

data class Usage(
    @SerializedName("prompt_tokens")
    val promptTokens: Int,
    @SerializedName("completion_tokens")
    val completionTokens: Int,
    @SerializedName("total_tokens")
    val totalTokens: Int
)

// --- 本地消息历史 (用于 UI 展示) ---

data class Message(
    val id: String,
    val sessionId: String,
    val role: String,       // "user" 或 "assistant"
    val content: String,
    val createdAt: Long,
    val parts: List<Part>? = null
)

data class Part(
    val type: String,       // "text", "image"
    val content: String,
    val mimeType: String? = null
)

// --- MiMo API 客户端 ---

class MiMoClient(private val config: MiMoConfig) {

    companion object {
        private const val TAG = "MiMoClient"
        const val MODEL_CHAT = "mimo-v2.5-pro"
        const val MODEL_VISION = "mimo-v2.5"
        const val MODEL_TTS = "mimo-v2.5-tts"
        const val MODEL_ASR = "mimo-v2.5-asr"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    // ---- 核心请求方法 ----

    private fun buildChatUrl(): String {
        return config.baseUrl.trimEnd('/') + "/chat/completions"
    }

    private fun createRequest(jsonBody: String): Request {
        return Request.Builder()
            .url(buildChatUrl())
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Content-Type", "application/json")
            .build()
    }

    private fun <T> executeRequest(request: Request, clazz: Class<T>): T {
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "Unknown error"
            response.close()
            Log.e(TAG, "API error ${response.code}: $errorBody")
            throw Exception("API error: ${response.code} - $errorBody")
        }
        return response.use { resp ->
            val body = resp.body?.string() ?: throw Exception("Empty response")
            gson.fromJson(body, clazz)
        }
    }

    // ---- 聊天补全 (纯文本) ----

    suspend fun chatCompletion(
        messages: List<ChatMessage>,
        model: String = MODEL_CHAT,
        temperature: Double? = null,
        maxTokens: Int? = null
    ): ChatCompletionResponse = withContext(Dispatchers.IO) {
        val request = ChatCompletionRequest(
            model = model,
            messages = messages,
            stream = false,
            temperature = temperature,
            maxTokens = maxTokens
        )
        val json = gson.toJson(request)
        Log.d(TAG, "Chat request: model=$model, messages=${messages.size}")
        executeRequest(createRequest(json), ChatCompletionResponse::class.java)
    }

    // ---- 发送文本消息 (带角色 system prompt) ----

    suspend fun sendMessage(
        sessionId: String,
        history: List<ChatMessage>,
        userText: String,
        systemPrompt: String? = null
    ): ChatCompletionResponse {
        val messages = mutableListOf<ChatMessage>()
        if (systemPrompt != null) {
            messages.add(ChatMessage(role = "system", content = systemPrompt))
        }
        messages.addAll(history)
        messages.add(ChatMessage(role = "user", content = userText))
        return chatCompletion(messages)
    }

    // ---- 发送图片 + 文本 (多模态) ----

    suspend fun sendImage(
        sessionId: String,
        imageBase64: String,
        mimeType: String,
        text: String?,
        history: List<ChatMessage>,
        systemPrompt: String? = null
    ): ChatCompletionResponse {
        val dataUri = "data:$mimeType;base64,$imageBase64"
        val contentParts = mutableListOf<ContentPart>()
        if (text != null) {
            contentParts.add(ContentPart(type = "text", text = text))
        } else {
            contentParts.add(ContentPart(type = "text", text = "请描述这张图片"))
        }
        contentParts.add(ContentPart(
            type = "image_url",
            imageUrl = ImageUrl(url = dataUri)
        ))

        val messages = mutableListOf<ChatMessage>()
        if (systemPrompt != null) {
            messages.add(ChatMessage(role = "system", content = systemPrompt))
        }
        messages.addAll(history)
        messages.add(ChatMessage(role = "user", content = contentParts))

        return chatCompletion(messages, model = MODEL_VISION)
    }

    // ---- TTS 语音合成 ----

    suspend fun synthesizeSpeech(
        text: String,
        voice: String = "mimo_default",
        style: String? = null
    ): ByteArray = withContext(Dispatchers.IO) {
        val ttsContent = if (style != null) {
            "<style>$style</style>$text"
        } else {
            text
        }
        val messages = listOf(
            ChatMessage(role = "user", content = "请合成以下内容"),
            ChatMessage(role = "assistant", content = ttsContent)
        )
        val request = ChatCompletionRequest(
            model = MODEL_TTS,
            messages = messages,
            stream = false
        )
        val json = gson.toJson(request)
        Log.d(TAG, "TTS request: text=${text.take(50)}...")

        val response = client.newCall(createRequest(json)).execute()
        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "Unknown error"
            response.close()
            throw Exception("TTS API error: ${response.code} - $errorBody")
        }
        response.use { resp ->
            resp.body?.bytes() ?: throw Exception("Empty TTS response")
        }
    }

    // ---- ASR 语音识别 ----

    suspend fun transcribe(
        audioBase64: String,
        mimeType: String = "audio/m4a",
        language: String = "zh"
    ): String = withContext(Dispatchers.IO) {
        val dataUri = "data:$mimeType;base64,$audioBase64"
        val contentParts = listOf(
            ContentPart(
                type = "image_url",
                imageUrl = ImageUrl(url = dataUri)
            )
        )
        val messages = listOf(
            ChatMessage(role = "user", content = contentParts)
        )
        val request = ChatCompletionRequest(
            model = MODEL_ASR,
            messages = messages,
            stream = false
        )
        val json = gson.toJson(request)
        Log.d(TAG, "ASR request")

        val response = executeRequest(createRequest(json), ChatCompletionResponse::class.java)
        response.choices?.firstOrNull()?.message?.content?.toString()
            ?: throw Exception("ASR returned empty result")
    }

    // ---- 图片理解 (Vision) ----

    suspend fun describeImage(
        imageBase64: String,
        mimeType: String = "image/jpeg",
        prompt: String = "请描述这张图片"
    ): String = withContext(Dispatchers.IO) {
        val dataUri = "data:$mimeType;base64,$imageBase64"
        val contentParts = listOf(
            ContentPart(type = "text", text = prompt),
            ContentPart(type = "image_url", imageUrl = ImageUrl(url = dataUri))
        )
        val messages = listOf(
            ChatMessage(role = "user", content = contentParts)
        )
        val response = chatCompletion(messages, model = MODEL_VISION)
        response.choices?.firstOrNull()?.message?.content?.toString()
            ?: throw Exception("Vision returned empty result")
    }
}
