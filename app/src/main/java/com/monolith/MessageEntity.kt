package com.monolith

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val conversationId: String,
    val senderId: String,
    val role: String,
    val content: String,
    val timestamp: Long,
    val attachmentUris: String? = null,
    val isEncrypted: Boolean = false,

    // الحقول الجديدة للتفاعلات
    val reactionType: String? = null,
    val reactionColor: String? = null,

    // حقول الشبكة العصبية (الخطوط الذكية)
    val linkedMessageIds: String? = null, // معرفات الرسائل المرتبطة (مفصولة بفواصل)
    val isPinned: Boolean = false, // ميزة التثبيت
    val iconType: String? = null, // نوع الإشارة: REPLY, QUESTION, ANALYSIS, REFERENCE
    val pathColor: String? = null, // لون الخط الرابط (HEX)
    val uuid: String = UUID.randomUUID().toString() // معرف فريد لكل رسالة
)
