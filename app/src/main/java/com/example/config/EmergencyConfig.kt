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
        )
    )

    fun getRadiusForDuration(elapsedSeconds: Long, isFastDemo: Boolean = false): RadiusStage {
        val stages = RADIUS_STAGES
        var current = stages.first()
        for (stage in stages) {
            val threshold = if (isFastDemo) stage.fastThresholdSeconds else stage.standardThresholdSeconds
            if (elapsedSeconds >= threshold) {
                current = stage
            }
        }
        return current
    }

    fun formatDistance(meters: Double): String {
        return if (meters >= 1000.0) {
            String.format(java.util.Locale.US, "%.1f km", meters / 1000.0)
        } else {
            String.format(java.util.Locale.US, "%.0f m", meters)
        }
    }
}
