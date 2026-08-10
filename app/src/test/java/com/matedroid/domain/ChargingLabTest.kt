package com.matedroid.domain

import com.matedroid.data.local.entity.ChargeCostOverride
import com.matedroid.data.local.entity.ChargeDetailAggregate
import com.matedroid.data.local.entity.ChargeSummary
import com.matedroid.data.local.entity.ChargingRateRule
import com.matedroid.data.local.entity.ChargingRateScope
import com.matedroid.data.local.entity.SmartPlace
import com.matedroid.data.local.entity.SmartPlaceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ChargingLabTest {

    private fun charge(
        id: Int,
        start: String,
        energyAdded: Double,
        energyUsed: Double? = null,
        durationMin: Int = 60,
        startBatteryLevel: Int = 20,
        endBatteryLevel: Int = 80,
        latitude: Double = 0.0,
        longitude: Double = 0.0,
        cost: Double? = null
    ) = ChargeSummary(
        chargeId = id, carId = 1, startDate = start, endDate = start, durationMin = durationMin,
        address = "Somewhere", latitude = latitude, longitude = longitude,
        energyAdded = energyAdded, energyUsed = energyUsed, cost = cost,
        startBatteryLevel = startBatteryLevel, endBatteryLevel = endBatteryLevel,
        outsideTempAvg = null, odometer = 0.0
    )

    private fun aggregate(chargeId: Int, isFastCharger: Boolean, maxChargerPower: Int? = null) = ChargeDetailAggregate(
        chargeId = chargeId, carId = 1, schemaVersion = 1, computedAt = 0L,
        isFastCharger = isFastCharger, fastChargerBrand = null, connectorType = null,
        maxChargerPower = maxChargerPower, maxChargerVoltage = null, maxChargerCurrent = null, chargerPhases = null,
        maxOutsideTemp = null, minOutsideTemp = null, chargePointCount = 0
    )

    // ---- Weighted charging efficiency ----

    @Test fun `weighted efficiency is SUM battery over SUM grid, not an average of per-session ratios`() {
        // Session A: 10 kWh battery / 12 kWh grid = 83.3%. Session B: 40 kWh battery / 44 kWh grid = 90.9%.
        // A naive average would be ~87.1%; the weighted ratio is 50/56 * 100 = ~89.29%.
        val sessions = listOf(
            charge(1, "2026-08-01T09:00:00-04:00", energyAdded = 10.0, energyUsed = 12.0),
            charge(2, "2026-08-01T10:00:00-04:00", energyAdded = 40.0, energyUsed = 44.0)
        )
        val headline = computeChargingHeadline(sessions, emptyMap())
        assertEquals(89.29, headline.weightedEfficiencyPercent!!, 0.01)
        assertEquals(2, headline.coherentSessionCount)
        assertEquals(50.0, headline.batteryEnergyKwh, 0.001)
    }

    // ---- Invalid/unavailable energy handling ----

    @Test fun `sessions with missing or incoherent grid energy are excluded from efficiency but still counted as battery energy`() {
        val sessions = listOf(
            charge(1, "2026-08-01T09:00:00-04:00", energyAdded = 10.0, energyUsed = 12.0), // coherent
            charge(2, "2026-08-01T10:00:00-04:00", energyAdded = 15.0, energyUsed = null), // no grid energy
            charge(3, "2026-08-01T11:00:00-04:00", energyAdded = 20.0, energyUsed = 5.0)   // incoherent: used < added
        )
        val headline = computeChargingHeadline(sessions, emptyMap())
        assertEquals(1, headline.coherentSessionCount)
        assertEquals(45.0, headline.batteryEnergyKwh, 0.001) // all three still count toward the honest total
        assertEquals(12.0, headline.gridEnergyKwh!!, 0.001)  // only the coherent session's grid energy
    }

    @Test fun `headline efficiency is null when no session has coherent grid energy`() {
        val sessions = listOf(charge(1, "2026-08-01T09:00:00-04:00", energyAdded = 10.0, energyUsed = null))
        val headline = computeChargingHeadline(sessions, emptyMap())
        assertNull(headline.weightedEfficiencyPercent)
        assertNull(headline.gridEnergyKwh)
        assertEquals(1, headline.sessionCount)
    }

    // ---- Period filtering ----

    @Test fun `period filtering includes the full inclusive window and excludes sessions outside it`() {
        val today = LocalDate.of(2026, 8, 10)
        val sessions = listOf(
            charge(1, "2026-08-10T08:00:00-04:00", energyAdded = 5.0), // today, in-window
            charge(2, "2026-07-12T08:00:00-04:00", energyAdded = 5.0), // 29 days back, in-window (30-day inclusive window)
            charge(3, "2026-07-11T08:00:00-04:00", energyAdded = 5.0)  // 30 days back, just outside the window
        )
        val filtered = filterChargesByPeriod(sessions, ChargingPeriod.Last30Days, today)
        assertEquals(setOf(1, 2), filtered.map { it.chargeId }.toSet())
    }

    @Test fun `AllTime period filtering is a no-op`() {
        val today = LocalDate.of(2026, 8, 10)
        val sessions = listOf(charge(1, "2020-01-01T08:00:00-04:00", energyAdded = 5.0))
        assertEquals(1, filterChargesByPeriod(sessions, ChargingPeriod.AllTime, today).size)
    }

    // ---- Place aggregation ----

    @Test fun `place breakdown groups by Home Work Custom Other using weighted math, not duplicated cost logic`() {
        val home = SmartPlace(id = 1, name = "Home", type = SmartPlaceType.HOME, latitude = 10.0, longitude = 10.0, radiusMeters = 100, createdAt = 0, updatedAt = 0)
        val places = listOf(home)
        val rules = listOf(
            ChargingRateRule(id = 1, scope = ChargingRateScope.PLACE, smartPlaceId = 1, name = "Home rate", priceMicrosPerKwh = 150_000, currencyCode = "USD", effectiveFrom = 0, createdAt = 0, updatedAt = 0)
        )
        val sessions = listOf(
            charge(1, "2026-08-01T09:00:00-04:00", energyAdded = 10.0, latitude = 10.0, longitude = 10.0), // at Home
            charge(2, "2026-08-02T09:00:00-04:00", energyAdded = 20.0, latitude = 50.0, longitude = 50.0)  // elsewhere -> Other
        )
        val breakdown = computePlaceBreakdown(sessions, rules, places, emptyList())
        val homeRow = breakdown.first { it.category == "Home" }
        val otherRow = breakdown.first { it.category == "Other" }
        assertEquals(1, homeRow.sessionCount)
        assertEquals(10.0, homeRow.batteryEnergyKwh, 0.001)
        assertEquals(150L, homeRow.costMinorUnits) // 10 kWh * $0.15/kWh = $1.50 = 150 minor units
        assertEquals(1, otherRow.sessionCount)
        assertNull(otherRow.costMinorUnits) // no default/place rate applies away from Home
    }

    // ---- AC/DC aggregation ----

    @Test fun `AC DC breakdown only classifies sessions with a known aggregate and computes weighted efficiency per group`() {
        val aggregates = mapOf(
            1 to aggregate(1, isFastCharger = false, maxChargerPower = 11),
            2 to aggregate(2, isFastCharger = true, maxChargerPower = 150)
            // charge 3 has no aggregate -> unclassified
        )
        val sessions = listOf(
            charge(1, "2026-08-01T09:00:00-04:00", energyAdded = 10.0, energyUsed = 12.0), // AC
            charge(2, "2026-08-02T09:00:00-04:00", energyAdded = 40.0, energyUsed = 44.0), // DC
            charge(3, "2026-08-03T09:00:00-04:00", energyAdded = 5.0, energyUsed = 6.0)    // unclassified
        )
        assertEquals(2, acDcClassifiedCount(sessions, aggregates))
        val breakdown = computeAcDcBreakdown(sessions, aggregates)
        val ac = breakdown.first { !it.isFastCharger }
        val dc = breakdown.first { it.isFastCharger }
        assertEquals(1, ac.sessionCount)
        assertEquals(83.33, ac.weightedEfficiencyPercent!!, 0.01)
        assertEquals(11, ac.medianPeakPowerKw)
        assertEquals(1, dc.sessionCount)
        assertEquals(150, dc.medianPeakPowerKw)
    }

    // ---- Effective $/kWh ----

    @Test fun `effective cost per kWh is weighted total cost over total cost-basis energy`() {
        val places = emptyList<SmartPlace>()
        val rules = listOf(
            ChargingRateRule(id = 1, scope = ChargingRateScope.DEFAULT, smartPlaceId = null, name = "Default", priceMicrosPerKwh = 200_000, currencyCode = "USD", effectiveFrom = 0, createdAt = 0, updatedAt = 0)
        )
        val sessions = listOf(
            charge(1, "2026-08-01T09:00:00-04:00", energyAdded = 10.0), // $0.20/kWh * 10 = $2.00
            charge(2, "2026-08-02T09:00:00-04:00", energyAdded = 30.0)  // $0.20/kWh * 30 = $6.00
        )
        val summary = computeChargingCostSummary(sessions, rules, places, emptyList())
        assertEquals(800L, summary.totalCostMinorUnits) // $8.00 total
        assertEquals(0, summary.effectiveCostPerKwh!!.compareTo(java.math.BigDecimal("0.2000")))
        assertEquals(2, summary.pricedSessions)
        assertEquals(0, summary.unavailableSessions)
    }

    @Test fun `free sessions contribute zero cost but count as priced`() {
        val places = emptyList<SmartPlace>()
        val rules = listOf(
            ChargingRateRule(id = 1, scope = ChargingRateScope.DEFAULT, smartPlaceId = null, name = "Free", priceMicrosPerKwh = 0, currencyCode = "USD", freeCharging = true, effectiveFrom = 0, createdAt = 0, updatedAt = 0)
        )
        val sessions = listOf(charge(1, "2026-08-01T09:00:00-04:00", energyAdded = 10.0))
        val summary = computeChargingCostSummary(sessions, rules, places, emptyList())
        assertEquals(0L, summary.totalCostMinorUnits)
        assertEquals(1, summary.freeSessions)
        assertEquals(1, summary.pricedSessions)
    }

    @Test fun `manual override wins over automatic rate resolution`() {
        val places = emptyList<SmartPlace>()
        val rules = listOf(
            ChargingRateRule(id = 1, scope = ChargingRateScope.DEFAULT, smartPlaceId = null, name = "Default", priceMicrosPerKwh = 200_000, currencyCode = "USD", effectiveFrom = 0, createdAt = 0, updatedAt = 0)
        )
        val overrides = listOf(ChargeCostOverride(chargeId = 1, mode = "COST", costMinorUnits = 999, currencyCode = "USD", updatedAt = 0))
        val sessions = listOf(charge(1, "2026-08-01T09:00:00-04:00", energyAdded = 10.0))
        val summary = computeChargingCostSummary(sessions, rules, places, overrides)
        assertEquals(999L, summary.totalCostMinorUnits)
    }

    // ---- Charge curve / taper ----

    @Test fun `charge curve bands average power within each 10 percent SOC band and detect the observed taper`() {
        val samples = listOf(
            ChargeCurveSample(20, 100.0),
            ChargeCurveSample(25, 120.0), // peak in 20-30 band
            ChargeCurveSample(35, 60.0),  // taper: falls below 90% of 120 (108)
            ChargeCurveSample(85, 20.0)
        )
        val result = computeChargeCurve(samples)!!
        assertEquals(120.0, result.taper!!.peakPowerKw, 0.001)
        assertEquals(25, result.taper.peakSoc)
        assertEquals(35, result.taper.taperStartSoc)
        assertEquals(110.0, result.bands[2]!!.avgPowerKw, 0.001) // 20-30% band = avg(100, 120)
    }

    @Test fun `charge curve is unavailable when there are no valid samples`() {
        assertTrue(computeChargeCurve(emptyList()) == null)
    }
}
