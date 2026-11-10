package com.monolith

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val type: String, // "PREFERENCE", "EMOTION", "FACT"
    val content: String, // "المستخدم يفضل الإجابات القصيرة"
    val timestamp: Long = System.currentTimeMillis(),
    val confidence: Float = 0.5f // درجة الثقة (0.0 - 1.0)
)
