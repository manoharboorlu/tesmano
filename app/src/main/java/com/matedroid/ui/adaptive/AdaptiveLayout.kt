package com.matedroid.ui.adaptive

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker

/** Product layout modes derived from the current app window, never from a device name. */
enum class TesManoLayoutMode { COMPACT_COVER, EXPANDED_MAIN }

enum class TesManoPosture { NONE, BOOK, TABLETOP }

@Immutable
data class AdaptiveLayoutInfo(
    val mode: TesManoLayoutMode,
    val posture: TesManoPosture = TesManoPosture.NONE,
    val hasSeparatingFold: Boolean = false
) {
    val supportsTwoPane: Boolean
        get() = mode == TesManoLayoutMode.EXPANDED_MAIN && posture != TesManoPosture.TABLETOP
}

/** The Fold8 Ultra cover/main breakpoint is a window policy, not a device-model check. */
object TesManoLayoutClassifier {
    private const val MAIN_MIN_WIDTH_DP = 600

    fun fromWidthDp(widthDp: Int): TesManoLayoutMode =
        if (widthDp >= MAIN_MIN_WIDTH_DP) TesManoLayoutMode.EXPANDED_MAIN
        else TesManoLayoutMode.COMPACT_COVER

    fun posture(feature: FoldingFeature?): TesManoPosture = when (feature?.orientation) {
        FoldingFeature.Orientation.VERTICAL -> TesManoPosture.BOOK
        FoldingFeature.Orientation.HORIZONTAL -> TesManoPosture.TABLETOP
        else -> TesManoPosture.NONE
    }
}

val LocalAdaptiveLayoutInfo = compositionLocalOf {
    AdaptiveLayoutInfo(TesManoLayoutMode.COMPACT_COVER)
}

@Composable
fun ProvideAdaptiveLayout(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val configuration = LocalConfiguration.current
    var foldingFeature by remember(activity) { mutableStateOf<FoldingFeature?>(null) }

    LaunchedEffect(activity) {
        if (activity == null) return@LaunchedEffect
        WindowInfoTracker.getOrCreate(context).windowLayoutInfo(activity).collect { layoutInfo ->
            foldingFeature = layoutInfo.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()
        }
    }

    val info = AdaptiveLayoutInfo(
        mode = TesManoLayoutClassifier.fromWidthDp(configuration.screenWidthDp),
        posture = TesManoLayoutClassifier.posture(foldingFeature),
        hasSeparatingFold = foldingFeature?.isSeparating == true
    )
    CompositionLocalProvider(LocalAdaptiveLayoutInfo provides info, content = content)
}
