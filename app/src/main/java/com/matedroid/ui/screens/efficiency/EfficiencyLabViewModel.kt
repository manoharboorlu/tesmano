package com.matedroid.ui.screens.efficiency

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matedroid.data.api.models.Units
import com.matedroid.data.local.dao.DriveSummaryDao
import com.matedroid.data.local.entity.DriveSummary
import com.matedroid.data.repository.ApiResult
import com.matedroid.data.repository.TeslamateRepository
import com.matedroid.domain.BandResult
import com.matedroid.domain.BaselineComparison
import com.matedroid.domain.EfficiencyPeriod
import com.matedroid.domain.EfficiencySummary
import com.matedroid.domain.RankedDrive
import com.matedroid.domain.SpeedBand
import com.matedroid.domain.TempBand
import com.matedroid.domain.TrendPoint
import com.matedroid.domain.TripLengthBand
import com.matedroid.domain.bestWorstDrives
import com.matedroid.domain.compareToBaseline
import com.matedroid.domain.computeEfficiencySummary
import com.matedroid.domain.efficiencyBySpeedBand
import com.matedroid.domain.efficiencyByOutsideTemp
import com.matedroid.domain.efficiencyByTripLength
import com.matedroid.domain.efficiencyTrend
import com.matedroid.domain.filterByPeriod
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EfficiencyLabUiState(
    val isLoading: Boolean = true,
    val period: EfficiencyPeriod = EfficiencyPeriod.Last30Days,
    val periodSummary: EfficiencySummary? = null,
    val baselineSummary: EfficiencySummary? = null,
    val baselineComparison: BaselineComparison? = null,
    val trend: List<TrendPoint> = emptyList(),
    val speedBands: List<BandResult<SpeedBand>> = emptyList(),
    val tripLengthBands: List<BandResult<TripLengthBand>> = emptyList(),
    val tempBands: List<BandResult<TempBand>> = emptyList(),
    val tempDataAvailable: Boolean = false,
    val bestDrives: List<RankedDrive> = emptyList(),
    val worstDrives: List<RankedDrive> = emptyList(),
    val minDistanceFilter: Double = 10.0,
    val units: Units? = null
)

@HiltViewModel
class EfficiencyLabViewModel @Inject constructor(
    private val driveSummaryDao: DriveSummaryDao,
    private val teslamateRepository: TeslamateRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(EfficiencyLabUiState())
    val uiState: StateFlow<EfficiencyLabUiState> = _uiState.asStateFlow()

    private var carId: Int = 0
    private var allDrives: List<DriveSummary> = emptyList()

    fun start(carId: Int) {
        if (this.carId == carId && allDrives.isNotEmpty()) return
        this.carId = carId
        viewModelScope.launch {
            when (val result = teslamateRepository.getCarStatus(carId)) {
                is ApiResult.Success -> _uiState.update { it.copy(units = result.data.units) }
                is ApiResult.Error -> Unit
            }
            allDrives = driveSummaryDao.getAllChronological(carId)
            recompute()
        }
    }

    fun selectPeriod(period: EfficiencyPeriod) {
        _uiState.update { it.copy(period = period) }
        recompute()
    }

    fun setMinDistanceFilter(min: Double) {
        _uiState.update { it.copy(minDistanceFilter = min) }
        recompute()
    }

    private fun recompute() {
        val state = _uiState.value
        val periodDrives = filterByPeriod(allDrives, state.period, LocalDate.now())
        val periodSummary = computeEfficiencySummary(periodDrives)
        val baselineSummary = computeEfficiencySummary(allDrives)
        val isFahrenheit = state.units?.unitOfTemperature != "C"
        val tempBands = efficiencyByOutsideTemp(periodDrives, isFahrenheit)
        val (best, worst) = bestWorstDrives(periodDrives, state.minDistanceFilter)

        _uiState.update {
            it.copy(
                isLoading = false,
                periodSummary = periodSummary,
                baselineSummary = baselineSummary,
                baselineComparison = compareToBaseline(periodSummary, baselineSummary),
                trend = efficiencyTrend(periodDrives, state.period),
                speedBands = efficiencyBySpeedBand(periodDrives),
                tripLengthBands = efficiencyByTripLength(periodDrives),
                tempBands = tempBands,
                tempDataAvailable = periodDrives.any { d -> d.outsideTempAvg != null },
                bestDrives = best,
                worstDrives = worst
            )
        }
    }
}
