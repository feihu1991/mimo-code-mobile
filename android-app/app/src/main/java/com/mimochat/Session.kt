package com.mimochat

data class Session(
    val id: String,
    val title: String,
    val createdAt: Long,
    var updatedAt: Long
)
