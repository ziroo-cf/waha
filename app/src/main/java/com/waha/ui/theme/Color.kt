package com.waha.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val PrimaryLight = Color(0xFFFF6600)
val OnPrimaryLight = Color(0xFFFFFFFF)
val PrimaryContainerLight = Color(0xFFB2F5EA)
val OnPrimaryContainerLight = Color(0xFF002019)

val SecondaryLight = Color(0xFF0E8539)
val OnSecondaryLight = Color(0xFF3E2723)
val SecondaryContainerLight = Color(0xFFFFECB3)
val OnSecondaryContainerLight = Color(0xFF2B1B00)

val TertiaryLight = Color(0xFFC8740C)
val OnTertiaryLight = Color(0xFFFFFFFF)
val TertiaryContainerLight = Color(0xFFFFCCBC)
val OnTertiaryContainerLight = Color(0xFF3E1300)

val ErrorLight = Color(0xFFBA1A1A)
val OnErrorLight = Color(0xFFFFFFFF)

val BackgroundLight = Color(0xFFFFFBF7)
val OnBackgroundLight = Color(0xFF191C1B)
val SurfaceLight = Color(0xFFFFFBF7)
val OnSurfaceLight = Color(0xFF191C1B)
val SurfaceVariantLight = Color(0xFFDDE5E2)
val OnSurfaceVariantLight = Color(0xFF414942)
val OutlineLight = Color(0xFF717975)

val PrimaryDark = Color(0xFFFF6600)
val OnPrimaryDark = Color(0xFF00332B)
val PrimaryContainerDark = Color(0xFF00695C)
val OnPrimaryContainerDark = Color(0xFF7FFFEB)

val SecondaryDark = Color(0xFF0E8539)
val OnSecondaryDark = Color(0xFF3E2723)
val SecondaryContainerDark = Color(0xFF7A5900)
val OnSecondaryContainerDark = Color(0xFFFFE08A)

val TertiaryDark = Color(0xFFC8740C)
val OnTertiaryDark = Color(0xFF4A1300)
val TertiaryContainerDark = Color(0xFF8C3D00)
val OnTertiaryContainerDark = Color(0xFFFFD9C8)

val ErrorDark = Color(0xFFFFB4AB)
val OnErrorDark = Color(0xFF690005)

val BackgroundDark = Color(0xFF18120E)
val OnBackgroundDark = Color(0xFFE1E3DF)
val SurfaceDark = Color(0xFF1B2820)
val OnSurfaceDark = Color(0xFFE1E3DF)
val SurfaceVariantDark = Color(0xFF1B2820)
val OnSurfaceVariantDark = Color(0xFFA8B3AC)
val OutlineDark = Color(0xFF8A938C)

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