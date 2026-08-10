package com.matedroid.ui.screens.charges

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matedroid.data.api.models.ChargeDetail
import com.matedroid.data.api.models.Units
import com.matedroid.data.local.SettingsDataStore
import com.matedroid.data.local.entity.SavedTripLeg
import com.matedroid.data.model.Currency
import com.matedroid.data.repository.ApiResult
import com.matedroid.data.repository.TeslamateRepository
import com.matedroid.domain.ChargeComparison
import com.matedroid.domain.ChargeComparisonRepository
import com.matedroid.domain.LegRef
import com.matedroid.domain.TripRepository
import com.matedroid.domain.ChargeCostPresentation
import com.matedroid.domain.ChargingCostRepository
import com.matedroid.domain.model.Trip
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChargeDetailUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val chargeDetail: ChargeDetail? = null,
    val units: Units? = null,
    val stats: ChargeDetailStats? = null,
    val currencySymbol: String = "€",
    /** Null means the captured session does not report a trustworthy charger classification. */
    val isDcCharge: Boolean? = null,
    val containingTrip: Pair<Long, Trip>? = null,
    val teslamateBaseUrl: String = "",
    val comparison: ChargeComparison? = null
    , val chargingCost: ChargeCostPresentation? = null
)

data class ChargeDetailStats(
    val powerMax: Int,
    val powerMin: Int,
    val powerAvg: Double,
    val voltageMax: Int,
    val voltageMin: Int,
    val voltageAvg: Double,
    val currentMax: Int,
    val currentMin: Int,
    val currentAvg: Double,
    val tempMax: Double,
    val tempMin: Double,
    val tempAvg: Double,
    val batteryStart: Int,
    val batteryEnd: Int,
    val batteryAdded: Int,
    val energyAdded: Double,
    /** TeslaMate's reported wall energy. It is intentionally nullable: do not invent a value. */
    val energyUsed: Double?,
    /** Only available when both reported energy figures make physical sense. */
    val efficiency: Double?,
    val durationMin: Int,
    val cost: Double?
)

@HiltViewModel
class ChargeDetailViewModel @Inject constructor(
    private val repository: TeslamateRepository,
    private val settingsDataStore: SettingsDataStore,
    private val tripRepository: TripRepository,
    private val chargeComparisonRepository: ChargeComparisonRepository,
    private val chargingCostRepository: ChargingCostRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChargeDetailUiState())
    val uiState: StateFlow<ChargeDetailUiState> = _uiState.asStateFlow()

    private var carId: Int? = null
    private var chargeId: Int? = null

    init {
        loadCurrency()
    }

    private fun loadCurrency() {
        viewModelScope.launch {
            val settings = settingsDataStore.settings.first()
            val currency = Currency.findByCode(settings.currencyCode)
            _uiState.update {
                it.copy(
                    currencySymbol = currency.symbol,
                    teslamateBaseUrl = settings.teslamateBaseUrl
                )
            }
        }
    }

    fun loadChargeDetail(carId: Int, chargeId: Int) {
        if (this.carId == carId && this.chargeId == chargeId && _uiState.value.chargeDetail != null) {
            return // Already loaded
        }

        this.carId = carId
        this.chargeId = chargeId

        viewModelScope.launch {
            val containing = tripRepository.findTripContaining(carId, SavedTripLeg.TYPE_CHARGE, chargeId)
            _uiState.update { it.copy(containingTrip = containing) }
        }

        viewModelScope.launch {
            val comparison = chargeComparisonRepository.findComparable(carId, chargeId)
            _uiState.update { it.copy(comparison = comparison) }
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            // Fetch charge detail and units in parallel
            val detailResult = repository.getChargeDetail(carId, chargeId)
            val statusResult = repository.getCarStatus(carId)

            val units = when (statusResult) {
                is ApiResult.Success -> statusResult.data.units
                is ApiResult.Error -> null
            }

            when (detailResult) {
                is ApiResult.Success -> {
                    val detail = detailResult.data
                    val stats = ChargeStatsCalculator.calculateStats(detail)
                    val isDcCharge = ChargeStatsCalculator.detectDcCharge(detail)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            chargeDetail = detail,
                            units = units,
                            stats = stats,
                            isDcCharge = isDcCharge,
                            error = null
                        )
                    }
                    refreshChargingCost(detail)
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = detailResult.message
                        )
                    }
                }
            }
        }
    }

    private fun refreshChargingCost(detail: ChargeDetail? = _uiState.value.chargeDetail) {
        viewModelScope.launch {
            detail ?: return@launch
            _uiState.update { it.copy(chargingCost = chargingCostRepository.costForCharge(detail)) }
        }
    }

    fun setManualCost(minorUnits: Long) {
        val id = chargeId ?: return
        viewModelScope.launch { chargingCostRepository.setManualCost(id, minorUnits, "USD"); refreshChargingCost() }
    }
    fun setManualFree() {
        val id = chargeId ?: return
        viewModelScope.launch { chargingCostRepository.setManualFree(id, "USD"); refreshChargingCost() }
    }
    fun useAutomaticCost() {
        val id = chargeId ?: return
        viewModelScope.launch { chargingCostRepository.useAutomatic(id); refreshChargingCost() }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /** Detach this charge from its containing saved trip (auto-transitions the trip to USER_EDITED). */
    fun removeFromTrip() {
        val tripId = _uiState.value.containingTrip?.first ?: return
        val charge = chargeId ?: return
        viewModelScope.launch {
            tripRepository.removeLegFromTrip(tripId, LegRef(SavedTripLeg.TYPE_CHARGE, charge))
            _uiState.update { it.copy(containingTrip = null) }
        }
    }

}
