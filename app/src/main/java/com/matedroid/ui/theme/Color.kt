package com.matedroid.ui.theme

import androidx.compose.ui.graphics.Color

// TesMano dark-only foundation: restrained graphite surfaces and Performance red.
val PerformanceRed = Color(0xFFC74A4A)
val PerformanceRedContainer = Color(0xFF5A2227)
/** Reserved for actual charging state and charging-session affordances. */
val ChargingGreen = Color(0xFF6FA987)
val GraphiteBlack = Color(0xFF101112)
val GraphiteSurface = Color(0xFF181A1C)
val GraphiteSurfaceRaised = Color(0xFF222529)
val GraphiteOutline = Color(0xFF454A50)
val CoolNeutral = Color(0xFFB8C0C9)

// Neutral base colors (cool grey/blue-grey - works with any car color)
val NeutralPrimary = Color(0xFF5C6670)
val NeutralDark = Color(0xFF1C1F23)

// Status colors (semantic - always fixed)
val StatusSuccess = Color(0xFF4CAF50)
val StatusWarning = Color(0xFFFF9800)
val StatusError = Color(0xFFF44336)

// Light theme colors - neutral cool grey
val PrimaryLight = Color(0xFF5C6670)
val OnPrimaryLight = Color.White
val PrimaryContainerLight = Color(0xFFE8EAEC)
val OnPrimaryContainerLight = Color(0xFF1C1F23)
val SecondaryLight = Color(0xFF5C6670)
val OnSecondaryLight = Color.White
val SecondaryContainerLight = Color(0xFFE2E4E6)
val OnSecondaryContainerLight = Color(0xFF1B1D1F)
val TertiaryLight = Color(0xFF6B7A8C)
val OnTertiaryLight = Color.White
val BackgroundLight = Color(0xFFFAFAFA)
val OnBackgroundLight = Color(0xFF1C1B1F)
val SurfaceLight = Color(0xFFFAFAFA)
val OnSurfaceLight = Color(0xFF1C1B1F)
val SurfaceVariantLight = Color(0xFFE8EAEC)
val OnSurfaceVariantLight = Color(0xFF44474A)
val OutlineLight = Color(0xFF74777A)
val OutlineVariantLight = Color(0xFFC4C6C8)
val SurfaceContainerLight = Color(0xFFF0F1F3)
val SurfaceContainerHighLight = Color(0xFFECEDEF)
val SurfaceContainerHighestLight = Color(0xFFE6E7E9)
val ErrorLight = StatusError
val OnErrorLight = Color.White

// Dark theme colors - neutral cool grey
val PrimaryDark = PerformanceRed
val OnPrimaryDark = Color(0xFFFFFFFF)
val PrimaryContainerDark = PerformanceRedContainer
val OnPrimaryContainerDark = Color(0xFFFFDAD9)
val SecondaryDark = CoolNeutral
val OnSecondaryDark = GraphiteBlack
val SecondaryContainerDark = GraphiteSurfaceRaised
val OnSecondaryContainerDark = Color(0xFFE0E5EA)
val TertiaryDark = Color(0xFF9AB1C5)
val OnTertiaryDark = GraphiteBlack
val BackgroundDark = GraphiteBlack
val OnBackgroundDark = Color(0xFFE5E8EB)
val SurfaceDark = GraphiteSurface
val OnSurfaceDark = Color(0xFFE5E8EB)
val SurfaceVariantDark = GraphiteSurfaceRaised
val OnSurfaceVariantDark = Color(0xFFC4CAD1)
val OutlineDark = Color(0xFF9098A1)
val OutlineVariantDark = GraphiteOutline
val SurfaceContainerDark = GraphiteSurface
val SurfaceContainerHighDark = GraphiteSurfaceRaised
val SurfaceContainerHighestDark = Color(0xFF2B2F34)
val ErrorDark = Color(0xFFFFB4AB)
val OnErrorDark = Color(0xFF690005)

// Other colors
val BoundaryColor = Color(0xFF4CAF50)
