package com.matedroid.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matedroid.data.api.models.Units
import com.matedroid.data.local.dao.ChargeSummaryDao
import com.matedroid.data.local.dao.DriveSummaryDao
import com.matedroid.data.local.entity.ChargeSummary
import com.matedroid.data.local.entity.DriveEndpointCache
import com.matedroid.data.local.entity.DriveSummary
import com.matedroid.data.repository.ApiResult
import com.matedroid.data.repository.TeslamateRepository
import com.matedroid.domain.BaselineComparison
import com.matedroid.domain.BatteryAnalyticsCalculator
import com.matedroid.domain.BatteryConfidence
import com.matedroid.domain.ChargeCostPresentation
import com.matedroid.domain.ChargingCostRepository
import com.matedroid.domain.EfficiencyPeriod
import com.matedroid.domain.EfficiencyResult
import com.matedroid.domain.EfficiencySummary
import com.matedroid.domain.RealWorldRangeCalculator
import com.matedroid.domain.SmartPlacesRepository
import com.matedroid.domain.TodaySummary
import com.matedroid.domain.chargeLocalDate
import com.matedroid.domain.compareToBaseline
import com.matedroid.domain.computeEfficiencySummary
import com.matedroid.domain.computeTodaySummary
import com.matedroid.domain.filterByPeriod
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeCockpitUiState(
    val isLoading: Boolean = true,
    val units: Units? = null,
    val today: TodaySummary? = null,
    val todayChargeCostMinorUnits: Long? = null,
    val todayChargeCostCurrency: String? = null,
    val todayChargeIsFree: Boolean = false,
    val efficiencyPeriodSummary: EfficiencySummary? = null,
    val efficiencyComparison: BaselineComparison? = null,
    val capacityKwh: Double? = null,
    val capacityConfidence: BatteryConfidence? = null,
    val capacityChangePercent: Double? = null,
    val rangeEfficiency: EfficiencyResult? = null,
    val lastDrive: DriveSummary? = null,
    val lastDriveEndpoint: DriveEndpointCache? = null,
    val lastCharge: ChargeSummary? = null,
    val lastChargeCost: ChargeCostPresentation? = null
)

/**
 * Local-only aggregation for the unfolded Home cockpit. Reuses the same calculators as their full
 * screens (Battery Lab, Real-World Range, Driving Efficiency Lab, Charging cost engine) against
 * already-synced summaries — never fetches a Drive/Charge Detail and never re-derives their math.
 */
@HiltViewModel
class HomeCockpitViewModel @Inject constructor(
    private val driveSummaryDao: DriveSummaryDao,
    private val chargeSummaryDao: ChargeSummaryDao,
    private val chargingCostRepository: ChargingCostRepository,
    private val smartPlacesRepository: SmartPlacesRepository,
    private val teslamateRepository: TeslamateRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeCockpitUiState())
    val uiState: StateFlow<HomeCockpitUiState> = _uiState.asStateFlow()

    private var carId: Int = 0

    fun start(carId: Int) {
        if (this.carId == carId && !_uiState.value.isLoading) return
        this.carId = carId
        viewModelScope.launch {
            when (val result = teslamateRepository.getCarStatus(carId)) {
                is ApiResult.Success -> _uiState.update { it.copy(units = result.data.units) }
                is ApiResult.Error -> Unit
            }

            val allDrives = driveSummaryDao.getAllChronological(carId)
            val recentDrivesDesc = driveSummaryDao.getRecentPageForCar(carId, 500, 0)
            val allCharges = chargeSummaryDao.getAllForCar(carId)
            val today = LocalDate.now()

            val todaySummary = computeTodaySummary(allDrives, allCharges, today)
            val todaysCharges = allCharges.filter { chargeLocalDate(it) == today }
            val todayCosts = todaysCharges.map { chargingCostRepository.costForCharge(it) }
            val (todayCostMinor, todayCostCurrency, todayFree) = summarizeTodayCost(todayCosts)

            val periodSummary = computeEfficiencySummary(filterByPeriod(allDrives, EfficiencyPeriod.Last30Days, today))
            val baselineSummary = computeEfficiencySummary(allDrives)

            val capacity = BatteryAnalyticsCalculator.estimate(allCharges)

            val rangeWindow = RealWorldRangeCalculator.defaultWindow(recentDrivesDesc)
            val rangeEfficiency = RealWorldRangeCalculator.efficiency(recentDrivesDesc, rangeWindow)

            val lastDrive = allDrives.lastOrNull()
            val lastDriveEndpoint = lastDrive?.let { smartPlacesRepository.endpointForDrive(carId, it.driveId) }

            val lastCharge = allCharges.lastOrNull()
            val lastChargeCost = lastCharge?.let { chargingCostRepository.costForCharge(it) }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    today = todaySummary,
                    todayChargeCostMinorUnits = todayCostMinor,
                    todayChargeCostCurrency = todayCostCurrency,
                    todayChargeIsFree = todayFree,
                    efficiencyPeriodSummary = periodSummary,
                    efficiencyComparison = compareToBaseline(periodSummary, baselineSummary),
                    capacityKwh = capacity.kwh,
                    capacityConfidence = capacity.confidence,
                    capacityChangePercent = capacity.changeFromBaselinePercent,
                    rangeEfficiency = rangeEfficiency,
                    lastDrive = lastDrive,
                    lastDriveEndpoint = lastDriveEndpoint,
                    lastCharge = lastCharge,
                    lastChargeCost = lastChargeCost
                )
            }
        }
    }

    private fun summarizeTodayCost(costs: List<ChargeCostPresentation>): Triple<Long?, String?, Boolean> {
        if (costs.isEmpty()) return Triple(null, null, false)
        if (costs.all { it.isFree }) return Triple(0L, costs.first().currencyCode, true)
        val currencies = costs.map { it.currencyCode }.toSet()
        val priced = costs.filter { it.costMinorUnits != null }
        if (priced.isEmpty() || currencies.size != 1) return Triple(null, null, false)
        return Triple(priced.sumOf { it.costMinorUnits!! }, currencies.first(), false)
    }
}
