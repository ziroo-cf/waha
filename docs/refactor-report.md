# Waha App Refactor — September 14, 2026

Final state: `assembleDebug` BUILD SUCCESSFUL, zero compiler warnings.

## 1. Bugs & warnings
- **Kotlin plugin mismatch fixed**: `kotlin("plugin.serialization") version 2.0.20` conflicted with Kotlin 2.2.10. Both now use the version catalog alias `kotlin-serialization` (2.2.10).
- **Deprecated APIs replaced**:
  - `Divider` → `HorizontalDivider` (Settings, VideoPlayerModal)
  - `LocalLifecycleOwner` (compose package) → `androidx.lifecycle.compose.LocalLifecycleOwner`
  - `View.systemUiVisibility` flags → `WindowInsetsControllerCompat.hide/show(WindowInsetsCompat.Type.systemBars())`
- **Compose correctness**: `VideoPlayerModal` wrote `lastLoadedId` during composition; video load/switch now runs in a `SideEffect`.
- 2 deprecation warnings → 0.

## 2. Security
- ~~Parental PIN stored as salted SHA-256 hash with backup exclusions~~ — feature was added in this refactor cycle and **later removed entirely at the user's request** (deleted `ParentalPinStore`, `ParentalPinDialog`, settings row, and backup exclusions).

## 3. Dead code / files removed
- Template colors `purple_*`, `teal_*`, `black`, `white` from `colors.xml` (kept only `ic_launcher_background`).
- Deleted: `values/ic_launcher_background.xml` (duplicate), `drawable/ic_launcher_background.xml` (unused vector), `ExampleUnitTest.kt`, `ExampleInstrumentedTest.kt`.
- Removed unused imports (`ArrowForward`), unused `update` lambda in `AndroidView`, redundant duplicate theme aliases.

## 4. Redundancy removed (shared helpers)
- `LazyListState.HideOnScrollEffect(onVisibilityChange)` — was duplicated in Home + Saved screens.
- `VideoItem.displayThumbnailUrl()` — YouTube thumbnail fallback was repeated 5×.
- `Activity.enterImmersiveFullscreen()` / `exitImmersiveFullscreen()` — insets logic was inlined twice.
- ViewModel: single mapping pass rows → `VideoItem`; category groups derived from the same list. `Success(shuffledVideos)` → `Success(videos)`.
- `VideoItem` now has optional `categoryLabel` (merged `meta`-suffix logic).

## 5. Performance & clean code
- Single-pass category filtering; O(1) category lookup in Home list.
- Reactive saved state: `SavedVideosStore.rememberSavedState(videoId)` (`derivedStateOf`) for bookmark icons; `SavedVideosStore.savedVideosState(allVideos)` (remember on `savedIds.size`) for the Saved list. No manual `isSaved` resync.
- Dependencies deduplicated: hardcoded `activity-compose:1.9.3` and `lifecycle-viewmodel-compose:2.8.7` duplicates removed; `lifecycle-runtime-compose` added via catalog.

## Build verification
- `./gradlew compileDebugKotlin` → SUCCESS, 0 warnings
- `./gradlew assembleDebug` → SUCCESS (1m 39s, 38 tasks)
