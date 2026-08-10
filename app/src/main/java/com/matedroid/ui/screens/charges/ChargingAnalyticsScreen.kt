package com.matedroid.ui.screens.charges

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Bolt
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.matedroid.domain.ChargingAnalyticsPeriod
import com.matedroid.domain.ChargingAnalyticsSnapshot
import com.matedroid.ui.adaptive.LocalAdaptiveLayoutInfo
import com.matedroid.ui.components.BarChartData
import com.matedroid.ui.components.InteractiveBarChart
import com.matedroid.ui.theme.ChargingGreen
import java.math.BigDecimal
import java.math.RoundingMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChargingAnalyticsScreen(
    carId: Int,
    onNavigateBack: () -> Unit,
    onNavigateToChargeDetail: (Int) -> Unit,
    viewModel: ChargingAnalyticsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(carId) { viewModel.setCarId(carId) }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Charging analytics") },
            navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
        )
    }) { padding ->
        val snapshot = state.snapshot
        if (snapshot == null) {
            Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.Center) {
                Text(if (state.loading) "Loading local charging history…" else "No charging history yet", style = MaterialTheme.typography.titleMedium)
                if (!state.loading) Text("Charging costs appear once cached charge sessions are available.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            ChargingAnalyticsContent(snapshot, state.period, viewModel::selectPeriod, onNavigateToChargeDetail, Modifier.padding(padding))
        }
    }
}

@Composable
private fun ChargingAnalyticsContent(
    snapshot: ChargingAnalyticsSnapshot,
    period: ChargingAnalyticsPeriod,
    onPeriod: (ChargingAnalyticsPeriod) -> Unit,
    onCharge: (Int) -> Unit,
    modifier: Modifier
) {
    val twoPane = LocalAdaptiveLayoutInfo.current.supportsTwoPane
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PeriodSelector(period, onPeriod) }
        item { SummaryCard(snapshot) }
        if (twoPane) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        TrendCard(snapshot)
                        SecondaryMetrics(snapshot)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) { BreakdownCard(snapshot) }
                }
            }
        } else {
            item { TrendCard(snapshot) }
            item { BreakdownCard(snapshot) }
            item { SecondaryMetrics(snapshot) }
        }
        if (snapshot.sessions.isNotEmpty()) {
            item { Text("Sessions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            items(snapshot.sessions.size.coerceAtMost(12)) { index ->
                val session = snapshot.sessions.sortedByDescending { it.localDate }[index]
                Card(modifier = Modifier.fillMaxWidth().clickable { onCharge(session.summary.chargeId) }) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Bolt, null, tint = ChargingGreen)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(session.locationName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${session.localDate} · ${session.category}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(costText(session.cost.costMinorUnits, session.cost.currencyCode), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable private fun PeriodSelector(current: ChargingAnalyticsPeriod, onPeriod: (ChargingAnalyticsPeriod) -> Unit) =
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        items(listOf(ChargingAnalyticsPeriod.CurrentMonth, ChargingAnalyticsPeriod.PreviousMonth, ChargingAnalyticsPeriod.CurrentYear, ChargingAnalyticsPeriod.AllTime).size) { index ->
            val period = listOf(ChargingAnalyticsPeriod.CurrentMonth, ChargingAnalyticsPeriod.PreviousMonth, ChargingAnalyticsPeriod.CurrentYear, ChargingAnalyticsPeriod.AllTime)[index]
            FilterChip(selected = current == period, onClick = { onPeriod(period) }, label = { Text(period.label) })
        }
    }

@Composable private fun SummaryCard(snapshot: ChargingAnalyticsSnapshot) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(snapshot.period.label.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if (snapshot.mixedCurrencies) "Multiple currencies" else costText(snapshot.totalCostMinorUnits, snapshot.currencyCode), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(if (snapshot.totalCostMinorUnits == null) "Charging cost unavailable for this period" else "Estimated charging cost", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric("Cost-basis energy", kwh(snapshot.costBasisKwh))
                Metric("Effective rate", snapshot.weightedCostPerKwh?.let { "\$${it.setScale(3, RoundingMode.HALF_UP)} / kWh" } ?: "Unavailable")
                Metric("Sessions", snapshot.totalSessions.toString())
            }
            Text("Cost coverage ${snapshot.coveragePercent ?: 0}% · ${snapshot.pricedSessions} of ${snapshot.totalSessions} costed/free · ${snapshot.unavailableSessions} unavailable", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable private fun TrendCard(snapshot: ChargingAnalyticsSnapshot) {
    Card { Column(Modifier.padding(16.dp)) {
        Text("Cost trend", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(if (snapshot.period == ChargingAnalyticsPeriod.CurrentMonth || snapshot.period == ChargingAnalyticsPeriod.PreviousMonth) "Daily charging spend" else "Monthly charging spend", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        val chart = snapshot.trend.map { BarChartData(it.label.takeLast(if (snapshot.period == ChargingAnalyticsPeriod.CurrentMonth) 2 else 5), (it.costMinorUnits ?: 0).toDouble() / 100, costText(it.costMinorUnits, it.currencyCode)) }
        when {
            chart.isEmpty() -> Text("No charge sessions in this period.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            chart.all { it.value == 0.0 } -> Text("All charging sessions in this period were free.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> InteractiveBarChart(chart, barColor = MaterialTheme.colorScheme.error, showEveryNthLabel = (chart.size / 6).coerceAtLeast(1), valueFormatter = { "\$%.2f".format(it) })
        }
    } }
}

@Composable private fun BreakdownCard(snapshot: ChargingAnalyticsSnapshot) {
    Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("By charging location", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (snapshot.breakdown.isEmpty()) Text("No charge sessions in this period.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        snapshot.breakdown.forEach { item ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${item.category} · ${kwh(item.costBasisKwh)} · ${item.sessions} sessions" + if (item.unavailableSessions > 0) " · ${item.unavailableSessions} unavailable" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(costText(item.costMinorUnits, item.currencyCode), fontWeight = FontWeight.SemiBold)
            }
        }
    } }
}

@Composable private fun SecondaryMetrics(snapshot: ChargingAnalyticsSnapshot) = Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text("Coverage & energy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Text("${snapshot.freeSessions} free sessions · ${snapshot.manualSessions} manual override${if (snapshot.manualSessions == 1) "" else "s"}")
    Text("${kwh(snapshot.gridKwh)} grid-reported · ${kwh(snapshot.batteryFallbackKwh)} battery-added fallback", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("Charging spend / mile", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(snapshot.spendPerMile?.let { "\$${it.setScale(3, RoundingMode.HALF_UP)} / mi" } ?: "Unavailable", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    Text("Uses drive distance in the same period; it is spend, not exact energy cost per driven mile.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
} }

@Composable private fun Metric(label: String, value: String) = Column { Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold); Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
private fun costText(minor: Long?, currency: String?): String = if (minor == null || currency == null) "Unavailable" else if (currency == "USD") "\$%.2f".format(minor / 100.0) else "$currency %.2f".format(minor / 100.0)
private fun kwh(value: BigDecimal): String = "%.1f kWh".format(value)
