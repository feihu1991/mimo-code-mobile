package com.mimochat.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

data class MiMoConfig(
    val baseUrl: String,
    val apiKey: String
)

data class CreateSessionResponse(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long
)

data class SendMessageRequest(
    val parts: List<PartInput>,
    val system: String? = null
)

data class PartInput(
    val type: String,
    val content: String,
    val mimeType: String? = null
)

data class TranscriptionResponse(
    val text: String
)

class MiMoClient(private val config: MiMoConfig) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    private fun Request.executeAndParse(): okhttp3.Response {
        val response = client.newCall(this).execute()
        if (!response.isSuccessful) {
            response.close()
            throw Exception("API error: ${response.code}")
        }
        return response
    }

    private inline fun <reified T> Request.executeAndParseJson(): T {
        val response = executeAndParse()
        return response.use { resp ->
            val body = resp.body?.string() ?: throw Exception("Empty response")
            gson.fromJson(body, T::class.java)
        }
    }

    suspend fun createSession(title: String? = null): CreateSessionResponse = withContext(Dispatchers.IO) {
        val body = if (title != null) gson.toJson(mapOf("title" to title)) else "{}"
        Request.Builder()
            .url("${config.baseUrl}/session")
            .post(body.toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Content-Type", "application/json")
            .build()
            .executeAndParseJson()
    }

    suspend fun getMessages(sessionId: String): List<Message> = withContext(Dispatchers.IO) {
        val response = Request.Builder()
            .url("${config.baseUrl}/session/${sessionId}/messages")
            .get()
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .build()
            .executeAndParse()
        response.use { resp ->
            val body = resp.body?.string() ?: throw Exception("Empty response")
            gson.fromJson(body, object : TypeToken<List<Message>>() {}.type)
        }
    }

    suspend fun sendMessage(sessionId: String, content: String, system: String? = null): Message = withContext(Dispatchers.IO) {
        val requestBody = SendMessageRequest(parts = listOf(PartInput(type = "text", content = content)), system = system)
        Request.Builder()
            .url("${config.baseUrl}/session/${sessionId}/prompt")
            .post(gson.toJson(requestBody).toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Content-Type", "application/json")
            .build()
            .executeAndParseJson()
    }

    suspend fun sendImage(sessionId: String, imageBase64: String, mimeType: String, text: String? = null): Message = withContext(Dispatchers.IO) {
        val parts = mutableListOf<PartInput>()
        if (text != null) parts.add(PartInput(type = "text", content = text))
        parts.add(PartInput(type = "file", content = imageBase64, mimeType = mimeType))
        Request.Builder()
            .url("${config.baseUrl}/session/${sessionId}/prompt")
            .post(gson.toJson(SendMessageRequest(parts = parts)).toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Content-Type", "application/json")
            .build()
            .executeAndParseJson()
    }

    suspend fun transcribe(audioFile: File, language: String = "zh"): String = withContext(Dispatchers.IO) {
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", audioFile.name, audioFile.asRequestBody("audio/m4a".toMediaType()))
            .addFormDataPart("model", "whisper-1")
            .addFormDataPart("language", language)
            .addFormDataPart("response_format", "json")
            .build()
        val response = Request.Builder()
            .url("${config.baseUrl}/audio/transcriptions")
            .post(requestBody)
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .build()
            .executeAndParse()
        response.use { resp ->
            val body = resp.body?.string() ?: throw Exception("Empty response")
            gson.fromJson(body, TranscriptionResponse::class.java).text
        }
    }

    suspend fun synthesizeSpeech(text: String, voice: String = "alloy", model: String = "tts-1", speed: Float = 1.0f): ByteArray = withContext(Dispatchers.IO) {
        val requestBody = gson.toJson(mapOf("model" to model, "input" to text, "voice" to voice, "speed" to speed))
        val response = Request.Builder()
            .url("${config.baseUrl}/audio/speech")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Content-Type", "application/json")
            .build()
            .executeAndParse()
        response.use { resp -> resp.body?.bytes() ?: throw Exception("Empty response body") }
    }
}
