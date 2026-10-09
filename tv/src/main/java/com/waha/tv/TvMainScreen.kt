package com.waha.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/**
 * The TV destinations, mirroring the phone build's bottom navigation.
 *
 * A phone reaches these with a thumb on a bottom bar; a TV reaches them with
 * the remote on a sidebar, which is the same four destinations re-shaped for
 * D-pad travel.
 */
enum class TvDestination(val label: String, val icon: ImageVector) {
    Home("الرئيسية", Icons.Default.Home),
    Search("البحث", Icons.Default.Search),
    Saved("المحفوظات", Icons.Default.Bookmark),
    History("السجل", Icons.Default.History)
}

/**
 * The app shell: a permanent sidebar on the layout's start edge — the phone
 * build's tablet rail, made permanent — with the current destination beside it.
 *
 * The shell runs right-to-left (see [WahaTvTheme]), so the first `Row` child
 * sits on the right, matching the phone's Arabic layout.
 */
@Composable
fun TvMainScreen(
    destination: TvDestination,
    onDestinationSelect: (TvDestination) -> Unit,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(WahaTvColors.Background)
    ) {
        TvSidebar(current = destination, onSelect = onDestinationSelect)
        Box(modifier = Modifier.fillMaxSize()) { content() }
    }
}

@Composable
private fun TvSidebar(current: TvDestination, onSelect: (TvDestination) -> Unit) {
    Column(
        modifier = Modifier
            .width(WahaTvDimens.SidebarWidth)
            .fillMaxHeight()
            .background(WahaTvColors.Surface)
            .padding(horizontal = 16.dp, vertical = WahaTvDimens.ScreenPadding / 2),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TvBrandMark(modifier = Modifier.padding(start = 6.dp, end = 6.dp, bottom = 18.dp))

        TvDestination.entries.forEach { destination ->
            TvSidebarItem(
                destination = destination,
                selected = destination == current,
                onClick = { onSelect(destination) }
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "تنقّل بأسهم الريموت، واختر بالزر الأوسط",
            color = WahaTvColors.TextMuted,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        )
    }
}

/**
 * One sidebar entry. The colours are driven from the focus state directly so
 * the label flips to dark-on-accent the moment the remote lands on it — the
 * sidebar is the one place where a card's colours must work in both states.
 */
@Composable
private fun TvSidebarItem(
    destination: TvDestination,
    selected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val content = tvItemContentColor(selected = selected, isFocused = isFocused)

    Card(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = Modifier
            .fillMaxWidth()
            .height(WahaTvDimens.SidebarItemHeight),
        shape = CardDefaults.shape(RoundedCornerShape(WahaTvDimens.CornerRadius)),
        colors = tvItemColors(selected = selected, isFocused = isFocused),
        scale = CardDefaults.scale(focusedScale = 1.06f),
        border = CardDefaults.border(focusedBorder = tvFocusBorder()),
        glow = CardDefaults.glow(focusedGlow = tvFocusGlow())
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = destination.icon,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(26.dp)
            )
            Text(
                text = destination.label,
                color = content,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}
