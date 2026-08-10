package com.matedroid.ui.screens.charges

import com.matedroid.data.api.models.ChargeDetail

object ChargeStatsCalculator {

    fun calculateStats(detail: ChargeDetail): ChargeDetailStats {
        val points = detail.chargePoints ?: emptyList()

        // Power stats
        val powers = points.mapNotNull { it.chargerPower }
        val powerMax = powers.maxOrNull() ?: 0
        val powerMin = powers.filter { it > 0 }.minOrNull() ?: 0
        val powerAvg = if (powers.isNotEmpty()) powers.average() else 0.0

        // Voltage stats
        val voltages = points.mapNotNull { it.chargerVoltage }
        val voltageMax = voltages.maxOrNull() ?: 0
        val voltageMin = voltages.filter { it > 0 }.minOrNull() ?: 0
        val voltageAvg = if (voltages.isNotEmpty()) voltages.average() else 0.0

        // Current stats
        val currents = points.mapNotNull { it.chargerCurrent }
        val currentMax = currents.maxOrNull() ?: 0
        val currentMin = currents.filter { it > 0 }.minOrNull() ?: 0
        val currentAvg = if (currents.isNotEmpty()) currents.average() else 0.0

        // Temperature stats
        val temps = points.mapNotNull { it.outsideTemp }
        val tempMax = temps.maxOrNull() ?: detail.outsideTempAvg ?: 0.0
        val tempMin = temps.minOrNull() ?: detail.outsideTempAvg ?: 0.0
        val tempAvg = if (temps.isNotEmpty()) temps.average() else detail.outsideTempAvg ?: 0.0

        // Battery stats
        val batteryLevels = points.mapNotNull { it.batteryLevel }
        val batteryStart = batteryLevels.firstOrNull() ?: detail.startBatteryLevel ?: 0
        val batteryEnd = batteryLevels.lastOrNull() ?: detail.currentOrEndBatteryLevel ?: 0
        val batteryAdded = batteryEnd - batteryStart

        // Energy stats
        val energyAdded = detail.chargeEnergyAdded ?: 0.0
        // TeslaMate has historically exposed incomplete or inconsistent charge-energy values.
        // Keep the upstream reading as reported data, rather than substituting energy added and
        // presenting a fabricated 100% efficiency.
        val energyUsed = detail.chargeEnergyUsed?.takeIf { it > 0.0 }
        val efficiency = energyUsed
            ?.takeIf { it >= energyAdded && energyAdded > 0.0 }
            ?.let { (energyAdded / it) * 100.0 }

        return ChargeDetailStats(
            powerMax = powerMax,
            powerMin = powerMin,
            powerAvg = powerAvg,
            voltageMax = voltageMax,
            voltageMin = voltageMin,
            voltageAvg = voltageAvg,
            currentMax = currentMax,
            currentMin = currentMin,
            currentAvg = currentAvg,
            tempMax = tempMax,
            tempMin = tempMin,
            tempAvg = tempAvg,
            batteryStart = batteryStart,
            batteryEnd = batteryEnd,
            batteryAdded = batteryAdded,
            energyAdded = energyAdded,
            energyUsed = energyUsed,
            efficiency = efficiency,
            durationMin = detail.durationMin ?: 0,
            cost = detail.cost
        )
    }

    /**
     * Returns a classification only when the captured telemetry explicitly supports it.
     *
     * A missing phase is not evidence of DC charging: TeslaMate data can omit charger details,
     * particularly for older sessions. `fast_charger_present` is the reliable DC signal here;
     * positive phase counts are reliable AC signals. The UI labels all other sessions as unknown.
     */
    fun detectDcCharge(detail: ChargeDetail): Boolean? {
        val chargerDetails = detail.chargePoints
            ?.mapNotNull { it.chargerDetails }
            .orEmpty()
        if (chargerDetails.any { it.fastChargerPresent == true }) return true
        if (chargerDetails.any { (it.chargerPhases ?: 0) > 0 }) return false
        return null
    }

    /**
     * Average charging power (kW) above which an as-yet-unanalyzed charge is assumed to be DC.
     * AC tops out around 11 kW (22 kW on three phases); DC fast charging sustains far more, so a
     * threshold in between cleanly separates the two for the list heuristic.
     */
    const val DC_AVG_POWER_THRESHOLD_KW = 20.0

    /**
     * Best-effort DC/AC classification for the charges list.
     *
     * When the charge's detail has already been processed into an aggregate, return the exact
     * stored result. Otherwise (the detail hasn't been synced yet) fall back to an average-power
     * heuristic — energy added over duration — so recent DC charges aren't mislabeled as AC while
     * waiting for the background sync. The value self-corrects to exact once the charge is processed.
     */
    fun isDcCharge(
        chargeId: Int,
        energyAddedKwh: Double?,
        durationMin: Int?,
        dcChargeIds: Set<Int>,
        processedChargeIds: Set<Int>
    ): Boolean {
        if (chargeId in processedChargeIds) return chargeId in dcChargeIds
        val minutes = durationMin ?: return false
        val energy = energyAddedKwh ?: return false
        if (minutes <= 0) return false
        val avgPowerKw = energy / (minutes / 60.0)
        return avgPowerKw > DC_AVG_POWER_THRESHOLD_KW
    }
}
