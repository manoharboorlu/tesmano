package com.matedroid.ui.screens.charges

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matedroid.data.local.dao.ChargeSummaryDao
import com.matedroid.data.local.dao.DriveSummaryDao
import com.matedroid.data.local.dao.SmartPlacesDao
import com.matedroid.domain.ChargingAnalyticsPeriod
import com.matedroid.domain.ChargingAnalyticsSnapshot
import com.matedroid.domain.ChargingCostAnalyticsCalculator
import com.matedroid.domain.ChargingCostRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

data class ChargingAnalyticsUiState(
    val loading: Boolean = true,
    val period: ChargingAnalyticsPeriod = ChargingAnalyticsPeriod.CurrentMonth,
    val snapshot: ChargingAnalyticsSnapshot? = null
)

@HiltViewModel
class ChargingAnalyticsViewModel @Inject constructor(
    private val chargeSummaryDao: ChargeSummaryDao,
    private val driveSummaryDao: DriveSummaryDao,
    private val placesDao: SmartPlacesDao,
    private val costRepository: ChargingCostRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(ChargingAnalyticsUiState())
    val uiState: StateFlow<ChargingAnalyticsUiState> = _uiState.asStateFlow()
    private var carId: Int? = null
    private var sourceJob: Job? = null
    private var source: AnalyticsSource? = null

    fun setCarId(id: Int) {
        if (carId == id) return
        carId = id
        sourceJob?.cancel()
        sourceJob = viewModelScope.launch {
            combine(
                chargeSummaryDao.observeAllForCar(id),
                costRepository.observeRules(),
                costRepository.observeOverrides(),
                placesDao.observePlaces()
            ) { charges, rules, overrides, places -> AnalyticsSource(charges, rules, overrides, places) }
                .collect { latest ->
                    source = latest
                    refresh()
                }
        }
    }

    fun selectPeriod(period: ChargingAnalyticsPeriod) {
        if (_uiState.value.period == period) return
        _uiState.value = _uiState.value.copy(period = period)
        refresh()
    }

    private fun refresh() {
        val id = carId ?: return
        val latest = source ?: return
        val period = _uiState.value.period
        viewModelScope.launch {
            val range = period.range(Instant.now(), ZoneId.systemDefault())
            val distance = if (range == null) driveSummaryDao.sumDistance(id) else driveSummaryDao.sumDistanceInRange(
                id, range.start.atStartOfDay(ZoneId.systemDefault()).toInstant().toString(),
                range.endExclusive.atStartOfDay(ZoneId.systemDefault()).toInstant().toString()
            )
            val snapshot = ChargingCostAnalyticsCalculator.calculate(
                latest.charges, latest.rules, latest.places, latest.overrides, period, distance
            )
            _uiState.value = ChargingAnalyticsUiState(false, period, snapshot)
        }
    }

    private data class AnalyticsSource(
        val charges: List<com.matedroid.data.local.entity.ChargeSummary>,
        val rules: List<com.matedroid.data.local.entity.ChargingRateRule>,
        val overrides: List<com.matedroid.data.local.entity.ChargeCostOverride>,
        val places: List<com.matedroid.data.local.entity.SmartPlace>
    )
}
