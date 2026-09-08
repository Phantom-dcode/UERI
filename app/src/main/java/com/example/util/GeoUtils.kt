package com.example.util

import com.example.model.Responder
import com.example.model.ResponderStatus
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object GeoUtils {
    private const val EARTH_RADIUS_METERS = 6371000.0

    /**
     * Calculates the great-circle distance between two geographic coordinates
     * using the Haversine formula.
     */
    fun haversineDistanceMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(rLat1) * cos(rLat2) * sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return EARTH_RADIUS_METERS * c
    }

    /**
     * Projects a coordinate by distance (in meters) and bearing (in degrees)
     */
    fun offsetCoordinate(
        lat: Double,
        lon: Double,
        distanceMeters: Double,
        bearingDegrees: Double
    ): Pair<Double, Double> {
        val distRatio = distanceMeters / EARTH_RADIUS_METERS
        val bearingRad = Math.toRadians(bearingDegrees)
        val latRad = Math.toRadians(lat)
        val lonRad = Math.toRadians(lon)

        val newLatRad = Math.asin(
            sin(latRad) * cos(distRatio) +
                    cos(latRad) * sin(distRatio) * cos(bearingRad)
        )
        val newLonRad = lonRad + atan2(
            sin(bearingRad) * sin(distRatio) * cos(latRad),
            cos(distRatio) - sin(latRad) * sin(newLatRad)
        )

        return Pair(Math.toDegrees(newLatRad), Math.toDegrees(newLonRad))
    }

    /**
     * Generates standard initial prototype responders relative to victim coordinate.
     * Distances:
     * - Responder A: ~320m (eligible at 500m)
     * - Responder B: ~780m (eligible at 1.0km)
     * - Responder C: ~1.6km (eligible at 2.0km)
     * - Responder D: ~3.2km (eligible at 5.0km)
     * - Responder E: ~4.7km (eligible at 5.0km)
     */
    fun createInitialResponders(victimLat: Double, victimLon: Double): List<Responder> {
        val (latA, lonA) = offsetCoordinate(victimLat, victimLon, 320.0, 45.0)
        val (latB, lonB) = offsetCoordinate(victimLat, victimLon, 780.0, 150.0)
        val (latC, lonC) = offsetCoordinate(victimLat, victimLon, 1600.0, 260.0)
        val (latD, lonD) = offsetCoordinate(victimLat, victimLon, 3200.0, 330.0)
        val (latE, lonE) = offsetCoordinate(victimLat, victimLon, 4700.0, 90.0)

        return listOf(
            Responder(
                id = "resp-1",
                name = "Officer J. Miller",
                role = "Patrol Unit #4",
                latitude = latA,
                longitude = lonA,
                status = ResponderStatus.AVAILABLE,
                distanceMeters = 320.0,
                etaMinutes = 2
            ),
            Responder(
                id = "resp-2",
                name = "Dr. Elena Chen",
                role = "Paramedic / Medic",
                latitude = latB,
                longitude = lonB,
                status = ResponderStatus.AVAILABLE,
                distanceMeters = 780.0,
                etaMinutes = 4
            ),
            Responder(
                id = "resp-3",
                name = "Sarah Jenkins",
                role = "Community Responder",
                latitude = latC,
                longitude = lonC,
                status = ResponderStatus.AVAILABLE,
                distanceMeters = 1600.0,
                etaMinutes = 8
            ),
            Responder(
                id = "resp-4",
                name = "Rescue Unit 12",
                role = "Emergency Services",
                latitude = latD,
                longitude = lonD,
                status = ResponderStatus.AVAILABLE,
                distanceMeters = 3200.0,
                etaMinutes = 12
            ),
            Responder(
                id = "resp-5",
                name = "David Park",
                role = "Trained Volunteer",
                latitude = latE,
                longitude = lonE,
                status = ResponderStatus.AVAILABLE,
                distanceMeters = 4700.0,
                etaMinutes = 15
            )
        )
    }
}
