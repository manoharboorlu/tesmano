package com.matedroid.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Semantic colors for charts and future analytical surfaces. */
@Immutable
data class TesManoChartColors(
    val primarySeries: Color,
    val secondarySeries: Color,
    val positive: Color,
    val warning: Color,
    val grid: Color
)

val LocalTesManoChartColors = staticCompositionLocalOf {
    TesManoChartColors(
        primarySeries = PerformanceRed,
        secondarySeries = CoolNeutral,
        positive = StatusSuccess,
        warning = StatusWarning,
        grid = GraphiteOutline
    )
}

object TesManoSpacing {
    val xSmall: Dp = 4.dp
    val small: Dp = 8.dp
    val medium: Dp = 16.dp
    val large: Dp = 24.dp
    val xLarge: Dp = 32.dp
}
