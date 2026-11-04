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
        // The TV build keeps its own version string; the phone build ships as "0.3b".
        versionName = "0.3.1"

        // The D-pad/focus tests under src/androidTest run on a device or TV
        // emulator (landscape, 16:9 — see the android-tv-testing skill).
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // This is the build a set-top box installs: shrinking and optimizing is
            // what removes the debug-time class loading and the reflection lookups
            // R8 can resolve ahead of time. The TV-27 baseline profile is compiled
            // into it, so the first frames skip the JIT warm-up too.
            optimization {
                enable = true
            }
            proguardFiles("proguard-rules.pro")
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
        // Settings shows BuildConfig.VERSION_NAME.
        buildConfig = true
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

    // The screens draw the same Material glyphs the phone build uses, but from
    // TvIcons: the extended set ships thousands of vectors (and their dex) for the
    // sixteen this build draws.
    // The player's progress bar is the phone's own Material 3 Slider, from the
    // same BOM, so the two builds cannot drift into different-looking sliders.
    implementation(libs.androidx.compose.material3)

    // Compose for TV: focus-aware (D-pad first) Material components.
    // androidx.tv:tv-foundation is deliberately not declared: its 1.0.0 artifact
    // only ships TV ime/text helpers, while the TV lazy containers were folded
    // into androidx.compose.foundation, which the browse grid below uses.
    implementation(libs.androidx.tv.material)

    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-ui:1.11.1")
    implementation("io.coil-kt:coil-compose:2.7.0")
    // Ships the baseline profile (src/main/baseline-prof.txt) with the APK and
    // installs it on first run, so the TV box starts warm instead of JIT-cold.
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    testImplementation(libs.junit)
    // TV ViewModel state is verified through its StateFlow, on a virtual clock.
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("app.cash.turbine:turbine:1.2.0")

    // Focus and D-pad behaviour is asserted on a real TV surface: press real
    // remote keys at tagged nodes (never a touch click) and read the focused node.
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
