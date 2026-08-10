package com.matedroid.ui.screens.charging

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matedroid.data.api.models.Units
import com.matedroid.data.local.SettingsDataStore
import com.matedroid.data.local.dao.AggregateDao
import com.matedroid.data.local.dao.ChargeSummaryDao
import com.matedroid.data.local.dao.ChargingCostDao
import com.matedroid.data.local.dao.SmartPlacesDao
import com.matedroid.data.local.entity.ChargeCurveAggregate
import com.matedroid.data.local.entity.ChargeDetailAggregate
import com.matedroid.data.local.entity.ChargeSummary
import com.matedroid.data.model.Currency
import com.matedroid.data.repository.ApiResult
import com.matedroid.data.repository.TeslamateRepository
import com.matedroid.domain.AcDcBreakdown
import com.matedroid.domain.ChargeCurveBand
import com.matedroid.domain.ChargeTaperInfo
import com.matedroid.domain.ChargingCostSummary
import com.matedroid.domain.ChargingHeadline
import com.matedroid.domain.ChargingPeriod
import com.matedroid.domain.ChargingLabTrendPoint
import com.matedroid.domain.PlaceChargingBreakdown
import com.matedroid.domain.RankedCharge
import com.matedroid.domain.SocBandResult
import com.matedroid.domain.acDcClassifiedCount
import com.matedroid.domain.chargingByStartSoc
import com.matedroid.domain.chargingTrend
import com.matedroid.domain.computeAcDcBreakdown
import com.matedroid.domain.computeChargingCostSummary
import com.matedroid.domain.computeChargingHeadline
import com.matedroid.domain.computePlaceBreakdown
import com.matedroid.domain.fastestChargingSessions
import com.matedroid.domain.filterChargesByPeriod
import com.matedroid.domain.largestEnergyAddedSessions
import com.matedroid.domain.longestChargingSessions
import com.matedroid.domain.mergeCurveBands
import com.matedroid.domain.mostEfficientChargingSessions
import com.matedroid.domain.toBands
import com.matedroid.domain.toTaper
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChargingLabUiState(
    val isLoading: Boolean = true,
    val period: ChargingPeriod = ChargingPeriod.Last30Days,
    val headline: ChargingHeadline? = null,
    val trend: List<ChargingLabTrendPoint> = emptyList(),
    val placeBreakdown: List<PlaceChargingBreakdown> = emptyList(),
    val acDcBreakdown: List<AcDcBreakdown> = emptyList(),
    val acDcClassifiedCount: Int = 0,
    val socBands: List<SocBandResult> = emptyList(),
    val fastestSessions: List<RankedCharge> = emptyList(),
    val mostEfficientSessions: List<RankedCharge> = emptyList(),
    val largestEnergySessions: List<RankedCharge> = emptyList(),
    val longestSessions: List<RankedCharge> = emptyList(),
    val curveCoverageCount: Int = 0,
    val curveBands: List<ChargeCurveBand?> = emptyList(),
    val taper: ChargeTaperInfo? = null,
    val costSummary: ChargingCostSummary? = null,
    val units: Units? = null,
    val currencySymbol: String = "$"
)

@HiltViewModel
class ChargingLabViewModel @Inject constructor(
    private val chargeSummaryDao: ChargeSummaryDao,
    private val aggregateDao: AggregateDao,
    private val smartPlacesDao: SmartPlacesDao,
    private val chargingCostDao: ChargingCostDao,
    private val settingsDataStore: SettingsDataStore,
    private val teslamateRepository: TeslamateRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChargingLabUiState())
    val uiState: StateFlow<ChargingLabUiState> = _uiState.asStateFlow()

    private var carId: Int = 0
    private var allSessions: List<ChargeSummary> = emptyList()
    private var aggregatesByChargeId: Map<Int, ChargeDetailAggregate> = emptyMap()
    private var curveAggregates: List<ChargeCurveAggregate> = emptyList()

    fun start(carId: Int) {
        if (this.carId == carId && allSessions.isNotEmpty()) return
        this.carId = carId
        viewModelScope.launch {
            when (val result = teslamateRepository.getCarStatus(carId)) {
                is ApiResult.Success -> _uiState.update { it.copy(units = result.data.units) }
                is ApiResult.Error -> Unit
            }
            val settings = settingsDataStore.settings.first()
            _uiState.update { it.copy(currencySymbol = Currency.findByCode(settings.currencyCode).symbol) }

            allSessions = chargeSummaryDao.getAllForCar(carId)
            aggregatesByChargeId = aggregateDao.getChargeAggregatesForCar(carId).associateBy { it.chargeId }
            curveAggregates = aggregateDao.getChargeCurveAggregatesForCar(carId)
            recompute()
        }
    }

    fun selectPeriod(period: ChargingPeriod) {
        _uiState.update { it.copy(period = period) }
        recompute()
    }

    private fun recompute() {
        viewModelScope.launch {
            val state = _uiState.value
            val periodSessions = filterChargesByPeriod(allSessions, state.period, LocalDate.now())
            val periodChargeIds = periodSessions.map { it.chargeId }.toSet()
            val periodCurves = curveAggregates.filter { it.chargeId in periodChargeIds }

            val rules = chargingCostDao.activeRules()
            val places = smartPlacesDao.activePlaces()
            val overrides = chargingCostDao.observeOverrides().first()

            val bestCurve = periodCurves.maxByOrNull { it.sampleCount }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    headline = computeChargingHeadline(periodSessions, aggregatesByChargeId),
                    trend = chargingTrend(periodSessions, state.period),
                    placeBreakdown = computePlaceBreakdown(periodSessions, rules, places, overrides),
                    acDcBreakdown = computeAcDcBreakdown(periodSessions, aggregatesByChargeId),
                    acDcClassifiedCount = acDcClassifiedCount(periodSessions, aggregatesByChargeId),
                    socBands = chargingByStartSoc(periodSessions),
                    fastestSessions = fastestChargingSessions(periodSessions),
                    mostEfficientSessions = mostEfficientChargingSessions(periodSessions),
                    largestEnergySessions = largestEnergyAddedSessions(periodSessions),
                    longestSessions = longestChargingSessions(periodSessions),
                    curveCoverageCount = periodCurves.size,
                    curveBands = mergeCurveBands(periodCurves.map { c -> c.toBands() }),
                    taper = bestCurve?.toTaper(),
                    costSummary = computeChargingCostSummary(periodSessions, rules, places, overrides)
                )
            }
        }
    }
}
