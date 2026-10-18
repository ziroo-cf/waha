package com.waha.tv.ui.screens

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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.NativeKeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardColors
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.waha.core.R as CoreR
import com.waha.data.SavedVideosStore
import com.waha.domain.VideoItem
import com.waha.domain.displayThumbnailUrl
import com.waha.tv.TvCardMeta
import com.waha.tv.TvTestTags
import com.waha.tv.ui.theme.WahaTvColors

/**
 * The keys a TV remote sends to activate whatever the remote is on.
 *
 * Compose's own `clickable` turns Enter into a click, but a remote sends DPAD_CENTER, which
 * foundation leaves alone — which is why Compose-for-TV's components handle these three keys
 * themselves. The rail and the player's own buttons are drawn without those components, so
 * they share this handling instead.
 */
private val TvEnterKeyCodes = intArrayOf(
    NativeKeyEvent.KEYCODE_DPAD_CENTER,
    NativeKeyEvent.KEYCODE_ENTER,
    NativeKeyEvent.KEYCODE_NUMPAD_ENTER
)

/**
 * Runs [onClick] when the remote's centre/OK key is released on the focused element, and
 * swallows the press so nothing else reacts to it.
 */
internal fun Modifier.tvClickOnEnterKey(onClick: () -> Unit): Modifier =
    onKeyEvent { event ->
        if (event.nativeKeyEvent.keyCode !in TvEnterKeyCodes) return@onKeyEvent false
        if (event.type == KeyEventType.KeyUp) onClick()
        true
    }

/**
 * The brand's focus border: thick and in the primary accent, so the focused card is
 * unmistakable.
 *
 * No shape is passed: tv-material3 reads the default border shape as "the shape of the
 * element underneath", so one border serves the cards' rounded rectangles and the rail's
 * and chips' pills without any of them restating its own corners.
 */
@Composable
internal fun tvFocusBorder(): Border =
    Border(border = BorderStroke(WahaTvDimens.FocusBorderWidth, WahaTvColors.Accent))

/** The brand's focus glow: a soft accent halo around the focused element. */
@Composable
internal fun tvFocusGlow(): Glow =
    Glow(elevationColor = WahaTvColors.Accent.copy(alpha = 0.55f), elevation = WahaTvDimens.FocusGlowRadius)

/** The brand's focus scale: none — focus is drawn, never animated into size. */
internal fun tvFocusScale(): Float = WahaTvDimens.FocusScale

/**
 * Card colours for the small focusable controls (the search page's rows, the on-screen
 * keyboard's keys, the settings rows): surface when idle, tinted when selected, and solid
 * brand accent while the remote is on it.
 *
 * The content colour comes from [tvItemContentColor] rather than from the component's own
 * resolution, so the label is dark-on-accent in the same frame the fill turns accent: focus
 * here never waits on anything, which is the rule the whole TV build follows.
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
 * One focusable video tile: a wide 16:9 landscape card whose detail sits
 * permanently underneath it — the title, the channel, and the running time — so a
 * viewer browsing from the sofa always knows what a row holds without pressing
 * anything.
 *
 * Focus is a coloured border (plus a soft accent glow and a play glyph); the card
 * itself never grows, so moving the remote cannot shift the shelf around.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TvVideoCard(
    video: VideoItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Fixed rail width; null lets the caller's constraints size the card. */
    width: Dp? = WahaTvDimens.CardWidth,
    /** Optional muted line under the title (the history grid's "watched x ago"). */
    footnote: String? = null,
    /** 0f..1f watched so far; draws a thin progress bar over the artwork. */
    progressFraction: Float? = null
) {
    val savedIds = SavedVideosStore.savedIds
    val isSaved = remember(savedIds.size, video.id) { savedIds.contains(video.id) }
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Card(
        onClick = onClick,
        modifier = modifier
            .then(if (width != null) Modifier.width(width) else Modifier)
            .testTag(TvTestTags.card(video.id)),
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
                .aspectRatio(WahaTvDimens.CardAspectRatio)
                .testTag(TvTestTags.artwork(video.id))
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

            // The running time rides on the artwork itself — a dark pill in the card's
            // bottom-left corner, the way a TV card shows it — so the detail block below
            // can stay title and channel only, with nothing written twice on one card.
            TvCardMeta.timeLabel(video)?.let { time ->
                Text(
                    text = time,
                    color = WahaTvColors.OnMedia,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(WahaTvDimens.BadgeInset)
                        .background(
                            color = WahaTvColors.MediaScrim,
                            shape = RoundedCornerShape(WahaTvDimens.BadgeCornerRadius)
                        )
                        .padding(
                            horizontal = WahaTvDimens.BadgePaddingHorizontal,
                            vertical = WahaTvDimens.BadgePaddingVertical
                        )
                        .testTag(TvTestTags.durationBadge(video.id))
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
                        tint = WahaTvColors.OnMedia,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // How far the viewer got — the "أكمل المشاهدة" rail's whole point.
            if (progressFraction != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(WahaTvDimens.ProgressBarHeight)
                        .background(WahaTvColors.ProgressTrack)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progressFraction.coerceIn(0f, 1f))
                            .background(WahaTvColors.Accent)
                    )
                }
            }
        }

        // Always the same two facts, in the same order and at the same gutter on every
        // card — title over category, nothing conditional on focus and nothing that the
        // library may or may not have filled in. The title is given two lines' worth of
        // room (even when it needs one) so a shelf of cards keeps one height.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = WahaTvDimens.CardMetaPaddingHorizontal,
                    end = WahaTvDimens.CardMetaPaddingHorizontal,
                    top = WahaTvDimens.CardMetaPaddingTop,
                    bottom = WahaTvDimens.CardMetaPaddingBottom
                )
        ) {
            Text(
                text = video.title,
                color = WahaTvColors.TextWarm,
                style = MaterialTheme.typography.titleSmall,
                minLines = WahaTvDimens.CardTitleMaxLines,
                maxLines = WahaTvDimens.CardTitleMaxLines,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = TvCardMeta.categoryLabel(video),
                color = WahaTvColors.TextMuted,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = WahaTvDimens.CardMetaLineSpacing)
            )

            if (footnote != null) {
                Text(
                    text = footnote,
                    color = WahaTvColors.TextMuted,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = WahaTvDimens.CardMetaLineSpacing)
                )
            }
        }
    }
}

/**
 * Section headline above a rail, with an optional action at its far edge (the
 * library's "مسح الكل" beside السجل). Both share the screen's own gutter.
 */
@Composable
fun TvSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = WahaTvDimens.ScreenPadding,
                end = WahaTvDimens.ScreenPadding,
                bottom = 14.dp
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            color = WahaTvColors.TextWarm,
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )

        action?.invoke()
    }
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
    /** Stable identity of this row (a category key, or the Continue Watching id). */
    railKey: String,
    title: String,
    videos: List<VideoItem>,
    onVideoClick: (VideoItem) -> Unit,
    modifier: Modifier = Modifier,
    /** Optional muted line under the title (the history row's "watched x ago"). */
    footnote: (VideoItem) -> String? = { null },
    /** 0f..1f watched so far per video; null (the default) draws no bar. */
    progress: (VideoItem) -> Float? = { null },
    /**
     * Shown in the row's place when it has no videos. A rail with no message
     * disappears entirely (the home screen's categories never show an empty row).
     */
    emptyMessage: String? = null,
    /** Optional action beside the title (the library's "مسح الكل" on السجل). */
    action: (@Composable () -> Unit)? = null
) {
    if (videos.isEmpty() && emptyMessage == null) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = WahaTvDimens.SectionSpacing)
            .testTag(TvTestTags.rail(railKey))
    ) {
        TvSectionHeader(title = title, action = action)

        if (videos.isEmpty()) {
            Text(
                text = emptyMessage.orEmpty(),
                color = WahaTvColors.TextMuted,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = WahaTvDimens.ScreenPadding)
            )
        } else {
            TvVideoRow(
                videos = videos,
                onVideoClick = onVideoClick,
                footnote = footnote,
                progress = progress
            )
        }
    }
}

/**
 * One horizontal run of cards, with the screen's gutters and card spacing.
 *
 * Shared by the home rails, the library's two rows and the search results, so a
 * card looks and scrolls the same wherever it appears.
 */
@Composable
fun TvVideoRow(
    videos: List<VideoItem>,
    onVideoClick: (VideoItem) -> Unit,
    modifier: Modifier = Modifier,
    footnote: (VideoItem) -> String? = { null },
    progress: (VideoItem) -> Float? = { null }
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = WahaTvDimens.ScreenPadding),
        horizontalArrangement = Arrangement.spacedBy(WahaTvDimens.CardSpacing),
        modifier = modifier.fillMaxWidth()
    ) {
        items(videos, key = { it.id }) { video ->
            TvVideoCard(
                video = video,
                onClick = { onVideoClick(video) },
                footnote = footnote(video),
                progressFraction = progress(video)
            )
        }
    }
}

/**
 * The Waha mark on its own: the phone build's launcher artwork, so both apps are
 * recognisably the same product.
 *
 * The wordmark is not drawn here — the rail slides it in itself, in step with the
 * destination names beside it.
 */
@Composable
fun TvBrandMark(
    modifier: Modifier = Modifier,
    logoSize: Dp = WahaTvDimens.RailLogoSize
) {
    Image(
        painter = painterResource(id = CoreR.drawable.ic_logo),
        contentDescription = "شعار واحة",
        modifier = modifier
            .size(logoSize)
            .clip(CircleShape)
    )
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
