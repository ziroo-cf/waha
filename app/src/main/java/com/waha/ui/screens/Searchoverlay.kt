package com.waha.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "رجوع", tint = WahaTextWarm)
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
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("اكتب عنوان الفيديو الذي تبحث عنه", color = WahaTextMuted, fontSize = 14.sp)
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
                        VideoCard(video = video, onClick = { onVideoClick(video) })
                    }
                }
            }
        }
    }
}