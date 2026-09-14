package com.waha.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.waha.data.RecentSearchesStore
import com.waha.ui.theme.WahaCardBg
import com.waha.ui.theme.WahaDarkBg
import com.waha.ui.theme.WahaLine
import com.waha.ui.theme.WahaTealBright
import com.waha.ui.theme.WahaTextMuted
import com.waha.ui.theme.WahaTextWarm
import kotlinx.coroutines.delay

@Composable
fun SearchOverlay(
    allVideos: List<VideoItem>,
    onVideoClick: (VideoItem) -> Unit,
    onClose: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    val results = remember(query, allVideos) {
        if (query.isBlank()) emptyList()
        else allVideos.filter { it.title.contains(query, ignoreCase = true) }
    }

    LaunchedEffect(Unit) {
        delay(150)
        focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WahaDarkBg)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {}
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = WahaTextWarm)
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                shape = RoundedCornerShape(100.dp),
                placeholder = { Text("ابحث عن فيديو هنا", color = WahaTextMuted) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = WahaTextMuted) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "مسح", tint = WahaTextMuted)
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { RecentSearchesStore.add(query) }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = WahaTextWarm,
                    unfocusedTextColor = WahaTextWarm,
                    focusedBorderColor = WahaTealBright,
                    unfocusedBorderColor = WahaLine,
                    focusedContainerColor = WahaCardBg,
                    unfocusedContainerColor = WahaCardBg
                )
            )
        }

        when {
            query.isBlank() -> {
                val recentQueries = RecentSearchesStore.recentQueries
                if (recentQueries.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("اكتب عنوان الفيديو الذي تبحث عنه", color = WahaTextMuted, fontSize = 14.sp)
                    }
                } else {
                    RecentSearchesSection(
                        recentQueries = recentQueries,
                        onRecentSearchClick = { query = it },
                        onRecentSearchRemove = { RecentSearchesStore.remove(it) },
                        onClearAll = { RecentSearchesStore.clear() }
                    )
                }
            }
            results.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("لم نجد أي نتائج لـ \"$query\"", color = WahaTextMuted, fontSize = 14.sp)
                }
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(results, key = { it.id }) { video ->
                        VideoCard(
                            video = video,
                            onClick = {
                                RecentSearchesStore.add(query)
                                onVideoClick(video)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentSearchesSection(
    recentQueries: List<String>,
    onRecentSearchClick: (String) -> Unit,
    onRecentSearchRemove: (String) -> Unit,
    onClearAll: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = WahaTextMuted,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "البحث الأخير",
                    color = WahaTextWarm,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            TextButton(onClick = onClearAll) {
                Text("مسح الكل", color = WahaTealBright, fontSize = 13.sp)
            }
        }

        Column {
            recentQueries.forEach { queryText ->
                RecentSearchRow(
                    queryText = queryText,
                    onClick = { onRecentSearchClick(queryText) },
                    onRemove = { onRecentSearchRemove(queryText) }
                )
            }
        }
    }
}

@Composable
private fun RecentSearchRow(
    queryText: String,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.History,
            contentDescription = null,
            tint = WahaTextMuted,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = queryText,
            color = WahaTextWarm,
            fontSize = 14.sp,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) {
            Icon(
                imageVector = Icons.Default.DeleteOutline,
                contentDescription = "حذف من البحث الأخير",
                tint = WahaTextMuted,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}