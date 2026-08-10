package com.matedroid.ui.components

import androidx.annotation.StringRes
import com.matedroid.R
import com.matedroid.domain.BatteryConfidence
import com.matedroid.domain.CapacityExclusion
import com.matedroid.domain.MetricAvailability
import com.matedroid.domain.MetricSemantic
import com.matedroid.domain.QualityReason

/** Maps TesMano's shared quality vocabulary to localized, user-facing text. Enum names are never shown directly. */

@StringRes
fun MetricSemantic.labelRes(): Int = when (this) {
    MetricSemantic.MEASURED -> R.string.quality_semantic_measured
    MetricSemantic.DERIVED -> R.string.quality_semantic_derived
    MetricSemantic.ESTIMATED -> R.string.quality_semantic_estimated
}

@StringRes
fun MetricAvailability.labelRes(): Int = when (this) {
    MetricAvailability.AVAILABLE -> R.string.quality_availability_available
    MetricAvailability.PARTIAL -> R.string.quality_availability_partial
    MetricAvailability.UNAVAILABLE -> R.string.quality_availability_unavailable
}

@StringRes
fun BatteryConfidence.labelRes(): Int = when (this) {
    BatteryConfidence.LOW -> R.string.quality_confidence_low
    BatteryConfidence.MEDIUM -> R.string.quality_confidence_medium
    BatteryConfidence.HIGH -> R.string.quality_confidence_high
}

@StringRes
fun QualityReason.explanationRes(): Int = when (this) {
    QualityReason.INSUFFICIENT_HISTORY -> R.string.quality_reason_insufficient_history
    QualityReason.MISSING_ENDPOINTS -> R.string.quality_reason_missing_endpoints
    QualityReason.MISSING_RATE -> R.string.quality_reason_missing_rate
    QualityReason.MISSING_ENERGY -> R.string.quality_reason_missing_energy
    QualityReason.INCOHERENT_ENERGY -> R.string.quality_reason_incoherent_energy
    QualityReason.INSUFFICIENT_SOC_SPAN -> R.string.quality_reason_insufficient_soc_span
    QualityReason.MISSING_CAPACITY_ESTIMATE -> R.string.quality_reason_missing_capacity_estimate
    QualityReason.INSUFFICIENT_DRIVE_COVERAGE -> R.string.quality_reason_insufficient_drive_coverage
    QualityReason.SOURCE_NOT_RETAINED -> R.string.quality_reason_source_not_retained
    QualityReason.SOURCE_MISSING_OR_ZERO -> R.string.quality_reason_source_missing_or_zero
    QualityReason.MIXED_CURRENCY -> R.string.quality_reason_mixed_currency
    QualityReason.DETAIL_NOT_CACHED -> R.string.quality_reason_detail_not_cached
    QualityReason.NO_MATCHING_SMART_PLACE -> R.string.quality_reason_no_matching_smart_place
    QualityReason.NO_VALID_SAMPLES -> R.string.quality_reason_no_valid_samples
}

@StringRes
fun CapacityExclusion.labelRes(): Int = when (this) {
    CapacityExclusion.MISSING_SOC -> R.string.battery_exclusion_missing_soc
    CapacityExclusion.SMALL_SOC_SPAN -> R.string.battery_exclusion_small_span
    CapacityExclusion.MISSING_ENERGY -> R.string.battery_exclusion_missing_energy
    CapacityExclusion.IMPLAUSIBLE -> R.string.battery_exclusion_implausible
    CapacityExclusion.OUTLIER -> R.string.battery_exclusion_outlier
}
