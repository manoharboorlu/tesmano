package com.matedroid.ui.screens.efficiency

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.matedroid.R
import com.matedroid.data.api.models.Units
import com.matedroid.domain.BandResult
import com.matedroid.domain.BaselineComparison
import com.matedroid.domain.EfficiencyPeriod
import com.matedroid.domain.RankedDrive
import com.matedroid.domain.TrendPoint
import com.matedroid.domain.model.UnitFormatter
import com.matedroid.ui.adaptive.LocalAdaptiveLayoutInfo
import com.matedroid.ui.components.BarChartData
import com.matedroid.ui.components.InteractiveBarChart
import com.matedroid.ui.components.MateDroidLoadingPlaceholder
import com.matedroid.ui.theme.CarColorPalettes
import com.matedroid.ui.theme.ChargingGreen
import com.matedroid.ui.theme.PerformanceRed
import com.matedroid.ui.theme.StatusSuccess
import com.matedroid.util.formatDurationCompact
import com.matedroid.util.parseInstantAware

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EfficiencyLabScreen(
    carId: Int,
    exteriorColor: String? = null,
    onNavigateBack: () -> Unit,
    onNavigateToDriveDetail: (Int) -> Unit,
    viewModel: EfficiencyLabViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isDarkTheme = isSystemInDarkTheme()
    val palette = CarColorPalettes.forExteriorColor(exteriorColor, isDarkTheme)

    LaunchedEffect(carId) { viewModel.start(carId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.efficiency_lab_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PeriodChips(uiState.period, viewModel::selectPeriod)
            when {
                uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    MateDroidLoadingPlaceholder(color = palette.accent)
                }
                uiState.periodSummary?.driveCount == 0 -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.efficiency_no_data), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> EfficiencyLabContent(uiState, onNavigateToDriveDetail)
            }
        }
    }
}

@Composable
private fun PeriodChips(selected: EfficiencyPeriod, onSelect: (EfficiencyPeriod) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val periods = listOf(
            EfficiencyPeriod.Last7Days to R.string.efficiency_period_7d,
            EfficiencyPeriod.Last30Days to R.string.efficiency_period_30d,
            EfficiencyPeriod.Last90Days to R.string.efficiency_period_90d,
            EfficiencyPeriod.AllTime to R.string.efficiency_period_all
        )
        periods.forEach { (period, labelRes) ->
            FilterChip(selected = selected == period, onClick = { onSelect(period) }, label = { Text(stringResource(labelRes)) })
        }
    }
}

@Composable
private fun EfficiencyLabContent(state: EfficiencyLabUiState, onNavigateToDriveDetail: (Int) -> Unit) {
    val twoPane = LocalAdaptiveLayoutInfo.current.supportsTwoPane
    if (twoPane) {
        Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                HeadlineCard(state)
                TrendCard(state.trend, state.units)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                FactorCard(stringResource(R.string.efficiency_speed_title), state.speedBands, { it.label }, state.units)
                FactorCard(stringResource(R.string.efficiency_trip_length_title), state.tripLengthBands, { it.label }, state.units)
                if (state.tempDataAvailable) {
                    FactorCard(stringResource(R.string.efficiency_temp_title), state.tempBands, { it.label }, state.units)
                }
                BestWorstCard(state, onNavigateToDriveDetail)
            }
        }
    } else {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            HeadlineCard(state)
            TrendCard(state.trend, state.units)
            FactorCard(stringResource(R.string.efficiency_speed_title), state.speedBands, { it.label }, state.units)
            FactorCard(stringResource(R.string.efficiency_trip_length_title), state.tripLengthBands, { it.label }, state.units)
            if (state.tempDataAvailable) {
                FactorCard(stringResource(R.string.efficiency_temp_title), state.tempBands, { it.label }, state.units)
            }
            BestWorstCard(state, onNavigateToDriveDetail)
        }
    }
}

@Composable
private fun HeadlineCard(state: EfficiencyLabUiState) {
    val summary = state.periodSummary ?: return
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Text(
                summary.weightedEfficiency?.let { UnitFormatter.formatEfficiency(it, state.units) } ?: stringResource(R.string.unknown),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.efficiency_quality_coverage, summary.validDriveCount, summary.driveCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeadlineStat(stringResource(R.string.efficiency_distance_driven), UnitFormatter.formatDistance(summary.totalDistance, state.units), Modifier.weight(1f))
                HeadlineStat(stringResource(R.string.efficiency_energy_used), UnitFormatter.formatEnergy(summary.totalEnergyKwh), Modifier.weight(1f))
                HeadlineStat(stringResource(R.string.efficiency_drive_count_label), summary.driveCount.toString(), Modifier.weight(1f))
            }
            state.baselineComparison?.let { comparison -> BaselineRow(comparison, state.units) }
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.efficiency_rated_benchmark_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun BaselineRow(comparison: BaselineComparison, units: Units?) {
    Spacer(Modifier.height(12.dp))
    Column {
        Text(stringResource(R.string.efficiency_personal_baseline), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(UnitFormatter.formatEfficiency(comparison.baselineWhPerUnit, units), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (comparison.isEffectivelyBaseline) {
            Text(
                stringResource(R.string.efficiency_matches_baseline),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val percentText = "%.1f%%".format(kotlin.math.abs(comparison.percentBetter))
            val moreEfficient = comparison.percentBetter > 0
            Text(
                stringResource(if (moreEfficient) R.string.efficiency_more_efficient else R.string.efficiency_less_efficient, percentText),
                style = MaterialTheme.typography.bodyMedium,
                color = if (moreEfficient) StatusSuccess else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HeadlineStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TrendCard(trend: List<TrendPoint>, units: Units?) {
    if (trend.isEmpty()) return
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.efficiency_trend_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            val barData = remember(trend, units) {
                trend.map { point ->
                    BarChartData(
                        label = point.label,
                        value = point.weightedEfficiency ?: 0.0,
                        displayValue = point.weightedEfficiency?.let {
                            "${UnitFormatter.formatEfficiency(it, units)} · ${UnitFormatter.formatDistance(point.distance, units, 0)}"
                        } ?: "—"
                    )
                }
            }
            InteractiveBarChart(
                data = barData,
                modifier = Modifier.fillMaxWidth(),
                barColor = MaterialTheme.colorScheme.primary,
                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                showEveryNthLabel = trendLabelStride(barData.size),
                valueFormatter = { UnitFormatter.formatEfficiency(it, units, 0) }
            )
        }
    }
}

/** Keeps roughly 8-10 x-axis labels visible regardless of how many trend points there are. */
private fun trendLabelStride(count: Int): Int = when {
    count <= 10 -> 1
    count <= 20 -> 2
    else -> (count / 8).coerceAtLeast(3)
}

@Composable
private fun <T> FactorCard(title: String, bands: List<BandResult<T>>, labelOf: (T) -> String, units: Units?) {
    if (bands.isEmpty()) return
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            bands.forEach { result ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(labelOf(result.band), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row {
                        Text(
                            result.summary.weightedEfficiency?.let { UnitFormatter.formatEfficiency(it, units, 0) } ?: stringResource(R.string.unknown),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (result.summary.validDriveCount > 0) {
                            Text(
                                "  (${result.summary.validDriveCount})",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.efficiency_association_disclaimer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun BestWorstCard(state: EfficiencyLabUiState, onNavigateToDriveDetail: (Int) -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.efficiency_min_distance_filter, UnitFormatter.formatDistance(state.minDistanceFilter, state.units, 0)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.efficiency_best_drives), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = ChargingGreen)
            state.bestDrives.forEach { RankedDriveRow(it, state.units, onClick = { onNavigateToDriveDetail(it.summary.driveId) }) }
            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.efficiency_worst_drives), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = PerformanceRed)
            state.worstDrives.forEach { RankedDriveRow(it, state.units, onClick = { onNavigateToDriveDetail(it.summary.driveId) }) }
        }
    }
}

@Composable
private fun RankedDriveRow(ranked: RankedDrive, units: Units?, onClick: () -> Unit) {
    val time = parseInstantAware(ranked.summary.startDate)?.let { "${it.toLocalDate()}" } ?: ""
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "${ranked.summary.startAddress} → ${ranked.summary.endAddress}",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1
            )
            Text(
                "$time · ${UnitFormatter.formatDistance(ranked.summary.distance, units)} · ${formatDurationCompact(ranked.summary.durationMin)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(UnitFormatter.formatEfficiency(ranked.efficiency, units, 0), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}
