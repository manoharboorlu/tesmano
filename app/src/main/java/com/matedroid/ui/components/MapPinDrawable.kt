package com.matedroid.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.content.res.Resources

/**
 * Creates a slick pin-needle map marker: a filled circle head with a thin needle pointing down.
 */
fun createPinMarkerDrawable(resources: Resources, headColorArgb: Int): Drawable {
    val density = resources.displayMetrics.density
    val width = (24 * density).toInt()
    val height = (36 * density).toInt()
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val cx = width / 2f
    val headRadius = 8 * density
    val headCy = headRadius + 1 * density

    // Needle
    val needlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF444444.toInt()
        style = Paint.Style.FILL
    }
    val needlePath = Path().apply {
        moveTo(cx - 2.5f * density, headCy + headRadius * 0.5f)
        lineTo(cx, height.toFloat() - 1 * density)
        lineTo(cx + 2.5f * density, headCy + headRadius * 0.5f)
        close()
    }
    canvas.drawPath(needlePath, needlePaint)

    // Head circle
    val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = headColorArgb
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, headCy, headRadius, headPaint)

    // Subtle border
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x44000000
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
    }
    canvas.drawCircle(cx, headCy, headRadius, borderPaint)

    return BitmapDrawable(resources, bitmap)
}

/**
 * Route endpoint pin with a one-letter label. Kept generic so route renderers can use a distinct
 * start/end treatment without coupling marker artwork to a particular screen.
 */
fun createLabeledPinMarkerDrawable(
    resources: Resources,
    headColorArgb: Int,
    label: String
): Drawable {
    val density = resources.displayMetrics.density
    val width = (28 * density).toInt()
    val height = (40 * density).toInt()
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cx = width / 2f
    val radius = 9 * density
    val headCy = radius + 1 * density

    val needlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF090A0B.toInt()
        style = Paint.Style.FILL
    }
    val needlePath = Path().apply {
        moveTo(cx - 3 * density, headCy + radius * 0.45f)
        lineTo(cx, height.toFloat() - density)
        lineTo(cx + 3 * density, headCy + radius * 0.45f)
        close()
    }
    canvas.drawPath(needlePath, needlePaint)

    val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = headColorArgb }
    canvas.drawCircle(cx, headCy, radius, headPaint)
    canvas.drawCircle(
        cx,
        headCy,
        radius,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x99090A0B.toInt()
            style = Paint.Style.STROKE
            strokeWidth = density
        }
    )
    val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textAlign = Paint.Align.CENTER
        textSize = 10 * density
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    val baseline = headCy - (labelPaint.fontMetrics.ascent + labelPaint.fontMetrics.descent) / 2f
    canvas.drawText(label.take(1).uppercase(), cx, baseline, labelPaint)
    return BitmapDrawable(resources, bitmap)
}

/**
 * Creates a map marker with a bolt/zap icon inside the circle head,
 * for charge stop locations. The bolt is drawn in white on the colored head.
 */
fun createZapMarkerDrawable(resources: Resources, headColorArgb: Int): Drawable {
    val density = resources.displayMetrics.density
    val width = (24 * density).toInt()
    val height = (36 * density).toInt()
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val cx = width / 2f
    val headRadius = 8 * density
    val headCy = headRadius + 1 * density

    // Needle
    val needlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF444444.toInt()
        style = Paint.Style.FILL
    }
    val needlePath = Path().apply {
        moveTo(cx - 2.5f * density, headCy + headRadius * 0.5f)
        lineTo(cx, height.toFloat() - 1 * density)
        lineTo(cx + 2.5f * density, headCy + headRadius * 0.5f)
        close()
    }
    canvas.drawPath(needlePath, needlePaint)

    // Head circle
    val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = headColorArgb
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, headCy, headRadius, headPaint)

    // Subtle border
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x44000000
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
    }
    canvas.drawCircle(cx, headCy, headRadius, borderPaint)

    // Bolt icon inside the head circle
    val boltPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.FILL
    }
    val s = headRadius * 0.55f
    val boltPath = Path().apply {
        moveTo(cx + 0.1f * s, headCy - 1.0f * s)
        lineTo(cx - 0.6f * s, headCy + 0.1f * s)
        lineTo(cx - 0.05f * s, headCy + 0.1f * s)
        lineTo(cx - 0.1f * s, headCy + 1.0f * s)
        lineTo(cx + 0.6f * s, headCy - 0.1f * s)
        lineTo(cx + 0.05f * s, headCy - 0.1f * s)
        close()
    }
    canvas.drawPath(boltPath, boltPaint)

    return BitmapDrawable(resources, bitmap)
}
