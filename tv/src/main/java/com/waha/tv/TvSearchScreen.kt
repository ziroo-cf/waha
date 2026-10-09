package com.waha.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.waha.data.RecentSearchesStore
import com.waha.domain.VideoItem

/**
 * TV search: the phone build's search overlay, re-thought for a remote.
 *
 * A phone gets a system keyboard for free; a TV in the living room only has a
 * D-pad, so typing happens on a grid of letters drawn on the screen (Arabic,
 * Latin and digits) and the recent-searches list that the phone stores is
 * reused as the starting point.
 */
@Composable
fun TvSearchScreen(
    allVideos: List<VideoItem>,
    onVideoClick: (VideoItem) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var page by remember { mutableStateOf(TvKeyboard.Page.Arabic) }
    val recentQueries = RecentSearchesStore.recentQueries

    val results = remember(query, allVideos) {
        if (query.isBlank()) emptyList()
        else allVideos.filter { TvKeyboard.matches(it.title, query) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = WahaTvDimens.ScreenPadding,
                    end = WahaTvDimens.ScreenPadding,
                    top = WahaTvDimens.ScreenPadding / 2
                ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "البحث في واحة",
                color = WahaTvColors.TextWarm,
                style = MaterialTheme.typography.headlineSmall
            )
            TvQueryField(query = query)
        }

        Box(modifier = Modifier.weight(1f)) {
            when {
                query.isBlank() && recentQueries.isEmpty() -> TvMessage(
                    "اكتب عنوان الفيديو بالأسفل باستخدام أسهم الريموت"
                )

                query.isBlank() -> TvRecentSearches(
                    recentQueries = recentQueries,
                    onQuerySelect = { query = it },
                    onClearAll = { RecentSearchesStore.clear() }
                )

                results.isEmpty() -> TvMessage("لم نجد أي نتائج لـ \"$query\"")

                else -> TvVideoGrid(
                    videos = results,
                    onVideoClick = { video ->
                        RecentSearchesStore.add(query)
                        onVideoClick(video)
                    },
                    contentPadding = PaddingValues(
                        start = WahaTvDimens.ScreenPadding,
                        end = WahaTvDimens.ScreenPadding,
                        top = WahaTvDimens.SectionSpacing / 2,
                        bottom = WahaTvDimens.SectionSpacing / 2
                    )
                )
            }
        }

        TvOnScreenKeyboard(
            page = page,
            onPageChange = { page = it },
            onCharacter = { character -> query = TvKeyboard.append(query, character) },
            onSpace = { query = TvKeyboard.append(query, " ") },
            onBackspace = { query = TvKeyboard.backspace(query) },
            onClear = { query = "" },
            onSearch = { RecentSearchesStore.add(query) }
        )
    }
}

/** The read-only pill that shows what has been typed so far. */
@Composable
private fun TvQueryField(query: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(WahaTvColors.Surface, RoundedCornerShape(100.dp))
            .padding(horizontal = 26.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (query.isEmpty()) {
            Text(
                text = "اكتب باستخدام لوحة المفاتيح بالأسفل",
                color = WahaTvColors.TextMuted,
                style = MaterialTheme.typography.bodyLarge
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = WahaTvColors.Accent,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = query,
                    color = WahaTvColors.TextWarm,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** The phone's recent-searches list, as a D-pad grid of focusable rows. */
@Composable
private fun TvRecentSearches(
    recentQueries: List<String>,
    onQuerySelect: (String) -> Unit,
    onClearAll: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = WahaTvDimens.ScreenPadding,
                end = WahaTvDimens.ScreenPadding,
                top = WahaTvDimens.SectionSpacing / 2
            ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = WahaTvColors.TextMuted,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "البحث الأخير",
                    color = WahaTvColors.TextWarm,
                    style = MaterialTheme.typography.titleLarge
                )
            }

            TvKeyButton(label = "مسح الكل", onClick = onClearAll, width = 140.dp)
        }

        recentQueries.take(6).forEach { queryText ->
            TvKeyButton(
                label = queryText,
                onClick = { onQuerySelect(queryText) },
                width = 420.dp,
                height = WahaTvDimens.SidebarItemHeight
            )
        }
    }
}

/**
 * The on-screen keyboard: every key is the same size, on one column grid.
 *
 * Uniform columns are what make the remote work: a letter always has a key
 * directly above it (the page switches and the actions sit on the top row) and
 * directly below it, so the D-pad can walk the whole keyboard without dead ends.
 * A narrower action row used to leave the arrows with no overlapping target.
 */
@Composable
private fun TvOnScreenKeyboard(
    page: TvKeyboard.Page,
    onPageChange: (TvKeyboard.Page) -> Unit,
    onCharacter: (String) -> Unit,
    onSpace: () -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(WahaTvColors.Surface)
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(KEY_GAP)
    ) {
        // Row 0: the page switches and the actions, on the letters' own columns.
        TvKeyboardRow {
            TvKeyboard.Page.entries.forEach { entry ->
                TvKeyButton(
                    label = entry.label,
                    selected = entry == page,
                    onClick = { onPageChange(entry) }
                )
            }
            TvKeyButton(label = "فراغ", onClick = onSpace)
            TvKeyButton(label = "حذف", onClick = onBackspace)
            TvKeyButton(label = "مسح", onClick = onClear)
            TvKeyButton(label = "ابحث", onClick = onSearch)
        }

        TvKeyboard.keyRows(page)
            .chunked(TvKeyboard.KEYS_PER_ROW)
            .forEach { rowKeys ->
                TvKeyboardRow {
                    rowKeys.forEach { key ->
                        TvKeyButton(label = key, onClick = { onCharacter(key) })
                    }
                }
            }
    }
}

private val KEY_GAP = 10.dp

/**
 * One row of the keyboard grid, pinned to the grid's exact width so a key always
 * lines up with the key above and below it (a short final row starts at the
 * layout's start edge, which is the right one in RTL).
 */
@Composable
private fun TvKeyboardRow(content: @Composable () -> Unit) {
    val gridWidth = WahaTvDimens.KeyboardKeyWidth * TvKeyboard.KEYS_PER_ROW +
            KEY_GAP * (TvKeyboard.KEYS_PER_ROW - 1)
    Row(
        modifier = Modifier.width(gridWidth),
        horizontalArrangement = Arrangement.spacedBy(KEY_GAP)
    ) {
        content()
    }
}

/**
 * One key: a focusable card that fills with the brand accent while focused.
 * Shared with the library screens' "مسح الكل" action.
 */
@Composable
internal fun TvKeyButton(
    label: String,
    onClick: () -> Unit,
    selected: Boolean = false,
    width: Dp = WahaTvDimens.KeyboardKeyWidth,
    height: Dp = WahaTvDimens.KeyboardKeyHeight
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val contentColor = tvItemContentColor(selected = selected, isFocused = isFocused)

    Card(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = Modifier
            .width(width)
            .height(height),
        shape = CardDefaults.shape(RoundedCornerShape(WahaTvDimens.CornerRadius)),
        colors = tvItemColors(selected = selected, isFocused = isFocused),
        scale = CardDefaults.scale(focusedScale = 1.08f),
        border = CardDefaults.border(focusedBorder = tvFocusBorder()),
        glow = CardDefaults.glow(focusedGlow = tvFocusGlow())
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = contentColor,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
