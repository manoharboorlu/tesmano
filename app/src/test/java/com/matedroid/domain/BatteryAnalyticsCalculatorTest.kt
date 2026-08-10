package com.matedroid.domain

import com.matedroid.data.local.entity.ChargeDetailAggregate
import com.matedroid.data.local.entity.ChargeSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BatteryAnalyticsCalculatorTest {
    private fun charge(id: Int, start: Int, end: Int, added: Double, used: Double? = null, date: String = "2026-08-01T12:00:00Z") = ChargeSummary(id, 1, date, date, 30, "", 0.0, 0.0, added, used, null, start, end, null, 0.0)
    private fun aggregate(id: Int, dc: Boolean) = ChargeDetailAggregate(
        chargeId = id, carId = 1, schemaVersion = 1, computedAt = 0, isFastCharger = dc,
        fastChargerBrand = null, connectorType = null, maxChargerPower = null, maxChargerVoltage = null,
        maxChargerCurrent = null, chargerPhases = null, maxOutsideTemp = null, minOutsideTemp = null, chargePointCount = 0
    )

    @Test fun `capacity uses battery added over meaningful soc span and robust median`() {
        val estimate = BatteryAnalyticsCalculator.estimate(listOf(charge(1, 20, 40, 12.0), charge(2, 30, 50, 12.2), charge(3, 40, 60, 11.8)))
        assertEquals(60.0, estimate.kwh!!, .01)
        assertEquals(3, estimate.accepted.size)
    }
    @Test fun `invalid small missing and implausible samples are excluded`() {
        val estimate = BatteryAnalyticsCalculator.estimate(listOf(charge(1, 20, 25, 4.0), charge(2, 20, 40, 0.0), charge(3, 20, 40, 50.0)))
        assertNull(estimate.kwh)
        assertEquals(1, estimate.exclusions[CapacityExclusion.SMALL_SOC_SPAN])
        assertEquals(1, estimate.exclusions[CapacityExclusion.MISSING_ENERGY])
        assertEquals(1, estimate.exclusions[CapacityExclusion.IMPLAUSIBLE])
    }
    @Test fun `outlier does not distort capacity or baseline`() {
        val list = listOf(charge(1, 20, 40, 12.0, date = "2026-01-01T00:00:00Z"), charge(2, 20, 40, 12.2, date = "2026-02-01T00:00:00Z"), charge(3, 20, 40, 11.8, date = "2026-03-01T00:00:00Z"), charge(4, 20, 40, 20.0, date = "2026-04-01T00:00:00Z"))
        val result = BatteryAnalyticsCalculator.estimate(list)
        assertEquals(60.0, result.kwh!!, .01); assertEquals(3, result.accepted.size); assertEquals(60.0, result.baselineKwh!!, .01)
    }
    @Test fun `weighted efficiency and ac dc separation use coherent grid energy`() {
        val result = BatteryAnalyticsCalculator.snapshot(listOf(charge(1, 20, 40, 10.0, 12.0), charge(2, 20, 40, 20.0, 25.0), charge(3, 20, 40, 10.0, 5.0)), listOf(aggregate(1, false), aggregate(2, true), aggregate(3, true)))
        assertEquals(10.0 / 12.0, result.ac.efficiency!!, .001); assertEquals(20.0 / 25.0, result.dc.efficiency!!, .001)
    }
}
