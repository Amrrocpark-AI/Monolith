
package com.monolith.network

import java.util.UUID

enum class DeviceRole {
    HOST,
    CLIENT,
    AGENT_NODE
}

enum class DeviceConnectionState {
    DISCOVERING,
    PAIRING,
    CONNECTED,
    DISCONNECTED,
    REVOKED
}

data class DeviceInfo(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val role: DeviceRole,
    val appVersion: String,
    val supportedFeatures: Set<String> = emptySet()
)

data class DeviceSession(
    val deviceId: String,
    val state: DeviceConnectionState,
    val pairedAt: Long,
    val permissions: Set<String> = emptySet()
)

data class NetworkTask(
    val id: String = UUID.randomUUID().toString(),
    val roomId: String? = null,
    val taskType: String,
    val payload: String,
    val createdAt: Long = System.currentTimeMillis()
)

data class NetworkTaskResult(
    val taskId: String,
    val success: Boolean,
    val result: String? = null,
    val error: String? = null
)
