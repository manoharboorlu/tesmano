package com.matedroid.ui.screens.drives

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.LocationOn
import com.matedroid.ui.icons.CustomIcons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlin.math.roundToInt
import com.matedroid.R
import com.matedroid.data.api.models.DriveDetail
import com.matedroid.data.api.models.DrivePosition
import com.matedroid.data.api.models.Units
import com.matedroid.data.repository.WeatherPoint
import com.matedroid.domain.DriveComparison
import com.matedroid.domain.DrivePlaceContext
import com.matedroid.domain.DriveTagSet
import com.matedroid.data.local.entity.UserDriveTag
import com.matedroid.domain.GeoPoint as TesManoGeoPoint
import com.matedroid.data.local.entity.SmartPlaceType
import com.matedroid.domain.model.UnitFormatter
import com.matedroid.ui.components.FullscreenLineChart
import com.matedroid.ui.components.MateDroidLoadingPlaceholder
import com.matedroid.ui.components.createLabeledPinMarkerDrawable
import com.matedroid.ui.components.RouteSegment
import com.matedroid.ui.components.addRouteEndpointMarker
import com.matedroid.ui.components.addRouteSegments
import com.matedroid.ui.components.applyTesManoDarkMapTreatment
import com.matedroid.ui.adaptive.LocalAdaptiveLayoutInfo
import com.matedroid.ui.screens.trips.displayName
import com.matedroid.ui.theme.CarColorPalette
import com.matedroid.ui.theme.CarColorPalettes
import com.matedroid.ui.theme.PerformanceRed
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import com.matedroid.util.formatDurationCompact
import com.matedroid.util.formatMedium
import com.matedroid.util.formatTime
import com.matedroid.util.parseIsoDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriveDetailScreen(
    carId: Int,
    driveId: Int,
    exteriorColor: String? = null,
    onNavigateBack: () -> Unit,
    onNavigateToTripDetail: (tripStartDate: String) -> Unit = {},
    onNavigateToCompare: (baseDriveId: Int) -> Unit = {},
    viewModel: DriveDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val isDarkTheme = isSystemInDarkTheme()
    val palette = CarColorPalettes.forExteriorColor(exteriorColor, isDarkTheme)

    LaunchedEffect(carId, driveId) {
        viewModel.loadDriveDetail(carId, driveId)
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.drive_details_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (uiState.isLoading) {
            MateDroidLoadingPlaceholder(
                color = palette.accent,
                modifier = Modifier.padding(padding)
            )
        } else {
            uiState.driveDetail?.let { detail ->
                DriveDetailContent(
                    detail = detail,
                    stats = uiState.stats,
                    units = uiState.units,
                    palette = palette,
                    weatherPoints = uiState.weatherPoints,
                    isLoadingWeather = uiState.isLoadingWeather,
                    containingTrip = uiState.containingTrip,
                    comparison = uiState.comparison,
                    placeContext = uiState.placeContext,
                    onCompareClick = { onNavigateToCompare(driveId) },
                    onNavigateToTripDetail = onNavigateToTripDetail,
                    onRemoveFromTrip = viewModel::removeFromTrip,
                    onMarkCommute = viewModel::markCommute,
                    onUnmarkCommute = viewModel::unmarkCommute,
                    onReturnToAutomatic = viewModel::returnToAutomaticCommute,
                    onSaveEndpoint = viewModel::saveEndpointAsPlace,
                    tags = uiState.tags,
                    availableTags = uiState.availableTags,
                    onAddTag = viewModel::addTag,
                    onCreateTag = viewModel::createAndAddTag,
                    onRemoveTag = viewModel::removeTag,
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }
}

@Composable
private fun DriveDetailContent(
    detail: DriveDetail,
    stats: DriveDetailStats?,
    units: Units?,
    palette: CarColorPalette,
    weatherPoints: List<WeatherPoint>,
    isLoadingWeather: Boolean,
    containingTrip: Pair<Long, com.matedroid.domain.model.Trip>?,
    comparison: DriveComparison?,
    placeContext: DrivePlaceContext?,
    onCompareClick: () -> Unit,
    onNavigateToTripDetail: (String) -> Unit,
    onRemoveFromTrip: () -> Unit,
    onMarkCommute: () -> Unit,
    onUnmarkCommute: () -> Unit,
    onReturnToAutomatic: () -> Unit,
    onSaveEndpoint: (String, String, TesManoGeoPoint, String?, Int) -> Unit,
    tags: DriveTagSet,
    availableTags: List<UserDriveTag>,
    onAddTag: (Long) -> Unit,
    onCreateTag: (String) -> Unit,
    onRemoveTag: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val is24Hour = android.text.format.DateFormat.is24HourFormat(LocalContext.current)
    val adaptive = LocalAdaptiveLayoutInfo.current
    val scrollState = rememberScrollState()
    var sharedXFraction by remember { mutableStateOf<Float?>(null) }
    var endpointToSave by remember { mutableStateOf<Pair<String, TesManoGeoPoint>?>(null) }

    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.isScrollInProgress }
            .collect { isScrolling -> if (isScrolling) sharedXFraction = null }
    }

    val positions = detail.positions
    val hasCharts = positions != null && positions.size > 2
    val timeLabels = remember(positions) {
        if (hasCharts) extractTimeLabels(positions!!, is24Hour) else emptyList()
    }
    val fractionToTimeLabel: (Float) -> String = remember(positions) {
        label@{ fraction: Float ->
            val pos = positions
            if (pos == null || pos.size <= 2) return@label ""
            val index = (fraction * pos.lastIndex).roundToInt().coerceIn(0, pos.lastIndex)
            pos[index].date?.let { dateStr ->
                parseIsoDateTime(dateStr)?.formatTime(java.util.Locale.getDefault(), is24Hour) ?: ""
            } ?: ""
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .pointerInput(Unit) { detectTapGestures { sharedXFraction = null } }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        DrivePlacesCard(
            detail = detail,
            context = placeContext,
            onMarkCommute = onMarkCommute,
            onUnmarkCommute = onUnmarkCommute,
            onReturnToAutomatic = onReturnToAutomatic,
            onSaveStart = { point -> endpointToSave = "Start" to point },
            onSaveEnd = { point -> endpointToSave = "Destination" to point }
        )
        DriveTagsCard(tags, availableTags, onAddTag, onCreateTag, onRemoveTag)
        if (adaptive.supportsTwoPane) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                DriveSummaryColumn(
                    detail = detail,
                    stats = stats,
                    units = units,
                    palette = palette,
                    is24Hour = is24Hour,
                    comparison = comparison,
                    onCompareClick = onCompareClick,
                    modifier = Modifier.weight(1f)
                )
                DriveVisualColumn(
                    positions = positions,
                    hasCharts = hasCharts,
                    units = units,
                    palette = palette,
                    timeLabels = timeLabels,
                    sharedXFraction = sharedXFraction,
                    onXSelected = { sharedXFraction = it },
                    fractionToTimeLabel = fractionToTimeLabel,
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            DriveSummaryColumn(
                detail = detail,
                stats = stats,
                units = units,
                palette = palette,
                is24Hour = is24Hour,
                comparison = comparison,
                onCompareClick = onCompareClick
            )
            DriveVisualColumn(
                positions = positions,
                hasCharts = hasCharts,
                units = units,
                palette = palette,
                timeLabels = timeLabels,
                sharedXFraction = sharedXFraction,
                onXSelected = { sharedXFraction = it },
                fractionToTimeLabel = fractionToTimeLabel
            )
        }

        // Weather along the way
        if (isLoadingWeather || weatherPoints.isNotEmpty()) {
            WeatherAlongTheWayCard(
                weatherPoints = weatherPoints,
                units = units,
                isLoading = isLoadingWeather
            )
        }

        // Everything else behind a tap
        stats?.let { s ->
            DriveMoreDetails(
                detail = detail,
                stats = s,
                units = units,
                palette = palette,
                positions = if (hasCharts) positions else null,
                timeLabels = timeLabels,
                sharedXFraction = sharedXFraction,
                onXSelected = { sharedXFraction = it },
                fractionToTimeLabel = fractionToTimeLabel
            )
        }

        // Part-of-trip banner — kept at the very end
        if (containingTrip != null) {
            val (_, trip) = containingTrip
            com.matedroid.ui.components.PartOfTripCard(
                tripRoute = trip.displayName(),
                onNavigateToTrip = { onNavigateToTripDetail(trip.startDate) },
                onConfirmRemove = onRemoveFromTrip
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    endpointToSave?.let { (label, point) ->
        SavePlaceDialog(
            label = label,
            onDismiss = { endpointToSave = null },
            onSave = { name, type ->
                val address = if (label == "Start") detail.startAddress else detail.endAddress
                onSaveEndpoint(name, type, point, address, 300)
                endpointToSave = null
            }
        )
    }
}

@Composable
private fun DriveTagsCard(tags: DriveTagSet, available: List<UserDriveTag>, onAdd: (Long) -> Unit, onCreate: (String) -> Unit, onRemove: (Long) -> Unit) {
    var chooserOpen by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    if (chooserOpen) AlertDialog(
        onDismissRequest = { chooserOpen = false },
        title = { Text("Add tag") },
        text = { Column {
            available.filterNot { candidate -> tags.manual.any { it.id == candidate.id } }.forEach { tag ->
                TextButton(onClick = { onAdd(tag.id); chooserOpen = false }) { Text(tag.name) }
            }
            TextButton(onClick = { chooserOpen = false; creating = true }) { Text("Create new tag") }
        } },
        confirmButton = { TextButton(onClick = { chooserOpen = false }) { Text("Close") } }
    )
    if (creating) {
        var name by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { creating = false }, title = { Text("New tag") }, text = { androidx.compose.material3.OutlinedTextField(name, { name = it }, label = { Text("Tag name") }, singleLine = true) }, confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onCreate(name); creating = false }) { Text("Add") } }, dismissButton = { TextButton(onClick = { creating = false }) { Text("Cancel") } })
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("TAGS", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = PerformanceRed)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (tags.commute) Text("COMMUTE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                tags.manual.forEach { tag -> TextButton(onClick = { onRemove(tag.id) }) { Text("${tag.name} ×") } }
                TextButton(onClick = { chooserOpen = true }) { Text("+ Add") }
            }
            Text("Commute is derived from Home ↔ Work; user tags are manual.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DrivePlacesCard(
    detail: DriveDetail,
    context: DrivePlaceContext?,
    onMarkCommute: () -> Unit,
    onUnmarkCommute: () -> Unit,
    onReturnToAutomatic: () -> Unit,
    onSaveStart: (TesManoGeoPoint) -> Unit,
    onSaveEnd: (TesManoGeoPoint) -> Unit
) {
    val points = detail.positions.orEmpty().mapNotNull { position ->
        position.latitude?.let { latitude -> position.longitude?.let { longitude -> TesManoGeoPoint(latitude, longitude) } }
    }
    if (points.isEmpty()) return
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("SMART PLACES", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = PerformanceRed)
            Text("${context?.start?.name ?: detail.startAddress ?: "Unknown"}  →  ${context?.end?.name ?: detail.endAddress ?: "Unknown"}", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (context?.commute == true) {
                    Button(onClick = onUnmarkCommute) { Text("Unmark commute") }
                } else {
                    Button(onClick = onMarkCommute) { Text("Mark commute") }
                }
                if (context?.commuteIsManual == true) TextButton(onClick = onReturnToAutomatic) { Text("Use automatic") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onSaveStart(points.first()) }) { Text("Save start as place") }
                TextButton(onClick = { onSaveEnd(points.last()) }) { Text("Save destination as place") }
            }
        }
    }
}

@Composable
private fun SavePlaceDialog(label: String, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(label) }
    var type by rememberSaveable { mutableStateOf(SmartPlaceType.CUSTOM) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save $label as place") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.OutlinedTextField(name, { name = it }, label = { Text("Place name") })
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SmartPlaceType.all.forEach { candidate ->
                        androidx.compose.material3.FilterChip(selected = type == candidate, onClick = { type = candidate }, label = { Text(candidate.lowercase().replaceFirstChar { it.uppercase() }) })
                    }
                }
                Text("You can adjust the 300 m radius in Smart Places.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onSave(name, type) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun DriveSummaryColumn(
    detail: DriveDetail,
    stats: DriveDetailStats?,
    units: Units?,
    palette: CarColorPalette,
    is24Hour: Boolean,
    comparison: DriveComparison?,
    onCompareClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.drive_detail_label),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = PerformanceRed
                )
                Spacer(Modifier.height(8.dp))
                DriveHeroSection(
                    detail = detail,
                    stats = stats,
                    units = units,
                    palette = palette,
                    is24Hour = is24Hour
                )
            }
        }
        stats?.let { DriveStatTiles(stats = it, units = units, palette = palette) }
        comparison?.let { DriveCompareCard(comparison = it, palette = palette, onClick = onCompareClick) }
    }
}

@Composable
private fun DriveVisualColumn(
    positions: List<DrivePosition>?,
    hasCharts: Boolean,
    units: Units?,
    palette: CarColorPalette,
    timeLabels: List<String>,
    sharedXFraction: Float?,
    onXSelected: (Float?) -> Unit,
    fractionToTimeLabel: (Float) -> String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        positions?.takeIf { it.isNotEmpty() }?.let {
            DriveMapCard(positions = it, routeColor = PerformanceRed)
        }
        if (hasCharts && positions?.any { it.speed != null } == true) {
            SpeedChartCard(
                positions = positions,
                units = units,
                color = palette.accent,
                timeLabels = timeLabels,
                externalSelectedFraction = sharedXFraction,
                onXSelected = onXSelected,
                fractionToTimeLabel = fractionToTimeLabel
            )
        }
    }
}

/**
 * Compact hero: route (from → to), the dominant figure (distance) in the car's accent colour, a
 * balanced row of labelled key figures (avg speed, battery swing, duration), and a footer with the
 * start time and energy used. Replaces the old verbose header.
 */
@Composable
private fun DriveHeroSection(
    detail: DriveDetail,
    stats: DriveDetailStats?,
    units: Units?,
    palette: CarColorPalette,
    is24Hour: Boolean
) {
    val unknownLocation = stringResource(R.string.unknown_location)
    val avgLabel = stringResource(R.string.average_speed)
    val batteryLabel = stringResource(R.string.battery)
    val durationLabel = stringResource(R.string.duration)
    val energyLabel = stringResource(R.string.energy)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Route
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            RouteLine(
                color = palette.accent,
                label = stringResource(R.string.from),
                value = detail.startAddress ?: unknownLocation
            )
            RouteLine(
                color = MaterialTheme.colorScheme.tertiary,
                label = stringResource(R.string.to),
                value = detail.endAddress ?: unknownLocation
            )
        }

        // Dominant figure: distance
        stats?.let { s ->
            Text(
                text = UnitFormatter.formatDistance(s.distance, units),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = palette.accent
            )

            // Balanced row of labelled key figures
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HeroStat(
                    label = avgLabel,
                    value = UnitFormatter.formatSpeed(s.avgSpeedFromDistance, units),
                    modifier = Modifier.weight(1f)
                )
                HeroStat(
                    label = batteryLabel,
                    value = "${s.batteryStart}% → ${s.batteryEnd}%",
                    modifier = Modifier.weight(1f)
                )
                HeroStat(
                    label = durationLabel,
                    value = formatDurationCompact(s.durationMin),
                    modifier = Modifier.weight(1f)
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Footer: start time and energy used
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = formatDateTime(detail.startDate, is24Hour),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "$energyLabel · %.1f kWh".format(s.energyUsed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RouteLine(color: Color, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Default.LocationOn,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = color
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "$label  ",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}

/** A single labelled figure: small uppercase label over a bold value. */
@Composable
private fun HeroStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = label.uppercase(java.util.Locale.getDefault()),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}

/** A row of accent tiles for efficiency and its main confounders (temperature, elevation gain). */
@Composable
private fun DriveStatTiles(
    stats: DriveDetailStats,
    units: Units?,
    palette: CarColorPalette
) {
    val efficiencyLabel = stringResource(R.string.efficiency)
    val temperatureLabel = stringResource(R.string.temperature)
    val gainLabel = stringResource(R.string.gain)

    val tiles = buildList {
        add(efficiencyLabel to UnitFormatter.formatEfficiency(stats.efficiency, units))
        stats.outsideTempAvg?.let { add(temperatureLabel to UnitFormatter.formatTemperature(it, units)) }
        if (stats.elevationGain > 0) add(gainLabel to "+%,d m".format(stats.elevationGain))
    }
    if (tiles.isEmpty()) return

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        tiles.forEach { (label, value) ->
            DriveStatTile(label = label, value = value, accent = palette.accent, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun DriveStatTile(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = 0.12f))
            .padding(vertical = 12.dp, horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label.uppercase(java.util.Locale.getDefault()),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = accent,
            maxLines = 1
        )
    }
}

/** Entry point into the drive-comparison screen, shown only for drives with comparable siblings. */
@Composable
private fun DriveCompareCard(
    comparison: DriveComparison,
    palette: CarColorPalette,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(palette.accent.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(palette.accent.copy(alpha = 0.20f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Insights,
                contentDescription = null,
                tint = palette.accent,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.compare_drives_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = pluralStringResource(
                    R.plurals.compare_drives_nearby,
                    comparison.others.size,
                    comparison.others.size
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Collapsible section holding the detailed stat cards and the secondary charts. */
@Composable
private fun DriveMoreDetails(
    detail: DriveDetail,
    stats: DriveDetailStats,
    units: Units?,
    palette: CarColorPalette,
    positions: List<DrivePosition>?,
    timeLabels: List<String>,
    sharedXFraction: Float?,
    onXSelected: (Float?) -> Unit,
    fractionToTimeLabel: (Float) -> String
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "driveMoreDetailsChevron")

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { expanded = !expanded }
                .padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(if (expanded) R.string.hide_details else R.string.more_details),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = palette.accent
            )
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = null,
                tint = palette.accent,
                modifier = Modifier
                    .size(22.dp)
                    .rotate(rotation)
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                StatsSectionCard(
                    title = stringResource(R.string.speed),
                    icon = Icons.Default.Speed,
                    stats = listOf(
                        StatItem(stringResource(R.string.maximum), UnitFormatter.formatSpeed(stats.speedMax.toDouble(), units)),
                        // Point samples are irregular; keep their simple mean clearly labelled.
                        StatItem(stringResource(R.string.sampled_average), UnitFormatter.formatSpeed(stats.speedAvg, units)),
                        StatItem(stringResource(R.string.avg_distance), UnitFormatter.formatSpeed(stats.avgSpeedFromDistance, units))
                    )
                )
                StatsSectionCard(
                    title = stringResource(R.string.trip),
                    icon = CustomIcons.SteeringWheel,
                    stats = listOf(
                        StatItem(stringResource(R.string.distance), UnitFormatter.formatDistance(stats.distance, units)),
                        StatItem(stringResource(R.string.duration), formatDurationCompact(stats.durationMin)),
                        StatItem(stringResource(R.string.efficiency), UnitFormatter.formatEfficiency(stats.efficiency, units))
                    )
                )
                StatsSectionCard(
                    title = stringResource(R.string.battery),
                    icon = Icons.Default.BatteryChargingFull,
                    stats = listOf(
                        StatItem(stringResource(R.string.start), "${stats.batteryStart}%"),
                        StatItem(stringResource(R.string.end), "${stats.batteryEnd}%"),
                        StatItem(stringResource(R.string.used), "${stats.batteryUsed}%"),
                        StatItem(stringResource(R.string.energy), "%.2f kWh".format(stats.energyUsed))
                    )
                )
                StatsSectionCard(
                    title = stringResource(R.string.power),
                    icon = Icons.Default.Bolt,
                    stats = listOf(
                        StatItem(stringResource(R.string.max_accel), "${stats.powerMax} kW"),
                        StatItem(stringResource(R.string.min_regen), "${stats.powerMin} kW"),
                        StatItem(stringResource(R.string.average), "%.1f kW".format(stats.powerAvg))
                    )
                )
                if (stats.elevationMax > 0 || stats.elevationMin > 0) {
                    StatsSectionCard(
                        title = stringResource(R.string.elevation),
                        icon = Icons.Default.Landscape,
                        stats = listOf(
                            StatItem(stringResource(R.string.maximum), "%,d m".format(stats.elevationMax)),
                            StatItem(stringResource(R.string.minimum), "%,d m".format(stats.elevationMin)),
                            StatItem(stringResource(R.string.gain), "+%,d m".format(stats.elevationGain)),
                            StatItem(stringResource(R.string.loss), "-%,d m".format(stats.elevationLoss))
                        )
                    )
                }
                if (stats.outsideTempAvg != null || stats.insideTempAvg != null) {
                    StatsSectionCard(
                        title = stringResource(R.string.temperature),
                        icon = Icons.Default.DeviceThermostat,
                        stats = listOfNotNull(
                            stats.outsideTempAvg?.let { StatItem(stringResource(R.string.outside), UnitFormatter.formatTemperature(it, units)) },
                            stats.insideTempAvg?.let { StatItem(stringResource(R.string.inside), UnitFormatter.formatTemperature(it, units)) }
                        )
                    )
                }

                // Secondary charts
                if (positions != null) {
                    PowerChartCard(
                        positions = positions,
                        timeLabels = timeLabels,
                        externalSelectedFraction = sharedXFraction,
                        onXSelected = onXSelected,
                        fractionToTimeLabel = fractionToTimeLabel
                    )
                    BatteryChartCard(
                        positions = positions,
                        timeLabels = timeLabels,
                        externalSelectedFraction = sharedXFraction,
                        onXSelected = onXSelected,
                        fractionToTimeLabel = fractionToTimeLabel
                    )
                    if (positions.any { it.elevation != null && it.elevation != 0 }) {
                        ElevationChartCard(
                            positions = positions,
                            timeLabels = timeLabels,
                            externalSelectedFraction = sharedXFraction,
                            onXSelected = onXSelected,
                            fractionToTimeLabel = fractionToTimeLabel
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DriveMapCard(positions: List<DrivePosition>, routeColor: Color) {
    val context = LocalContext.current
    val routeColorArgb = routeColor.toArgb()
    val validPositions = positions.filter { it.latitude != null && it.longitude != null }

    if (validPositions.isEmpty()) return

    val startPoint = validPositions.firstOrNull()
    val endPoint = validPositions.lastOrNull()

    fun openInMaps() {
        if (startPoint != null && endPoint != null) {
            // Open Google Maps with directions from start to end
            val uri = Uri.parse(
                "https://www.google.com/maps/dir/?api=1" +
                        "&origin=${startPoint.latitude},${startPoint.longitude}" +
                        "&destination=${endPoint.latitude},${endPoint.longitude}" +
                        "&travelmode=driving"
            )
            val intent = Intent(Intent.ACTION_VIEW, uri)
            context.startActivity(intent)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { openInMaps() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.route_map),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                AndroidView(
                    factory = { ctx ->
                        MapView(ctx).apply {
                            setTileSource(TileSourceFactory.MAPNIK)
                            setMultiTouchControls(true)
                            // One finger scrolls the surrounding page; two fingers pan/zoom the map.
                            setOnTouchListener { v, event ->
                                v.parent?.requestDisallowInterceptTouchEvent(event.pointerCount >= 2)
                                false
                            }

                            val geoPoints = validPositions.map { pos ->
                                GeoPoint(pos.latitude!!, pos.longitude!!)
                            }

                            applyTesManoDarkMapTreatment()
                            addRouteSegments(
                                listOf(RouteSegment(geoPoints, routeColorArgb))
                            )
                            addRouteEndpointMarker(
                                point = geoPoints.first(),
                                label = "S",
                                title = ctx.getString(R.string.start),
                                color = routeColorArgb
                            )
                            addRouteEndpointMarker(
                                point = geoPoints.last(),
                                label = "E",
                                title = ctx.getString(R.string.end),
                                color = 0xFFE6E9ED.toInt()
                            )

                            if (geoPoints.isNotEmpty()) {
                                val north = geoPoints.maxOf { it.latitude }
                                val south = geoPoints.minOf { it.latitude }
                                val east = geoPoints.maxOf { it.longitude }
                                val west = geoPoints.minOf { it.longitude }

                                val latPadding = (north - south) * 0.15
                                val lonPadding = (east - west) * 0.15

                                val boundingBox = BoundingBox(
                                    north + latPadding,
                                    east + lonPadding,
                                    south - latPadding,
                                    west - lonPadding
                                )

                                post {
                                    zoomToBoundingBox(boundingBox, false)
                                    invalidate()
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

data class StatItem(val label: String, val value: String)

@Composable
private fun StatsSectionCard(
    title: String,
    icon: ImageVector,
    stats: List<StatItem>
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp

    val columnCount = when {
        screenWidth > 600 -> 4
        screenWidth > 340 -> 3
        else -> 2
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            val chunked = stats.chunked(columnCount)
            chunked.forEachIndexed { index, row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    row.forEach { stat ->
                        StatItemView(
                            label = stat.label,
                            value = stat.value,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    val emptySlots = columnCount - row.size
                    if (emptySlots > 0) {
                        repeat(emptySlots) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
                if (index < chunked.size - 1) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun StatItemView(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SpeedChartCard(
    positions: List<DrivePosition>,
    units: Units?,
    color: Color,
    timeLabels: List<String>,
    externalSelectedFraction: Float? = null,
    onXSelected: ((Float?) -> Unit)? = null,
    fractionToTimeLabel: ((Float) -> String)? = null
) {
    val speeds = remember(positions) { positions.mapNotNull { it.speed?.toFloat() } }
    if (speeds.size < 2) return

    // Speed is already in the user's unit (the API pre-converts before we store
    // it), so the chart plots the raw value — no km/h→mph math here.
    ChartCard(
        title = stringResource(R.string.speed_profile),
        icon = Icons.Default.Speed,
        data = speeds,
        color = color,
        unit = UnitFormatter.getSpeedUnit(units),
        timeLabels = timeLabels,
        externalSelectedFraction = externalSelectedFraction,
        onXSelected = onXSelected,
        fractionToTimeLabel = fractionToTimeLabel
    )
}

@Composable
private fun PowerChartCard(
    positions: List<DrivePosition>,
    timeLabels: List<String>,
    externalSelectedFraction: Float? = null,
    onXSelected: ((Float?) -> Unit)? = null,
    fractionToTimeLabel: ((Float) -> String)? = null
) {
    val powers = remember(positions) { positions.mapNotNull { it.power?.toFloat() } }
    if (powers.size < 2) return

    ChartCard(
        title = stringResource(R.string.power_profile),
        icon = Icons.Default.Bolt,
        data = powers,
        color = MaterialTheme.colorScheme.tertiary,
        unit = "kW",
        showZeroLine = true,
        timeLabels = timeLabels,
        externalSelectedFraction = externalSelectedFraction,
        onXSelected = onXSelected,
        fractionToTimeLabel = fractionToTimeLabel
    )
}

@Composable
private fun BatteryChartCard(
    positions: List<DrivePosition>,
    timeLabels: List<String>,
    externalSelectedFraction: Float? = null,
    onXSelected: ((Float?) -> Unit)? = null,
    fractionToTimeLabel: ((Float) -> String)? = null
) {
    val batteryLevels = remember(positions) { positions.mapNotNull { it.batteryLevel?.toFloat() } }
    if (batteryLevels.size < 2) return
    val fixedMinMax = remember(batteryLevels) {
        var yMin = (kotlin.math.floor(batteryLevels.min() / 10.0) * 10).toFloat()
        var yMax = (kotlin.math.ceil(batteryLevels.max() / 10.0) * 10).toFloat()
        if (yMin == yMax) { yMin -= 1; yMax += 1 }
        Pair(yMin, yMax)
    }

    ChartCard(
        title = stringResource(R.string.battery_level),
        icon = Icons.Default.BatteryChargingFull,
        data = batteryLevels,
        color = MaterialTheme.colorScheme.secondary,
        unit = "%",
        fixedMinMax = fixedMinMax,
        timeLabels = timeLabels,
        externalSelectedFraction = externalSelectedFraction,
        onXSelected = onXSelected,
        fractionToTimeLabel = fractionToTimeLabel
    )
}

@Composable
private fun ElevationChartCard(
    positions: List<DrivePosition>,
    timeLabels: List<String>,
    externalSelectedFraction: Float? = null,
    onXSelected: ((Float?) -> Unit)? = null,
    fractionToTimeLabel: ((Float) -> String)? = null
) {
    val elevations = remember(positions) { positions.mapNotNull { it.elevation?.toFloat() } }
    if (elevations.size < 2) return

    ChartCard(
        title = stringResource(R.string.elevation_profile),
        icon = Icons.Default.Landscape,
        data = elevations,
        color = Color(0xFF8B4513),
        unit = "m",
        timeLabels = timeLabels,
        externalSelectedFraction = externalSelectedFraction,
        onXSelected = onXSelected,
        fractionToTimeLabel = fractionToTimeLabel
    )
}

@Composable
private fun ChartCard(
    title: String,
    icon: ImageVector,
    data: List<Float>,
    color: Color,
    unit: String,
    showZeroLine: Boolean = false,
    fixedMinMax: Pair<Float, Float>? = null,
    timeLabels: List<String> = emptyList(),
    convertValue: (Float) -> Float = { it },
    externalSelectedFraction: Float? = null,
    onXSelected: ((Float?) -> Unit)? = null,
    fractionToTimeLabel: ((Float) -> String)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = color
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            FullscreenLineChart(
                data = data,
                color = color,
                unit = unit,
                showZeroLine = showZeroLine,
                fixedMinMax = fixedMinMax,
                timeLabels = timeLabels,
                convertValue = convertValue,
                externalSelectedFraction = externalSelectedFraction,
                onXSelected = onXSelected,
                fractionToTimeLabel = fractionToTimeLabel,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Extract 5 time labels from drive positions for X axis display.
 * Returns list of 5 time strings at 0%, 25%, 50%, 75%, and 100% positions.
 */
private fun extractTimeLabels(positions: List<DrivePosition>, is24Hour: Boolean? = null): List<String> {
    if (positions.isEmpty()) return listOf("", "", "", "", "")

    val locale = java.util.Locale.getDefault()
    val times = positions.mapNotNull { position ->
        position.date?.let { parseIsoDateTime(it) }
    }

    if (times.isEmpty()) return listOf("", "", "", "", "")

    val indices = listOf(0, times.size / 4, times.size / 2, times.size * 3 / 4, times.size - 1)
    return indices.map { idx ->
        times.getOrNull(idx.coerceIn(0, times.size - 1))?.formatTime(locale, is24Hour) ?: ""
    }
}

private fun formatDateTime(dateStr: String?, is24Hour: Boolean? = null): String {
    if (dateStr.isNullOrBlank()) return "Unknown"
    val dt = parseIsoDateTime(dateStr) ?: return dateStr
    val locale = java.util.Locale.getDefault()
    return "${dt.toLocalDate().formatMedium(locale)} ${dt.formatTime(locale, is24Hour)}"
}
