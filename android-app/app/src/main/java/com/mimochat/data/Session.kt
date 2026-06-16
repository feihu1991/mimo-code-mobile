package com.mimochat.data

data class Session(
    val id: String,
    val title: String,
    val createdAt: Long,
    var updatedAt: Long
)
