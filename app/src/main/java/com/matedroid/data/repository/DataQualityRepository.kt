package com.matedroid.data.repository

import com.matedroid.data.local.dao.AggregateDao
import com.matedroid.data.local.dao.ChargeSummaryDao
import com.matedroid.data.local.dao.DriveSummaryDao
import com.matedroid.data.local.dao.SmartPlacesDao
import com.matedroid.data.local.entity.SchemaVersion
import com.matedroid.domain.BatteryAnalyticsCalculator
import com.matedroid.domain.BatteryDataQuality
import com.matedroid.domain.ChargingAnalyticsPeriod
import com.matedroid.domain.ChargingCostAnalyticsCalculator
import com.matedroid.domain.ChargingCostRepository
import com.matedroid.domain.ChargingDataQuality
import com.matedroid.domain.DataQualitySummary
import com.matedroid.domain.DriveDataQuality
import com.matedroid.domain.MetricCoverage
import com.matedroid.domain.RangeDataQuality
import com.matedroid.domain.RealWorldRangeCalculator
import com.matedroid.domain.RouteCoverage
import com.matedroid.domain.RouteTagsRepository
import com.matedroid.domain.quality
import com.matedroid.domain.unavailableReasonBreakdown
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Assembles the Data Quality Center snapshot purely from cached local summaries and
 * already-trustworthy domain calculators (Battery, Real-World Range, Recurring Routes,
 * Charging Cost). It never fetches a Drive/Charge detail and never invents an estimate.
 */
@Singleton
class DataQualityRepository @Inject constructor(
    private val driveSummaryDao: DriveSummaryDao,
    private val chargeSummaryDao: ChargeSummaryDao,
    private val aggregateDao: AggregateDao,
    private val placesDao: SmartPlacesDao,
    private val routeTagsRepository: RouteTagsRepository,
    private val chargingCostRepository: ChargingCostRepository,
    private val teslamateRepository: TeslamateRepository
) {
    suspend fun summary(carId: Int): DataQualitySummary {
        val totalDrives = driveSummaryDao.count(carId)
        val routesSnapshot = routeTagsRepository.recurringRoutes(carId)
        val coverage = RouteCoverage(routesSnapshot.coverage.knownEndpoints, routesSnapshot.coverage.totalDrives)
        val detailEnriched = aggregateDao.countDriveAggregatesWithSchema(carId, SchemaVersion.CURRENT)
        val drives = DriveDataQuality(
            totalDrives = totalDrives,
            endpointCoverage = MetricCoverage(coverage.knownEndpoints, coverage.totalDrives),
            detailEnrichedDrives = detailEnriched,
            quality = coverage.quality()
        )

        val chargeSummaries = chargeSummaryDao.getAllForCar(carId)
        val rules = chargingCostRepository.observeRules().first()
        val overrides = chargingCostRepository.observeOverrides().first()
        val places = placesDao.activePlaces()
        val distanceKm = driveSummaryDao.sumDistance(carId)
        val chargingSnapshot = ChargingCostAnalyticsCalculator.calculate(
            chargeSummaries, rules, places, overrides, ChargingAnalyticsPeriod.AllTime, distanceKm
        )
        val charging = ChargingDataQuality(
            totalSessions = chargingSnapshot.totalSessions,
            pricedOrFreeSessions = chargingSnapshot.pricedSessions,
            unavailableSessions = chargingSnapshot.unavailableSessions,
            freeSessions = chargingSnapshot.freeSessions,
            manualSessions = chargingSnapshot.manualSessions,
            mixedCurrencies = chargingSnapshot.mixedCurrencies,
            unavailableReasons = chargingSnapshot.unavailableReasonBreakdown(),
            quality = chargingSnapshot.quality()
        )

        val chargeAggregates = aggregateDao.getChargeAggregatesForCar(carId)
        val batterySnapshot = BatteryAnalyticsCalculator.snapshot(chargeSummaries, chargeAggregates)
        val battery = BatteryDataQuality(
            acceptedSamples = batterySnapshot.capacity.accepted.size,
            excludedSamples = batterySnapshot.capacity.exclusions.values.sum(),
            baselineAvailable = batterySnapshot.capacity.baselineKwh != null,
            ratedRangeHistoryAvailable = batterySnapshot.ratedRangeAvailable,
            chargingEfficiencyAvailable = batterySnapshot.ac.efficiency != null || batterySnapshot.dc.efficiency != null,
            quality = batterySnapshot.capacity.quality()
        )

        val recentDrives = driveSummaryDao.getRecentPageForCar(carId, 500, 0)
        val window = RealWorldRangeCalculator.defaultWindow(recentDrives)
        val efficiency = RealWorldRangeCalculator.efficiency(recentDrives, window)
        val status = (teslamateRepository.getCarStatus(carId) as? ApiResult.Success)?.data?.status
        val capacityAvailable = batterySnapshot.capacity.kwh != null
        val realWorldRange = RealWorldRangeCalculator.range(
            batterySnapshot.capacity.kwh, status?.batteryLevel, efficiency, batterySnapshot.capacity.confidence
        )
        val range = RangeDataQuality(
            capacityConfidence = batterySnapshot.capacity.confidence,
            efficiencyConfidence = efficiency.confidence,
            windowLabel = window.label,
            quality = realWorldRange.quality(efficiency, capacityAvailable)
        )

        return DataQualitySummary(carId, drives, charging, battery, range)
    }
}
