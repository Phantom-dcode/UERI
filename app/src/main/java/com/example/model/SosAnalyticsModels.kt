package com.example.model

import com.example.config.EmergencyConfig
import com.example.util.GeoUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.roundToInt

/**
 * Represents a 2-hour aggregation bucket of SOS emergency calls over a 24-hour window.
 */
data class SosFrequencyBucket(
    val index: Int,
    val timeLabel: String,
    val windowRangeLabel: String,
    val startTimestamp: Long,
    val endTimestamp: Long,
    val totalCalls: Int,
    val criticalCalls: Int,
    val standardCalls: Int,
    val resolvedCalls: Int,
    val avgResponseMinutes: Float
)

/**
 * Represents the geographic distribution of SOS calls within a regional sector over the last 24 hours.
 */
data class GeographicSectorStat(
    val sectorId: String,
    val sectorName: String,
    val shortCode: String,
    val totalCalls: Int,
    val criticalCalls: Int,
    val activeCalls: Int,
    val sharePercentage: Float,
    val avgRadiusMeters: Double,
    val centerLat: Double,
    val centerLon: Double
)

/**
 * Represents an individual SOS incident point plotted on the 24-hour geographic distribution map.
 */
data class SosGeoIncidentPoint(
    val id: String,
    val emergencyId: String,
    val latitude: Double,
    val longitude: Double,
    val sectorId: String,
    val sectorName: String,
    val priority: String,
    val status: EmergencyStatus,
    val timestamp: Long,
    val radiusMeters: Double
)

/**
 * Comprehensive 24-hour SOS frequency and geographic distribution analytics state for the Control Hub.
 */
data class Sos24hAnalytics(
    val totalCalls24h: Int = 0,
    val criticalCalls24h: Int = 0,
    val activeCallsNow: Int = 0,
    val peakWindowLabel: String = "--:--",
    val peakWindowCalls: Int = 0,
    val avgResponseMinutes: Float = 0f,
    val frequencyBuckets: List<SosFrequencyBucket> = emptyList(),
    val geographicSectors: List<GeographicSectorStat> = emptyList(),
    val geoIncidentPoints: List<SosGeoIncidentPoint> = emptyList()
)

object SosAnalyticsCalculator {

    private const val TWENTY_FOUR_HOURS_MS = 24 * 60 * 60 * 1000L
    private const val TWO_HOURS_MS = 2 * 60 * 60 * 1000L

    /**
     * Generates deterministic baseline historical SOS sessions over the last 24 hours
     * relative to [nowMs] and center coordinate so the Control Hub has realistic
     * 24h telemetry alongside live Room & Firestore SOS sessions.
     */
    fun generateBaseline24hSessions(
        nowMs: Long = System.currentTimeMillis(),
        baseLat: Double = EmergencyConfig.DEFAULT_LATITUDE,
        baseLon: Double = EmergencyConfig.DEFAULT_LONGITUDE
    ): List<EmergencySession> {
        data class SeedSpec(
            val hoursAgo: Double,
            val distMeters: Double,
            val bearingDeg: Double,
            val priority: String,
            val type: String,
            val radius: Double,
            val etaMin: Int
        )

        val specs = listOf(
            SeedSpec(22.8, 420.0, 20.0, "CRITICAL", "MEDICAL / CARDIAC", 1000.0, 3),
            SeedSpec(21.4, 2400.0, 35.0, "HIGH", "VEHICULAR COLLISION", 2000.0, 5),
            SeedSpec(19.6, 3800.0, 110.0, "MEDIUM", "DISTURBANCE / SOS", 1000.0, 6),
            SeedSpec(18.2, 1500.0, 195.0, "CRITICAL", "FIRE / SMOKE HAZARD", 5000.0, 4),
            SeedSpec(16.9, 620.0, 310.0, "HIGH", "CITIZEN DISTRESS", 1000.0, 3),
            SeedSpec(15.5, 4200.0, 285.0, "MEDIUM", "POWER LINE DOWN", 2000.0, 7),
            SeedSpec(14.7, 2900.0, 95.0, "CRITICAL", "HARBOR WATER RESCUE", 5000.0, 4),
            SeedSpec(13.8, 780.0, 15.0, "HIGH", "PEDESTRIAN INJURY", 1000.0, 3),
            SeedSpec(12.4, 3100.0, 175.0, "CRITICAL", "MULTI-VEHICLE SOS", 5000.0, 5),
            SeedSpec(11.9, 510.0, 240.0, "HIGH", "ASSAULT / SECURITY", 1000.0, 2),
            SeedSpec(10.6, 1950.0, 48.0, "MEDIUM", "MEDICAL ASSISTANCE", 2000.0, 5),
            SeedSpec(9.8, 3400.0, 115.0, "CRITICAL", "INDUSTRIAL HAZMAT", 5000.0, 6),
            SeedSpec(8.7, 640.0, 80.0, "CRITICAL", "TRANSIT HUB SOS", 2000.0, 2),
            SeedSpec(8.1, 2200.0, 300.0, "HIGH", "HILLSIDE BRUSH FIRE", 5000.0, 6),
            SeedSpec(7.3, 1200.0, 190.0, "HIGH", "STRUCTURAL ENTRAPMENT", 2000.0, 4),
            SeedSpec(6.4, 480.0, 130.0, "CRITICAL", "ARMED ROBBERY SOS", 1000.0, 2),
            SeedSpec(5.6, 2750.0, 25.0, "MEDIUM", "WELFARE CHECK SOS", 1000.0, 5),
            SeedSpec(4.9, 3600.0, 265.0, "CRITICAL", "SEVERE TRAUMA ALERT", 5000.0, 4),
            SeedSpec(3.8, 890.0, 65.0, "HIGH", "NIGHT PATROL ESCALATION", 2000.0, 3),
            SeedSpec(3.2, 1850.0, 165.0, "CRITICAL", "HIGHWAY PILEUP SOS", 5000.0, 4),
            SeedSpec(2.4, 410.0, 345.0, "CRITICAL", "DOWNTOWN EMERGENCY", 1000.0, 2),
            SeedSpec(1.5, 2600.0, 105.0, "HIGH", "PORT FACILITY SOS", 2000.0, 4),
            SeedSpec(0.7, 720.0, 225.0, "CRITICAL", "BLE MESH RELAY SOS", 1000.0, 3)
        )

        return specs.mapIndexed { idx, spec ->
            val createdAt = nowMs - (spec.hoursAgo * 3600_000.0).toLong()
            val (lat, lon) = GeoUtils.offsetCoordinate(baseLat, baseLon, spec.distMeters, spec.bearingDeg)
            EmergencySession(
                id = "hist-24h-$idx",
                emergencyId = "UERI-24H-${100 + idx}",
                status = EmergencyStatus.RESOLVED,
                emergencyType = spec.type,
                priority = spec.priority,
                latitude = lat,
                longitude = lon,
                locationStatus = "ACTIVE",
                locationAccuracyMeters = 4.5f,
                radiusMeters = spec.radius,
                createdAt = createdAt,
                updatedAt = createdAt + (spec.etaMin * 60_000L),
                activeResponderName = "Unit #${(idx % 6) + 1}",
                activeResponderDistanceMeters = spec.distMeters,
                activeResponderEtaMinutes = spec.etaMin,
                peopleNotified = (spec.radius / 180.0).roundToInt().coerceAtLeast(4),
                respondersCount = ((spec.radius / 1000.0).roundToInt() + 1).coerceAtMost(8)
            )
        }
    }

    /**
     * Computes 24-hour frequency buckets and geographic sector distribution from
     * all recorded SOS sessions and the current active session.
     */
    fun compute24hAnalytics(
        sessions: List<EmergencySession>,
        activeSession: EmergencySession?,
        nowMs: Long = System.currentTimeMillis(),
        centerLat: Double = EmergencyConfig.DEFAULT_LATITUDE,
        centerLon: Double = EmergencyConfig.DEFAULT_LONGITUDE
    ): Sos24hAnalytics {
        val cutoffMs = nowMs - TWENTY_FOUR_HOURS_MS

        // Combine unique sessions within the last 24 hours
        val combinedMap = LinkedHashMap<String, EmergencySession>()
        sessions.forEach { s ->
            if (s.createdAt >= cutoffMs && s.emergencyId != "UERI-IDLE") {
                combinedMap[s.emergencyId] = s
            }
        }
        if (activeSession != null &&
            activeSession.status != EmergencyStatus.SAFE &&
            activeSession.emergencyId != "UERI-IDLE"
        ) {
            combinedMap[activeSession.emergencyId] = activeSession
        }

        val validSessions = combinedMap.values.sortedBy { it.createdAt }
        val hourFormatter = SimpleDateFormat("HH:mm", Locale.US)

        // 1. Build 12 two-hour frequency buckets covering [now - 24h .. now]
        val buckets = (0 until 12).map { bucketIdx ->
            val bucketStart = cutoffMs + (bucketIdx * TWO_HOURS_MS)
            val bucketEnd = if (bucketIdx == 11) nowMs + 60_000L else bucketStart + TWO_HOURS_MS

            val inBucket = validSessions.filter { it.createdAt in bucketStart until bucketEnd }
            val total = inBucket.size
            val critical = inBucket.count {
                it.priority.equals("CRITICAL", ignoreCase = true) ||
                    it.emergencyType.contains("CRITICAL", ignoreCase = true)
            }
            val standard = (total - critical).coerceAtLeast(0)
            val resolved = inBucket.count {
                it.status == EmergencyStatus.RESOLVED || it.status == EmergencyStatus.RESPONDER_FOUND
            }
            val avgEta = if (inBucket.isNotEmpty()) {
                inBucket.map { (it.activeResponderEtaMinutes ?: 4).coerceAtLeast(1) }.average().toFloat()
            } else {
                0f
            }

            val startLabel = hourFormatter.format(Date(bucketStart))
            val endLabel = hourFormatter.format(Date(minOf(nowMs, bucketEnd)))

            SosFrequencyBucket(
                index = bucketIdx,
                timeLabel = endLabel,
                windowRangeLabel = "$startLabel – $endLabel",
                startTimestamp = bucketStart,
                endTimestamp = bucketEnd,
                totalCalls = total,
                criticalCalls = critical,
                standardCalls = standard,
                resolvedCalls = resolved,
                avgResponseMinutes = avgEta
            )
        }

        // 2. Classify each incident into 5 geographic sectors relative to the regional center
        // Sectors:
        // - CENTRAL: within 1.0 km of centerLat/centerLon
        // - NORTH: > 1.0 km, bearing -45..45 deg (315..360 or 0..45)
        // - EAST: > 1.0 km, bearing 45..135 deg
        // - SOUTH: > 1.0 km, bearing 135..225 deg
        // - WEST: > 1.0 km, bearing 225..315 deg
        data class SectorMeta(
            val id: String,
            val name: String,
            val shortCode: String,
            val offsetDist: Double,
            val offsetBearing: Double
        )

        val sectorDefinitions = listOf(
            SectorMeta("SECTOR_CENTRAL", "Central Metro Core", "CTR", 0.0, 0.0),
            SectorMeta("SECTOR_NORTH", "North Civic District", "NTH", 2500.0, 0.0),
            SectorMeta("SECTOR_EAST", "East Harbor & Bay", "EST", 2500.0, 90.0),
            SectorMeta("SECTOR_SOUTH", "South Transit Corridor", "STH", 2500.0, 180.0),
            SectorMeta("SECTOR_WEST", "West Residential Hills", "WST", 2500.0, 270.0)
        )

        fun classifySector(lat: Double, lon: Double): SectorMeta {
            val dist = GeoUtils.haversineDistanceMeters(centerLat, centerLon, lat, lon)
            if (dist <= 1000.0) return sectorDefinitions[0]
            val dLon = Math.toRadians(lon - centerLon)
            val lat1 = Math.toRadians(centerLat)
            val lat2 = Math.toRadians(lat)
            val y = kotlin.math.sin(dLon) * kotlin.math.cos(lat2)
            val x = kotlin.math.cos(lat1) * kotlin.math.sin(lat2) -
                kotlin.math.sin(lat1) * kotlin.math.cos(lat2) * kotlin.math.cos(dLon)
            val bearing = (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
            return when {
                bearing >= 315.0 || bearing < 45.0 -> sectorDefinitions[1] // North
                bearing >= 45.0 && bearing < 135.0 -> sectorDefinitions[2] // East
                bearing >= 135.0 && bearing < 225.0 -> sectorDefinitions[3] // South
                else -> sectorDefinitions[4] // West
            }
        }

        val incidentPoints = validSessions.map { s ->
            val sector = classifySector(s.latitude, s.longitude)
            SosGeoIncidentPoint(
                id = s.id,
                emergencyId = s.emergencyId,
                latitude = s.latitude,
                longitude = s.longitude,
                sectorId = sector.id,
                sectorName = sector.name,
                priority = s.priority,
                status = s.status,
                timestamp = s.createdAt,
                radiusMeters = if (s.radiusMeters > 0.0) s.radiusMeters else 500.0
            )
        }

        val totalCalls = validSessions.size.coerceAtLeast(1)
        val sectorStats = sectorDefinitions.map { def ->
            val inSector = validSessions.filter { classifySector(it.latitude, it.longitude).id == def.id }
            val count = inSector.size
            val criticalCount = inSector.count {
                it.priority.equals("CRITICAL", ignoreCase = true) ||
                    it.emergencyType.contains("CRITICAL", ignoreCase = true)
            }
            val activeCount = inSector.count {
                it.status == EmergencyStatus.ACTIVE || it.status == EmergencyStatus.RESPONDER_FOUND
            }
            val avgRadius = if (inSector.isNotEmpty()) {
                inSector.map { if (it.radiusMeters > 0.0) it.radiusMeters else 500.0 }.average()
            } else {
                0.0
            }
            val (sLat, sLon) = if (def.offsetDist == 0.0) {
                Pair(centerLat, centerLon)
            } else {
                GeoUtils.offsetCoordinate(centerLat, centerLon, def.offsetDist, def.offsetBearing)
            }

            GeographicSectorStat(
                sectorId = def.id,
                sectorName = def.name,
                shortCode = def.shortCode,
                totalCalls = count,
                criticalCalls = criticalCount,
                activeCalls = activeCount,
                sharePercentage = (count.toFloat() / totalCalls.toFloat()) * 100f,
                avgRadiusMeters = avgRadius,
                centerLat = sLat,
                centerLon = sLon
            )
        }.sortedByDescending { it.totalCalls }

        val peakBucket = buckets.maxByOrNull { it.totalCalls }
        val overallCritical = validSessions.count {
            it.priority.equals("CRITICAL", ignoreCase = true) ||
                it.emergencyType.contains("CRITICAL", ignoreCase = true)
        }
        val activeNow = validSessions.count {
            it.status == EmergencyStatus.ACTIVE || it.status == EmergencyStatus.RESPONDER_FOUND
        }
        val overallAvgEta = if (validSessions.isNotEmpty()) {
            validSessions.map { (it.activeResponderEtaMinutes ?: 4).coerceAtLeast(1) }.average().toFloat()
        } else {
            0f
        }

        return Sos24hAnalytics(
            totalCalls24h = validSessions.size,
            criticalCalls24h = overallCritical,
            activeCallsNow = activeNow,
            peakWindowLabel = peakBucket?.windowRangeLabel ?: "--:--",
            peakWindowCalls = peakBucket?.totalCalls ?: 0,
            avgResponseMinutes = overallAvgEta,
            frequencyBuckets = buckets,
            geographicSectors = sectorStats,
            geoIncidentPoints = incidentPoints
        )
    }
}
