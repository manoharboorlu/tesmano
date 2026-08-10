package com.matedroid.ui.screens.battery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matedroid.data.local.dao.AggregateDao
import com.matedroid.data.local.dao.ChargeSummaryDao
import com.matedroid.domain.BatteryAnalyticsCalculator
import com.matedroid.domain.BatteryAnalyticsSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BatteryUiState(val loading: Boolean = true, val analytics: BatteryAnalyticsSnapshot? = null)

@HiltViewModel
class BatteryViewModel @Inject constructor(
    private val charges: ChargeSummaryDao, private val aggregates: AggregateDao
) : ViewModel() {
    private val _uiState = MutableStateFlow(BatteryUiState())
    val uiState: StateFlow<BatteryUiState> = _uiState.asStateFlow()
    private var carId: Int? = null
    fun setCarId(id: Int, efficiency: Double? = null) {
        if (carId == id) return
        carId = id
        viewModelScope.launch {
            charges.observeAllForCar(id).collectLatest { summaries ->
                _uiState.value = BatteryUiState(false, BatteryAnalyticsCalculator.snapshot(summaries, aggregates.getChargeAggregatesForCar(id)))
            }
        }
    }
}
