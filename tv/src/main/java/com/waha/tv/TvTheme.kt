package com.waha.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import androidx.tv.material3.lightColorScheme
import com.waha.brand.WahaBrandColors

/**
 * The TV colour scheme, built from the exact literals the phone build uses
 * ([WahaBrandColors]) so both screens feel like one product.
 *
 * Dark is the living-room default; the light scheme is kept alongside it so the
 * theme can follow the phone's saved preference later.
 */
private val WahaTvDarkColorScheme = darkColorScheme(
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

private val WahaTvLightColorScheme = lightColorScheme(
    primary = WahaBrandColors.PrimaryLight,
    onPrimary = WahaBrandColors.OnPrimaryLight,
    primaryContainer = WahaBrandColors.PrimaryContainerLight,
    onPrimaryContainer = WahaBrandColors.OnPrimaryContainerLight,
    secondary = WahaBrandColors.SecondaryLight,
    onSecondary = WahaBrandColors.OnSecondaryLight,
    secondaryContainer = WahaBrandColors.SecondaryContainerLight,
    onSecondaryContainer = WahaBrandColors.OnSecondaryContainerLight,
    tertiary = WahaBrandColors.TertiaryLight,
    onTertiary = WahaBrandColors.OnTertiaryLight,
    tertiaryContainer = WahaBrandColors.TertiaryContainerLight,
    onTertiaryContainer = WahaBrandColors.OnTertiaryContainerLight,
    error = WahaBrandColors.ErrorLight,
    onError = WahaBrandColors.OnErrorLight,
    background = WahaBrandColors.BackgroundLight,
    onBackground = WahaBrandColors.OnBackgroundLight,
    surface = WahaBrandColors.SurfaceLight,
    onSurface = WahaBrandColors.OnSurfaceLight,
    surfaceVariant = WahaBrandColors.SurfaceVariantLight,
    onSurfaceVariant = WahaBrandColors.OnSurfaceVariantLight,
    border = WahaBrandColors.OutlineLight
)

@Composable
fun WahaTvTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) WahaTvDarkColorScheme else WahaTvLightColorScheme

    // Waha is an Arabic-first product: the whole TV layout runs right-to-left,
    // exactly like the phone build (sidebar on the right, rails flowing left).
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = WahaTvTypography,
            content = content
        )
    }
}
