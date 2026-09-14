package com.waha.data

import com.waha.BuildConfig
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.serialization.json.Json

object SupabaseClientProvider {
    val client = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY
    ) {
        install(Postgrest)

        defaultSerializer = io.github.jan.supabase.serializer.KotlinXSerializer(
            Json { ignoreUnknownKeys = true }
        )
    }
}