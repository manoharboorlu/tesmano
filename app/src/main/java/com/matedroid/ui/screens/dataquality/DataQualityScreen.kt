package com.matedroid.ui.screens.dataquality

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.matedroid.R
import com.matedroid.domain.BatteryDataQuality
import com.matedroid.domain.ChargingDataQuality
import com.matedroid.domain.DataQualitySummary
import com.matedroid.domain.DriveDataQuality
import com.matedroid.domain.MetricAvailability
import com.matedroid.domain.QualityReason
import com.matedroid.domain.RangeDataQuality
import com.matedroid.ui.adaptive.LocalAdaptiveLayoutInfo
import com.matedroid.ui.components.explanationRes
import com.matedroid.ui.components.labelRes
import com.matedroid.ui.theme.TesManoSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataQualityScreen(onNavigateBack: () -> Unit, viewModel: DataQualityViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.data_quality_title)) },
            navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } }
        )
    }) { padding ->
        val summary = state.summary
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            summary == null -> Column(Modifier.fillMaxSize().padding(padding).padding(TesManoSpacing.large)) { Text(stringResource(R.string.data_quality_unavailable)) }
            else -> DataQualityContent(summary, Modifier.padding(padding))
        }
    }
}

@Composable
private fun DataQualityContent(summary: DataQualitySummary, modifier: Modifier) {
    val twoPane = LocalAdaptiveLayoutInfo.current.supportsTwoPane
    if (twoPane) {
        Row(modifier.fillMaxSize().padding(TesManoSpacing.medium), horizontalArrangement = Arrangement.spacedBy(TesManoSpacing.medium)) {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TesManoSpacing.medium)) {
                item { DrivesCard(summary.drives) }
                item { ChargingCard(summary.charging) }
            }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TesManoSpacing.medium)) {
                item { BatteryCard(summary.battery) }
                item { RangeCard(summary.range) }
                item { LimitationsCard() }
            }
        }
    } else {
        LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(TesManoSpacing.medium), verticalArrangement = Arrangement.spacedBy(TesManoSpacing.medium)) {
            item { DrivesCard(summary.drives) }
            item { ChargingCard(summary.charging) }
            item { BatteryCard(summary.battery) }
            item { RangeCard(summary.range) }
            item { LimitationsCard() }
        }
    }
}

@Composable private fun SectionTitle(text: String) = Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
@Composable private fun Muted(text: String) = Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
@Composable private fun ReasonLine(reason: QualityReason?) { reason?.let { Muted(stringResource(it.explanationRes())) } }

@Composable
private fun DrivesCard(drives: DriveDataQuality) = Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(TesManoSpacing.medium), verticalArrangement = Arrangement.spacedBy(TesManoSpacing.xSmall)) {
        SectionTitle(stringResource(R.string.data_quality_drives_title))
        Text(stringResource(R.string.data_quality_drives_total, drives.totalDrives), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Muted(stringResource(R.string.data_quality_drives_endpoint_coverage, drives.endpointCoverage.numerator, drives.endpointCoverage.denominator, drives.endpointCoverage.percent ?: 0))
        if (drives.detailEnrichedDrives > 0) Muted(stringResource(R.string.data_quality_drives_detail_enriched, drives.detailEnrichedDrives))
        ReasonLine(drives.quality.reason)
        if (drives.quality.availability != MetricAvailability.AVAILABLE) Muted(stringResource(R.string.data_quality_drives_hint))
    }
}

@Composable
private fun ChargingCard(charging: ChargingDataQuality) = Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(TesManoSpacing.medium), verticalArrangement = Arrangement.spacedBy(TesManoSpacing.xSmall)) {
        SectionTitle(stringResource(R.string.data_quality_charging_title))
        Text(stringResource(R.string.data_quality_charging_coverage, charging.pricedOrFreeSessions, charging.totalSessions, charging.quality.coverage?.percent ?: 0), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Muted(stringResource(R.string.data_quality_charging_breakdown, charging.freeSessions, charging.manualSessions, charging.unavailableSessions))
        charging.unavailableReasons[QualityReason.MISSING_RATE]?.let { Muted(stringResource(R.string.charging_reason_count_missing_rate, it)) }
        charging.unavailableReasons[QualityReason.MISSING_ENERGY]?.let { Muted(stringResource(R.string.charging_reason_count_missing_energy, it)) }
        if (charging.mixedCurrencies) Muted(stringResource(R.string.quality_reason_mixed_currency))
    }
}

@Composable
private fun BatteryCard(battery: BatteryDataQuality) = Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(TesManoSpacing.medium), verticalArrangement = Arrangement.spacedBy(TesManoSpacing.xSmall)) {
        SectionTitle(stringResource(R.string.data_quality_battery_title))
        Text(stringResource(R.string.data_quality_battery_samples, battery.acceptedSamples, battery.excludedSamples), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        battery.quality.confidence?.let { Muted(stringResource(it.labelRes())) }
        Muted(stringResource(if (battery.baselineAvailable) R.string.data_quality_battery_baseline_available else R.string.data_quality_battery_baseline_unavailable))
        Muted(stringResource(if (battery.chargingEfficiencyAvailable) R.string.data_quality_battery_efficiency_available else R.string.data_quality_battery_efficiency_unavailable))
        if (!battery.ratedRangeHistoryAvailable) Muted(stringResource(R.string.data_quality_battery_rated_range_history))
        ReasonLine(battery.quality.reason)
    }
}

@Composable
private fun RangeCard(range: RangeDataQuality) = Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(TesManoSpacing.medium), verticalArrangement = Arrangement.spacedBy(TesManoSpacing.xSmall)) {
        SectionTitle(stringResource(R.string.data_quality_range_title))
        Muted(stringResource(R.string.data_quality_range_window, range.windowLabel))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TesManoSpacing.medium)) {
            EvidenceColumn(stringResource(R.string.range_evidence_battery), range.capacityConfidence?.let { stringResource(it.labelRes()) })
            EvidenceColumn(stringResource(R.string.range_evidence_driving), range.efficiencyConfidence?.let { stringResource(it.labelRes()) })
            EvidenceColumn(stringResource(R.string.range_evidence_overall), range.quality.confidence?.let { stringResource(it.labelRes()) })
        }
        ReasonLine(range.quality.reason)
    }
}

@Composable
private fun RowScope.EvidenceColumn(label: String, value: String?) = Column(Modifier.weight(1f)) {
    Text(value ?: stringResource(R.string.range_evidence_unavailable), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    Muted(label)
}

@Composable
private fun LimitationsCard() = Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(TesManoSpacing.medium), verticalArrangement = Arrangement.spacedBy(TesManoSpacing.xSmall)) {
        SectionTitle(stringResource(R.string.data_quality_limitations_title))
        Muted(stringResource(R.string.data_quality_limitations_rated_range))
        Muted(stringResource(R.string.data_quality_limitations_temperature))
        Muted(stringResource(R.string.data_quality_limitations_no_backfill))
    }
}
