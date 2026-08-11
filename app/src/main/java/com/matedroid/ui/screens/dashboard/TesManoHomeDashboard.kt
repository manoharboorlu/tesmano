package com.matedroid.ui.screens.dashboard

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.matedroid.R
import com.matedroid.data.api.models.CarExterior
import com.matedroid.data.api.models.CarData
import com.matedroid.data.api.models.CarStatus
import com.matedroid.data.api.models.Units
import com.matedroid.data.local.CarImageOverride
import com.matedroid.data.local.entity.ChargeSummary
import com.matedroid.data.local.entity.DriveEndpointCache
import com.matedroid.data.local.entity.DriveSummary
import com.matedroid.domain.BatteryConfidence
import com.matedroid.domain.ChargeCostPresentation
import com.matedroid.domain.RealWorldRangeCalculator
import com.matedroid.domain.efficiencyOrNull
import com.matedroid.domain.model.CarImageResolver
import com.matedroid.domain.model.Trip
import com.matedroid.domain.model.UnitFormatter
import com.matedroid.ui.adaptive.LocalAdaptiveLayoutInfo
import com.matedroid.ui.components.addRouteEndpointMarker
import com.matedroid.ui.components.applyTesManoDarkMapTreatment
import com.matedroid.ui.theme.PerformanceRed
import com.matedroid.ui.theme.ChargingGreen
import com.matedroid.ui.theme.StatusSuccess
import com.matedroid.ui.theme.TesManoSpacing
import com.matedroid.ui.screens.trips.displayName
import com.matedroid.util.formatDurationCompact
import com.matedroid.util.parseInstantAware
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime

/**
 * Phase 2A's Home composition.  It deliberately consumes the dashboard's existing state rather
 * than adding a parallel query path: cached/current status, summary counts and trip data are
 * enough to make Home useful without fetching telemetry or waking the vehicle.
 */
@Composable
internal fun TesManoHomeDashboard(
    carId: Int,
    status: CarStatus,
    units: Units?,
    carName: String?,
    carModel: String?,
    carTrimBadging: String?,
    carExterior: CarExterior?,
    imageOverride: CarImageOverride?,
    totalCharges: Int?,
    totalDrives: Int?,
    latestTrip: Trip?,
    isRefreshing: Boolean,
    onNavigateToBattery: () -> Unit,
    onNavigateToCharges: () -> Unit,
    onNavigateToDrives: () -> Unit,
    onNavigateToTrips: () -> Unit,
    onNavigateToMileage: () -> Unit,
    onNavigateToDriveDetail: (Int) -> Unit = {},
    onNavigateToChargeDetail: (Int) -> Unit = {}
) {
    val adaptive = LocalAdaptiveLayoutInfo.current
    val scrollState = rememberScrollState()
    val sectionSpacing = TesManoSpacing.medium

    Column(
        modifier = Modifier
            .verticalScroll(scrollState)
            .padding(horizontal = TesManoSpacing.medium, vertical = TesManoSpacing.small),
        verticalArrangement = Arrangement.spacedBy(sectionSpacing)
    ) {
        if (isRefreshing) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = PerformanceRed,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        }

        if (adaptive.supportsTwoPane) {
            val cockpitViewModel: HomeCockpitViewModel = hiltViewModel()
            LaunchedEffect(carId) { cockpitViewModel.start(carId) }
            val cockpit by cockpitViewModel.uiState.collectAsStateWithLifecycle()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(TesManoSpacing.medium),
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(sectionSpacing)
                ) {
                    VehicleHero(
                        status = status,
                        units = units,
                        carName = carName,
                        carModel = carModel,
                        carTrimBadging = carTrimBadging,
                        carExterior = carExterior,
                        imageOverride = imageOverride,
                        expanded = false,
                        onNavigateToBattery = onNavigateToBattery
                    )
                    LastDriveCard(
                        drive = cockpit.lastDrive,
                        endpoint = cockpit.lastDriveEndpoint,
                        units = cockpit.units ?: units,
                        onClick = { cockpit.lastDrive?.let { onNavigateToDriveDetail(it.driveId) } }
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(sectionSpacing)
                ) {
                    RealWorldRangeCard(cockpit, status, onClick = onNavigateToBattery)
                    TodayCard(cockpit)
                    EfficiencyCard(cockpit)
                    BatteryEstimateCard(cockpit, onClick = onNavigateToBattery)
                    LastChargeCard(
                        charge = cockpit.lastCharge,
                        cost = cockpit.lastChargeCost,
                        units = cockpit.units ?: units,
                        onClick = { cockpit.lastCharge?.let { onNavigateToChargeDetail(it.chargeId) } }
                    )
                }
            }
        } else {
            VehicleHero(
                status = status,
                units = units,
                carName = carName,
                carModel = carModel,
                carTrimBadging = carTrimBadging,
                carExterior = carExterior,
                imageOverride = imageOverride,
                expanded = false,
                onNavigateToBattery = onNavigateToBattery
            )
            RecentActivity(
                latestTrip = latestTrip,
                units = units,
                totalDrives = totalDrives,
                totalCharges = totalCharges,
                onNavigateToTrips = onNavigateToTrips,
                onNavigateToDrives = onNavigateToDrives,
                onNavigateToCharges = onNavigateToCharges
            )
            QuickInsights(
                status = status,
                units = units,
                latestTrip = latestTrip,
                onNavigateToMileage = onNavigateToMileage,
                onNavigateToBattery = onNavigateToBattery
            )
        }
        Spacer(Modifier.height(TesManoSpacing.small))
    }
}

@Composable
private fun VehicleHero(
    status: CarStatus,
    units: Units?,
    carName: String?,
    carModel: String?,
    carTrimBadging: String?,
    carExterior: CarExterior?,
    imageOverride: CarImageOverride?,
    expanded: Boolean,
    onNavigateToBattery: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(20.dp),
        onClick = onNavigateToBattery
    ) {
        Column(modifier = Modifier.padding(TesManoSpacing.medium)) {
            Text(
                text = carName ?: status.displayName ?: "Tesla",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val descriptor = HomePresentation.vehicleIdentityDescriptor(carModel, carTrimBadging)
            if (descriptor.isNotBlank()) {
                Text(
                    text = descriptor,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                VehicleState(status)
            }
            TesManoVehicleImage(
                carModel = carModel,
                carTrimBadging = carTrimBadging,
                carExterior = carExterior,
                imageOverride = imageOverride,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (expanded) 250.dp else 190.dp)
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
            Spacer(Modifier.height(TesManoSpacing.small))
            Row(verticalAlignment = Alignment.Bottom) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = status.batteryLevel?.let { "$it%" } ?: "—",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "STATE OF CHARGE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = status.ratedBatteryRangeKm?.let { UnitFormatter.formatDistance(it, units, decimals = 0) } ?: "—",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "RATED RANGE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(TesManoSpacing.small))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                HomePresentation.freshnessLabel(status.stateSince)?.let { freshness ->
                    Text(
                        text = freshness,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                status.odometer?.let { odometer ->
                    Text(
                        text = "Odometer · ${UnitFormatter.formatDistance(odometer, units, decimals = 0)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

@Composable
private fun VehicleState(status: CarStatus) {
    val label = HomePresentation.vehicleStateLabel(status.state)
    val color = when (label) {
        "Charging" -> ChargingGreen
        "Driving" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(color = color.copy(alpha = 0.16f), shape = RoundedCornerShape(50)) {
        Text(
            text = label.uppercase(),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun RecentActivity(
    latestTrip: Trip?,
    units: Units?,
    totalDrives: Int?,
    totalCharges: Int?,
    onNavigateToTrips: () -> Unit,
    onNavigateToDrives: () -> Unit,
    onNavigateToCharges: () -> Unit
) {
    HomeSection(title = "RECENT ACTIVITY") {
        if (latestTrip != null) {
            ActivityLead(
                title = latestTrip.displayName(),
                detail = "${UnitFormatter.formatDistance(latestTrip.totalDistance, units, decimals = 0)} · ${UnitFormatter.formatEnergy(latestTrip.totalEnergyConsumed)}",
                onClick = onNavigateToTrips
            )
        } else {
            ActivityLead(
                title = "No recent road trips",
                detail = "Recorded drives remain available in Activity.",
                onClick = onNavigateToDrives
            )
        }
        Spacer(Modifier.height(TesManoSpacing.small))
        Row(horizontalArrangement = Arrangement.spacedBy(TesManoSpacing.small)) {
            SummaryMetric(
                value = totalDrives?.let { "%,d".format(it) } ?: "—",
                label = "RECORDED DRIVES",
                icon = Icons.Filled.Route,
                onClick = onNavigateToDrives,
                modifier = Modifier.weight(1f)
            )
            SummaryMetric(
                value = totalCharges?.let { "%,d".format(it) } ?: "—",
                label = "CHARGING SESSIONS",
                icon = Icons.Filled.ElectricBolt,
                iconTint = ChargingGreen,
                onClick = onNavigateToCharges,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun QuickInsights(
    status: CarStatus,
    units: Units?,
    latestTrip: Trip?,
    onNavigateToMileage: () -> Unit,
    onNavigateToBattery: () -> Unit
) {
    HomeSection(title = "QUICK INSIGHTS") {
        val range = status.ratedBatteryRangeKm?.let { UnitFormatter.formatDistance(it, units, decimals = 0) }
        val efficiency = latestTrip?.avgEfficiency?.let { UnitFormatter.formatEfficiency(it, units, decimals = 0) }
        val chargeAdded = status.chargeEnergyAdded?.takeIf { it >= 0 }?.let { UnitFormatter.formatEnergy(it) }

        InsightRow(
            icon = Icons.Filled.BatteryChargingFull,
            label = "Rated range",
            value = range ?: "Not available",
            onClick = onNavigateToBattery
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
        InsightRow(
            icon = Icons.Filled.Speed,
            label = if (efficiency != null) "Latest trip efficiency" else "Charge limit",
            value = efficiency ?: status.chargeLimitSoc?.let { "$it%" } ?: "Not available",
            onClick = onNavigateToMileage
        )
        if (chargeAdded != null) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
            InsightRow(
                icon = Icons.Filled.ElectricBolt,
                label = "Added this session",
                value = chargeAdded,
                iconTint = ChargingGreen,
                onClick = onNavigateToBattery
            )
        }
    }
}

// ---- Unfolded-only cockpit cards (Phase 10): reuse existing calculators/repositories only ----

@Composable
private fun RealWorldRangeCard(cockpit: HomeCockpitUiState, status: CarStatus, onClick: () -> Unit) {
    val range = remember(cockpit.rangeEfficiency, cockpit.capacityKwh, cockpit.capacityConfidence, status.batteryLevel) {
        cockpit.rangeEfficiency?.let { eff ->
            RealWorldRangeCalculator.range(cockpit.capacityKwh, status.batteryLevel, eff, cockpit.capacityConfidence)
        }
    }
    HomeSection(title = stringResource(R.string.home_real_world_range_title), onClick = onClick) {
        val toTen = range?.toTenMiles
        if (toTen == null) {
            Text(stringResource(R.string.home_range_unavailable), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@HomeSection
        }
        Text(
            stringResource(R.string.home_range_to_ten_percent, UnitFormatter.formatDistance(toTen, cockpit.units, 0)),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        val eff = cockpit.rangeEfficiency
        if (eff?.whPerMile != null) {
            Text(
                "${UnitFormatter.formatEfficiency(eff.whPerMile, cockpit.units, 0)} · ${stringResource(R.string.home_range_recent_window, eff.window.label)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TodayCard(cockpit: HomeCockpitUiState) {
    HomeSection(title = stringResource(R.string.home_today_title)) {
        val today = cockpit.today
        if (today == null || (today.driveCount == 0 && today.chargeSessionCount == 0)) {
            Text(stringResource(R.string.home_today_no_activity), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@HomeSection
        }
        if (today.driveCount > 0) {
            InsightRow(
                icon = Icons.Filled.Route,
                label = stringResource(R.string.home_today_miles_driven_label),
                value = UnitFormatter.formatDistance(today.milesDriven, cockpit.units, 0)
            )
            InsightRow(
                icon = Icons.Filled.Speed,
                label = stringResource(R.string.home_today_drive_energy_label),
                value = UnitFormatter.formatEnergy(today.driveEnergyKwh)
            )
        }
        if (today.chargeSessionCount > 0) {
            val energyText = today.chargeEnergyKwh?.let { "+${UnitFormatter.formatEnergy(it)}" }
            val costText = when {
                cockpit.todayChargeIsFree -> stringResource(R.string.home_last_charge_free)
                cockpit.todayChargeCostMinorUnits != null && cockpit.todayChargeCostCurrency != null ->
                    "${cockpit.todayChargeCostCurrency} %.2f".format(cockpit.todayChargeCostMinorUnits / 100.0)
                else -> null
            }
            InsightRow(
                icon = Icons.Filled.ElectricBolt,
                label = stringResource(R.string.home_today_charging_label),
                value = listOfNotNull(energyText, costText).joinToString(" · "),
                iconTint = ChargingGreen
            )
        }
    }
}

@Composable
private fun EfficiencyCard(cockpit: HomeCockpitUiState) {
    HomeSection(title = stringResource(R.string.home_efficiency_title)) {
        val periodEff = cockpit.efficiencyPeriodSummary?.weightedEfficiency
        if (periodEff == null) {
            Text(stringResource(R.string.home_efficiency_unavailable), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@HomeSection
        }
        Text(stringResource(R.string.home_efficiency_window), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(UnitFormatter.formatEfficiency(periodEff, cockpit.units, 0), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        cockpit.efficiencyComparison?.let { comparison ->
            Spacer(Modifier.height(4.dp))
            if (comparison.isEffectivelyBaseline) {
                Text(stringResource(R.string.efficiency_matches_baseline), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val better = comparison.percentBetter > 0
                val pct = "%.1f%%".format(kotlin.math.abs(comparison.percentBetter))
                Text(
                    "${if (better) "+" else "-"}$pct ${stringResource(R.string.home_efficiency_vs_baseline)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (better) StatusSuccess else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun BatteryEstimateCard(cockpit: HomeCockpitUiState, onClick: () -> Unit) {
    HomeSection(title = stringResource(R.string.home_battery_title), onClick = onClick) {
        val kwh = cockpit.capacityKwh
        if (kwh == null) {
            Text(stringResource(R.string.home_battery_unavailable), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@HomeSection
        }
        Text("%.1f kWh".format(kwh), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        val confidenceRes = when (cockpit.capacityConfidence) {
            BatteryConfidence.HIGH -> R.string.quality_confidence_high
            BatteryConfidence.MEDIUM -> R.string.quality_confidence_medium
            BatteryConfidence.LOW -> R.string.quality_confidence_low
            null -> null
        }
        confidenceRes?.let {
            Text(stringResource(it).uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        cockpit.capacityChangePercent?.let { change ->
            Spacer(Modifier.height(2.dp))
            val sign = if (change >= 0) "+" else ""
            Text(
                "$sign%.1f%%".format(change) + " " + stringResource(R.string.home_efficiency_vs_baseline),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LastChargeCard(
    charge: ChargeSummary?,
    cost: ChargeCostPresentation?,
    units: Units?,
    onClick: () -> Unit
) {
    HomeSection(title = stringResource(R.string.home_last_charge_title), onClick = if (charge != null) onClick else null) {
        if (charge == null) {
            Text(stringResource(R.string.home_last_charge_unavailable), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@HomeSection
        }
        val placeName = (cost?.place?.name ?: charge.address.takeIf { it.isNotBlank() }) ?: stringResource(R.string.unknown)
        Text(placeName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("+${UnitFormatter.formatEnergy(charge.energyAdded)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = ChargingGreen)
            val freeText = stringResource(R.string.home_last_charge_free)
            val costText = when {
                cost?.isFree == true -> freeText
                cost?.costMinorUnits != null -> "${cost.currencyCode} %.2f".format(cost.costMinorUnits / 100.0)
                else -> stringResource(R.string.unknown)
            }
            Text(costText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LastDriveCard(
    drive: DriveSummary?,
    endpoint: DriveEndpointCache?,
    units: Units?,
    onClick: () -> Unit
) {
    HomeSection(title = stringResource(R.string.home_last_drive_title), onClick = if (drive != null) onClick else null) {
        if (drive == null) {
            Text(stringResource(R.string.home_last_drive_unavailable), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@HomeSection
        }
        Text(
            "${drive.startAddress} → ${drive.endAddress}",
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        val efficiency = drive.efficiencyOrNull()
        val detailParts = listOfNotNull(
            UnitFormatter.formatDistance(drive.distance, units, 0),
            formatDurationCompact(drive.durationMin),
            efficiency?.let { UnitFormatter.formatEfficiency(it, units, 0) }
        )
        Text(
            detailParts.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(TesManoSpacing.small))
        LastDriveMap(endpoint, onClick)
    }
}

@Composable
private fun LastDriveMap(endpoint: DriveEndpointCache?, onClick: () -> Unit) {
    val startPoint = endpoint?.startLatitude?.let { lat -> endpoint.startLongitude?.let { lon -> GeoPoint(lat, lon) } }
    val endPoint = endpoint?.endLatitude?.let { lat -> endpoint.endLongitude?.let { lon -> GeoPoint(lat, lon) } }
    if (startPoint == null && endPoint == null) {
        Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.home_last_drive_map_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        return
    }
    val accentArgb = PerformanceRed.toArgb()
    val startLabel = stringResource(R.string.start)
    val endLabel = stringResource(R.string.end)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(12.dp))
    ) {
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(false)
                    setBuiltInZoomControls(false)
                    isClickable = false
                    applyTesManoDarkMapTreatment()
                    val points = listOfNotNull(startPoint, endPoint)
                    startPoint?.let { addRouteEndpointMarker(it, "S", startLabel, accentArgb) }
                    endPoint?.let { addRouteEndpointMarker(it, "E", endLabel, 0xFFE6E9ED.toInt()) }
                    if (points.size == 2) {
                        val north = points.maxOf { it.latitude }
                        val south = points.minOf { it.latitude }
                        val east = points.maxOf { it.longitude }
                        val west = points.minOf { it.longitude }
                        val latPad = ((north - south).takeIf { it > 0.0 } ?: 0.01) * 0.4
                        val lonPad = ((east - west).takeIf { it > 0.0 } ?: 0.01) * 0.4
                        post {
                            zoomToBoundingBox(BoundingBox(north + latPad, east + lonPad, south - latPad, west - lonPad), false)
                            invalidate()
                        }
                    } else {
                        controller.setZoom(14.0)
                        controller.setCenter(points.first())
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        // The embedded MapView otherwise swallows single taps as its own zoom-control gesture;
        // this transparent overlay is the sole tap target so "tap the map" reliably opens Drive Detail.
        Box(Modifier.fillMaxSize().clickable(onClick = onClick))
    }
}

@Composable
private fun HomeSection(title: String, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it }
    ) {
        Column(modifier = Modifier.padding(TesManoSpacing.medium)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = PerformanceRed,
                letterSpacing = androidx.compose.ui.unit.TextUnit(0.12f, androidx.compose.ui.unit.TextUnitType.Em)
            )
            Spacer(Modifier.height(TesManoSpacing.medium))
            content()
        }
    }
}

@Composable
private fun ActivityLead(title: String, detail: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.DirectionsCar,
            contentDescription = null,
            tint = PerformanceRed,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(TesManoSpacing.small))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun SummaryMetric(
    value: String,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.clickable(onClick = onClick)) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(6.dp))
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun InsightRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    iconTint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(TesManoSpacing.small))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun TesManoVehicleImage(
    carModel: String?,
    carTrimBadging: String?,
    carExterior: CarExterior?,
    imageOverride: CarImageOverride?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val assetPath = remember(carModel, carTrimBadging, carExterior, imageOverride) {
        when {
            imageOverride?.customAssetKey != null -> CarImageResolver.getCustomAssetOrFallback(imageOverride.customAssetKey) { candidate ->
                runCatching { context.assets.open(candidate).close() }.isSuccess
            }
            imageOverride != null -> CarImageResolver.getAssetPathForOverride(
                variant = imageOverride.variant,
                colorCode = CarImageResolver.mapColor(carExterior?.exteriorColor),
                wheelCode = imageOverride.wheelCode,
                trimBadging = carTrimBadging
            )
            else -> CarImageResolver.getAssetPath(
                model = carModel,
                exteriorColor = carExterior?.exteriorColor,
                wheelType = carExterior?.wheelType,
                trimBadging = carTrimBadging
            )
        }
    }
    val bitmap = remember(assetPath) {
        runCatching {
            context.assets.open(assetPath).use(BitmapFactory::decodeStream)
        }.getOrElse {
            context.assets.open(CarImageResolver.getCustomAssetOrFallback(CarImageResolver.LEGACY_MODEL_Y_PERFORMANCE_DARK_GEMINI) { false })
                .use(BitmapFactory::decodeStream)
        }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Vehicle image",
            modifier = modifier,
            contentScale = ContentScale.Fit
        )
    } else {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.DirectionsCar, contentDescription = "Vehicle image unavailable", modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

internal object HomePresentation {
    fun vehicleIdentityDescriptor(model: String?, trimBadging: String?): String {
        val modelName = when (model?.trim()?.uppercase()) {
            "Y", "MODEL Y" -> "Model Y"
            null, "" -> "Tesla"
            else -> model.trim().replaceFirstChar { it.uppercase() }
        }
        val trimName = when {
            trimBadging.orEmpty().startsWith("P", ignoreCase = true) ||
                trimBadging.orEmpty().contains("performance", ignoreCase = true) -> "Performance"
            trimBadging.orEmpty().contains("long", ignoreCase = true) || trimBadging in setOf("74", "74D") -> "Long Range"
            trimBadging == "50" -> "Standard"
            else -> null
        }
        return listOfNotNull(modelName, trimName).joinToString(" ")
    }

    fun vehicleStateLabel(state: String?): String = when (state?.lowercase()) {
        "charging" -> "Charging"
        "driving" -> "Driving"
        "asleep", "suspended" -> "Asleep"
        "offline" -> "Connection unavailable"
        else -> "Parked"
    }

    fun freshnessLabel(stateSince: String?, now: Instant = Instant.now()): String? = try {
        val since = stateSince?.let { OffsetDateTime.parse(it).toInstant() } ?: return null
        val minutes = Duration.between(since, now).toMinutes()
        when {
            minutes < 0 -> null
            minutes < 1 -> "Updated just now"
            minutes < 60 -> "Updated ${minutes}m ago"
            minutes < 24 * 60 -> "Updated ${minutes / 60}h ago"
            else -> "Updated ${minutes / (24 * 60)}d ago"
        }
    } catch (_: Exception) {
        null
    }
}

@Composable
internal fun TesManoHomeLoadingContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "Loading vehicle summary…",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun TesManoHomeEmptyContent() {
    HomeStateSurface(
        eyebrow = "HOME",
        title = "Waiting for the first vehicle summary",
        detail = "TesMano will show locally available vehicle data after the first read-only sync."
    )
}

@Composable
internal fun TesManoHomeErrorContent(message: String) {
    HomeStateSurface(
        eyebrow = "CONNECTION",
        title = "Vehicle summary unavailable",
        detail = message
    )
}

@Composable
internal fun TesManoCarUnavailableContent(
    cars: List<CarData>,
    selectedCarId: Int?,
    error: String?,
    onSelectCar: (Int) -> Unit,
    onRetry: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(TesManoSpacing.medium),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(TesManoSpacing.large)) {
            Text("CONNECTION", style = MaterialTheme.typography.labelMedium, color = PerformanceRed, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(TesManoSpacing.small))
            Text("Vehicle summary unavailable", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(TesManoSpacing.small))
            Text(
                error ?: "The selected vehicle has no current summary. Choose another vehicle or retry without waking it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(TesManoSpacing.medium))
            cars.forEach { car ->
                val selected = car.carId == selectedCarId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectCar(car.carId) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.DirectionsCar, contentDescription = null, tint = if (selected) PerformanceRed else MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(TesManoSpacing.small))
                    Text(car.displayName, modifier = Modifier.weight(1f), fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                    if (selected) Text("SELECTED", style = MaterialTheme.typography.labelSmall, color = PerformanceRed)
                }
            }
            TextButton(onClick = onRetry, modifier = Modifier.align(Alignment.End)) { Text("Retry") }
        }
    }
}

@Composable
private fun HomeStateSurface(eyebrow: String, title: String, detail: String) {
    Box(modifier = Modifier.fillMaxSize().padding(TesManoSpacing.medium), contentAlignment = Alignment.Center) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(20.dp)) {
            Column(modifier = Modifier.padding(TesManoSpacing.large)) {
                Text(eyebrow, style = MaterialTheme.typography.labelMedium, color = PerformanceRed, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(TesManoSpacing.small))
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(TesManoSpacing.small))
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
