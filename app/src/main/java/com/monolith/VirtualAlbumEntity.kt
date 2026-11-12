package com.monolith

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "virtual_albums")
data class VirtualAlbumEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val coverUri: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val isVault: Boolean = false,
    val sortOrder: Int = 0
)
