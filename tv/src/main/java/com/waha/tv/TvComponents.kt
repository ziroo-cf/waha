package com.waha.tv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardColors
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.FilterChipDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.waha.core.R as CoreR
import com.waha.data.SavedVideosStore
import com.waha.domain.VideoItem
import com.waha.domain.displayThumbnailUrl

/** How many cards per row in the grid screens (search, saved, history). */
private const val TV_GRID_COLUMNS = 4

/** The brand's focus border: thick and in the primary accent, so the focused card is unmistakable. */
@Composable
internal fun tvFocusBorder(): Border =
    Border(border = BorderStroke(WahaTvDimens.FocusBorderWidth, WahaTvColors.Accent))

/** The brand's focus glow: a soft accent halo around the focused element. */
@Composable
internal fun tvFocusGlow(): Glow =
    Glow(elevationColor = WahaTvColors.Accent.copy(alpha = 0.55f), elevation = WahaTvDimens.FocusGlowRadius)

/** The brand's focus scale: 1.1x, per Material's TV guidance. */
internal fun tvFocusScale(): Float = WahaTvDimens.FocusScale

/**
 * Card colours for the small focusable controls (sidebar entries, keyboard keys,
 * filter chips): transparent-to-surface when idle, tinted when selected, and
 * solid brand accent while the remote is on it.
 *
 * The content colour is returned too, because a card that turns accent-coloured
 * needs dark-on-accent text in the same frame — the default animated transition
 * would leave the label unreadable for a beat.
 */
@Composable
internal fun tvItemColors(selected: Boolean, isFocused: Boolean): CardColors =
    CardDefaults.colors(
        containerColor = when {
            isFocused -> WahaTvColors.Accent
            selected -> WahaTvColors.Accent.copy(alpha = 0.22f)
            else -> WahaTvColors.Surface
        },
        contentColor = tvItemContentColor(selected, isFocused),
        focusedContainerColor = WahaTvColors.Accent,
        focusedContentColor = WahaTvColors.OnAccent
    )

/** The label/icon colour that pairs with [tvItemColors]. */
@Composable
internal fun tvItemContentColor(selected: Boolean, isFocused: Boolean): Color = when {
    isFocused -> WahaTvColors.OnAccent
    selected -> WahaTvColors.Accent
    else -> WahaTvColors.TextMuted
}

/**
 * One focusable video tile, in the phone build's card idiom adapted to the
 * remote: same thumbnail, duration badge and two-line title, but the whole card
 * scales up and takes an accent border/glow while focused, and shows a play
 * glyph so "OK" is obviously the next step.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TvVideoCard(
    video: VideoItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Fixed rail width; null lets the caller's constraints (the grid) size the card. */
    width: Dp? = WahaTvDimens.CardWidth,
    /** Optional muted line under the title (the history grid's "watched x ago"). */
    footnote: String? = null
) {
    val savedIds = SavedVideosStore.savedIds
    val isSaved = remember(savedIds.size, video.id) { savedIds.contains(video.id) }
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Card(
        onClick = onClick,
        modifier = modifier.then(if (width != null) Modifier.width(width) else Modifier),
        interactionSource = interactionSource,
        shape = CardDefaults.shape(RoundedCornerShape(WahaTvDimens.CornerRadius)),
        colors = CardDefaults.colors(
            containerColor = WahaTvColors.Surface,
            focusedContainerColor = WahaTvColors.SurfaceVariant
        ),
        scale = CardDefaults.scale(focusedScale = tvFocusScale()),
        border = CardDefaults.border(focusedBorder = tvFocusBorder()),
        glow = CardDefaults.glow(focusedGlow = tvFocusGlow())
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(WahaTvDimens.CardHeight)
        ) {
            AsyncImage(
                model = video.displayThumbnailUrl(),
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .background(WahaTvColors.CardPlaceholder)
            )

            if (isFocused) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(WahaTvColors.MediaScrimSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(WahaTvColors.Accent, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "تشغيل",
                            tint = WahaTvColors.OnAccent,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }

            video.durationText?.let { duration ->
                TvCornerBadge(
                    text = duration,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                )
            }

            if (isSaved) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                        .background(WahaTvColors.SavedBadge, CircleShape)
                        .padding(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bookmark,
                        contentDescription = "محفوظ",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Text(
            text = video.title,
            color = WahaTvColors.TextWarm,
            style = MaterialTheme.typography.titleSmall,
            maxLines = WahaTvDimens.CardTitleMaxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        )
        if (video.meta.isNotBlank() || footnote != null) {
            Text(
                text = footnote ?: video.meta,
                color = WahaTvColors.TextMuted,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp)
            )
        }
    }
}

/** A small dark pill over artwork: durations, "محفوظ", and the like. */
@Composable
fun TvCornerBadge(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = Color.White,
        style = MaterialTheme.typography.labelMedium,
        modifier = modifier
            .background(WahaTvColors.MediaScrim, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

/** Section headline above a rail. */
@Composable
fun TvSectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        color = WahaTvColors.TextWarm,
        style = MaterialTheme.typography.headlineSmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(
            start = WahaTvDimens.ScreenPadding,
            end = WahaTvDimens.ScreenPadding,
            bottom = 14.dp
        )
    )
}

/**
 * A horizontal rail of cards — the TV equivalent of the phone's flat grid.
 *
 * The parent list scrolls vertically; each rail scrolls horizontally, and the
 * focus system walks between the two, which is exactly how a remote should
 * browse a library.
 */
@Composable
fun TvVideoRail(
    title: String,
    videos: List<VideoItem>,
    onVideoClick: (VideoItem) -> Unit,
    modifier: Modifier = Modifier
) {
    if (videos.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = WahaTvDimens.SectionSpacing)
    ) {
        TvSectionHeader(title)
        LazyRow(
            contentPadding = PaddingValues(horizontal = WahaTvDimens.ScreenPadding),
            horizontalArrangement = Arrangement.spacedBy(WahaTvDimens.CardSpacing),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(videos, key = { it.id }) { video ->
                TvVideoCard(video = video, onClick = { onVideoClick(video) })
            }
        }
    }
}

/** The grid screens (search results, saved, history) reuse the same card. */
@Composable
fun TvVideoGrid(
    videos: List<VideoItem>,
    onVideoClick: (VideoItem) -> Unit,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
    footnote: (VideoItem) -> String? = { null },
    contentPadding: PaddingValues = PaddingValues(
        start = WahaTvDimens.ScreenPadding,
        end = WahaTvDimens.ScreenPadding,
        top = WahaTvDimens.ScreenPadding,
        bottom = WahaTvDimens.ScreenPadding
    )
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(TV_GRID_COLUMNS),
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(WahaTvDimens.CardSpacing),
        verticalArrangement = Arrangement.spacedBy(WahaTvDimens.CardSpacing)
    ) {
        if (header != null) {
            item {
                Box(modifier = Modifier.fillMaxWidth()) { header() }
            }
        }
        items(videos, key = { it.id }) { video ->
            TvVideoCard(
                video = video,
                onClick = { onVideoClick(video) },
                modifier = Modifier.fillMaxWidth(),
                width = null,
                footnote = footnote(video)
            )
        }
    }
}

/** Category filter chips, D-pad first, reading from the brand accent. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvCategoryChips(
    tiles: List<TvCategoryTile>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = WahaTvDimens.ScreenPadding),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        items(tiles, key = { it.key }) { tile ->
            val selected = tile.key == selectedKey
            FilterChip(
                selected = selected,
                onClick = { onSelect(tile.key) },
                colors = FilterChipDefaults.colors(
                    containerColor = WahaTvColors.Surface,
                    contentColor = WahaTvColors.TextMuted,
                    focusedContainerColor = WahaTvColors.Accent,
                    focusedContentColor = WahaTvColors.OnAccent,
                    selectedContainerColor = WahaTvColors.Accent,
                    selectedContentColor = WahaTvColors.OnAccent,
                    focusedSelectedContainerColor = WahaTvColors.Accent,
                    focusedSelectedContentColor = WahaTvColors.OnAccent
                ),
                scale = FilterChipDefaults.scale(focusedScale = 1.12f),
                border = FilterChipDefaults.border(
                    border = Border(border = BorderStroke(1.dp, WahaTvColors.Line)),
                    selectedBorder = Border(border = BorderStroke(1.dp, WahaTvColors.Accent)),
                    focusedSelectedBorder = Border(
                        border = BorderStroke(WahaTvDimens.FocusBorderWidth, WahaTvColors.Accent)
                    )
                )
            ) {
                Text(text = tile.label, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/**
 * The Waha mark: the phone build's launcher artwork plus the wordmark, so the
 * two apps are recognisably the same product.
 */
@Composable
fun TvBrandMark(modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
    ) {
        Image(
            painter = painterResource(id = CoreR.drawable.ic_logo),
            contentDescription = "شعار واحة",
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(100.dp))
        )
        Text(
            text = "واحة",
            color = WahaTvColors.TextWarm,
            style = MaterialTheme.typography.headlineSmall
        )
    }
}

/** A centred message for the empty and error states. */
@Composable
fun TvMessage(text: String, modifier: Modifier = Modifier, color: Color? = null) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = color ?: WahaTvColors.TextMuted,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = WahaTvDimens.ScreenPadding)
        )
    }
}
