package com.matedroid.domain

import com.matedroid.data.local.entity.DriveSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RealWorldRangeCalculatorTest {
    private fun drive(id: Int, miles: Double, kwh: Double, date: String = "2026-08-09T12:00:00Z") = DriveSummary(id, 1, date, date, 10, "", "", miles, 0, 0, 0, 0, 50, 49, null, null, kwh, null)
    @Test fun `weighted efficiency and boundary drive are not averages`() {
        val e = RealWorldRangeCalculator.efficiency(listOf(drive(1, 30.0, 6.0), drive(2, 30.0, 12.0)), EfficiencyWindow.Mi50)
        assertEquals(60.0, e.miles, .01); assertEquals(300.0, e.whPerMile!!, .01)
    }
    @Test fun `short valid drive is included and invalid drives rejected`() {
        val e = RealWorldRangeCalculator.efficiency(listOf(drive(1, 1.0, .5), drive(2, 20.0, 0.0), drive(3, -1.0, 2.0)), EfficiencyWindow.Mi25)
        assertEquals(1.0, e.miles, .01); assertNull(e.confidence)
    }
    @Test fun `range uses capacity soc and wh units with reserve`() {
        val e = EfficiencyResult(EfficiencyWindow.Mi50, 250.0, 50.0, 12.5, 3, BatteryConfidence.HIGH)
        val r = RealWorldRangeCalculator.range(70.0, 60, e, BatteryConfidence.HIGH)
        assertEquals(168.0, r.toZeroMiles!!, .01); assertEquals(140.0, r.toTenMiles!!, .01)
    }
}
