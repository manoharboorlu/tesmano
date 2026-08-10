package com.matedroid.domain

import com.matedroid.data.local.entity.ChargeDetailAggregate
import com.matedroid.data.local.entity.ChargeSummary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

/** Provenance is deliberately visible wherever Battery Lab presents a metric. */
enum class BatteryMetricKind { MEASURED, DERIVED, ESTIMATED }
enum class BatteryConfidence { LOW, MEDIUM, HIGH }
enum class CapacityExclusion { MISSING_SOC, SMALL_SOC_SPAN, MISSING_ENERGY, IMPLAUSIBLE, OUTLIER }

data class CapacitySample(val chargeId: Int, val date: LocalDate, val kwh: Double)
data class BatteryCapacityEstimate(
    val kwh: Double?, val accepted: List<CapacitySample>, val exclusions: Map<CapacityExclusion, Int>,
    val confidence: BatteryConfidence?, val baselineKwh: Double?, val changeFromBaselinePercent: Double?
)
data class ChargingExposure(val sessions: Int, val kwh: Double, val efficiency: Double?)
data class BatteryAnalyticsSnapshot(
    val capacity: BatteryCapacityEstimate,
    val monthlyCapacity: List<Pair<String, Double>>,
    val ac: ChargingExposure, val dc: ChargingExposure,
    val ratedRangeAvailable: Boolean = false,
    val batteryTemperatureAvailable: Boolean = false
)

object BatteryAnalyticsCalculator {
    private const val MIN_SOC_DELTA = 12
    private const val MIN_CAPACITY = 10.0
    private const val MAX_CAPACITY = 150.0

    fun estimate(charges: List<ChargeSummary>, zone: ZoneId = ZoneId.systemDefault()): BatteryCapacityEstimate {
        val candidates = mutableListOf<CapacitySample>()
        val excluded = mutableMapOf<CapacityExclusion, Int>()
        fun reject(reason: CapacityExclusion) { excluded[reason] = (excluded[reason] ?: 0) + 1 }
        charges.forEach { charge ->
            val delta = charge.endBatteryLevel - charge.startBatteryLevel
            when {
                charge.startBatteryLevel !in 0..100 || charge.endBatteryLevel !in 0..100 -> reject(CapacityExclusion.MISSING_SOC)
                delta <= 0 -> reject(CapacityExclusion.MISSING_SOC)
                delta < MIN_SOC_DELTA -> reject(CapacityExclusion.SMALL_SOC_SPAN)
                !charge.energyAdded.isFinite() || charge.energyAdded <= 0 -> reject(CapacityExclusion.MISSING_ENERGY)
                else -> {
                    val kwh = charge.energyAdded / (delta / 100.0)
                    if (kwh !in MIN_CAPACITY..MAX_CAPACITY) reject(CapacityExclusion.IMPLAUSIBLE)
                    else parseDate(charge.startDate, zone)?.let { candidates += CapacitySample(charge.chargeId, it, kwh) }
                }
            }
        }
        val median = median(candidates.map { it.kwh })
        val mad = median?.let { center -> median(candidates.map { abs(it.kwh - center) }) }
        val accepted = if (median == null || mad == null || mad == 0.0) candidates else candidates.filter {
            abs(it.kwh - median) <= 3.5 * mad
        }
        val rejectedOutliers = candidates.size - accepted.size
        if (rejectedOutliers > 0) excluded[CapacityExclusion.OUTLIER] = rejectedOutliers
        val estimate = median(accepted.map { it.kwh })
        val baselineSamples = accepted.sortedBy { it.date }.take((accepted.size / 3).coerceAtLeast(3).coerceAtMost(6))
        val baseline = baselineSamples.takeIf { it.size >= 3 }?.let { median(it.map { sample -> sample.kwh }) }
        val confidence = estimate?.let { confidence(accepted, mad ?: 0.0) }
        return BatteryCapacityEstimate(estimate, accepted, excluded, confidence, baseline, baseline?.let { base -> (estimate!! - base) / base * 100 })
    }

    fun snapshot(charges: List<ChargeSummary>, aggregates: List<ChargeDetailAggregate>): BatteryAnalyticsSnapshot {
        val capacity = estimate(charges)
        val aggregateByCharge = aggregates.associateBy { it.chargeId }
        fun exposure(dc: Boolean): ChargingExposure {
            val sessions = charges.filter { aggregateByCharge[it.chargeId]?.isFastCharger == dc }
            val battery = sessions.sumOf { it.energyAdded.takeIf(Double::isFinite)?.coerceAtLeast(0.0) ?: 0.0 }
            val grid = sessions.sumOf { charge -> charge.energyUsed?.takeIf { it.isFinite() && it >= charge.energyAdded && charge.energyAdded > 0 } ?: 0.0 }
            val coherentBattery = sessions.filter { it.energyUsed?.let { used -> used.isFinite() && used >= it.energyAdded && it.energyAdded > 0 } == true }.sumOf { it.energyAdded }
            val efficiency = if (grid > 0) coherentBattery / grid else null
            return ChargingExposure(sessions.size, battery, efficiency)
        }
        val months = capacity.accepted.groupBy { "%04d-%02d".format(it.date.year, it.date.monthValue) }
            .map { it.key to (median(it.value.map { sample -> sample.kwh }) ?: 0.0) }.sortedBy { it.first }
        return BatteryAnalyticsSnapshot(capacity, months, exposure(false), exposure(true))
    }

    private fun confidence(samples: List<CapacitySample>, dispersion: Double): BatteryConfidence = when {
        samples.size >= 8 && dispersion <= 2.0 && samples.map { it.date }.distinct().size >= 4 -> BatteryConfidence.HIGH
        samples.size >= 3 && dispersion <= 5.0 -> BatteryConfidence.MEDIUM
        else -> BatteryConfidence.LOW
    }
    private fun median(values: List<Double>): Double? = values.takeIf { it.isNotEmpty() }?.sorted()?.let { sorted ->
        val mid = sorted.size / 2; if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2 else sorted[mid]
    }
    private fun parseDate(value: String, zone: ZoneId): LocalDate? = runCatching { Instant.parse(value).atZone(zone).toLocalDate() }.getOrNull()
}
