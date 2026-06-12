package com.mimochat

data class Message(
    val id: String,
    val sessionId: String,
    val role: String, // "user" or "assistant"
    val content: String,
    val createdAt: Long,
    val parts: List<Part>? = null
)

data class Part(
    val type: String, // "text", "file", "image"
    val content: String,
    val mimeType: String? = null
)
