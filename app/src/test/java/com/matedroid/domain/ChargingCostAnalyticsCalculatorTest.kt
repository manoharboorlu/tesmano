package com.matedroid.domain

import com.matedroid.data.local.entity.ChargeCostOverride
import com.matedroid.data.local.entity.ChargeCostOverrideMode
import com.matedroid.data.local.entity.ChargeSummary
import com.matedroid.data.local.entity.ChargingRateRule
import com.matedroid.data.local.entity.ChargingRateScope
import com.matedroid.data.local.entity.SmartPlace
import com.matedroid.data.local.entity.SmartPlaceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class ChargingCostAnalyticsCalculatorTest {
    private val zone = ZoneId.of("America/New_York")
    private val now = Instant.parse("2026-08-20T12:00:00Z")
    private fun charge(id: Int, date: String, added: Double = 10.0, used: Double? = 12.0, lat: Double = 40.0) = ChargeSummary(
        id, 1, date, date, 30, "Known address", lat, -74.0, added, used, null, 50, 70, null, 0.0
    )
    private fun rate(micros: Long = 200_000, from: Long = 0, free: Boolean = false, currency: String = "USD") = ChargingRateRule(
        scope = ChargingRateScope.DEFAULT, name = "Default", priceMicrosPerKwh = micros, currencyCode = currency,
        freeCharging = free, effectiveFrom = from, createdAt = 0, updatedAt = 0
    )
    private fun snapshot(
        charges: List<ChargeSummary>, rules: List<ChargingRateRule> = listOf(rate()), overrides: List<ChargeCostOverride> = emptyList(),
        period: ChargingAnalyticsPeriod = ChargingAnalyticsPeriod.CurrentMonth, distance: Double = 100.0
    ) = ChargingCostAnalyticsCalculator.calculate(charges, rules, emptyList(), overrides, period, distance, now, zone)

    @Test fun `current month reports exact totals coverage weighted rate and spend per mile`() {
        val result = snapshot(listOf(charge(1, "2026-08-02T12:00:00Z"), charge(2, "2026-08-12T12:00:00Z", 5.0, 5.0), charge(3, "2026-08-14T12:00:00Z", -1.0, null)))
        assertEquals(340L, result.totalCostMinorUnits)
        assertEquals(2, result.pricedSessions)
        assertEquals(1, result.unavailableSessions)
        assertEquals("0.2000", result.weightedCostPerKwh.toString())
        assertEquals("0.0547", result.spendPerMile.toString())
    }

    @Test fun `year and lifetime preserve effective dated prices`() {
        val january = charge(1, "2026-01-10T12:00:00Z")
        val august = charge(2, "2026-08-10T12:00:00Z")
        val changed = Instant.parse("2026-07-01T00:00:00Z").toEpochMilli()
        val rules = listOf(rate(220_000), rate(260_000, changed))
        val year = snapshot(listOf(january, august), rules, period = ChargingAnalyticsPeriod.CurrentYear)
        assertEquals(576L, year.totalCostMinorUnits)
        assertEquals(2, year.trend.size)
        assertEquals(576L, snapshot(listOf(january, august), rules, period = ChargingAnalyticsPeriod.AllTime).totalCostMinorUnits)
    }

    @Test fun `location breakdown, manual cost and manual free retain precedence`() {
        val home = SmartPlace(1, "Home", SmartPlaceType.HOME, 40.0, -74.0, 500, true, 0, 0)
        val manual = ChargeCostOverride(2, ChargeCostOverrideMode.COST, 777, "USD", 0)
        val free = ChargeCostOverride(3, ChargeCostOverrideMode.FREE, null, "USD", 0)
        val result = ChargingCostAnalyticsCalculator.calculate(
            listOf(charge(1, "2026-08-02T12:00:00Z"), charge(2, "2026-08-03T12:00:00Z"), charge(3, "2026-08-04T12:00:00Z", used = null)),
            listOf(rate()), listOf(home), listOf(manual, free), ChargingAnalyticsPeriod.CurrentMonth, 0.0, now, zone
        )
        assertEquals(1017L, result.totalCostMinorUnits)
        assertEquals(1, result.freeSessions)
        assertEquals(2, result.manualSessions)
        assertEquals("Home", result.breakdown.single().category)
    }

    @Test fun `automatic free includes known energy but never requires it`() {
        val result = snapshot(listOf(charge(1, "2026-08-02T12:00:00Z"), charge(2, "2026-08-03T12:00:00Z", -1.0, null)), listOf(rate(free = true)))
        assertEquals(0L, result.totalCostMinorUnits)
        assertEquals(2, result.freeSessions)
        assertEquals(0, result.costBasisKwh.compareTo(java.math.BigDecimal("12.0")))
        assertEquals("0.0000", result.weightedCostPerKwh.toString())
    }

    @Test fun `mixed currencies are never summed and missing drive distance has no spend per mile`() {
        val usd = rate(200_000, 0, currency = "USD")
        val cad = rate(200_000, Instant.parse("2026-08-10T00:00:00Z").toEpochMilli(), currency = "CAD")
        val result = snapshot(listOf(charge(1, "2026-08-02T12:00:00Z"), charge(2, "2026-08-12T12:00:00Z")), listOf(usd, cad), distance = 0.0)
        assertTrue(result.mixedCurrencies)
        assertNull(result.totalCostMinorUnits)
        assertNull(result.spendPerMile)
    }

    @Test fun `rate edit updates derived result while manual override stays authoritative`() {
        val session = charge(1, "2026-08-15T12:00:00Z")
        assertEquals(240L, snapshot(listOf(session), listOf(rate(200_000))).totalCostMinorUnits)
        assertEquals(312L, snapshot(listOf(session), listOf(rate(260_000))).totalCostMinorUnits)
        val override = ChargeCostOverride(1, ChargeCostOverrideMode.COST, 999, "USD", 0)
        assertEquals(999L, snapshot(listOf(session), listOf(rate(260_000)), listOf(override)).totalCostMinorUnits)
    }
}
