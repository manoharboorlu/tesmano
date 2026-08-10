package com.matedroid.ui.components

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

/** A colored route segment deliberately keeps the renderer ready for future server-backed segments. */
data class RouteSegment(val points: List<GeoPoint>, val color: Int)

/**
 * Darken/desaturate the existing OSM tiles instead of replacing the map provider. Route lines and
 * endpoint pins stay unfiltered overlays, preserving a clear hierarchy in dark UI.
 *
 * The luminance weights below are proportioned like the original filter (green-leaning, matching
 * OSM's park/land tint) but scaled up from ~0.31 to ~0.55 total retained brightness, plus a small
 * additive lift, so roads/labels/water stay legible on the physical device instead of just the
 * route line being visible against a near-black tile.
 */
fun MapView.applyTesManoDarkMapTreatment() {
    setBackgroundColor(0xFF101112.toInt())
    mapOverlay.setLoadingBackgroundColor(0xFF101112.toInt())
    mapOverlay.setLoadingLineColor(0xFF30343A.toInt())
    mapOverlay.setColorFilter(
        ColorMatrixColorFilter(
            ColorMatrix(
                floatArrayOf(
                    0.14f, 0.36f, 0.05f, 0f, 20f,
                    0.14f, 0.36f, 0.05f, 0f, 20f,
                    0.14f, 0.36f, 0.05f, 0f, 20f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
    )
}

/** Two-layer segments make the route distinct from both dark tiles and dense street geometry. */
fun MapView.addRouteSegments(segments: List<RouteSegment>) {
    segments.filter { it.points.size > 1 }.forEach { segment ->
        overlays.add(
            Polyline().apply {
                setPoints(segment.points)
                outlinePaint.color = 0xCC090A0B.toInt()
                outlinePaint.strokeWidth = 16f
                outlinePaint.strokeCap = Paint.Cap.ROUND
                outlinePaint.strokeJoin = Paint.Join.ROUND
            }
        )
        overlays.add(
            Polyline().apply {
                setPoints(segment.points)
                outlinePaint.color = segment.color
                outlinePaint.strokeWidth = 9f
                outlinePaint.strokeCap = Paint.Cap.ROUND
                outlinePaint.strokeJoin = Paint.Join.ROUND
            }
        )
    }
}

fun MapView.addRouteEndpointMarker(
    point: GeoPoint,
    label: String,
    title: String,
    color: Int,
    relatedObject: Any? = null,
    onClick: (() -> Boolean)? = null
) {
    overlays.add(
        Marker(this).apply {
            position = point
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            this.title = title
            icon = createLabeledPinMarkerDrawable(resources, color, label)
            this.relatedObject = relatedObject
            if (onClick != null) {
                setOnMarkerClickListener { _, _ -> onClick() }
            }
        }
    )
}

/** A charge-stop marker using the established bolt/zap pin instead of a lettered label. */
fun MapView.addChargeMarker(
    point: GeoPoint,
    title: String,
    color: Int,
    relatedObject: Any? = null,
    onClick: (() -> Boolean)? = null
) {
    overlays.add(
        Marker(this).apply {
            position = point
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            this.title = title
            icon = createZapMarkerDrawable(resources, color)
            this.relatedObject = relatedObject
            if (onClick != null) {
                setOnMarkerClickListener { _, _ -> onClick() }
            }
        }
    )
}
