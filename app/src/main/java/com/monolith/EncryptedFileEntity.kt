package com.monolith

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "encrypted_files")
data class EncryptedFileEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(), // معرف فريد للملف
    val originalName: String, // الاسم الأصلي للملف (مثلاً: image.jpg)
    val encryptedPath: String, // المسار الذي تم حفظ الملف المشفر فيه
    val uniquePasswordEncrypted: String, // كلمة المرور الفريدة (مشفرة بالمفتاح الرئيسي)
    val isSteganography: Boolean = false, // هل الملف مخفي داخل صورة (تمويه)؟
    val carrierImagePath: String? = null, // إذا كان مموهاً، ما هو مسار الصورة التي تخفيه؟
    val fileSize: Long = 0L, // حجم الملف (لإحصائيات لوحة الصحة)
    val dateEncrypted: Long = System.currentTimeMillis() // تاريخ التشفير
)
