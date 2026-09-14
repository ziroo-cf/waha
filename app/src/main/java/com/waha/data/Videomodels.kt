package com.waha.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VideoRow(
    val id: String,
    @SerialName("channel_id") val channelId: String? = null,
    val title: String? = null,
    val thumbnail: String? = null,
    val category: String? = null,
    val status: String = "pending"
)