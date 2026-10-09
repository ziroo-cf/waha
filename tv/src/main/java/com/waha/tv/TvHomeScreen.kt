package com.waha.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.waha.domain.VideoItem
import com.waha.domain.displayThumbnailUrl

/**
 * The TV home screen: the phone build's library, re-shaped for a remote.
 *
 * The phone shows one flat scrolling grid with a category chip bar; a TV browses
 * by rows, so the same content becomes a hero title, the category chips, and one
 * horizontal rail per category ("أحدث الإضافات" first).
 */
@Composable
fun TvHomeScreen(
    uiState: TvUiState,
    onVideoClick: (VideoItem) -> Unit,
    onRetry: () -> Unit
) {
    when (uiState) {
        is TvUiState.Loading -> TvMessage("جارٍ تحميل المحتوى…")

        is TvUiState.Error -> TvErrorState(onRetry = onRetry)

        is TvUiState.Ready -> if (uiState.videos.isEmpty()) {
            TvMessage("لا توجد فيديوهات منشورة بعد")
        } else {
            // A 720p TV is only ~540dp tall, and a fixed 360dp hero would then
            // push every rail off the screen — the rails are the point of this
            // screen, so the hero scales down with the viewport instead.
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val heroHeight = (maxHeight * 0.38f).coerceIn(180.dp, WahaTvDimens.HeroHeight)
                TvHomeContent(
                    videos = uiState.videos,
                    heroHeight = heroHeight,
                    onVideoClick = onVideoClick
                )
            }
        }
    }
}

@Composable
private fun TvHomeContent(
    videos: List<VideoItem>,
    heroHeight: Dp,
    onVideoClick: (VideoItem) -> Unit
) {
    var selectedCategory by remember { mutableStateOf(TvRails.ALL_CATEGORY_KEY) }
    val listState = rememberLazyListState()

    val rails = remember(videos, selectedCategory) {
        TvRails.filter(TvRails.build(videos), selectedCategory)
    }
    val tiles = remember(videos) { TvRails.categoryTiles(videos) }
    val hero = rails.firstOrNull()?.videos?.firstOrNull()

    // Filtering changes the whole list, so start again from the hero.
    LaunchedEffect(selectedCategory) { listState.scrollToItem(0) }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = WahaTvDimens.ScreenPadding / 2, bottom = WahaTvDimens.ScreenPadding)
    ) {
        if (hero != null) {
            item(key = "hero") {
                TvHero(video = hero, height = heroHeight, onPlay = { onVideoClick(hero) })
            }
        }

        item(key = "filters") {
            TvCategoryChips(
                tiles = tiles,
                selectedKey = selectedCategory,
                onSelect = { selectedCategory = it },
                modifier = Modifier.padding(top = WahaTvDimens.SectionSpacing / 2)
            )
        }

        items(rails, key = { it.key }) { rail ->
            TvVideoRail(
                title = rail.title,
                videos = rail.videos,
                onVideoClick = onVideoClick
            )
        }
    }
}

/** The featured title: the phone build's card artwork, blown up to a banner. */
@Composable
private fun TvHero(video: VideoItem, height: Dp, onPlay: () -> Unit) {
    Card(
        onClick = onPlay,
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .padding(horizontal = WahaTvDimens.ScreenPadding),
        shape = CardDefaults.shape(RoundedCornerShape(WahaTvDimens.CornerRadius)),
        colors = CardDefaults.colors(
            containerColor = WahaTvColors.Surface,
            focusedContainerColor = WahaTvColors.SurfaceVariant
        ),
        scale = CardDefaults.scale(focusedScale = WahaTvDimens.HeroFocusScale),
        border = CardDefaults.border(focusedBorder = tvFocusBorder()),
        glow = CardDefaults.glow(focusedGlow = tvFocusGlow())
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = video.displayThumbnailUrl(),
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .background(WahaTvColors.CardPlaceholder)
            )

            // Artwork fades into the page background so the title reads cleanly.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                WahaTvColors.MediaScrimSoft,
                                WahaTvColors.Background.copy(alpha = 0.85f)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = video.title,
                    color = WahaTvColors.TextWarm,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                val meta = listOfNotNull(
                    video.meta.takeIf { it.isNotBlank() },
                    video.durationText?.takeIf { it.isNotBlank() }
                ).joinToString(" • ")
                if (meta.isNotBlank()) {
                    Text(
                        text = meta,
                        color = WahaTvColors.TextMuted,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // A brand-accent play affordance; the whole card is the target.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .background(WahaTvColors.Accent, RoundedCornerShape(100.dp))
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = WahaTvColors.OnAccent,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "تشغيل",
                        color = WahaTvColors.OnAccent,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun TvErrorState(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "تعذّر تحميل المحتوى",
            color = WahaTvColors.TextWarm,
            style = MaterialTheme.typography.titleLarge
        )
        Button(
            onClick = onRetry,
            scale = ButtonDefaults.scale(focusedScale = 1.08f),
            modifier = Modifier.padding(top = 24.dp)
        ) {
            Text(text = "إعادة المحاولة", style = MaterialTheme.typography.labelLarge)
        }
    }
}
