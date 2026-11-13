package com.monolith

import java.util.UUID

data class VirtualAlbumEntity(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val coverUri: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val isVault: Boolean = false,
    val sortOrder: Int = 0
)
