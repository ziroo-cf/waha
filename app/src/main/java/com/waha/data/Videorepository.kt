package com.waha.data

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order

class VideoRepository {

    suspend fun getVideos(): List<VideoRow> {
        return SupabaseClientProvider.client.from("videos")
            .select(columns = Columns.ALL) {
                filter {
                    eq("status", "approved")
                }
                order(column = "created_at", order = Order.DESCENDING)
            }
            .decodeList<VideoRow>()
    }
}