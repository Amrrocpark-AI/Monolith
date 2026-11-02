
package com.monolith

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserEntity(
    @PrimaryKey
    val id: String, // معرف فريد للشخص (مثلاً بصمة الهاش)
    val name: String,
    val bio: String = "",
    val profileImageUri: String? = null, // رابط الصورة الشخصية
    val publicKey: String = "", // مفتاح التشفير العام (للمستقبل)
    val isCurrentUser: Boolean = false, // هل هذا هو المستخدم الحالي للتطبيق؟
    val followersCount: Int = 0,
    val followingCount: Int = 0
)
