package com.matedroid.ui.screens.tripmap

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.matedroid.R
import com.matedroid.data.api.models.Units
import com.matedroid.data.local.dao.DriveEdgeCoordinateResult
import com.matedroid.domain.ChargeCostPresentation
import com.matedroid.domain.DrivePlaceContext
import com.matedroid.domain.TripDayTotals
import com.matedroid.domain.TripEntry
import com.matedroid.domain.model.UnitFormatter
import com.matedroid.ui.adaptive.LocalAdaptiveLayoutInfo
import com.matedroid.ui.components.MateDroidLoadingPlaceholder
import com.matedroid.ui.components.addChargeMarker
import com.matedroid.ui.components.addRouteEndpointMarker
import com.matedroid.ui.components.applyTesManoDarkMapTreatment
import com.matedroid.ui.theme.CarColorPalettes
import com.matedroid.ui.theme.ChargingGreen
import com.matedroid.util.formatDurationCompact
import com.matedroid.util.formatMedium
import com.matedroid.util.formatTime
import com.matedroid.util.parseInstantAware
import java.time.LocalDate
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

private fun TripEntry.key(): String = when (this) {
    is TripEntry.Drive -> "drive-${summary.driveId}"
    is TripEntry.Charge -> "charge-${summary.chargeId}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripMapScreen(
    carId: Int,
    date: String?,
    exteriorColor: String? = null,
    onNavigateBack: () -> Unit,
    onNavigateToDriveDetail: (Int) -> Unit,
    onNavigateToChargeDetail: (Int) -> Unit,
    viewModel: TripMapViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isDarkTheme = isSystemInDarkTheme()
    val palette = CarColorPalettes.forExteriorColor(exteriorColor, isDarkTheme)

    LaunchedEffect(carId) {
        viewModel.start(carId, date?.let { runCatching { LocalDate.parse(it) }.getOrNull() })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(uiState.date?.formatMedium() ?: stringResource(R.string.trip_map_title))
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::selectPreviousDay, enabled = uiState.hasPreviousDay) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.trip_map_previous_day))
                    }
                    IconButton(onClick = viewModel::selectNextDay, enabled = uiState.hasNextDay) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.trip_map_next_day))
                    }
                }
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                MateDroidLoadingPlaceholder(color = palette.accent)
            }
            uiState.entries.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.trip_map_no_activity),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            else -> TripMapContent(uiState, Modifier.padding(padding), viewModel, onNavigateToDriveDetail, onNavigateToChargeDetail)
        }
    }
}

@Composable
private fun TripMapContent(
    state: TripMapUiState,
    modifier: Modifier,
    viewModel: TripMapViewModel,
    onNavigateToDriveDetail: (Int) -> Unit,
    onNavigateToChargeDetail: (Int) -> Unit
) {
    val twoPane = LocalAdaptiveLayoutInfo.current.supportsTwoPane
    if (twoPane) {
        Row(modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                TripMapCard(state, viewModel::selectEntry)
                TripTotalsCard(state.totals, state.units, state.currencySymbol)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TripTimeline(state, viewModel::selectEntry, onNavigateToDriveDetail, onNavigateToChargeDetail)
            }
        }
    } else {
        Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            TripMapCard(state, viewModel::selectEntry)
            TripTotalsCard(state.totals, state.units, state.currencySymbol)
            TripTimeline(state, viewModel::selectEntry, onNavigateToDriveDetail, onNavigateToChargeDetail)
        }
    }
}

@Composable
private fun TripMapCard(state: TripMapUiState, onSelect: (String?) -> Unit) {
    val driveColorArgb = MaterialTheme.colorScheme.primary.toArgb()
    val chargeColorArgb = ChargingGreen.toArgb()
    val hasAnyLocation = state.driveCoordinates.isNotEmpty() || state.entries.any { it is TripEntry.Charge }

    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.route_map), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(8.dp))) {
                if (!hasAnyLocation) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.trip_map_no_locations),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    AndroidView(
                        factory = { ctx -> MapView(ctx).apply { setTileSource(TileSourceFactory.MAPNIK); setMultiTouchControls(true) } },
                        update = { mapView ->
                            mapView.overlays.clear()
                            mapView.applyTesManoDarkMapTreatment()
                            mapView.setOnTouchListener { v, event ->
                                v.parent?.requestDisallowInterceptTouchEvent(event.pointerCount >= 2)
                                false
                            }
                            val allPoints = mutableListOf<GeoPoint>()

                            state.entries.filterIsInstance<TripEntry.Drive>().forEach { entry ->
                                val id = entry.summary.driveId
                                val coords = state.driveCoordinates[id]
                                val selected = state.selectedKey == entry.key()
                                val color = if (selected) driveColorArgb else (driveColorArgb and 0x00FFFFFF) or 0xB0000000.toInt()
                                coords?.let {
                                    val startPoint = GeoPoint(it.startLatitude, it.startLongitude)
                                    allPoints += startPoint
                                    mapView.addRouteEndpointMarker(
                                        point = startPoint, label = "S", title = entry.summary.startAddress, color = color,
                                        onClick = { onSelect(entry.key()); true }
                                    )
                                    if (it.endLatitude != null && it.endLongitude != null) {
                                        val endPoint = GeoPoint(it.endLatitude, it.endLongitude)
                                        allPoints += endPoint
                                        mapView.addRouteEndpointMarker(
                                            point = endPoint, label = "E", title = entry.summary.endAddress, color = color,
                                            onClick = { onSelect(entry.key()); true }
                                        )
                                    }
                                }
                            }
                            state.entries.filterIsInstance<TripEntry.Charge>().forEach { entry ->
                                val summary = entry.summary
                                val selected = state.selectedKey == entry.key()
                                val color = if (selected) chargeColorArgb else (chargeColorArgb and 0x00FFFFFF) or 0xB0000000.toInt()
                                val point = GeoPoint(summary.latitude, summary.longitude)
                                allPoints += point
                                mapView.addChargeMarker(
                                    point = point, title = summary.address, color = color,
                                    onClick = { onSelect(entry.key()); true }
                                )
                            }

                            if (allPoints.size == 1) {
                                mapView.post {
                                    mapView.controller.setZoom(15.0)
                                    mapView.controller.setCenter(allPoints.first())
                                }
                            } else if (allPoints.size > 1) {
                                val north = allPoints.maxOf { it.latitude }
                                val south = allPoints.minOf { it.latitude }
                                val east = allPoints.maxOf { it.longitude }
                                val west = allPoints.minOf { it.longitude }
                                val latPad = ((north - south) * 0.2).coerceAtLeast(0.002)
                                val lonPad = ((east - west) * 0.2).coerceAtLeast(0.002)
                                val box = BoundingBox(north + latPad, east + lonPad, south - latPad, west - lonPad)
                                mapView.post { mapView.zoomToBoundingBox(box, false); mapView.invalidate() }
                            }
                            mapView.invalidate()
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun TripTotalsCard(totals: TripDayTotals?, units: Units?, currencySymbol: String) {
    if (totals == null) return
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.trip_map_totals_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TotalStat(stringResource(R.string.trip_map_distance), UnitFormatter.formatDistance(totals.totalDistanceKm, units), Modifier.weight(1f))
                TotalStat(stringResource(R.string.trip_map_driving_time), formatDurationCompact(totals.driveDurationMin), Modifier.weight(1f))
                TotalStat(stringResource(R.string.trip_map_charging_time), formatDurationCompact(totals.chargeDurationMin), Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TotalStat(
                    stringResource(R.string.trip_map_driving_energy),
                    totals.driveEnergyKwh?.let { UnitFormatter.formatEnergy(it) } ?: stringResource(R.string.unknown),
                    Modifier.weight(1f)
                )
                TotalStat(
                    stringResource(R.string.stats_avg_efficiency),
                    totals.weightedEfficiencyWhKm?.let { UnitFormatter.formatEfficiency(it, units) } ?: stringResource(R.string.unknown),
                    Modifier.weight(1f)
                )
                TotalStat(stringResource(R.string.trip_map_charging_energy), UnitFormatter.formatEnergy(totals.chargeEnergyKwh), Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            val costText = when {
                totals.chargeCount == 0 -> UnitFormatter.formatCost(0.0, currencySymbol)
                totals.chargeCostMixedCurrencies -> stringResource(R.string.trip_map_mixed_currency)
                totals.chargeCostMinorUnits == null -> stringResource(R.string.trip_map_cost_unavailable)
                else -> UnitFormatter.formatCost(totals.chargeCostMinorUnits / 100.0, currencySymbol)
            }
            TotalStat(stringResource(R.string.trip_map_charging_cost), costText, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun TotalStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TripTimeline(
    state: TripMapUiState,
    onSelect: (String?) -> Unit,
    onNavigateToDriveDetail: (Int) -> Unit,
    onNavigateToChargeDetail: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        state.entries.forEach { entry ->
            val selected = state.selectedKey == entry.key()
            when (entry) {
                is TripEntry.Drive -> DriveTimelineCard(
                    entry.summary, state.drivePlaces[entry.summary.driveId], state.units, selected,
                    onClick = { onSelect(entry.key()); onNavigateToDriveDetail(entry.summary.driveId) }
                )
                is TripEntry.Charge -> ChargeTimelineCard(
                    entry.summary, state.chargeCosts[entry.summary.chargeId], state.units, state.currencySymbol, selected,
                    onClick = { onSelect(entry.key()); onNavigateToChargeDetail(entry.summary.chargeId) }
                )
            }
        }
    }
}

@Composable
private fun DriveTimelineCard(
    summary: com.matedroid.data.local.entity.DriveSummary,
    placeContext: DrivePlaceContext?,
    units: Units?,
    selected: Boolean,
    onClick: () -> Unit
) {
    val unknown = stringResource(R.string.activity_unknown_location)
    val start = placeContext?.start?.name ?: summary.startAddress.ifBlank { unknown }
    val end = placeContext?.end?.name ?: summary.endAddress.ifBlank { unknown }
    val time = parseInstantAware(summary.startDate)?.formatTime() ?: ""
    val details = buildList {
        if (summary.distance > 0) add(UnitFormatter.formatDistance(summary.distance, units))
        if (summary.durationMin > 0) add(formatDurationCompact(summary.durationMin))
        summary.efficiency?.takeIf { it > 0 && summary.distance >= 1 }?.let { add(UnitFormatter.formatEfficiency(it, units, 0)) }
    }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.DirectionsCar, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 2.dp))
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(stringResource(R.string.activity_drive), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("$start → $end", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(3.dp))
                Text(time, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (details.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(details.joinToString("  ·  "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ChargeTimelineCard(
    summary: com.matedroid.data.local.entity.ChargeSummary,
    cost: ChargeCostPresentation?,
    units: Units?,
    currencySymbol: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val unknown = stringResource(R.string.activity_unknown_location)
    val location = cost?.place?.name ?: summary.address.ifBlank { unknown }
    val time = parseInstantAware(summary.startDate)?.formatTime() ?: ""
    val costText = when {
        cost == null || (cost.costMinorUnits == null && !cost.isFree) -> stringResource(R.string.trip_map_cost_unavailable)
        cost.isFree -> stringResource(R.string.charge_free)
        else -> UnitFormatter.formatCost(cost.costMinorUnits!! / 100.0, currencySymbol)
    }
    val details = buildList {
        if (summary.durationMin > 0) add(formatDurationCompact(summary.durationMin))
        if (summary.energyAdded > 0) add(stringResource(R.string.activity_energy_added, UnitFormatter.formatEnergy(summary.energyAdded)))
        add(costText)
    }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.Bolt, null, tint = ChargingGreen, modifier = Modifier.padding(top = 2.dp))
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(stringResource(R.string.activity_charge), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(location, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(3.dp))
                Text(time, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (details.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(details.joinToString("  ·  "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
