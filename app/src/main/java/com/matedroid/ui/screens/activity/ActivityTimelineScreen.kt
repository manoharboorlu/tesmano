package com.matedroid.ui.screens.activity

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.matedroid.R
import com.matedroid.data.api.models.Units
import com.matedroid.data.local.entity.ChargeSummary
import com.matedroid.data.local.entity.DriveSummary
import com.matedroid.domain.model.UnitFormatter
import com.matedroid.ui.adaptive.LocalAdaptiveLayoutInfo
import com.matedroid.util.formatDurationCompact
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityTimelineScreen(
    carId: Int,
    onNavigateBack: () -> Unit,
    onNavigateToDriveDetail: (Int) -> Unit,
    onNavigateToChargeDetail: (Int) -> Unit,
    viewModel: ActivityTimelineViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val layout = LocalAdaptiveLayoutInfo.current
    val listState = rememberLazyListState()
    val filtered = remember(uiState.entries, uiState.filter) {
        uiState.entries.filter { entry ->
            when (uiState.filter) {
                ActivityFilter.ALL -> true
                ActivityFilter.DRIVES -> entry is ActivityEntry.Drive
                ActivityFilter.CHARGES -> entry is ActivityEntry.Charge
            }
        }
    }
    val rows = remember(filtered) { buildTimelineRows(filtered) }

    LaunchedEffect(carId) { viewModel.setCarId(carId) }
    LaunchedEffect(uiState.filter) { listState.scrollToItem(0) }
    LaunchedEffect(listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index, rows.size, uiState.hasMore) {
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@LaunchedEffect
        if (uiState.hasMore && lastVisible >= rows.lastIndex - 8) viewModel.loadMore()
    }
    LaunchedEffect(uiState.filter, filtered.isEmpty(), uiState.entries.isNotEmpty(), uiState.hasMore, uiState.isLoading) {
        if (!uiState.isLoading && uiState.entries.isNotEmpty() && filtered.isEmpty() && uiState.hasMore) {
            viewModel.loadMore()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.activity_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .widthIn(max = if (layout.supportsTwoPane) 660.dp else 600.dp)
                        .fillMaxWidth()
                ) {
                    ActivityHeader(
                        filter = uiState.filter,
                        isSyncing = uiState.isSyncing,
                        syncProgress = uiState.syncProgress?.percentage,
                        onFilterSelected = viewModel::setFilter
                    )
                    when {
                        uiState.isLoading -> LoadingActivity()
                        uiState.entries.isEmpty() -> EmptyActivity(filter = ActivityFilter.ALL)
                        filtered.isEmpty() -> EmptyActivity(filter = uiState.filter)
                        else -> LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 32.dp)
                        ) {
                            items(rows, key = { it.key }) { row ->
                                when (row) {
                                    is TimelineRow.DateHeader -> DateHeader(row.date)
                                    is TimelineRow.EntryRow -> ActivityRow(
                                        entry = row.entry,
                                        units = uiState.units,
                                        onClick = {
                                            when (val entry = row.entry) {
                                                is ActivityEntry.Drive -> onNavigateToDriveDetail(entry.id)
                                                is ActivityEntry.Charge -> onNavigateToChargeDetail(entry.id)
                                            }
                                        }
                                    )
                                }
                            }
                            if (uiState.isLoadingMore) {
                                item("loading") { LoadingMore() }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityHeader(
    filter: ActivityFilter,
    isSyncing: Boolean,
    syncProgress: Float?,
    onFilterSelected: (ActivityFilter) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text(
            text = if (isSyncing) stringResource(R.string.activity_refreshing) else stringResource(R.string.activity_timeline_cached),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TimelineFilterChip(ActivityFilter.ALL, filter, onFilterSelected)
            TimelineFilterChip(ActivityFilter.DRIVES, filter, onFilterSelected)
            TimelineFilterChip(ActivityFilter.CHARGES, filter, onFilterSelected)
        }
        if (isSyncing) {
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { syncProgress ?: 0f },
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.activity_cached_offline),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun TimelineFilterChip(
    value: ActivityFilter,
    selected: ActivityFilter,
    onSelected: (ActivityFilter) -> Unit
) {
    val label = when (value) {
        ActivityFilter.ALL -> stringResource(R.string.activity_filter_all)
        ActivityFilter.DRIVES -> stringResource(R.string.activity_filter_drives)
        ActivityFilter.CHARGES -> stringResource(R.string.activity_filter_charges)
    }
    FilterChip(selected = value == selected, onClick = { onSelected(value) }, label = { Text(label) })
}

@Composable
private fun LoadingActivity() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
private fun EmptyActivity(filter: ActivityFilter) {
    val title = when (filter) {
        ActivityFilter.ALL -> stringResource(R.string.activity_no_cached)
        ActivityFilter.DRIVES -> stringResource(R.string.activity_no_filter_results, stringResource(R.string.activity_filter_drives).lowercase())
        ActivityFilter.CHARGES -> stringResource(R.string.activity_no_filter_results, stringResource(R.string.activity_filter_charges).lowercase())
    }
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (filter == ActivityFilter.ALL) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.activity_no_cached_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DateHeader(date: LocalDate?) {
    Text(
        text = date?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.getDefault()))
            ?: stringResource(R.string.unknown),
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun ActivityRow(entry: ActivityEntry, units: Units?, onClick: () -> Unit) {
    val context = LocalContext.current
    val time = entry.localStart()?.let { local ->
        val start = DateFormat.getTimeFormat(context).format(java.util.Date(local.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()))
        val end = entry.localEnd()?.let { finish ->
            DateFormat.getTimeFormat(context).format(java.util.Date(finish.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()))
        }
        if (end == null || start == end) start else "$start – $end"
    } ?: stringResource(R.string.unknown)
    val (label, icon, headline, details) = when (entry) {
        is ActivityEntry.Drive -> driveRowContent(entry.summary, units)
        is ActivityEntry.Charge -> chargeRowContent(entry.summary)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (entry is ActivityEntry.Drive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.padding(top = 3.dp)
        )
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(headline, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Text(time, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (details.isNotEmpty()) {
                Spacer(Modifier.height(5.dp))
                Text(details.joinToString("  ·  "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun driveRowContent(summary: DriveSummary, units: Units?): TimelineContent {
    val unknown = stringResource(R.string.activity_unknown_location)
    val start = summary.startAddress.ifBlank { unknown }
    val end = summary.endAddress.ifBlank { unknown }
    val details = buildList {
        if (summary.distance > 0) add(UnitFormatter.formatDistance(summary.distance, units))
        if (summary.durationMin > 0) add(formatDurationCompact(summary.durationMin))
        summary.efficiency?.takeIf { it > 0 && summary.distance >= 1 }?.let { add(UnitFormatter.formatEfficiency(it, units, 0)) }
        summary.energyConsumed?.takeIf { it >= 0.5 }?.let { add(UnitFormatter.formatEnergy(it)) }
    }
    return TimelineContent(stringResource(R.string.activity_drive), Icons.Filled.DirectionsCar, "$start → $end", details)
}

@Composable
private fun chargeRowContent(summary: ChargeSummary): TimelineContent {
    val location = summary.address.ifBlank { stringResource(R.string.activity_unknown_location) }
    val details = buildList {
        add(stringResource(R.string.activity_soc_range, summary.startBatteryLevel, summary.endBatteryLevel))
        if (summary.energyAdded > 0) add(stringResource(R.string.activity_energy_added, UnitFormatter.formatEnergy(summary.energyAdded)))
        if (summary.durationMin > 0) add(formatDurationCompact(summary.durationMin))
    }
    return TimelineContent(stringResource(R.string.activity_charge), Icons.Filled.Bolt, location, details)
}

private data class TimelineContent(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val headline: String,
    val details: List<String>
)

@Composable
private fun LoadingMore() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(modifier = Modifier.height(18.dp))
        Text(
            stringResource(R.string.activity_loading_more),
            modifier = Modifier.padding(start = 10.dp),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private sealed interface TimelineRow {
    val key: String
    data class DateHeader(val date: LocalDate?) : TimelineRow { override val key = "date-$date" }
    data class EntryRow(val entry: ActivityEntry) : TimelineRow {
        override val key = "${entry::class.simpleName}-${entry.id}"
    }
}

private fun buildTimelineRows(entries: List<ActivityEntry>): List<TimelineRow> = buildList {
    var previousDate: LocalDate? = null
    entries.forEach { entry ->
        val date = entry.localStart()?.toLocalDate()
        if (date != previousDate) {
            add(TimelineRow.DateHeader(date))
            previousDate = date
        }
        add(TimelineRow.EntryRow(entry))
    }
}

private fun ActivityEntry.localStart(): LocalDateTime? = localDateTime(startDate)
private fun ActivityEntry.localEnd(): LocalDateTime? = localDateTime(endDate)

private fun localDateTime(value: String): LocalDateTime? =
    try {
        LocalDateTime.ofInstant(Instant.parse(value), ZoneId.systemDefault())
    } catch (_: DateTimeParseException) {
        try { LocalDateTime.parse(value.replace("Z", "")) } catch (_: DateTimeParseException) { null }
    }
