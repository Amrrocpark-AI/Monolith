package com.monolith

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

// 1. جدول الألبوم نفسه
@Entity(tableName = "virtual_albums")
data class VirtualAlbumEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val coverUri: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val isVault: Boolean = false, // هل هو ألبوم سري (خزنة)؟
    val sortOrder: Int = 0 // للتحكم في ترتيب الألبومات
)

// 2. جدول الربط (يسمح للصورة الواحدة أن تكون في عدة ألبومات)
@Entity(
    tableName = "album_media_cross_ref",
    primaryKeys = ["albumId", "mediaUri"],
    foreignKeys = [
        ForeignKey(
            entity = VirtualAlbumEntity::class,
            parentColumns = ["id"],
            childColumns = ["albumId"],
            onDelete = ForeignKey.CASCADE // إذا حُذف الألبوم، تُحذف الروابط فقط
        )
    ],
    indices = [Index("albumId"), Index("mediaUri")]
)
data class AlbumMediaCrossRef(
    val albumId: String,
    val mediaUri: String
)
