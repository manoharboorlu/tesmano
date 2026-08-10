package com.matedroid.ui.screens.tripmap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matedroid.data.api.models.Units
import com.matedroid.data.local.SettingsDataStore
import com.matedroid.data.local.dao.ChargeSummaryDao
import com.matedroid.data.local.dao.DriveEdgeCoordinateResult
import com.matedroid.data.local.dao.DriveSummaryDao
import com.matedroid.data.local.entity.ChargeSummary
import com.matedroid.data.local.entity.DriveSummary
import com.matedroid.data.local.entity.SmartPlace
import com.matedroid.data.model.Currency
import com.matedroid.data.repository.ApiResult
import com.matedroid.data.repository.TeslamateRepository
import com.matedroid.data.local.dao.AggregateDao
import com.matedroid.domain.ChargeCostPresentation
import com.matedroid.domain.ChargingCostRepository
import com.matedroid.domain.DrivePlaceContext
import com.matedroid.domain.GeoPoint
import com.matedroid.domain.SmartPlaceMatcher
import com.matedroid.domain.SmartPlacesRepository
import com.matedroid.domain.TripDayTotals
import com.matedroid.domain.TripEntry
import com.matedroid.domain.computeDayTotals
import com.matedroid.domain.mergeChronological
import com.matedroid.util.parseInstantAware
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TripMapUiState(
    val isLoading: Boolean = true,
    val date: LocalDate? = null,
    val hasPreviousDay: Boolean = false,
    val hasNextDay: Boolean = false,
    val entries: List<TripEntry> = emptyList(),
    val totals: TripDayTotals? = null,
    val chargeCosts: Map<Int, ChargeCostPresentation> = emptyMap(),
    val drivePlaces: Map<Int, DrivePlaceContext> = emptyMap(),
    val driveCoordinates: Map<Int, DriveEdgeCoordinateResult> = emptyMap(),
    val chargePlaces: Map<Int, SmartPlace?> = emptyMap(),
    val units: Units? = null,
    val currencySymbol: String = "€",
    val selectedKey: String? = null
)

@HiltViewModel
class TripMapViewModel @Inject constructor(
    private val driveSummaryDao: DriveSummaryDao,
    private val chargeSummaryDao: ChargeSummaryDao,
    private val aggregateDao: AggregateDao,
    private val smartPlacesRepository: SmartPlacesRepository,
    private val chargingCostRepository: ChargingCostRepository,
    private val teslamateRepository: TeslamateRepository,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(TripMapUiState())
    val uiState: StateFlow<TripMapUiState> = _uiState.asStateFlow()

    private var carId: Int = 0
    private var availableDays: List<LocalDate> = emptyList()
    private var allDrives: List<DriveSummary> = emptyList()
    private var allCharges: List<ChargeSummary> = emptyList()

    fun start(carId: Int, initialDate: LocalDate?) {
        this.carId = carId
        loadUnits(carId)
        loadCurrency()
        viewModelScope.launch {
            // Loaded once per screen entry — cheap local Room reads, same scale Mileage already
            // performs on every open. Filtering happens in Kotlin via [parseInstantAware] so a
            // day's membership always matches Activity's own instant-aware grouping, regardless
            // of whether the backend's configured timezone offset differs from the device's.
            allDrives = driveSummaryDao.getAllChronological(carId)
            allCharges = chargeSummaryDao.getAllForCar(carId)
            availableDays = (allDrives.map { it.startDate } + allCharges.map { it.startDate })
                .mapNotNull { localDateOf(it) }
                .distinct()
                .sorted()
            val day = initialDate ?: availableDays.lastOrNull() ?: LocalDate.now()
            loadDay(day)
        }
    }

    fun selectPreviousDay() {
        val current = _uiState.value.date ?: return
        val previous = availableDays.lastOrNull { it < current } ?: return
        loadDay(previous)
    }

    fun selectNextDay() {
        val current = _uiState.value.date ?: return
        val next = availableDays.firstOrNull { it > current } ?: return
        loadDay(next)
    }

    fun selectEntry(key: String?) {
        _uiState.update { it.copy(selectedKey = key) }
    }

    private fun loadUnits(carId: Int) {
        viewModelScope.launch {
            when (val result = teslamateRepository.getCarStatus(carId)) {
                is ApiResult.Success -> _uiState.update { it.copy(units = result.data.units) }
                is ApiResult.Error -> Unit
            }
        }
    }

    private fun loadCurrency() {
        viewModelScope.launch {
            val settings = settingsDataStore.settings.first()
            val currency = Currency.findByCode(settings.currencyCode)
            _uiState.update { it.copy(currencySymbol = currency.symbol) }
        }
    }

    /** Instant-aware local calendar date — must match Activity's own day-grouping exactly. */
    private fun localDateOf(startDate: String): LocalDate? = parseInstantAware(startDate)?.toLocalDate()

    private fun loadDay(date: LocalDate) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, date = date, selectedKey = null) }
            val drives = allDrives.filter { localDateOf(it.startDate) == date }
            val charges = allCharges.filter { localDateOf(it.startDate) == date }

            val chargeCosts = charges.associate { it.chargeId to chargingCostRepository.costForCharge(it) }
            val drivePlaces = smartPlacesRepository.contextsForDrives(carId, drives.map { it.driveId })
            val driveCoordinates = aggregateDao.getDriveEdgeCoordinates(drives.map { it.driveId }).associateBy { it.driveId }
            val places = smartPlacesRepository.observePlaces().first()
            val chargePlaces = charges.associate { charge ->
                val point = GeoPoint(charge.latitude, charge.longitude)
                charge.chargeId to SmartPlaceMatcher.match(point, places)
            }

            val totals = computeDayTotals(drives, charges, chargeCosts)

            _uiState.update {
                it.copy(
                    isLoading = false,
                    date = date,
                    hasPreviousDay = availableDays.any { d -> d < date },
                    hasNextDay = availableDays.any { d -> d > date },
                    entries = mergeChronological(drives, charges),
                    totals = totals,
                    chargeCosts = chargeCosts,
                    drivePlaces = drivePlaces,
                    driveCoordinates = driveCoordinates,
                    chargePlaces = chargePlaces
                )
            }
        }
    }
}
