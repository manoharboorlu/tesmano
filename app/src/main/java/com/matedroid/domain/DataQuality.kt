package com.matedroid.domain

/**
 * Shared provenance/coverage/availability vocabulary for TesMano's derived and estimated
 * analytics. A [MetricQuality] never recomputes a metric — it describes the provenance,
 * availability, and (where a domain-specific model already exists) confidence of a result
 * already produced by [BatteryAnalyticsCalculator], [RealWorldRangeCalculator],
 * [RecurringRouteDetector], or [ChargingCostAnalyticsCalculator].
 */
enum class MetricSemantic { MEASURED, DERIVED, ESTIMATED }
enum class MetricAvailability { AVAILABLE, PARTIAL, UNAVAILABLE }

/** Structured, localizable reasons a metric is partial or unavailable. Never shown to users by name. */
enum class QualityReason {
    INSUFFICIENT_HISTORY,
    MISSING_ENDPOINTS,
    MISSING_RATE,
    MISSING_ENERGY,
    INCOHERENT_ENERGY,
    INSUFFICIENT_SOC_SPAN,
    MISSING_CAPACITY_ESTIMATE,
    INSUFFICIENT_DRIVE_COVERAGE,
    SOURCE_NOT_RETAINED,
    SOURCE_MISSING_OR_ZERO,
    MIXED_CURRENCY,
    DETAIL_NOT_CACHED,
    NO_MATCHING_SMART_PLACE,
    NO_VALID_SAMPLES
}

data class MetricCoverage(val numerator: Int, val denominator: Int) {
    val percent: Int? get() = if (denominator <= 0) null else numerator * 100 / denominator
}

data class MetricQuality(
    val semantic: MetricSemantic,
    val availability: MetricAvailability,
    val confidence: BatteryConfidence? = null,
    val coverage: MetricCoverage? = null,
    val reason: QualityReason? = null
)

// ---- Battery capacity ----

fun BatteryCapacityEstimate.quality(): MetricQuality {
    val total = accepted.size + exclusions.values.sum()
    val availability = if (kwh != null) MetricAvailability.AVAILABLE else MetricAvailability.UNAVAILABLE
    val reason = if (kwh != null) null else when (exclusions.maxByOrNull { it.value }?.key) {
        CapacityExclusion.SMALL_SOC_SPAN -> QualityReason.INSUFFICIENT_SOC_SPAN
        CapacityExclusion.MISSING_ENERGY -> QualityReason.MISSING_ENERGY
        else -> QualityReason.NO_VALID_SAMPLES
    }
    return MetricQuality(MetricSemantic.ESTIMATED, availability, confidence, MetricCoverage(accepted.size, total), reason)
}

// ---- Real-world range ----

fun RealWorldRange.quality(efficiency: EfficiencyResult, capacityAvailable: Boolean): MetricQuality {
    val availability = if (toZeroMiles != null) MetricAvailability.AVAILABLE else MetricAvailability.UNAVAILABLE
    val reason = if (toZeroMiles != null) null else when {
        !capacityAvailable -> QualityReason.MISSING_CAPACITY_ESTIMATE
        efficiency.confidence == null -> QualityReason.INSUFFICIENT_DRIVE_COVERAGE
        else -> QualityReason.NO_VALID_SAMPLES
    }
    return MetricQuality(MetricSemantic.ESTIMATED, availability, confidence, null, reason)
}

// ---- Recurring routes ----

/** Distinguishes "nothing to discover from yet" from "genuinely no repeated pattern". */
enum class RouteCoverageState { NO_ENDPOINTS, PARTIAL_COVERAGE, FULL_COVERAGE_NO_PATTERN }

fun RouteCoverage.state(): RouteCoverageState = when {
    knownEndpoints <= 0 -> RouteCoverageState.NO_ENDPOINTS
    knownEndpoints < totalDrives -> RouteCoverageState.PARTIAL_COVERAGE
    else -> RouteCoverageState.FULL_COVERAGE_NO_PATTERN
}

fun RouteCoverage.quality(): MetricQuality {
    val state = state()
    val availability = when (state) {
        RouteCoverageState.NO_ENDPOINTS -> MetricAvailability.UNAVAILABLE
        RouteCoverageState.PARTIAL_COVERAGE -> MetricAvailability.PARTIAL
        RouteCoverageState.FULL_COVERAGE_NO_PATTERN -> MetricAvailability.AVAILABLE
    }
    val reason = when (state) {
        RouteCoverageState.NO_ENDPOINTS -> if (totalDrives == 0) QualityReason.INSUFFICIENT_HISTORY else QualityReason.MISSING_ENDPOINTS
        RouteCoverageState.PARTIAL_COVERAGE -> QualityReason.MISSING_ENDPOINTS
        RouteCoverageState.FULL_COVERAGE_NO_PATTERN -> null
    }
    return MetricQuality(MetricSemantic.DERIVED, availability, null, MetricCoverage(knownEndpoints, totalDrives), reason)
}

// ---- Charging cost ----

/** Why one session's cost is unavailable. Distinguishes the two collapsed-to-null cases in [ChargeCostPresentation]. */
fun ChargeCostPresentation.unavailableReason(): QualityReason? {
    if (costMinorUnits != null) return null
    return if (rate == null) QualityReason.MISSING_RATE else QualityReason.MISSING_ENERGY
}

fun ChargingAnalyticsSnapshot.unavailableReasonBreakdown(): Map<QualityReason, Int> =
    sessions.mapNotNull { it.cost.unavailableReason() }.groupingBy { it }.eachCount()

fun ChargingAnalyticsSnapshot.quality(): MetricQuality {
    val availability = when {
        totalSessions == 0 -> MetricAvailability.UNAVAILABLE
        mixedCurrencies || unavailableSessions > 0 -> MetricAvailability.PARTIAL
        else -> MetricAvailability.AVAILABLE
    }
    val reason = when {
        totalSessions == 0 -> QualityReason.INSUFFICIENT_HISTORY
        mixedCurrencies -> QualityReason.MIXED_CURRENCY
        unavailableSessions > 0 -> unavailableReasonBreakdown().maxByOrNull { it.value }?.key
        else -> null
    }
    return MetricQuality(MetricSemantic.DERIVED, availability, null, MetricCoverage(pricedSessions, totalSessions), reason)
}

// ---- Data Quality Center aggregate shapes ----

data class DriveDataQuality(
    val totalDrives: Int,
    val endpointCoverage: MetricCoverage,
    val detailEnrichedDrives: Int,
    val quality: MetricQuality
)

data class ChargingDataQuality(
    val totalSessions: Int,
    val pricedOrFreeSessions: Int,
    val unavailableSessions: Int,
    val freeSessions: Int,
    val manualSessions: Int,
    val mixedCurrencies: Boolean,
    val unavailableReasons: Map<QualityReason, Int>,
    val quality: MetricQuality
)

data class BatteryDataQuality(
    val acceptedSamples: Int,
    val excludedSamples: Int,
    val baselineAvailable: Boolean,
    val ratedRangeHistoryAvailable: Boolean,
    val chargingEfficiencyAvailable: Boolean,
    val quality: MetricQuality
)

data class RangeDataQuality(
    val capacityConfidence: BatteryConfidence?,
    val efficiencyConfidence: BatteryConfidence?,
    val windowLabel: String,
    val quality: MetricQuality
)

data class DataQualitySummary(
    val carId: Int,
    val drives: DriveDataQuality,
    val charging: ChargingDataQuality,
    val battery: BatteryDataQuality,
    val range: RangeDataQuality
)
