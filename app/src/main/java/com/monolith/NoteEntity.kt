package com.monolith

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val content: String,
    val type: String = "NOTE",
    val timestamp: Long = System.currentTimeMillis(),
    val dueDate: Long? = null,
    val reminderTime: Long? = null,
    val isPinned: Boolean = false,
    val colorHex: String = "#2A2A2A",
    val coverPattern: String? = null,
    val coverSticker: String? = null
)
