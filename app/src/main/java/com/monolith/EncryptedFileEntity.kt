package com.monolith

import java.util.UUID

data class EncryptedFileEntity(
    val id: String = UUID.randomUUID().toString(),
    val originalName: String,
    val encryptedPath: String,
    val uniquePasswordEncrypted: String,
    val isSteganography: Boolean = false,
    val carrierImagePath: String? = null,
    val fileSize: Long = 0L,
    val dateEncrypted: Long = System.currentTimeMillis()
)
