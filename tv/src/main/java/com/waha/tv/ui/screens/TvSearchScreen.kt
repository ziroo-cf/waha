package com.waha.tv.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
import com.waha.tv.TvTestTags
import com.waha.tv.ui.theme.WahaTvColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** How long typing pauses before the library is filtered. */
internal const val SEARCH_DEBOUNCE_MS = 120L

/**
 * What the viewer has typed, and which keyboard page they are on.
 *
 * The keyboard reads only [page] and the field only [query], so a keystroke recomposes
 * the two nodes that show the text — never the thirty-five keys the viewer is walking
 * with the remote. Marked stable so lambdas that capture it stay comparable too.
 */
@Stable
internal class TvSearchState {

    var query by mutableStateOf("")
        private set

    var page by mutableStateOf(TvKeyboard.Page.Arabic)
        private set

    fun type(text: String) {
        query = TvKeyboard.append(query, text)
    }

    fun backspace() {
        query = TvKeyboard.backspace(query)
    }

    fun clear() {
        query = ""
    }

    /** Replaces the query wholesale (a recent search the viewer picked). */
    fun showQuery(text: String) {
        query = text
    }

    fun showPage(selected: TvKeyboard.Page) {
        page = selected
    }
}

/** A query and the videos that matched it, so the screen knows when a search settled. */
private data class TvSearchResults(
    val query: String = "",
    val videos: List<VideoItem> = emptyList()
)

/**
 * TV search: the phone build's search overlay, re-thought for a remote.
 *
 * A phone gets a system keyboard for free; a TV in the living room only has a
 * D-pad, so typing happens on a grid of letters drawn on the screen (Arabic,
 * Latin and digits) and the recent-searches list that the phone stores is
 * reused as the starting point.
 *
 * The results are one row of cards, and the keyboard never folds away: with the
 * results beside the keys instead of filling the screen, the viewer can keep
 * typing while watching the row narrow — no lost keyboard to press back into.
 *
 * Filtering is debounced and runs on a worker thread: a pass over the whole library
 * must never sit between a key press and the frame that shows it, and a viewer who
 * holds a key down should get one search, not one per letter.
 */
@Composable
fun TvSearchScreen(
    allVideos: List<VideoItem>,
    onVideoClick: (VideoItem) -> Unit
) {
    val state = remember { TvSearchState() }
    val query = state.query
    val recentQueries = RecentSearchesStore.recentQueries

    val results by produceState(TvSearchResults(), query, allVideos) {
        if (query.isBlank()) {
            value = TvSearchResults(query, emptyList())
            return@produceState
        }
        // Every keystroke restarts this effect, so the pause is the debounce.
        delay(SEARCH_DEBOUNCE_MS)
        val matches = withContext(Dispatchers.Default) {
            allVideos.filter { TvKeyboard.matches(it.title, query) }
        }
        value = TvSearchResults(query, matches)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Just the query field: the rail's highlighted البحث already names the page,
        // and on a 720p TV a duplicated heading costs the results grid a whole row.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = WahaTvDimens.ScreenPadding,
                    end = WahaTvDimens.ScreenPadding,
                    top = WahaTvDimens.SectionSpacing / 2
                )
        ) {
            TvQueryField(query = query)
        }

        Box(modifier = Modifier.weight(1f)) {
            when {
                query.isBlank() && recentQueries.isEmpty() -> TvMessage(
                    "اكتب عنوان الفيديو بالأسفل باستخدام أسهم الريموت"
                )

                query.isBlank() -> TvRecentSearches(
                    recentQueries = recentQueries,
                    onQuerySelect = state::showQuery,
                    onClearAll = { RecentSearchesStore.clear() }
                )

                // While a search is still settling, the previous matches stay up: a
                // 120ms blank or a flashed "no results" reads as a glitch.
                results.videos.isNotEmpty() -> TvVideoRow(
                    videos = results.videos,
                    onVideoClick = { video ->
                        RecentSearchesStore.add(query)
                        onVideoClick(video)
                    },
                    modifier = Modifier
                        .padding(top = WahaTvDimens.CardSpacing / 2)
                        .testTag(TvTestTags.SearchResults)
                )

                results.query == query -> TvMessage("لم نجد أي نتائج لـ \"$query\"")

                // The first search is still in flight: show nothing rather than a
                // message that the next frame will contradict.
                else -> Unit
            }
        }

        TvOnScreenKeyboard(
            state = state,
            // Remembered on the state so the keyboard's parameters do not change when
            // the query does — that is what keeps its keys out of the recomposition.
            onSearch = remember(state) { { RecentSearchesStore.add(state.query) } }
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
                    imageVector = TvIcons.Search,
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

/**
 * The phone's recent-searches list, as a D-pad list of focusable rows.
 *
 * It scrolls instead of folding the keyboard away: the space above the keyboard
 * is fixed now, and six recent queries do not fit it at 720p, so the remote
 * scrolls the list the same way it scrolls any other list.
 */
@Composable
private fun TvRecentSearches(
    recentQueries: List<String>,
    onQuerySelect: (String) -> Unit,
    onClearAll: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = WahaTvDimens.ScreenPadding,
            end = WahaTvDimens.ScreenPadding,
            top = WahaTvDimens.SectionSpacing / 2,
            bottom = 8.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "recent_header") {
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
                        imageVector = TvIcons.History,
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
        }

        items(recentQueries.take(6)) { queryText ->
            TvKeyButton(
                label = queryText,
                onClick = { onQuerySelect(queryText) },
                width = 420.dp,
                height = WahaTvDimens.ListRowHeight
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
    state: TvSearchState,
    onSearch: () -> Unit
) {
    // The keyboard reads only the page it is on — never the query — so typing repaints the
    // field and the results row while these thirty-five keys stay exactly as they are.
    val page = state.page

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(WahaTvColors.Surface)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(WahaTvDimens.KeyboardKeyGap)
    ) {
        // Row 0: the page switches and the actions, on the letters' own columns,
        // so an up-press from the results always lands on a key.
        TvKeyboardRow {
            TvKeyboard.Page.entries.forEach { entry ->
                TvKeyButton(
                    label = entry.label,
                    selected = entry == page,
                    onClick = { state.showPage(entry) }
                )
            }
            TvKeyButton(label = "فراغ", onClick = { state.type(" ") })
            TvKeyButton(label = "حذف", onClick = state::backspace)
            TvKeyButton(label = "مسح", onClick = state::clear)
            TvKeyButton(label = "ابحث", onClick = onSearch)
        }

        TvKeyboard.keyRows(page)
            .chunked(TvKeyboard.KEYS_PER_ROW)
            .forEach { rowKeys ->
                TvKeyboardRow {
                    rowKeys.forEach { key ->
                        TvKeyButton(
                            label = key,
                            onClick = { state.type(key) }
                        )
                    }
                }
            }
    }
}

/**
 * One row of the keyboard grid, pinned to the grid's exact width so a key always
 * lines up with the key above and below it (a short final row starts at the
 * layout's start edge, which is the right one in RTL).
 */
@Composable
private fun TvKeyboardRow(content: @Composable () -> Unit) {
    val gridWidth = WahaTvDimens.KeyboardKeyWidth * TvKeyboard.KEYS_PER_ROW +
            WahaTvDimens.KeyboardKeyGap * (TvKeyboard.KEYS_PER_ROW - 1)
    Row(
        modifier = Modifier.width(gridWidth),
        horizontalArrangement = Arrangement.spacedBy(WahaTvDimens.KeyboardKeyGap)
    ) {
        content()
    }
}

/**
 * One key: a focusable card that fills with the brand accent while focused.
 * Shared with the library screen's "مسح الكل" action.
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
        scale = CardDefaults.scale(focusedScale = WahaTvDimens.FocusScale),
        border = CardDefaults.border(focusedBorder = tvFocusBorder())
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
