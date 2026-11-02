
package com.monolith

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey
    val id: String,
    val conversationId: String, // لربط الرسالة بمحادثة معينة أو مجموعة
    val senderId: String, // معرف المرسل
    val role: String, // USER, AI, SYSTEM
    val content: String,
    val timestamp: Long,
    val attachmentUris: String? = null, // سنحفظ الروابط كنص مفصول بفواصل
    val isEncrypted: Boolean = false // ميزة مستقبلية
)
