package com.matedroid.ui.screens.activity

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.matedroid.data.local.dao.ChargeSummaryDao
import com.matedroid.data.local.dao.DriveSummaryDao
import com.matedroid.data.local.entity.ChargeSummary
import com.matedroid.data.local.entity.DriveSummary
import com.matedroid.data.api.models.Units
import com.matedroid.data.repository.ApiResult
import com.matedroid.data.repository.TeslamateRepository
import com.matedroid.data.sync.DataSyncWorker
import com.matedroid.data.sync.SyncManager
import com.matedroid.domain.model.SyncPhase
import com.matedroid.domain.model.SyncProgress
import com.matedroid.domain.DrivePlaceContext
import com.matedroid.domain.SmartPlacesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ActivityFilter { ALL, DRIVES, CHARGES }

sealed interface ActivityEntry {
    val id: Int
    val startDate: String
    val endDate: String
    val durationMinutes: Int

    data class Drive(val summary: DriveSummary) : ActivityEntry {
        override val id get() = summary.driveId
        override val startDate get() = summary.startDate
        override val endDate get() = summary.endDate
        override val durationMinutes get() = summary.durationMin
    }

    data class Charge(val summary: ChargeSummary) : ActivityEntry {
        override val id get() = summary.chargeId
        override val startDate get() = summary.startDate
        override val endDate get() = summary.endDate
        override val durationMinutes get() = summary.durationMin
    }
}

data class ActivityTimelineUiState(
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val filter: ActivityFilter = ActivityFilter.ALL,
    val entries: List<ActivityEntry> = emptyList(),
    val hasMore: Boolean = true,
    val units: Units? = null,
    val syncProgress: SyncProgress? = null,
    val drivePlaces: Map<Int, DrivePlaceContext> = emptyMap()
) {
    val isSyncing: Boolean
        get() = syncProgress?.phase?.let { it !in setOf(SyncPhase.IDLE, SyncPhase.COMPLETE, SyncPhase.ERROR) } == true
}

/**
 * Reads only cached list summaries. Detail telemetry remains strictly on demand in its detail screens.
 * Each source is read in fixed-size pages; the merged buffer preserves newest-first chronology.
 */
@HiltViewModel
class ActivityTimelineViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val driveSummaryDao: DriveSummaryDao,
    private val chargeSummaryDao: ChargeSummaryDao,
    private val teslamateRepository: TeslamateRepository,
    private val syncManager: SyncManager,
    private val smartPlacesRepository: SmartPlacesRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(ActivityTimelineUiState())
    val uiState: StateFlow<ActivityTimelineUiState> = _uiState.asStateFlow()

    private var carId: Int? = null
    private var driveOffset = 0
    private var chargeOffset = 0
    private var sourceMayHaveMore = true
    private val pendingEntries = mutableListOf<ActivityEntry>()
    private var syncJob: Job? = null

    fun setCarId(id: Int) {
        if (carId == id) return
        carId = id
        observeSync(id)
        loadUnits(id)
        resetAndLoad()
    }

    fun setFilter(filter: ActivityFilter) {
        _uiState.update { it.copy(filter = filter) }
    }

    fun loadMore() {
        if (_uiState.value.isLoadingMore || !sourceMayHaveMore && pendingEntries.isEmpty()) return
        loadPage(initial = false)
    }

    fun refresh() {
        if (syncManager.syncStatus.value.isAnySyncing) return
        _uiState.update { it.copy(isRefreshing = true) }
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val request = OneTimeWorkRequestBuilder<DataSyncWorker>()
            .setConstraints(constraints)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(DataSyncWorker.TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            DataSyncWorker.WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    private fun resetAndLoad() {
        driveOffset = 0
        chargeOffset = 0
        sourceMayHaveMore = true
        pendingEntries.clear()
        _uiState.value = ActivityTimelineUiState(units = _uiState.value.units)
        loadPage(initial = true)
    }

    private fun loadPage(initial: Boolean) {
        val id = carId ?: return
        viewModelScope.launch {
            _uiState.update {
                if (initial) it.copy(isLoading = true) else it.copy(isLoadingMore = true)
            }
            val drives = if (sourceMayHaveMore) {
                driveSummaryDao.getRecentPageForCar(id, PAGE_SIZE, driveOffset)
            } else emptyList()
            val charges = if (sourceMayHaveMore) {
                chargeSummaryDao.getRecentPageForCar(id, PAGE_SIZE, chargeOffset)
            } else emptyList()
            driveOffset += drives.size
            chargeOffset += charges.size
            sourceMayHaveMore = drives.size == PAGE_SIZE || charges.size == PAGE_SIZE
            pendingEntries += drives.map(ActivityEntry::Drive)
            pendingEntries += charges.map(ActivityEntry::Charge)
            pendingEntries.sortByDescending { it.timelineEpochMillis() }

            val nextEntries = pendingEntries.take(PAGE_SIZE)
            pendingEntries.subList(0, nextEntries.size).clear()
            val drivePlaces = smartPlacesRepository.contextsForDrives(id, nextEntries.filterIsInstance<ActivityEntry.Drive>().map { it.id })
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    entries = it.entries + nextEntries,
                    drivePlaces = it.drivePlaces + drivePlaces,
                    hasMore = sourceMayHaveMore || pendingEntries.isNotEmpty()
                )
            }
        }
    }

    private fun observeSync(id: Int) {
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            syncManager.syncStatus.collect { status ->
                val progress = status.carProgresses[id]
                _uiState.update {
                    it.copy(
                        syncProgress = progress,
                        isRefreshing = progress?.phase?.let { phase ->
                            phase !in setOf(SyncPhase.IDLE, SyncPhase.COMPLETE, SyncPhase.ERROR)
                        } == true
                    )
                }
                if (progress?.phase == SyncPhase.COMPLETE) resetAndLoad()
            }
        }
    }

    /** Fetches only the existing lightweight car-status envelope for display units. */
    private fun loadUnits(id: Int) {
        viewModelScope.launch {
            when (val result = teslamateRepository.getCarStatus(id)) {
                is ApiResult.Success -> _uiState.update { it.copy(units = result.data.units) }
                is ApiResult.Error -> Unit // Cached timeline remains available with the existing fallback.
            }
        }
    }

    companion object {
        private const val PAGE_SIZE = 60
    }
}

fun ActivityEntry.timelineEpochMillis(zoneId: ZoneId = ZoneId.systemDefault()): Long =
    try {
        Instant.parse(startDate).toEpochMilli()
    } catch (_: DateTimeParseException) {
        try {
            LocalDateTime.parse(startDate.replace("Z", "")).atZone(zoneId).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) {
            Long.MIN_VALUE
        }
    }
