package com.matedroid.ui.screens.routes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import com.matedroid.R
import com.matedroid.domain.RecurringRoute
import com.matedroid.domain.RecurringRoutesSnapshot
import com.matedroid.domain.RouteCoverageState
import com.matedroid.domain.state
import com.matedroid.ui.adaptive.LocalAdaptiveLayoutInfo
import com.matedroid.util.formatDurationCompact
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringRoutesScreen(
    carId: Int,
    onNavigateBack: () -> Unit,
    onNavigateToDriveDetail: (Int) -> Unit,
    viewModel: RecurringRoutesViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val adaptive = LocalAdaptiveLayoutInfo.current
    LaunchedEffect(carId) { viewModel.load(carId) }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Recurring routes") },
            navigationIcon = { IconButton(onClick = { if (!adaptive.supportsTwoPane && state.selectedKey != null) viewModel.select(null) else onNavigateBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
        )
    }) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.snapshot == null || state.snapshot!!.routes.isEmpty() -> EmptyRoutes(state.snapshot, Modifier.padding(padding))
            adaptive.supportsTwoPane -> Row(Modifier.fillMaxSize().padding(padding).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                RouteList(state.snapshot!!, state.selected?.key, viewModel::select, Modifier.weight(.9f))
                RouteDetail(state.selected!!, viewModel::name, viewModel::keepUnnamed, viewModel::dismiss, onNavigateToDriveDetail, Modifier.weight(1.1f))
            }
            state.selectedKey == null -> RouteList(state.snapshot!!, null, viewModel::select, Modifier.fillMaxSize().padding(padding))
            else -> RouteDetail(state.selected!!, viewModel::name, viewModel::keepUnnamed, viewModel::dismiss, onNavigateToDriveDetail, Modifier.fillMaxSize().padding(padding))
        }
    }
}

@Composable
private fun EmptyRoutes(snapshot: RecurringRoutesSnapshot?, modifier: Modifier) = Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Filled.Route, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        val coverage = snapshot?.coverage
        val (titleRes, bodyRes) = when (coverage?.state()) {
            null, RouteCoverageState.NO_ENDPOINTS -> R.string.routes_empty_no_endpoints_title to R.string.routes_empty_no_endpoints_body
            RouteCoverageState.PARTIAL_COVERAGE -> R.string.routes_empty_partial_title to R.string.routes_empty_partial_body
            RouteCoverageState.FULL_COVERAGE_NO_PATTERN -> R.string.routes_empty_no_pattern_title to R.string.routes_empty_no_pattern_body
        }
        Text(stringResource(titleRes), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            if (coverage == null) stringResource(bodyRes) else stringResource(bodyRes, coverage.knownEndpoints, coverage.totalDrives),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RouteList(snapshot: RecurringRoutesSnapshot, selectedKey: String?, onSelect: (String?) -> Unit, modifier: Modifier) {
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("RECURRING ROUTES", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.routes_coverage_summary, snapshot.coverage.knownEndpoints, snapshot.coverage.totalDrives), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(snapshot.routes, key = { it.key }) { route ->
            Card(Modifier.fillMaxWidth().clickable { onSelect(route.key) }) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(route.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (route.commute) Text("COMMUTE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    if (route.displayName != route.endpoints) Text(route.endpoints, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(5.dp))
                    Text("${route.occurrenceCount} drives · ${route.totalDistance.oneDecimal()} total · ${route.averageDistance.oneDecimal()} avg", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun RouteDetail(route: RecurringRoute, onName: (RecurringRoute, String?) -> Unit, onKeepUnnamed: (RecurringRoute) -> Unit, onDismiss: (RecurringRoute) -> Unit, onNavigateToDriveDetail: (Int) -> Unit, modifier: Modifier) {
    var editingName by remember(route.key) { mutableStateOf(false) }
    if (editingName) {
        var value by remember(route.key) { mutableStateOf(route.metadata?.name.orEmpty()) }
        AlertDialog(onDismissRequest = { editingName = false }, title = { Text("Name route") }, text = { OutlinedTextField(value, { value = it }, label = { Text("Route name") }, singleLine = true) }, confirmButton = { TextButton(onClick = { onName(route, value); editingName = false }) { Text("Save") } }, dismissButton = { TextButton(onClick = { editingName = false }) { Text("Cancel") } })
    }
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(route.displayName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(route.endpoints, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (route.commute) Text("COMMUTE · derived only from Home ↔ Work", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp))
        }
        item { RouteStats(route) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { editingName = true }) { Text(if (route.metadata?.name == null) "Name route" else "Rename") }
                if (route.metadata?.confirmed != true) TextButton(onClick = { onKeepUnnamed(route) }) { Text("Keep unnamed") }
                TextButton(onClick = { onDismiss(route) }) { Text("Dismiss") }
            }
        }
        item { Text("RECENT OCCURRENCES", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
        items(route.driveIds.take(12), key = { it }) { driveId ->
            Text("Drive #$driveId", modifier = Modifier.fillMaxWidth().clickable { onNavigateToDriveDetail(driveId) }.padding(vertical = 12.dp), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun RouteStats(route: RecurringRoute) = Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("${route.occurrenceCount} drives", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text("${route.totalDistance.oneDecimal()} total · ${route.averageDistance.oneDecimal()} average", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${route.averageDurationMinutes.toInt()} min average duration", color = MaterialTheme.colorScheme.onSurfaceVariant)
        route.weightedEfficiencyWhPerDistance?.let { Text("${it.toInt()} Wh/distance unit · weighted", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Text("Most recent ${route.mostRecent.localDateLabel()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
    }
}

private fun Double.oneDecimal() = "%.1f".format(java.util.Locale.getDefault(), this)
private fun String.localDateLabel(): String = runCatching { Instant.parse(this).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) }.getOrDefault("Unknown")
