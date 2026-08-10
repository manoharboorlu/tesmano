package com.matedroid.ui.screens.battery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matedroid.data.local.dao.AggregateDao
import com.matedroid.data.local.dao.ChargeSummaryDao
import com.matedroid.data.local.dao.DriveSummaryDao
import com.matedroid.data.repository.ApiResult
import com.matedroid.data.repository.TeslamateRepository
import com.matedroid.domain.BatteryAnalyticsCalculator
import com.matedroid.domain.BatteryAnalyticsSnapshot
import com.matedroid.domain.EfficiencyResult
import com.matedroid.domain.EfficiencyWindow
import com.matedroid.domain.RealWorldRange
import com.matedroid.domain.RealWorldRangeCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BatteryUiState(val loading: Boolean = true, val analytics: BatteryAnalyticsSnapshot? = null, val soc: Int? = null, val ratedRange: Double? = null, val teslaEstimatedRange: Double? = null, val window: EfficiencyWindow = EfficiencyWindow.Mi50, val efficiency: EfficiencyResult? = null, val range: RealWorldRange? = null)

@HiltViewModel
class BatteryViewModel @Inject constructor(
    private val charges: ChargeSummaryDao, private val aggregates: AggregateDao, private val drives: DriveSummaryDao, private val repository: TeslamateRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(BatteryUiState())
    val uiState: StateFlow<BatteryUiState> = _uiState.asStateFlow()
    private var carId: Int? = null
    fun setCarId(id: Int, efficiency: Double? = null) {
        if (carId == id) return
        carId = id
        viewModelScope.launch {
            val status = (repository.getCarStatus(id) as? ApiResult.Success)?.data?.status
            _uiState.value = _uiState.value.copy(soc = status?.batteryLevel, ratedRange = status?.ratedBatteryRangeKm?.takeIf { it > 0 }, teslaEstimatedRange = status?.estBatteryRangeKm?.takeIf { it > 0 })
            val recent = drives.getRecentPageForCar(id, 500, 0)
            val default = RealWorldRangeCalculator.defaultWindow(recent)
            charges.observeAllForCar(id).collectLatest { summaries ->
                val analytics = BatteryAnalyticsCalculator.snapshot(summaries, aggregates.getChargeAggregatesForCar(id))
                updateRange(analytics, recent, default)
            }
        }
    }
    fun selectWindow(window: EfficiencyWindow) { val id = carId ?: return; viewModelScope.launch { _uiState.value.analytics?.let { updateRange(it, drives.getRecentPageForCar(id, 500, 0), window) } } }
    private fun updateRange(analytics: BatteryAnalyticsSnapshot, recent: List<com.matedroid.data.local.entity.DriveSummary>, window: EfficiencyWindow) {
        val efficiency = RealWorldRangeCalculator.efficiency(recent, window)
        val range = RealWorldRangeCalculator.range(analytics.capacity.kwh, _uiState.value.soc, efficiency, analytics.capacity.confidence)
        _uiState.value = _uiState.value.copy(loading = false, analytics = analytics, window = window, efficiency = efficiency, range = range)
    }
}
