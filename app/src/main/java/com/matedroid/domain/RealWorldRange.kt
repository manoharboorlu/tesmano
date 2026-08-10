package com.matedroid.domain

import com.matedroid.data.local.entity.DriveSummary
import java.time.Instant
import java.time.ZoneId

sealed class EfficiencyWindow(val label: String) {
    data object Mi25 : EfficiencyWindow("25 mi"); data object Mi50 : EfficiencyWindow("50 mi"); data object Mi100 : EfficiencyWindow("100 mi")
    data object Days7 : EfficiencyWindow("7d"); data object Days30 : EfficiencyWindow("30d")
    companion object { val all = listOf(Mi25, Mi50, Mi100, Days7, Days30) }
}
data class EfficiencyResult(val window: EfficiencyWindow, val whPerMile: Double?, val miles: Double, val kwh: Double, val drives: Int, val confidence: BatteryConfidence?)
data class RealWorldRange(val toZeroMiles: Double?, val toTenMiles: Double?, val remainingZeroKwh: Double?, val remainingTenKwh: Double?, val confidence: BatteryConfidence?)

object RealWorldRangeCalculator {
    fun efficiency(drivesNewestFirst: List<DriveSummary>, window: EfficiencyWindow, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): EfficiencyResult {
        val cutoff = when (window) { EfficiencyWindow.Days7 -> now.minusSeconds(7 * 86400L); EfficiencyWindow.Days30 -> now.minusSeconds(30 * 86400L); else -> null }
        val target = when (window) { EfficiencyWindow.Mi25 -> 25.0; EfficiencyWindow.Mi50 -> 50.0; EfficiencyWindow.Mi100 -> 100.0; else -> Double.MAX_VALUE }
        val selected = mutableListOf<DriveSummary>(); var miles = 0.0; var kwh = 0.0
        for (drive in drivesNewestFirst) {
            if (cutoff != null && runCatching { Instant.parse(drive.startDate) }.getOrNull()?.isBefore(cutoff) == true) continue
            val energy = drive.energyConsumed
            if (drive.distance <= 0 || energy == null || energy <= 0 || !energy.isFinite()) continue
            // Boundary-drive semantics: include the whole valid drive; never invent prorated energy.
            selected += drive; miles += drive.distance; kwh += energy
            if (miles >= target) break
        }
        val wh = if (miles > 0) kwh * 1000 / miles else null
        val minimum = when (window) { EfficiencyWindow.Mi25 -> 15.0; EfficiencyWindow.Mi50 -> 30.0; EfficiencyWindow.Mi100 -> 60.0; EfficiencyWindow.Days7 -> 10.0; EfficiencyWindow.Days30 -> 25.0 }
        val confidence = when { wh == null || miles < minimum -> null; miles >= target.coerceAtMost(60.0) && selected.size >= 3 -> BatteryConfidence.HIGH; selected.size >= 2 -> BatteryConfidence.MEDIUM; else -> BatteryConfidence.LOW }
        return EfficiencyResult(window, wh, miles, kwh, selected.size, confidence)
    }
    fun range(capacityKwh: Double?, soc: Int?, efficiency: EfficiencyResult, capacityConfidence: BatteryConfidence?): RealWorldRange {
        if (capacityKwh == null || soc == null || soc !in 0..100 || efficiency.whPerMile == null || efficiency.confidence == null) return RealWorldRange(null, null, null, null, null)
        val zero = capacityKwh * soc / 100.0; val ten = capacityKwh * (soc - 10).coerceAtLeast(0) / 100.0
        fun miles(kwh: Double) = kwh * 1000 / efficiency.whPerMile!!
        val confidence = minOf(capacityConfidence ?: BatteryConfidence.LOW, efficiency.confidence, compareBy { it.ordinal })
        return RealWorldRange(miles(zero), miles(ten), zero, ten, confidence)
    }
    fun defaultWindow(drives: List<DriveSummary>): EfficiencyWindow = if (efficiency(drives, EfficiencyWindow.Mi50).confidence != null) EfficiencyWindow.Mi50 else EfficiencyWindow.Mi100
}
