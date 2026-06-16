package com.mimochat

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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

class MiMoClient(private val config: MiMoConfig) {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
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
        val body = if (title != null) {
            gson.toJson(mapOf("title" to title))
        } else {
            "{}"
        }
        
        Request.Builder()
            .url("${config.baseUrl}/session")
            .post(body.toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Content-Type", "application/json")
            .build()
            .executeAndParseJson()
    }
    
    suspend fun getMessages(sessionId: String): List<Message> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${config.baseUrl}/session/${sessionId}/messages")
            .get()
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .build()
        
        val response = request.executeAndParse()
        response.use { resp ->
            val body = resp.body?.string() ?: throw Exception("Empty response")
            val type = object : TypeToken<List<Message>>() {}.type
            gson.fromJson(body, type)
        }
    }
    
    suspend fun sendMessage(
        sessionId: String,
        content: String,
        system: String? = null
    ): Message = withContext(Dispatchers.IO) {
        val parts = listOf(PartInput(type = "text", content = content))
        val requestBody = SendMessageRequest(parts = parts, system = system)
        
        Request.Builder()
            .url("${config.baseUrl}/session/${sessionId}/prompt")
            .post(gson.toJson(requestBody).toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Content-Type", "application/json")
            .build()
            .executeAndParseJson()
    }
    
    suspend fun sendImage(
        sessionId: String,
        imageBase64: String,
        mimeType: String,
        text: String? = null
    ): Message = withContext(Dispatchers.IO) {
        val parts = mutableListOf<PartInput>()
        if (text != null) {
            parts.add(PartInput(type = "text", content = text))
        }
        parts.add(PartInput(type = "file", content = imageBase64, mimeType = mimeType))
        
        val requestBody = SendMessageRequest(parts = parts)
        
        Request.Builder()
            .url("${config.baseUrl}/session/${sessionId}/prompt")
            .post(gson.toJson(requestBody).toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Content-Type", "application/json")
            .build()
            .executeAndParseJson()
    }
}
