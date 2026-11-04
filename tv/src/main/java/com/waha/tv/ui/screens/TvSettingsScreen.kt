package com.waha.tv.ui.screens

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.waha.tv.BuildConfig
import com.waha.tv.ui.theme.WahaTvColors

/**
 * The TV settings screen.
 *
 * The phone build reaches settings from the top bar; the TV build has it as the
 * last destination on its rail. There is no appearance section: the TV build is
 * dark-only, so the only things to read here are the current version and a short
 * note about what Waha is. The page's name is not repeated — the rail's
 * highlighted icon holds it.
 */
@Composable
fun TvSettingsScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = WahaTvDimens.ScreenPadding,
                end = WahaTvDimens.ScreenPadding,
                top = WahaTvDimens.ScreenPadding / 2,
                bottom = WahaTvDimens.ScreenPadding
            ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        TvSettingsSectionTitle("عن واحة")

        TvSettingsRow(
            title = "الإصدار",
            value = BuildConfig.VERSION_NAME
        )

        TvSettingsRow(
            title = "واحة",
            value = "بيئة آمنة للأطفال",
            hint = "فيديوهات مختارة ومصنّفة يدويًا، بلا إعلانات ولا محتوى غير لائق."
        )
    }
}

@Composable
private fun TvSettingsSectionTitle(title: String) {
    Text(
        text = title,
        color = WahaTvColors.Accent,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )
}

/**
 * One settings row: a focusable card that reads like a list item — title, a value on
 * the trailing side, and an optional hint.
 */
@Composable
private fun TvSettingsRow(
    title: String,
    value: String,
    hint: String? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val colors = tvItemColors(selected = false, isFocused = isFocused)
    val contentColor = tvItemContentColor(selected = false, isFocused = isFocused)

    Card(
        // Informational, so the centre key does nothing — but the row stays focusable,
        // so the remote walks the page instead of falling straight through it.
        onClick = {},
        interactionSource = interactionSource,
        modifier = Modifier
            .fillMaxWidth()
            .height(if (hint != null) 96.dp else 72.dp),
        shape = CardDefaults.shape(RoundedCornerShape(WahaTvDimens.CornerRadius)),
        colors = colors,
        scale = CardDefaults.scale(focusedScale = WahaTvDimens.FocusScale),
        border = CardDefaults.border(focusedBorder = tvFocusBorder())
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = contentColor,
                    style = MaterialTheme.typography.titleMedium
                )
                if (hint != null) {
                    Text(
                        text = hint,
                        color = contentColor.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            Text(
                text = value,
                color = if (isFocused) contentColor else WahaTvColors.Accent,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}
