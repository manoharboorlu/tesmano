package com.matedroid.domain

import com.matedroid.data.local.entity.ChargeSummary
import com.matedroid.data.local.entity.ChargingRateRule
import com.matedroid.data.local.entity.ChargingRateScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class DataQualityTest {
    private val zone = ZoneId.of("America/New_York")
    private val now = Instant.parse("2026-08-20T12:00:00Z")

    private fun charge(id: Int, start: Int, end: Int, added: Double, used: Double? = null, date: String = "2026-08-01T12:00:00Z") =
        ChargeSummary(id, 1, date, date, 30, "", 0.0, 0.0, added, used, null, start, end, null, 0.0)

    private fun rate(micros: Long = 200_000, from: Long = 0, currency: String = "USD") = ChargingRateRule(
        scope = ChargingRateScope.DEFAULT, name = "Default", priceMicrosPerKwh = micros, currencyCode = currency,
        freeCharging = false, effectiveFrom = from, createdAt = 0, updatedAt = 0
    )

    // ---- MetricCoverage ----

    @Test fun `coverage percent is null for a zero denominator instead of dividing by zero`() {
        assertNull(MetricCoverage(0, 0).percent)
        assertEquals(60, MetricCoverage(6, 10).percent)
    }

    // ---- Battery capacity quality ----

    @Test fun `battery capacity quality is estimated and available with accepted-sample coverage`() {
        val estimate = BatteryAnalyticsCalculator.estimate(listOf(charge(1, 20, 40, 12.0), charge(2, 30, 50, 12.2), charge(3, 40, 60, 11.8)))
        val quality = estimate.quality()
        assertEquals(MetricSemantic.ESTIMATED, quality.semantic)
        assertEquals(MetricAvailability.AVAILABLE, quality.availability)
        assertEquals(MetricCoverage(3, 3), quality.coverage)
        assertNull(quality.reason)
    }

    @Test fun `battery capacity quality reports insufficient soc span as the dominant reason`() {
        val estimate = BatteryAnalyticsCalculator.estimate(listOf(charge(1, 20, 25, 4.0), charge(2, 20, 26, 4.0), charge(3, 20, 40, 0.0)))
        val quality = estimate.quality()
        assertEquals(MetricAvailability.UNAVAILABLE, quality.availability)
        assertEquals(QualityReason.INSUFFICIENT_SOC_SPAN, quality.reason)
        assertEquals(0, quality.coverage!!.numerator)
    }

    @Test fun `battery capacity quality reports missing energy as the dominant reason`() {
        val estimate = BatteryAnalyticsCalculator.estimate(listOf(charge(1, 20, 40, 0.0), charge(2, 20, 40, -1.0)))
        assertEquals(QualityReason.MISSING_ENERGY, estimate.quality().reason)
    }

    @Test fun `battery capacity quality with no charges stays unavailable not zero`() {
        val quality = BatteryAnalyticsCalculator.estimate(emptyList()).quality()
        assertEquals(MetricAvailability.UNAVAILABLE, quality.availability)
        assertEquals(QualityReason.NO_VALID_SAMPLES, quality.reason)
        assertEquals(MetricCoverage(0, 0), quality.coverage)
    }

    // ---- Real-World Range quality ----

    @Test fun `range quality composes confidence and is available when both battery and driving evidence exist`() {
        val efficiency = EfficiencyResult(EfficiencyWindow.Mi50, 250.0, 50.0, 12.5, 3, BatteryConfidence.HIGH)
        val range = RealWorldRangeCalculator.range(70.0, 60, efficiency, BatteryConfidence.HIGH)
        val quality = range.quality(efficiency, capacityAvailable = true)
        assertEquals(MetricSemantic.ESTIMATED, quality.semantic)
        assertEquals(MetricAvailability.AVAILABLE, quality.availability)
        assertEquals(BatteryConfidence.HIGH, quality.confidence)
        assertNull(quality.reason)
    }

    @Test fun `range quality reports missing capacity estimate when there is no usable-capacity estimate`() {
        val efficiency = EfficiencyResult(EfficiencyWindow.Mi50, 250.0, 50.0, 12.5, 3, BatteryConfidence.HIGH)
        val range = RealWorldRangeCalculator.range(null, 60, efficiency, null)
        val quality = range.quality(efficiency, capacityAvailable = false)
        assertEquals(MetricAvailability.UNAVAILABLE, quality.availability)
        assertEquals(QualityReason.MISSING_CAPACITY_ESTIMATE, quality.reason)
    }

    @Test fun `range quality reports insufficient drive coverage when the selected window lacks confidence`() {
        val efficiency = EfficiencyResult(EfficiencyWindow.Mi25, null, 2.0, 0.5, 1, null)
        val range = RealWorldRangeCalculator.range(70.0, 60, efficiency, BatteryConfidence.HIGH)
        val quality = range.quality(efficiency, capacityAvailable = true)
        assertEquals(MetricAvailability.UNAVAILABLE, quality.availability)
        assertEquals(QualityReason.INSUFFICIENT_DRIVE_COVERAGE, quality.reason)
    }

    // ---- Recurring route coverage ----

    @Test fun `route coverage state distinguishes no endpoints, partial coverage and full coverage without a pattern`() {
        assertEquals(RouteCoverageState.NO_ENDPOINTS, RouteCoverage(0, 101).state())
        assertEquals(RouteCoverageState.PARTIAL_COVERAGE, RouteCoverage(6, 101).state())
        assertEquals(RouteCoverageState.FULL_COVERAGE_NO_PATTERN, RouteCoverage(101, 101).state())
    }

    @Test fun `route coverage quality reports insufficient history when there is no local history at all`() {
        val quality = RouteCoverage(0, 0).quality()
        assertEquals(MetricAvailability.UNAVAILABLE, quality.availability)
        assertEquals(QualityReason.INSUFFICIENT_HISTORY, quality.reason)
    }

    @Test fun `route coverage quality is partial with a missing-endpoints reason for sparse coverage`() {
        val quality = RouteCoverage(6, 101).quality()
        assertEquals(MetricSemantic.DERIVED, quality.semantic)
        assertEquals(MetricAvailability.PARTIAL, quality.availability)
        assertEquals(QualityReason.MISSING_ENDPOINTS, quality.reason)
        assertEquals(MetricCoverage(6, 101), quality.coverage)
    }

    @Test fun `route coverage quality is available with no reason once coverage is complete`() {
        val quality = RouteCoverage(101, 101).quality()
        assertEquals(MetricAvailability.AVAILABLE, quality.availability)
        assertNull(quality.reason)
    }

    // ---- Charging cost coverage ----

    @Test fun `charging snapshot quality aggregates missing-energy reasons and reflects session coverage`() {
        val priced = charge(1, 20, 40, 10.0, 12.0, "2026-08-02T12:00:00Z")
        val missingEnergy = charge(2, 20, 40, -1.0, null, "2026-08-03T12:00:00Z")
        val snapshot = ChargingCostAnalyticsCalculator.calculate(
            listOf(priced, missingEnergy), listOf(rate()), emptyList(), emptyList(), ChargingAnalyticsPeriod.AllTime, 0.0, now, zone
        )
        assertEquals(1, snapshot.unavailableSessions)
        assertEquals(mapOf(QualityReason.MISSING_ENERGY to 1), snapshot.unavailableReasonBreakdown())
        val quality = snapshot.quality()
        assertEquals(MetricSemantic.DERIVED, quality.semantic)
        assertEquals(MetricAvailability.PARTIAL, quality.availability)
        assertEquals(QualityReason.MISSING_ENERGY, quality.reason)
        assertEquals(MetricCoverage(1, 2), quality.coverage)
    }

    @Test fun `charging snapshot quality aggregates missing-rate reasons when no rule applies`() {
        val missingRate = charge(1, 20, 40, 10.0, 12.0, "2026-08-04T12:00:00Z")
        val snapshot = ChargingCostAnalyticsCalculator.calculate(
            listOf(missingRate), emptyList(), emptyList(), emptyList(), ChargingAnalyticsPeriod.AllTime, 0.0, now, zone
        )
        assertEquals(mapOf(QualityReason.MISSING_RATE to 1), snapshot.unavailableReasonBreakdown())
        assertEquals(QualityReason.MISSING_RATE, snapshot.quality().reason)
    }

    @Test fun `charging snapshot quality with no sessions stays unavailable not zero`() {
        val quality = ChargingCostAnalyticsCalculator.calculate(
            emptyList(), listOf(rate()), emptyList(), emptyList(), ChargingAnalyticsPeriod.AllTime, 0.0, now, zone
        ).quality()
        assertEquals(MetricAvailability.UNAVAILABLE, quality.availability)
        assertEquals(QualityReason.INSUFFICIENT_HISTORY, quality.reason)
    }

    @Test fun `charging snapshot quality is partial with a mixed-currency reason even when every session is individually priced`() {
        val usd = rate(200_000, 0, currency = "USD")
        val cad = rate(200_000, Instant.parse("2026-08-10T00:00:00Z").toEpochMilli(), currency = "CAD")
        val snapshot = ChargingCostAnalyticsCalculator.calculate(
            listOf(charge(1, 20, 40, 10.0, 12.0, "2026-08-02T12:00:00Z"), charge(2, 20, 40, 10.0, 12.0, "2026-08-12T12:00:00Z")),
            listOf(usd, cad), emptyList(), emptyList(), ChargingAnalyticsPeriod.AllTime, 0.0, now, zone
        )
        assertTrue(snapshot.mixedCurrencies)
        assertEquals(0, snapshot.unavailableSessions)
        val quality = snapshot.quality()
        assertEquals(MetricAvailability.PARTIAL, quality.availability)
        assertEquals(QualityReason.MIXED_CURRENCY, quality.reason)
    }
}
