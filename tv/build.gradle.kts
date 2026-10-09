plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.waha.tv"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        // A separate applicationId so the TV build can be installed next to the
        // phone build (and so both keep independent launcher entries).
        applicationId = "com.waha.tv"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "0.1b"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        // Stream resolution inside :core relies on java.time via NewPipeExtractor.
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    // Same data layer, stores and stream resolution as the phone build.
    implementation(project(":core"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.core.ktx)

    // The TV screens reuse the phone build's icons (bookmark, history, home,
    // search) so both apps' affordances stay identical.
    implementation("androidx.compose.material:material-icons-extended")

    // Compose for TV: focus-aware (D-pad first) Material components.
    // androidx.tv:tv-foundation is deliberately not declared: its 1.0.0 artifact
    // only ships TV ime/text helpers, while the TV lazy containers were folded
    // into androidx.compose.foundation, which the browse grid below uses.
    implementation(libs.androidx.tv.material)

    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-ui:1.11.1")
    implementation("io.coil-kt:coil-compose:2.7.0")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    testImplementation(libs.junit)
}
