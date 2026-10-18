package com.example.config

data class RadiusStage(
    val stageIndex: Int,
    val radiusMeters: Double,
    val displayLabel: String,
    val standardThresholdSeconds: Long,
    val fastThresholdSeconds: Long
)

object EmergencyConfig {
    const val DEFAULT_LATITUDE = 37.774929
    const val DEFAULT_LONGITUDE = -122.419416

    val RADIUS_STAGES = listOf(
        RadiusStage(
            stageIndex = 0,
            radiusMeters = 500.0,
            displayLabel = "500 m",
            standardThresholdSeconds = 0L,
            fastThresholdSeconds = 0L
        ),
        RadiusStage(
            stageIndex = 1,
            radiusMeters = 1000.0,
            displayLabel = "1.0 km",
            standardThresholdSeconds = 10L,
            fastThresholdSeconds = 5L
        ),
        RadiusStage(
            stageIndex = 2,
            radiusMeters = 2000.0,
            displayLabel = "2.0 km",
            standardThresholdSeconds = 20L,
            fastThresholdSeconds = 10L
        ),
        RadiusStage(
            stageIndex = 3,
            radiusMeters = 5000.0,
            displayLabel = "5.0 km",
            standardThresholdSeconds = 30L,
            fastThresholdSeconds = 15L
        ),
        RadiusStage(
            stageIndex = 4,
            radiusMeters = 7500.0,
            displayLabel = "7.5 km",
            standardThresholdSeconds = 40L,
            fastThresholdSeconds = 20L
        ),
        RadiusStage(
            stageIndex = 5,
            radiusMeters = 10000.0,
            displayLabel = "10.0 km",
            standardThresholdSeconds = 50L,
            fastThresholdSeconds = 25L
        ),
        RadiusStage(
            stageIndex = 6,
            radiusMeters = 15000.0,
            displayLabel = "15.0 km",
            standardThresholdSeconds = 60L,
            fastThresholdSeconds = 30L
        ),
        RadiusStage(
            stageIndex = 7,
            radiusMeters = 20000.0,
            displayLabel = "20.0 km",
            standardThresholdSeconds = 70L,
            fastThresholdSeconds = 35L
        ),
        RadiusStage(
            stageIndex = 8,
            radiusMeters = 30000.0,
            displayLabel = "30.0 km",
            standardThresholdSeconds = 80L,
            fastThresholdSeconds = 40L
        ),
        RadiusStage(
            stageIndex = 9,
            radiusMeters = 50000.0,
            displayLabel = "50.0 km",
            standardThresholdSeconds = 90L,
            fastThresholdSeconds = 45L
        ),
        RadiusStage(
            stageIndex = 10,
            radiusMeters = 75000.0,
            displayLabel = "75.0 km",
            standardThresholdSeconds = 100L,
            fastThresholdSeconds = 50L
        ),
        RadiusStage(
            stageIndex = 11,
            radiusMeters = 100000.0,
            displayLabel = "100.0 km",
            standardThresholdSeconds = 110L,
            fastThresholdSeconds = 55L
        )
    )

    /**
     * Calculates the active broadcast radius based on elapsed time without an upper ceiling.
     * As time continues to delay, the distance increases indefinitely along with time.
     */
    fun getRadiusForDuration(elapsedSeconds: Long, isFastDemo: Boolean = false): RadiusStage {
        val stages = RADIUS_STAGES
        val lastPredefined = stages.last()
        val lastThreshold = if (isFastDemo) lastPredefined.fastThresholdSeconds else lastPredefined.standardThresholdSeconds

        // If within predefined table
        if (elapsedSeconds <= lastThreshold) {
            var current = stages.first()
            for (stage in stages) {
                val threshold = if (isFastDemo) stage.fastThresholdSeconds else stage.standardThresholdSeconds
                if (elapsedSeconds >= threshold) {
                    current = stage
                }
            }
            return current
        }

        // Indefinite time-delay escalation beyond 100km:
        // Continues expanding by +25 km for every additional time interval
        val stepInterval = if (isFastDemo) 5L else 10L
        val extraSteps = (elapsedSeconds - lastThreshold) / stepInterval
        val extraDistance = (extraSteps + 1L) * 25000.0
        val totalMeters = lastPredefined.radiusMeters + extraDistance
        val stageIdx = lastPredefined.stageIndex + extraSteps.toInt() + 1

        return RadiusStage(
            stageIndex = stageIdx,
            radiusMeters = totalMeters,
            displayLabel = formatDistance(totalMeters),
            standardThresholdSeconds = lastPredefined.standardThresholdSeconds + ((extraSteps + 1L) * 10L),
            fastThresholdSeconds = lastPredefined.fastThresholdSeconds + ((extraSteps + 1L) * 5L)
        )
    }

    fun formatDistance(meters: Double): String {
        return if (meters >= 1000.0) {
            String.format(java.util.Locale.US, "%.1f km", meters / 1000.0)
        } else {
            String.format(java.util.Locale.US, "%.0f m", meters)
        }
    }
}
