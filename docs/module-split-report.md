# Waha — Monolith → Multi-module split

Three modules, one shared data layer:

| Module | Type | Namespace / applicationId | Responsibility |
| --- | --- | --- | --- |
| `:core` | `com.android.library` | `com.waha.core` | Business/data layer: Supabase, repositories, stream extraction (NewPipe + Piped), DataStore stores, the suggestion engine, playback resolution. No phone or TV UI. |
| `:mobile` | `com.android.application` | `com.waha` / `com.waha` | Touch-first phone + tablet UI (Compose Material 3, pull-to-refresh, search, saved, history, settings, touch player). |
| `:tv` | `com.android.application` | `com.waha.tv` / `com.waha.tv` | Android TV UI (`LEANBACK_LAUNCHER`) built on Compose for TV, D-pad driven. |

`settings.gradle.kts` now contains `include(":core", ":mobile", ":tv")`; `app/` was renamed to `mobile/` with `git mv` so history is preserved.

## 1. What moved where

### app/ → mobile/ (whole module, `git mv`)
Every UI file, resource, font, launcher icon, `keepRules/`, and `WatchHistoryScreenTest.kt`.
Package names are unchanged (`com.waha`, `com.waha.ui.screens`, `com.waha.ui.theme`), so the
mobile sources needed no package rewrites.

### mobile/data/ → core/data/ (package names kept as `com.waha.data`)
| File | Note |
| --- | --- |
| `Supabaseclient.kt` | now reads `com.waha.core.BuildConfig` |
| `Videorepository.kt` | unchanged |
| `Videomodels.kt` | unchanged (flexible duration parser) |
| `Pipedapi.kt` | unchanged |
| `Youtubeextractor.kt` | unchanged (NewPipeExtractor) |
| `Savedvideosstore.kt` | unchanged |
| `Watchhistorystore.kt` | unchanged |
| `Recentsearchesstore.kt` | unchanged |
| `Themepreferencestore.kt` | unchanged |

### Extracted out of the UI into :core (new files)
| New file | Came from | Contents |
| --- | --- | --- |
| `core/domain/VideoItem.kt` | `WahaHomeScreen.kt` | `VideoItem` model, `displayThumbnailUrl()`, and the new shared `VideoRow.toVideoItem()` mapping (previously private in `Wahahomeviewmodel.kt`). |
| `core/domain/VideoSuggestionsEngine.kt` | `VideoPlayerModal.kt` | The whole "شاهد المزيد" engine as an object: `build()`, `suggestionScore()`, `seriesTokens()`, `episodeNumber()`, `diversifyByCategory()` + its regexes/weights. |
| `core/domain/PlaybackResolver.kt` | `VideoPlayerModal.kt` | `PlaybackResolver.resolve()` (on-device extraction → Piped fallback, with both timeouts), `extractYouTubeId()`, `formatPlaybackTime()`, `PipedPlaybackSource.toMediaSource()`, `WahaPlayer.create()`. |

### Test moves
| From | To |
| --- | --- |
| `app/src/test/.../ui/screens/SuggestionsTest.kt` | `core/src/test/java/com/waha/domain/SuggestionsTest.kt` — now targets `VideoSuggestionsEngine.*` (14 tests). |
| — | `core/src/test/java/com/waha/domain/PlaybackResolverTest.kt` — new: id recovery from raw ids / thumbnail URLs (3 tests). |
| `app/src/test/.../WatchHistoryScreenTest.kt` | `mobile/src/test/java/com/waha/ui/screens/WatchHistoryScreenTest.kt` (unchanged; `watchedAgoLabel` is phone copy). |

### Deleted dead code
- `sharesSeries()` in the player: defined but never called.
- The stale `"falling back to the embedded player"` comment (that path no longer exists).
- The duplicated `formatTime()` in the mobile player — `:core`'s `formatPlaybackTime()` is now shared by both players.
- The private `VideoRow.toVideoItem()` in the mobile ViewModel, replaced by the core extension.

## 2. Gradle changes

- `gradle/libs.versions.toml`: added the `android-library` plugin alias, `androidx-compose-foundation`, and `androidx-tv-material` (1.1.0).
- `build.gradle.kts` (root): `alias(libs.plugins.android.library) apply false`.
- `core/build.gradle.kts` (new): library plugin + Kotlin serialization; reads `SUPABASE_URL` /
  `SUPABASE_ANON_KEY` from `local.properties` into **its own** `BuildConfig`; exposes
  `media3-exoplayer` and `supabase-postgrest` with `api` (they appear in core's public API);
  keeps core-library desugaring for NewPipe.
- `mobile/build.gradle.kts`: now `implementation(project(":core"))`; dropped Supabase, Ktor,
  serialization, NewPipe, DataStore and the local-properties/BuildConfig-field block;
  still keeps `buildConfig = true` because `SettingsScreen` shows `BuildConfig.VERSION_NAME`.
- `tv/build.gradle.kts` (new): application module depending on `:core` + Compose for TV.
- `.gitignore`: `/core/build`, `/mobile/build`, `/tv/build`.

Credentials never enter version control: `:core` reads them from the (ignored)
`local.properties`, exactly as the old `app` module did.

## 3. The TV module

`MainActivity` (Compose + `WahaTvTheme`) → `TvApp` → either `TvHomeScreen` or `TvPlayerScreen`.

- **Browse**: `LazyVerticalGrid` of `androidx.tv.material3.Card`s (focus scale via
  `CardDefaults.scale(focusedScale = 1.08f)`), loaded by `TvHomeViewModel` from the same
  `VideoRepository` and `toVideoItem()` mapping as the phone build.
- **Player**: D-pad only — centre/OK play-pause, left/right seek ±10 s (handled with
  `onPreviewKeyEvent` so focus never walks away mid-playback), up/down reveal overlays, back exits.
  Stream resolution and the ExoPlayer itself come from `:core`.
- **Manifest**: `android.software.leanback` / `android.hardware.touchscreen` declared as *not
  required* so the APK can also be side-loaded on an emulator for development, plus both
  `LEANBACK_LAUNCHER` and `LAUNCHER` categories.

**Note on `androidx.tv:tv-foundation`:** it is deliberately **not** a dependency. Its 1.0.0
artifact ships only `TvImeOptions` / `TvKeyboardAlignment`, and the TV lazy containers
(`androidx.tv.foundation.lazy.*`) were folded into `androidx.compose.foundation` — which is what
the browse grid uses. Declaring it would have added a dependency nothing compiles against.
`androidx.tv:tv-material:1.1.0` supplies the focus-aware Material components.

## 4. Verification (run on this checkout)

| Command | Result |
| --- | --- |
| `./gradlew :core:testDebugUnitTest` | BUILD SUCCESSFUL — 17 tests, 0 failures (`SuggestionsTest` 14, `PlaybackResolverTest` 3) |
| `./gradlew :mobile:assembleDebug` | BUILD SUCCESSFUL |
| `./gradlew :mobile:testDebugUnitTest` | BUILD SUCCESSFUL — `WatchHistoryScreenTest` 1 test, 0 failures |
| `./gradlew :tv:assembleDebug` | BUILD SUCCESSFUL |

Artifact checks:

- `mobile/build/outputs/apk/debug/mobile-debug.apk` — `package: com.waha`, `versionName 0.3b`,
  launchable activity `com.waha.MainActivity` (unchanged from the monolith).
- `tv/build/outputs/apk/debug/tv-debug.apk` — `package: com.waha.tv`, label `واحة TV`,
  `launchable-activity com.waha.tv.MainActivity` **and** `leanback-launchable-activity
  com.waha.tv.MainActivity`, category `android.intent.category.LEANBACK_LAUNCHER` present.
- `core/build/generated/source/buildConfig/debug/com/waha/core/BuildConfig.java` contains a
  non-empty `SUPABASE_URL`, i.e. the credentials from `local.properties` reached `:core`.

## 5. Known gaps

- The TV UI is a working skeleton: browse + play only. No search, saved, history, settings or
  quality/speed sheet yet — those are the next TV screens to port.
- No `android:banner` (320×180) asset yet; required before publishing the TV app to Play.
- Behaviour was verified by compilation, unit tests and APK inspection — the apps were not run
  on a device/emulator in this session, so UI behaviour is unverified end-to-end.
