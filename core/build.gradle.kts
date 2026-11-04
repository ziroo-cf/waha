import java.io.FileInputStream
import java.util.Properties

// Supabase credentials stay out of version control: they live in local.properties
// and are baked into this module's BuildConfig, since :core owns the data layer.
val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        load(FileInputStream(localPropertiesFile))
    }
}

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    // Room generates the DAO implementations and the schema at build time.
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.waha.core"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 24

        buildConfigField(
            "String",
            "SUPABASE_URL",
            "\"${localProperties.getProperty("SUPABASE_URL", "")}\""
        )
        buildConfigField(
            "String",
            "SUPABASE_ANON_KEY",
            "\"${localProperties.getProperty("SUPABASE_ANON_KEY", "")}\""
        )
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        // NewPipeExtractor relies on java.time / java.util.stream / Optional.
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        buildConfig = true
    }
}

ksp {
    // Versioned schemas are the input for Room's migration tests and for any
    // future migration: without them a schema change is a guessing game.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // Data layer. postgrest is exposed because SupabaseClientProvider.client is
    // part of this module's public API.
    api(platform("io.github.jan-tennert.supabase:bom:3.0.3"))
    api("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.ktor:ktor-client-android:3.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Offline-first library cache: Room is the single source of truth the UI reads.
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)

    // Local stores (Compose snapshot state, DataStore preferences).
    implementation(libs.androidx.compose.runtime)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // The shared brand palette is typed with Compose `Color`, so ui-graphics is
    // part of this module's public API; both UI modules build their theme from it.
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui.graphics)

    // Streams are resolved here and handed to the UI modules as Media3 sources,
    // so media3-exoplayer is part of the public API.
    api("androidx.media3:media3-exoplayer:1.11.1")

    // Extracts playable YouTube stream URLs on-device (no server, no embed).
    implementation("com.github.TeamNewPipe:NewPipeExtractor:0.26.5")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    testImplementation(libs.junit)
    // The library's paging loop is a suspend function; drive it on a test dispatcher.
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
