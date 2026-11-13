package com.monolith

import java.util.UUID

data class MemoryEntity(
    val id: String = UUID.randomUUID().toString(),
    val type: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val confidence: Float = 0.5f
)
