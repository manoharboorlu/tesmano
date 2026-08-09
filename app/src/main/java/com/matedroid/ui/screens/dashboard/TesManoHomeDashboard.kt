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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.matedroid.data.api.models.CarExterior
import com.matedroid.data.api.models.CarData
import com.matedroid.data.api.models.CarStatus
import com.matedroid.data.api.models.Units
import com.matedroid.data.local.CarImageOverride
import com.matedroid.domain.model.CarImageResolver
import com.matedroid.domain.model.Trip
import com.matedroid.domain.model.UnitFormatter
import com.matedroid.ui.adaptive.LocalAdaptiveLayoutInfo
import com.matedroid.ui.theme.PerformanceRed
import com.matedroid.ui.theme.TesManoSpacing
import com.matedroid.ui.screens.trips.displayName
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
    onNavigateToMileage: () -> Unit
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(TesManoSpacing.medium),
                verticalAlignment = Alignment.Top
            ) {
                VehicleHero(
                    status = status,
                    units = units,
                    carName = carName,
                    carModel = carModel,
                    carTrimBadging = carTrimBadging,
                    carExterior = carExterior,
                    imageOverride = imageOverride,
                    expanded = true,
                    onNavigateToBattery = onNavigateToBattery,
                    modifier = Modifier.weight(1.08f)
                )
                Column(
                    modifier = Modifier.weight(0.92f),
                    verticalArrangement = Arrangement.spacedBy(sectionSpacing)
                ) {
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "VEHICLE",
                    style = MaterialTheme.typography.labelMedium,
                    color = PerformanceRed,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = androidx.compose.ui.unit.TextUnit(0.12f, androidx.compose.ui.unit.TextUnitType.Em)
                )
                Spacer(Modifier.weight(1f))
                VehicleState(status)
            }
            Spacer(Modifier.height(TesManoSpacing.small))
            Text(
                text = carName ?: status.displayName ?: "Tesla",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val descriptor = listOfNotNull(carModel?.let { "Model $it" }, carTrimBadging).joinToString(" · ")
            if (descriptor.isNotBlank()) {
                Text(
                    text = descriptor,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
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
                        text = status.odometer?.let { UnitFormatter.formatDistance(it, units, decimals = 0) } ?: "—",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "ODOMETER",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            HomePresentation.freshnessLabel(status.stateSince)?.let { freshness ->
                Spacer(Modifier.height(TesManoSpacing.small))
                Text(
                    text = freshness,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun VehicleState(status: CarStatus) {
    val label = HomePresentation.vehicleStateLabel(status.state)
    val color = when (label) {
        "Charging" -> PerformanceRed
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
                onClick = onNavigateToBattery
            )
        }
    }
}

@Composable
private fun HomeSection(title: String, content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.clickable(onClick = onClick)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
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
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(19.dp))
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
