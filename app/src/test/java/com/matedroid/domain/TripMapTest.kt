package com.matedroid.domain

import com.matedroid.data.local.entity.ChargeSummary
import com.matedroid.data.local.entity.DriveSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripMapTest {

    private fun drive(
        id: Int,
        start: String,
        distance: Double = 10.0,
        durationMin: Int = 20,
        energyConsumed: Double? = 2.0
    ) = DriveSummary(
        driveId = id, carId = 1, startDate = start, endDate = start, durationMin = durationMin,
        startAddress = "Home", endAddress = "Work", distance = distance, speedMax = 60, speedAvg = 40,
        powerMax = 100, powerMin = -20, startBatteryLevel = 80, endBatteryLevel = 70,
        outsideTempAvg = null, insideTempAvg = null, energyConsumed = energyConsumed, efficiency = null
    )

    private fun charge(id: Int, start: String, durationMin: Int = 30, energyAdded: Double = 10.0) = ChargeSummary(
        chargeId = id, carId = 1, startDate = start, endDate = start, durationMin = durationMin,
        address = "Work", latitude = 0.0, longitude = 0.0, energyAdded = energyAdded, energyUsed = null,
        cost = null, startBatteryLevel = 40, endBatteryLevel = 70, outsideTempAvg = null, odometer = 0.0
    )

    private fun cost(minorUnits: Long?, currency: String = "USD", free: Boolean = false) =
        ChargeCostPresentation(costMinorUnits = minorUnits, currencyCode = currency, isFree = free)

    // ---- Chronological merge ----

    @Test fun `merges drives and charges into a single ascending timeline`() {
        val d1 = drive(1, "2026-08-08T09:00:00-04:00")
        val c1 = charge(1, "2026-08-08T09:40:00-04:00")
        val d2 = drive(2, "2026-08-08T10:30:00-04:00")

        val merged = mergeChronological(listOf(d2, d1), listOf(c1))

        assertEquals(listOf(TripEntry.Drive(d1), TripEntry.Charge(c1), TripEntry.Drive(d2)), merged)
    }

    @Test fun `merge orders entries straddling local midnight correctly`() {
        // Same offset throughout, as every record from one TeslaMate deployment actually is —
        // this guards against an off-by-one at the day boundary rather than a mid-string bug.
        val lateNight = charge(1, "2026-08-10T23:50:00-04:00")
        val justAfterMidnight = drive(1, "2026-08-11T00:05:00-04:00")

        val merged = mergeChronological(listOf(justAfterMidnight), listOf(lateNight))

        assertEquals(TripEntry.Charge(lateNight), merged.first())
        assertEquals(TripEntry.Drive(justAfterMidnight), merged.last())
    }

    // ---- Daily totals ----

    @Test fun `totals sum distance and durations across all drives and charges`() {
        val drives = listOf(drive(1, "2026-08-08T09:00:00-04:00", distance = 18.4, durationMin = 31), drive(2, "2026-08-08T12:00:00-04:00", distance = 18.7, durationMin = 35))
        val charges = listOf(charge(1, "2026-08-08T10:00:00-04:00", durationMin = 42, energyAdded = 14.2))

        val totals = computeDayTotals(drives, charges, chargeCosts = mapOf(1 to cost(0, free = true)))

        assertEquals(2, totals.driveCount)
        assertEquals(1, totals.chargeCount)
        assertEquals(37.1, totals.totalDistanceKm, 0.0001)
        assertEquals(66, totals.driveDurationMin)
        assertEquals(42, totals.chargeDurationMin)
        assertEquals(14.2, totals.chargeEnergyKwh, 0.0001)
    }

    @Test fun `driving energy and efficiency stay unavailable rather than zero when no drive has valid energy`() {
        val drives = listOf(drive(1, "2026-08-08T09:00:00-04:00", distance = 10.0, energyConsumed = null))
        val totals = computeDayTotals(drives, emptyList(), emptyMap())

        assertNull(totals.driveEnergyKwh)
        assertNull(totals.weightedEfficiencyWhKm)
    }

    @Test fun `weighted efficiency is SUM valid energy over SUM valid distance, excluding drives without energy`() {
        val drives = listOf(
            drive(1, "2026-08-08T09:00:00-04:00", distance = 10.0, energyConsumed = 2.0), // 200 Wh/km on its own
            drive(2, "2026-08-08T10:00:00-04:00", distance = 100.0, energyConsumed = null) // no energy — must not dilute the ratio
        )
        val totals = computeDayTotals(drives, emptyList(), emptyMap())

        // Only the first drive contributes to both numerator and denominator.
        assertEquals(2.0, totals.driveEnergyKwh!!, 0.0001)
        assertEquals(200.0, totals.weightedEfficiencyWhKm!!, 0.0001)
    }

    @Test fun `no charges yields zero charging energy and unavailable cost, not an error`() {
        val totals = computeDayTotals(emptyList(), emptyList(), emptyMap())
        assertEquals(0.0, totals.chargeEnergyKwh, 0.0001)
        assertNull(totals.chargeCostMinorUnits)
        assertNull(totals.chargeCostCurrency)
        assertTrue(!totals.chargeCostMixedCurrencies)
    }

    // ---- Charging cost total ----

    @Test fun `charging cost sums priced and free sessions in a single currency`() {
        val charges = listOf(charge(1, "2026-08-08T09:00:00-04:00"), charge(2, "2026-08-08T11:00:00-04:00"))
        val costs = mapOf(1 to cost(1500), 2 to cost(0, free = true))

        val totals = computeDayTotals(emptyList(), charges, costs)

        assertEquals(1500L, totals.chargeCostMinorUnits)
        assertEquals("USD", totals.chargeCostCurrency)
        assertTrue(!totals.chargeCostMixedCurrencies)
    }

    @Test fun `mixed currency charging cost has no single total`() {
        val charges = listOf(charge(1, "2026-08-08T09:00:00-04:00"), charge(2, "2026-08-08T11:00:00-04:00"))
        val costs = mapOf(1 to cost(1500, currency = "USD"), 2 to cost(1200, currency = "EUR"))

        val totals = computeDayTotals(emptyList(), charges, costs)

        assertNull(totals.chargeCostMinorUnits)
        assertNull(totals.chargeCostCurrency)
        assertTrue(totals.chargeCostMixedCurrencies)
    }

    @Test fun `unavailable cost for every session leaves the total unavailable rather than zero`() {
        val charges = listOf(charge(1, "2026-08-08T09:00:00-04:00"))
        val costs = mapOf(1 to cost(null))

        val totals = computeDayTotals(emptyList(), charges, costs)

        assertNull(totals.chargeCostMinorUnits)
    }
}
