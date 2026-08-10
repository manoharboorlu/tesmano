package com.matedroid.ui.screens.dataquality

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matedroid.data.local.SettingsDataStore
import com.matedroid.data.repository.ApiResult
import com.matedroid.data.repository.DataQualityRepository
import com.matedroid.data.repository.TeslamateRepository
import com.matedroid.domain.DataQualitySummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DataQualityUiState(val loading: Boolean = true, val summary: DataQualitySummary? = null)

@HiltViewModel
class DataQualityViewModel @Inject constructor(
    private val repository: DataQualityRepository,
    private val teslamateRepository: TeslamateRepository,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {
    private val _uiState = MutableStateFlow(DataQualityUiState())
    val uiState: StateFlow<DataQualityUiState> = _uiState.asStateFlow()
    private var loaded = false

    fun load() {
        if (loaded) return
        loaded = true
        viewModelScope.launch {
            val carId = resolveCarId()
            _uiState.value = if (carId == null) DataQualityUiState(loading = false) else DataQualityUiState(loading = false, summary = repository.summary(carId))
        }
    }

    private suspend fun resolveCarId(): Int? {
        val cars = (teslamateRepository.getCars() as? ApiResult.Success)?.data.orEmpty()
        val lastCarId = settingsDataStore.settings.first().lastSelectedCarId
        return if (lastCarId != null && cars.any { it.carId == lastCarId }) lastCarId else cars.firstOrNull()?.carId
    }
}
