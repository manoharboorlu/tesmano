package com.matedroid.ui.screens.places

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.matedroid.data.local.entity.SmartPlace
import com.matedroid.data.local.entity.SmartPlaceType
import com.matedroid.data.local.entity.ChargingRateRule
import com.matedroid.data.local.entity.ChargingRateScope
import com.matedroid.ui.adaptive.LocalAdaptiveLayoutInfo
import com.matedroid.ui.components.createLabeledPinMarkerDrawable
import com.matedroid.ui.theme.PerformanceRed
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Polygon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartPlacesScreen(
    onNavigateBack: () -> Unit,
    viewModel: SmartPlacesViewModel = hiltViewModel()
) {
    val places by viewModel.places.collectAsStateWithLifecycle()
    val chargingRates by viewModel.chargingRates.collectAsStateWithLifecycle()
    val adaptive = LocalAdaptiveLayoutInfo.current
    var editing by remember { mutableStateOf<SmartPlace?>(null) }
    var creating by remember { mutableStateOf(false) }
    Scaffold(
        topBar = { TopAppBar(title = { Text("Smart Places") }, navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) },
        floatingActionButton = { FloatingActionButton(onClick = { creating = true }) { Icon(Icons.Default.Add, "Add place") } }
    ) { padding ->
        val editor: @Composable () -> Unit = {
            if (creating || editing != null) PlaceEditor(
                existing = editing,
                initialCenter = places.firstOrNull(),
                rate = editing?.let { place -> chargingRates.firstOrNull { it.scope == ChargingRateScope.PLACE && it.smartPlaceId == place.id && it.enabled } },
                onSave = { viewModel.save(it); creating = false; editing = null },
                onSaveRate = viewModel::saveRate,
                onCancel = { creating = false; editing = null },
                onDelete = { editing?.let(viewModel::delete); editing = null }
            )
        }
        if (adaptive.supportsTwoPane) {
            Row(Modifier.fillMaxSize().padding(padding).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PlacesList(places, { editing = it; creating = false }, Modifier.weight(1f))
                Column(Modifier.weight(1f)) { editor(); DefaultRateEditor(chargingRates.firstOrNull { it.scope == ChargingRateScope.DEFAULT && it.enabled }, viewModel::saveRate) }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                PlacesList(places, { editing = it; creating = false }, Modifier.weight(1f))
                if (creating || editing != null) { Spacer(Modifier.height(12.dp)); editor() }
                Spacer(Modifier.height(12.dp)); DefaultRateEditor(chargingRates.firstOrNull { it.scope == ChargingRateScope.DEFAULT && it.enabled }, viewModel::saveRate)
            }
        }
    }
}

@Composable
private fun PlacesList(places: List<SmartPlace>, onEdit: (SmartPlace) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("YOUR PLACES", style = MaterialTheme.typography.labelMedium, color = PerformanceRed, fontWeight = FontWeight.Bold)
        if (places.isEmpty()) Text("Save a drive start or destination from Drive Detail, or add a place here.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        places.forEach { place ->
            Card(onClick = { onEdit(place) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Text(place.name, style = MaterialTheme.typography.titleMedium)
                    place.address?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    Text("${place.type.lowercase().replaceFirstChar { it.uppercase() }} · ${place.radiusMeters} m${if (!place.enabled) " · Disabled" else ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun PlaceEditor(
    existing: SmartPlace?,
    initialCenter: SmartPlace?,
    rate: ChargingRateRule?,
    onSave: (SmartPlace) -> Unit,
    onSaveRate: (ChargingRateRule) -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    var name by rememberSaveable(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var type by rememberSaveable(existing?.id) { mutableStateOf(existing?.type ?: SmartPlaceType.CUSTOM) }
    var latitude by rememberSaveable(existing?.id) { mutableStateOf(existing?.latitude ?: initialCenter?.latitude ?: 0.0) }
    var longitude by rememberSaveable(existing?.id) { mutableStateOf(existing?.longitude ?: initialCenter?.longitude ?: 0.0) }
    var radius by rememberSaveable(existing?.id) { mutableStateOf((existing?.radiusMeters ?: 300).toFloat()) }
    var enabled by rememberSaveable(existing?.id) { mutableStateOf(existing?.enabled ?: true) }
    var rateText by rememberSaveable(existing?.id) { mutableStateOf(rate?.let { "%.4f".format(it.priceMicrosPerKwh / 1_000_000.0) }.orEmpty()) }
    var freeCharging by rememberSaveable(existing?.id) { mutableStateOf(rate?.freeCharging ?: false) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (existing == null) "New place" else "Edit place", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { SmartPlaceType.all.forEach { candidate -> FilterChip(type == candidate, { type = candidate }, { Text(candidate.lowercase().replaceFirstChar { it.uppercase() }) }) } }
            existing?.address?.takeIf { it.isNotBlank() }?.let { address ->
                Text(address, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("Tap the map to move this place.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            PlaceLocationMap(
                latitude = latitude,
                longitude = longitude,
                radiusMeters = radius.toInt(),
                onLocationChanged = { point -> latitude = point.latitude; longitude = point.longitude }
            )
            Text("Radius ${radius.toInt()} m", style = MaterialTheme.typography.labelLarge)
            Slider(value = radius, onValueChange = { radius = it }, valueRange = 100f..2000f)
            Text("CHARGING RATE", style = MaterialTheme.typography.labelMedium, color = PerformanceRed, fontWeight = FontWeight.Bold)
            if (!freeCharging) OutlinedTextField(rateText, { rateText = it }, label = { Text("USD per kWh") }, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Free charging"); Switch(freeCharging, { freeCharging = it }) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Enabled"); Switch(enabled, { enabled = it }) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = name.isNotBlank(), onClick = {
                    onSave(SmartPlace(existing?.id ?: 0, name, type, latitude, longitude, radius.toInt(), enabled, existing?.createdAt ?: System.currentTimeMillis(), System.currentTimeMillis(), existing?.address))
                    existing?.id?.let { placeId ->
                        val price = rateText.toBigDecimalOrNull()?.movePointRight(6)?.longValueExact() ?: 0
                        if (freeCharging || rateText.isNotBlank()) onSaveRate(ChargingRateRule(scope = ChargingRateScope.PLACE, smartPlaceId = placeId, name = name, priceMicrosPerKwh = price, currencyCode = "USD", freeCharging = freeCharging, effectiveFrom = if (rate == null) 0 else System.currentTimeMillis(), createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()))
                    }
                }) { Text("Save") }
                androidx.compose.material3.TextButton(onClick = onCancel) { Text("Cancel") }
                if (existing != null) IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete place") }
            }
        }
    }
}

@Composable
private fun DefaultRateEditor(rate: ChargingRateRule?, onSave: (ChargingRateRule) -> Unit) {
    var value by rememberSaveable(rate?.id) { mutableStateOf(rate?.let { "%.4f".format(it.priceMicrosPerKwh / 1_000_000.0) }.orEmpty()) }
    var free by rememberSaveable(rate?.id) { mutableStateOf(rate?.freeCharging ?: false) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("DEFAULT CHARGING RATE", style = MaterialTheme.typography.labelMedium, color = PerformanceRed, fontWeight = FontWeight.Bold)
            Text("For locations outside Smart Places", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!free) OutlinedTextField(value, { value = it }, label = { Text("USD per kWh") }, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Free charging"); Switch(free, { free = it }) }
            Button(enabled = free || value.toBigDecimalOrNull() != null, onClick = { val price = value.toBigDecimalOrNull()?.movePointRight(6)?.longValueExact() ?: 0; onSave(ChargingRateRule(scope = ChargingRateScope.DEFAULT, name = "Default", priceMicrosPerKwh = price, currencyCode = "USD", freeCharging = free, effectiveFrom = if (rate == null) 0 else System.currentTimeMillis(), createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis())) }) { Text("Save default rate") }
        }
    }
}

private class PlaceMapState(
    val events: MapEventsOverlay,
    var lastPoint: GeoPoint? = null
)

@Composable
private fun PlaceLocationMap(
    latitude: Double,
    longitude: Double,
    radiusMeters: Int,
    onLocationChanged: (GeoPoint) -> Unit
) {
    val context = LocalContext.current
    val current = remember(latitude, longitude) { GeoPoint(latitude, longitude) }
    AndroidView(
        factory = { ctx ->
            val events = MapEventsOverlay(object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(point: GeoPoint): Boolean {
                    onLocationChanged(point)
                    return true
                }

                override fun longPressHelper(point: GeoPoint): Boolean = false
            })
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                setBackgroundColor(0xFF101112.toInt())
                mapOverlay.setLoadingBackgroundColor(0xFF101112.toInt())
                mapOverlay.setLoadingLineColor(0xFF30343A.toInt())
                mapOverlay.setColorFilter(
                    ColorMatrixColorFilter(
                        ColorMatrix(floatArrayOf(
                            0.08f, 0.20f, 0.03f, 0f, 0f,
                            0.08f, 0.20f, 0.03f, 0f, 0f,
                            0.08f, 0.20f, 0.03f, 0f, 0f,
                            0f, 0f, 0f, 1f, 0f
                        ))
                    )
                )
                tag = PlaceMapState(events)
            }
        },
        update = { map ->
            val state = map.tag as PlaceMapState
            map.overlays.clear()
            map.overlays.add(state.events)
            map.overlays.add(
                Polygon().apply {
                    points = Polygon.pointsAsCircle(current, radiusMeters.toDouble())
                    fillColor = 0x26D34B4B
                    outlinePaint.color = PerformanceRed.toArgb()
                    outlinePaint.strokeWidth = context.resources.displayMetrics.density * 2f
                }
            )
            map.overlays.add(
                Marker(map).apply {
                    position = current
                    title = "Place center"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    icon = createLabeledPinMarkerDrawable(context.resources, PerformanceRed.toArgb(), "P")
                }
            )
            if (state.lastPoint != current) {
                map.controller.setCenter(current)
                map.controller.setZoom(15.5)
                state.lastPoint = current
            }
            map.invalidate()
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
    )
}
