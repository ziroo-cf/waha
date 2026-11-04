package com.waha.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.waha.brand.WahaBrandColors

// The literals live in :core (WahaBrandColors) so the phone and TV builds share
// one brand palette; these names are what the phone screens already use.
val PrimaryLight = WahaBrandColors.PrimaryLight
val OnPrimaryLight = WahaBrandColors.OnPrimaryLight
val PrimaryContainerLight = WahaBrandColors.PrimaryContainerLight
val OnPrimaryContainerLight = WahaBrandColors.OnPrimaryContainerLight

val SecondaryLight = WahaBrandColors.SecondaryLight
val OnSecondaryLight = WahaBrandColors.OnSecondaryLight
val SecondaryContainerLight = WahaBrandColors.SecondaryContainerLight
val OnSecondaryContainerLight = WahaBrandColors.OnSecondaryContainerLight

val TertiaryLight = WahaBrandColors.TertiaryLight
val OnTertiaryLight = WahaBrandColors.OnTertiaryLight
val TertiaryContainerLight = WahaBrandColors.TertiaryContainerLight
val OnTertiaryContainerLight = WahaBrandColors.OnTertiaryContainerLight

val ErrorLight = WahaBrandColors.ErrorLight
val OnErrorLight = WahaBrandColors.OnErrorLight

val BackgroundLight = WahaBrandColors.BackgroundLight
val OnBackgroundLight = WahaBrandColors.OnBackgroundLight
val SurfaceLight = WahaBrandColors.SurfaceLight
val OnSurfaceLight = WahaBrandColors.OnSurfaceLight
val SurfaceVariantLight = WahaBrandColors.SurfaceVariantLight
val OnSurfaceVariantLight = WahaBrandColors.OnSurfaceVariantLight
val OutlineLight = WahaBrandColors.OutlineLight

val PrimaryDark = WahaBrandColors.PrimaryDark
val OnPrimaryDark = WahaBrandColors.OnPrimaryDark
val PrimaryContainerDark = WahaBrandColors.PrimaryContainerDark
val OnPrimaryContainerDark = WahaBrandColors.OnPrimaryContainerDark

val SecondaryDark = WahaBrandColors.SecondaryDark
val OnSecondaryDark = WahaBrandColors.OnSecondaryDark
val SecondaryContainerDark = WahaBrandColors.SecondaryContainerDark
val OnSecondaryContainerDark = WahaBrandColors.OnSecondaryContainerDark

val TertiaryDark = WahaBrandColors.TertiaryDark
val OnTertiaryDark = WahaBrandColors.OnTertiaryDark
val TertiaryContainerDark = WahaBrandColors.TertiaryContainerDark
val OnTertiaryContainerDark = WahaBrandColors.OnTertiaryContainerDark

val ErrorDark = WahaBrandColors.ErrorDark
val OnErrorDark = WahaBrandColors.OnErrorDark

val BackgroundDark = WahaBrandColors.BackgroundDark
val OnBackgroundDark = WahaBrandColors.OnBackgroundDark
val SurfaceDark = WahaBrandColors.SurfaceDark
val OnSurfaceDark = WahaBrandColors.OnSurfaceDark
val SurfaceVariantDark = WahaBrandColors.SurfaceVariantDark
val OnSurfaceVariantDark = WahaBrandColors.OnSurfaceVariantDark
val OutlineDark = WahaBrandColors.OutlineDark

val WahaTeal: Color
    @Composable get() = MaterialTheme.colorScheme.primary

val WahaTealBright: Color
    @Composable get() = MaterialTheme.colorScheme.primary

val WahaGold: Color
    @Composable get() = MaterialTheme.colorScheme.secondary

val WahaDarkBg: Color
    @Composable get() = MaterialTheme.colorScheme.background

val WahaCardBg: Color
    @Composable get() = MaterialTheme.colorScheme.surface

val WahaCardBg2: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceVariant

val WahaTextWarm: Color
    @Composable get() = MaterialTheme.colorScheme.onBackground

val WahaTextMuted: Color
    @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

val WahaLine: Color
    @Composable get() = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
