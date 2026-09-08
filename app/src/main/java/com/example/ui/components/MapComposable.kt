package com.example.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.model.EmergencySession
import com.example.model.Responder

/**
 * MapComposable: High-reliability geolocation map interface.
 * Uses realistic vector cartography with dynamic multi-hop coverage zone,
 * live GPS victim beacon, interactive responder units, and multiple map layers (Map, Satellite, Radar).
 */
@Composable
fun MapComposable(
    session: EmergencySession,
    responders: List<Responder>,
    modifier: Modifier = Modifier,
    hasLocationPermission: Boolean = true,
    onResponderSelected: ((Responder) -> Unit)? = null
) {
    TacticalRadarMap(
        session = session,
        responders = responders,
        modifier = modifier,
        onResponderSelected = onResponderSelected
    )
}
