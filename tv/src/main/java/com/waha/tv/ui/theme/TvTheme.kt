package com.waha.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import com.waha.brand.WahaBrandColors

/**
 * The TV's only colour scheme, built from the exact literals the phone build uses
 * ([WahaBrandColors]) so both screens feel like one product.
 */
private val WahaTvColorScheme = darkColorScheme(
    primary = WahaBrandColors.PrimaryDark,
    onPrimary = WahaBrandColors.OnPrimaryDark,
    primaryContainer = WahaBrandColors.PrimaryContainerDark,
    onPrimaryContainer = WahaBrandColors.OnPrimaryContainerDark,
    secondary = WahaBrandColors.SecondaryDark,
    onSecondary = WahaBrandColors.OnSecondaryDark,
    secondaryContainer = WahaBrandColors.SecondaryContainerDark,
    onSecondaryContainer = WahaBrandColors.OnSecondaryContainerDark,
    tertiary = WahaBrandColors.TertiaryDark,
    onTertiary = WahaBrandColors.OnTertiaryDark,
    tertiaryContainer = WahaBrandColors.TertiaryContainerDark,
    onTertiaryContainer = WahaBrandColors.OnTertiaryContainerDark,
    error = WahaBrandColors.ErrorDark,
    onError = WahaBrandColors.OnErrorDark,
    background = WahaBrandColors.BackgroundDark,
    onBackground = WahaBrandColors.OnBackgroundDark,
    surface = WahaBrandColors.SurfaceDark,
    onSurface = WahaBrandColors.OnSurfaceDark,
    surfaceVariant = WahaBrandColors.SurfaceVariantDark,
    onSurfaceVariant = WahaBrandColors.OnSurfaceVariantDark,
    border = WahaBrandColors.OutlineDark
)

/**
 * Waha on TV is dark, always: a living-room screen is watched in the dark, and the
 * library's artwork is graded against black. Nothing here follows the phone's theme
 * preference, and there is no light scheme to fall back to.
 */
@Composable
fun WahaTvTheme(content: @Composable () -> Unit) {
    // Waha is an Arabic-first product: the whole TV layout runs right-to-left,
    // exactly like the phone build (sidebar on the right, rails flowing left).
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = WahaTvColorScheme,
            typography = WahaTvTypography,
            content = content
        )
    }
}
