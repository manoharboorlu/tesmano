package com.matedroid.ui.screens.charging

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.matedroid.R
import com.matedroid.domain.AcDcBreakdown
import com.matedroid.domain.ChargingHeadline
import com.matedroid.domain.ChargingPeriod
import com.matedroid.domain.ChargingLabTrendPoint
import com.matedroid.domain.PlaceChargingBreakdown
import com.matedroid.domain.RankedCharge
import com.matedroid.domain.model.UnitFormatter
import com.matedroid.ui.adaptive.LocalAdaptiveLayoutInfo
import com.matedroid.ui.components.BarChartData
import com.matedroid.ui.components.InteractiveBarChart
import com.matedroid.ui.components.MateDroidLoadingPlaceholder
import com.matedroid.ui.theme.CarColorPalettes
import com.matedroid.ui.theme.ChargingGreen
import com.matedroid.ui.theme.PerformanceRed
import com.matedroid.util.parseInstantAware

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChargingLabScreen(
    carId: Int,
    exteriorColor: String? = null,
    onNavigateBack: () -> Unit,
    onNavigateToChargeDetail: (Int) -> Unit,
    viewModel: ChargingLabViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isDarkTheme = isSystemInDarkTheme()
    val palette = CarColorPalettes.forExteriorColor(exteriorColor, isDarkTheme)

    LaunchedEffect(carId) { viewModel.start(carId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.charging_lab_title)) },
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
                uiState.headline?.sessionCount == 0 -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.charging_no_data), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> ChargingLabContent(uiState, onNavigateToChargeDetail)
            }
        }
    }
}

@Composable
private fun PeriodChips(selected: ChargingPeriod, onSelect: (ChargingPeriod) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val periods = listOf(
            ChargingPeriod.Last7Days to R.string.efficiency_period_7d,
            ChargingPeriod.Last30Days to R.string.efficiency_period_30d,
            ChargingPeriod.Last90Days to R.string.efficiency_period_90d,
            ChargingPeriod.AllTime to R.string.efficiency_period_all
        )
        periods.forEach { (period, labelRes) ->
            FilterChip(selected = selected == period, onClick = { onSelect(period) }, label = { Text(stringResource(labelRes)) })
        }
    }
}

@Composable
private fun ChargingLabContent(state: ChargingLabUiState, onNavigateToChargeDetail: (Int) -> Unit) {
    val twoPane = LocalAdaptiveLayoutInfo.current.supportsTwoPane
    if (twoPane) {
        Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                HeadlineCard(state)
                TrendCard(state.trend)
                CurveCard(state)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                PlaceCard(state.placeBreakdown, state.currencySymbol)
                AcDcCard(state)
                SocCard(state)
                BestWorstCard(state, onNavigateToChargeDetail)
            }
        }
    } else {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            HeadlineCard(state)
            TrendCard(state.trend)
            PlaceCard(state.placeBreakdown, state.currencySymbol)
            AcDcCard(state)
            SocCard(state)
            CurveCard(state)
            BestWorstCard(state, onNavigateToChargeDetail)
        }
    }
}

private fun formatPercent(value: Double?): String = value?.let { "%.1f%%".format(it) } ?: "—"
private fun formatKw(value: Double?): String = value?.let { "%.1f kW".format(it) } ?: "—"
private fun formatCost(minor: Long?, currency: String?, symbol: String, unavailableText: String): String =
    if (minor == null || currency == null) unavailableText else "$symbol%.2f".format(minor / 100.0)

@Composable
private fun HeadlineCard(state: ChargingLabUiState) {
    val headline = state.headline ?: return
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Text(
                formatPercent(headline.weightedEfficiencyPercent),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.charging_quality_coverage, headline.coherentSessionCount, headline.sessionCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeadlineStat(stringResource(R.string.charging_sessions_label), headline.sessionCount.toString(), Modifier.weight(1f))
                HeadlineStat(stringResource(R.string.charging_battery_energy), UnitFormatter.formatEnergy(headline.batteryEnergyKwh), Modifier.weight(1f))
                HeadlineStat(stringResource(R.string.charging_peak_power), headline.peakPowerKw?.let { "$it kW" } ?: "—", Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeadlineStat(stringResource(R.string.charging_avg_duration), headline.avgDurationMin?.let { "%.0f min".format(it) } ?: "—", Modifier.weight(1f))
                HeadlineStat(stringResource(R.string.charging_avg_rate), headline.avgChargingRateKw?.let { "%.1f kWh/hr".format(it) } ?: "—", Modifier.weight(1f))
                state.costSummary?.let { cost ->
                    HeadlineStat(stringResource(R.string.charging_total_cost), formatCost(cost.totalCostMinorUnits, cost.currencyCode, state.currencySymbol, stringResource(R.string.charging_cost_unavailable)), Modifier.weight(1f))
                }
            }
            state.costSummary?.effectiveCostPerKwh?.let { rate ->
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.charging_effective_rate, "${state.currencySymbol}${rate.stripTrailingZeros().toPlainString()}"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
private fun TrendCard(trend: List<ChargingLabTrendPoint>) {
    if (trend.isEmpty()) return
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.charging_trend_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            val barData = remember(trend) {
                trend.map { point ->
                    BarChartData(
                        label = point.label,
                        value = point.batteryEnergyKwh,
                        displayValue = "${UnitFormatter.formatEnergy(point.batteryEnergyKwh)}" +
                            (point.weightedEfficiencyPercent?.let { " · ${formatPercent(it)}" } ?: "")
                    )
                }
            }
            InteractiveBarChart(
                data = barData,
                modifier = Modifier.fillMaxWidth(),
                barColor = ChargingGreen,
                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                showEveryNthLabel = trendLabelStride(barData.size),
                valueFormatter = { "%.0f kWh".format(it) }
            )
        }
    }
}

private fun trendLabelStride(count: Int): Int = when {
    count <= 10 -> 1
    count <= 20 -> 2
    else -> (count / 8).coerceAtLeast(3)
}

@Composable
private fun PlaceCard(breakdown: List<PlaceChargingBreakdown>, currencySymbol: String) {
    if (breakdown.isEmpty()) return
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.charging_place_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            breakdown.forEach { row ->
                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(row.category, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(formatCost(row.costMinorUnits, row.currencyCode, currencySymbol, stringResource(R.string.charging_cost_unavailable)), style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        "${row.sessionCount} sessions · ${UnitFormatter.formatEnergy(row.batteryEnergyKwh)} · ${formatPercent(row.weightedEfficiencyPercent)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun AcDcCard(state: ChargingLabUiState) {
    val breakdown = state.acDcBreakdown
    val total = state.headline?.sessionCount ?: 0
    val sparse = breakdown.isEmpty() || breakdown.size < 2 || (total > 0 && state.acDcClassifiedCount * 2 < total)
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.charging_acdc_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            if (sparse) {
                Text(
                    stringResource(R.string.charging_acdc_sparse, state.acDcClassifiedCount, total),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                breakdown.forEach { row -> AcDcRow(row) }
            }
        }
    }
}

@Composable
private fun AcDcRow(row: AcDcBreakdown) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(if (row.isFastCharger) "DC" else "AC", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "${row.sessionCount} · ${UnitFormatter.formatEnergy(row.batteryEnergyKwh)} · ${formatPercent(row.weightedEfficiencyPercent)}" +
                (row.medianPeakPowerKw?.let { " · ${it}kW median" } ?: ""),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SocCard(state: ChargingLabUiState) {
    if (state.socBands.isEmpty() || state.socBands.all { it.sessionCount == 0 }) return
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.charging_soc_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            state.socBands.forEach { band ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(band.band.label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${band.sessionCount} · ${UnitFormatter.formatEnergy(band.batteryEnergyKwh)}", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun CurveCard(state: ChargingLabUiState) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.charging_curve_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            if (state.curveCoverageCount == 0) {
                Text(stringResource(R.string.charging_curve_unavailable), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            Text(
                pluralStringResource(R.plurals.charging_curve_coverage, state.curveCoverageCount, state.curveCoverageCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            val barData = remember(state.curveBands) {
                state.curveBands.filterNotNull().map { band -> BarChartData(band.label, band.avgPowerKw, formatKw(band.avgPowerKw)) }
            }
            if (barData.isNotEmpty()) {
                InteractiveBarChart(
                    data = barData,
                    modifier = Modifier.fillMaxWidth(),
                    barColor = MaterialTheme.colorScheme.primary,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    valueFormatter = { "%.0f kW".format(it) }
                )
            }
            state.taper?.let { taper ->
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.charging_taper_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${formatKw(taper.peakPowerKw)} peak near ${taper.peakSoc}% SOC" +
                        (taper.taperStartSoc?.let { " · tapering by ~$it% SOC" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun BestWorstCard(state: ChargingLabUiState, onNavigateToChargeDetail: (Int) -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            RankedSection(stringResource(R.string.charging_fastest), state.fastestSessions, ChargingGreen, onNavigateToChargeDetail) { "%.1f kWh/hr".format(it) }
            RankedSection(stringResource(R.string.charging_most_efficient), state.mostEfficientSessions, ChargingGreen, onNavigateToChargeDetail) { formatPercent(it) }
            RankedSection(stringResource(R.string.charging_largest_energy), state.largestEnergySessions, MaterialTheme.colorScheme.primary, onNavigateToChargeDetail) { UnitFormatter.formatEnergy(it) }
            RankedSection(stringResource(R.string.charging_longest_session), state.longestSessions, PerformanceRed, onNavigateToChargeDetail) { "%.0f min".format(it) }
        }
    }
}

@Composable
private fun RankedSection(
    title: String,
    sessions: List<RankedCharge>,
    accent: Color,
    onNavigateToChargeDetail: (Int) -> Unit,
    formatMetric: (Double) -> String
) {
    if (sessions.isEmpty()) return
    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = accent)
    sessions.forEach { ranked ->
        val time = parseInstantAware(ranked.summary.startDate)?.let { "${it.toLocalDate()}" } ?: ""
        Row(
            Modifier.fillMaxWidth().clickable { onNavigateToChargeDetail(ranked.summary.chargeId) }.padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text(ranked.summary.address, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                Text(time, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(formatMetric(ranked.metricValue), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
    }
    Spacer(Modifier.height(10.dp))
}
