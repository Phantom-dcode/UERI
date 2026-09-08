package com.example.transport

import com.example.model.EmergencySession

/**
 * UERI Emergency Transport Abstraction
 *
 * Current implementation: CloudEmergencyTransport
 * Future roadmap: OfflineRelayTransport (BLE mesh, Wi-Fi Direct peer-to-peer relay
 * when cellular internet connectivity is offline or jammed).
 *
 * Architecture Note:
 * Future versions of UERI may support multi-hop emergency communication through
 * nearby smartphones and mesh nodes when internet connectivity is unavailable.
 */
interface EmergencyTransport {
    suspend fun broadcastEmergency(session: EmergencySession): Boolean
    suspend fun broadcastResolution(session: EmergencySession): Boolean
    suspend fun updateRadius(session: EmergencySession, newRadiusMeters: Double): Boolean
    val transportName: String
    val isMeshCapable: Boolean
}

class CloudEmergencyTransport : EmergencyTransport {
    override val transportName: String = "Cloud / Real-Time WebSocket & StateFlow"
    override val isMeshCapable: Boolean = false

    override suspend fun broadcastEmergency(session: EmergencySession): Boolean {
        // Broadcasts immediately across state flows and cloud listeners
        return true
    }

    override suspend fun broadcastResolution(session: EmergencySession): Boolean {
        return true
    }

    override suspend fun updateRadius(session: EmergencySession, newRadiusMeters: Double): Boolean {
        return true
    }
}

class OfflineRelayTransport : EmergencyTransport {
    override val transportName: String = "Device-to-Device Mesh Relay (Conceptual)"
    override val isMeshCapable: Boolean = true

    override suspend fun broadcastEmergency(session: EmergencySession): Boolean {
        // In future production versions: P2P BLE beacon advertising and local Wi-Fi Direct packet routing
        return true
    }

    override suspend fun broadcastResolution(session: EmergencySession): Boolean {
        return true
    }

    override suspend fun updateRadius(session: EmergencySession, newRadiusMeters: Double): Boolean {
        return true
    }
}
