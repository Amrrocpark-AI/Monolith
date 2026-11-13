package com.monolith

import java.util.UUID

data class TagEntity(
    val id: String = UUID.randomUUID().toString(),
    val name: String
)
