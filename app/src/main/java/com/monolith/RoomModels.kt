
package com.monolith.rooms

import java.util.UUID

enum class RoomRole {
    OWNER,
    ADMIN,
    MEMBER,
    AGENT
}

data class RoomMember(
    val deviceId: String,
    val displayName: String,
    val role: RoomRole
)

data class PrivateRoom(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val ownerId: String,
    val createdAt: Long = System.currentTimeMillis(),
    val members: List<RoomMember> = emptyList()
)

data class RoomMessage(
    val id: String = UUID.randomUUID().toString(),
    val roomId: String,
    val senderId: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isAgentMessage: Boolean = false
)
