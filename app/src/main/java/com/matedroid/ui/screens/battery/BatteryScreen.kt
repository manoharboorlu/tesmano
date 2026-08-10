package com.matedroid.ui.screens.battery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.matedroid.R
import com.matedroid.domain.BatteryAnalyticsSnapshot
import com.matedroid.domain.EfficiencyWindow
import com.matedroid.domain.MetricSemantic
import com.matedroid.domain.quality
import com.matedroid.ui.adaptive.LocalAdaptiveLayoutInfo
import com.matedroid.ui.components.BarChartData
import com.matedroid.ui.components.InteractiveBarChart
import com.matedroid.ui.components.explanationRes
import com.matedroid.ui.components.labelRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryScreen(carId: Int, efficiency: Double?, exteriorColor: String? = null, onNavigateBack: () -> Unit, viewModel: BatteryViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(carId) { viewModel.setCarId(carId, efficiency) }
    Scaffold(topBar = { TopAppBar(title = { Text("Battery analytics") }, navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { padding ->
        state.analytics?.let { BatteryContent(it, state, viewModel::selectWindow, Modifier.padding(padding)) } ?: Column(Modifier.fillMaxSize().padding(padding).padding(24.dp)) { Text("Loading local battery history…") }
    }
}

@Composable private fun BatteryContent(data: BatteryAnalyticsSnapshot, state: BatteryUiState, onWindow: (EfficiencyWindow) -> Unit, modifier: Modifier) {
    val twoPane = LocalAdaptiveLayoutInfo.current.supportsTwoPane
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { RealWorldCard(state, onWindow) }
        item { CapacitySummary(data) }
        if (twoPane) item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) { CapacityTrend(data); RangeCard() }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) { ChargingCard(data); MethodCard(data) }
        } } else {
            item { CapacityTrend(data) }; item { RangeCard() }; item { ChargingCard(data) }; item { MethodCard(data) }
        }
    }
}

@Composable private fun RealWorldCard(state: BatteryUiState, onWindow: (EfficiencyWindow) -> Unit) = Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Label("TESMANO REAL-WORLD · ESTIMATED")
    val range = state.range
    Text(range?.toTenMiles?.let { "%.0f mi to 10%%".format(it) } ?: "Real-world range unavailable", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Text(range?.toZeroMiles?.let { "%.0f mi to 0%%".format(it) } ?: "Needs current SOC, usable-capacity estimate, and enough recent driving.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(state.efficiency?.whPerMile?.let { "%.0f Wh/mi · %s represented · %d drives".format(it, "%.1f mi".format(state.efficiency!!.miles), state.efficiency!!.drives) } ?: "Efficiency unavailable")
    RangeEvidence(state)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        item { WindowChip(EfficiencyWindow.Mi25, state.window, onWindow) }; item { WindowChip(EfficiencyWindow.Mi50, state.window, onWindow) }; item { WindowChip(EfficiencyWindow.Mi100, state.window, onWindow) }; item { WindowChip(EfficiencyWindow.Days7, state.window, onWindow) }; item { WindowChip(EfficiencyWindow.Days30, state.window, onWindow) }
    }
    Text("Tesla Rated: ${state.ratedRange?.let { "%.0f mi".format(it) } ?: "Unavailable"} · Tesla Estimated: ${state.teslaEstimatedRange?.let { "%.0f mi".format(it) } ?: "Unavailable"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("Based on recent driving; not route-aware and does not predict elevation, weather, traffic, HVAC, speed, or load.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
} }
@Composable private fun WindowChip(window: EfficiencyWindow, selected: EfficiencyWindow, onWindow: (EfficiencyWindow) -> Unit) = FilterChip(selected == window, { onWindow(window) }, { Text(window.label) })

@Composable private fun RangeEvidence(state: BatteryUiState) {
    val range = state.range
    val efficiency = state.efficiency
    if (range == null || efficiency == null) return
    if (range.confidence != null) {
        Text(
            "${stringResource(R.string.range_evidence_battery)} ${state.analytics?.capacity?.confidence?.let { stringResource(it.labelRes()) } ?: stringResource(R.string.range_evidence_unavailable)} · " +
                "${stringResource(R.string.range_evidence_driving)} ${efficiency.confidence?.let { stringResource(it.labelRes()) } ?: stringResource(R.string.range_evidence_unavailable)} · " +
                "${stringResource(R.string.range_evidence_overall)} ${stringResource(range.confidence.labelRes())}",
            style = MaterialTheme.typography.bodySmall
        )
    } else {
        val reason = range.quality(efficiency, state.analytics?.capacity?.kwh != null).reason
        Text(reason?.let { stringResource(it.explanationRes()) } ?: stringResource(R.string.range_evidence_unavailable), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable private fun CapacitySummary(data: BatteryAnalyticsSnapshot) = Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Label("BATTERY · ${MetricSemantic.ESTIMATED}")
    val capacity = data.capacity
    Text(capacity.kwh?.let { "%.1f kWh".format(it) } ?: "Unavailable", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    Text("Estimated usable capacity", color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(capacity.confidence?.let { "${stringResource(it.labelRes())} · ${capacity.accepted.size} accepted samples" } ?: "No reliable capacity samples", style = MaterialTheme.typography.bodyMedium)
    capacity.changeFromBaselinePercent?.let { Text("Change vs observed baseline  %+.1f%%".format(it), style = MaterialTheme.typography.titleSmall) }
} }

@Composable private fun CapacityTrend(data: BatteryAnalyticsSnapshot) = Card { Column(Modifier.padding(16.dp)) {
    Text("Capacity trend", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); Label("ESTIMATED · robust monthly median")
    val bars = data.monthlyCapacity.map { BarChartData(it.first.takeLast(2), it.second, "%.1f kWh".format(it.second)) }
    Spacer(Modifier.height(8.dp))
    if (bars.isEmpty()) Text("No accepted capacity samples yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) else InteractiveBarChart(bars, valueFormatter = { "%.1f kWh".format(it) })
} }

@Composable private fun RangeCard() = Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text("Range", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Label("DERIVED / MEASURED")
    Text("Historical rated-range samples are not retained in local summaries.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("100% rated-range equivalent and Tesla Estimated Range are unavailable until authoritative samples are cached; zero is never treated as a range.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
} }

@Composable private fun ChargingCard(data: BatteryAnalyticsSnapshot) = Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text("Charging exposure", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); Label("MEASURED / DERIVED")
    Exposure("AC", data.ac); Exposure("DC", data.dc)
    Text("Efficiency is weighted battery-added energy ÷ coherent grid-reported energy. It is not drivetrain efficiency.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("Charging curve unavailable: local aggregates retain no raw power-by-SOC telemetry. Battery temperature is not in the current contract and is deferred.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
} }

@Composable private fun Exposure(name: String, value: com.matedroid.domain.ChargingExposure) = Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Text("$name · ${value.sessions} sessions · %.1f kWh".format(value.kwh))
    Text(value.efficiency?.let { "%.1f%% efficient".format(it * 100) } ?: "Efficiency unavailable", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
@Composable private fun MethodCard(data: BatteryAnalyticsSnapshot) = Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Row { Icon(Icons.Filled.Info, null); Spacer(Modifier.width(8.dp)); Text("How this is estimated", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
    Text("Battery-added kWh ÷ meaningful SOC increase. Grid energy is not used because charging losses would inflate capacity.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("SOC is recorded in whole percentages, so the 12% minimum span reduces quantization noise. Capacity is rounded to 0.1 kWh. Small SOC changes, incomplete data, impossible values, and outliers are excluded. This is not Tesla's official battery diagnostic.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("Accepted ${data.capacity.accepted.size} · Excluded ${data.capacity.exclusions.values.sum()}", style = MaterialTheme.typography.bodySmall)
    if (data.capacity.exclusions.isNotEmpty()) {
        val parts = data.capacity.exclusions.entries.map { (reason, count) -> "$count ${stringResource(reason.labelRes())}" }
        Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
} }
@Composable private fun Label(value: String) = Text(value, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
