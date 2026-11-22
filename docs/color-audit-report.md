# Waha — Color & Theming Audit (`:core`, `:mobile`, `:tv`)

**Scope:** all Kotlin sources and XML resources under `core/src`, `mobile/src`, `tv/src` (generated `build/` output excluded).
**Mode:** read-only analysis. No source file was modified.
**Modules reviewed:** `:core` (`com.waha.brand`, `com.waha.data`), `:mobile` (`com.waha`, `com.waha.ui.*`), `:tv` (`com.waha.tv.ui.*`).
**Files with color definitions:** 4 Kotlin (`WahaBrandColors.kt`, `Color.kt`, `Theme.kt`, `TvColors.kt`, `TvTheme.kt` — 5 with the TV scheme) + 3 XML (`colors.xml` ×2, `themes.xml` ×2) + 1 vector drawable (`tv_banner.xml`).
**Files with color consumption:** 12 mobile/Compose files and 9 TV/Compose files (listed in §3).

---

## 0. Executive summary

| # | Finding | Severity |
|---|---|---|
| 1 | The brand palette is genuinely centralised: **42 literal tokens** live once in `:core`'s `WahaBrandColors`; `:mobile` re-exports them under legacy names and `:tv` builds its scheme from the same literals. No literal is duplicated between phone and TV themes. | ✅ Good |
| 2 | `:tv` is **dark-only**. It never defines a light scheme and ignores `ThemePreferenceStore`. `:mobile` is light/dark, defaulting to dark. | ℹ️ By design |
| 3 | **44 hardcoded color expressions** bypass the theme system (24 in `:mobile`, 20 in `:tv`), almost all media scrims / overlay chrome over video artwork. | ⚠️ Medium |
| 4 | `WahaGold` (`:mobile`, `Color.kt:68`) is **never referenced** — only imported (`VideoPlayerModal.kt:77`). Dead token. | ⚠️ Low |
| 5 | `WahaTvColors.SurfaceVariant` (`TvColors.kt:30`) is **never referenced**. Dead token. | ⚠️ Low |
| 6 | Two near-duplicate panel blacks: `WahaTvColors.PanelBackground` `0xF2131313` and a private `PanelInk` `0xFF121212` in `TvPlayerScreen.kt:923`. | ⚠️ Low |
| 7 | `:mobile`'s `WahaTeal` and `WahaTealBright` are **the same value** (`colorScheme.primary`), as are `WahaCardBg`/`WahaCardBg2` vs `surface`/`surfaceVariant` — several aliases add no semantics. | ℹ️ Info |
| 8 | Neither scheme overrides `surfaceTint`, `outlineVariant`, `errorContainer`, `inverse*`, `surfaceContainer*`, `scrim`. Material components that read those roles (bottom sheet scrim, dialogs, dividers) fall back to **Material's baseline palette**, not Waha's. | ⚠️ Medium |
| 9 | The launcher background `#FF6D00` (both `colors.xml`) is **not** the brand orange `#FFFF6600`. | ℹ️ Low |
| 10 | Both `themes.xml` use `@android:color/black` as `windowBackground` regardless of the chosen theme — a light-mode cold start flashes black. | ℹ️ Low |

---

## 1. Master Color Palette Table

### 1.1 `:core` — shared brand literals (`WahaBrandColors`)

Defined in `core/src/main/java/com/waha/brand/WahaBrandColors.kt`. These are the **only** `Color(0x…)` literals in the app's theme system; every other module consumes them.

| Token / Variable Name | Hex Code | Kotlin Definition | Defined In (Module & File) |
|---|---|---|---|
| `PrimaryLight` | `#FF6600` | `Color(0xFFFF6600)` | `:core` → `core/src/main/java/com/waha/brand/WahaBrandColors.kt:17` |
| `OnPrimaryLight` | `#FFFFFF` | `Color(0xFFFFFFFF)` | `:core` → `…/WahaBrandColors.kt:18` |
| `PrimaryContainerLight` | `#B2F5EA` | `Color(0xFFB2F5EA)` | `:core` → `…/WahaBrandColors.kt:19` |
| `OnPrimaryContainerLight` | `#002019` | `Color(0xFF002019)` | `:core` → `…/WahaBrandColors.kt:20` |
| `SecondaryLight` | `#0E8539` | `Color(0xFF0E8539)` | `:core` → `…/WahaBrandColors.kt:22` |
| `OnSecondaryLight` | `#3E2723` | `Color(0xFF3E2723)` | `:core` → `…/WahaBrandColors.kt:23` |
| `SecondaryContainerLight` | `#FFECB3` | `Color(0xFFFFECB3)` | `:core` → `…/WahaBrandColors.kt:24` |
| `OnSecondaryContainerLight` | `#2B1B00` | `Color(0xFF2B1B00)` | `:core` → `…/WahaBrandColors.kt:25` |
| `TertiaryLight` | `#C8740C` | `Color(0xFFC8740C)` | `:core` → `…/WahaBrandColors.kt:27` |
| `OnTertiaryLight` | `#FFFFFF` | `Color(0xFFFFFFFF)` | `:core` → `…/WahaBrandColors.kt:28` |
| `TertiaryContainerLight` | `#FFCCBC` | `Color(0xFFFFCCBC)` | `:core` → `…/WahaBrandColors.kt:29` |
| `OnTertiaryContainerLight` | `#3E1300` | `Color(0xFF3E1300)` | `:core` → `…/WahaBrandColors.kt:30` |
| `ErrorLight` | `#BA1A1A` | `Color(0xFFBA1A1A)` | `:core` → `…/WahaBrandColors.kt:32` |
| `OnErrorLight` | `#FFFFFF` | `Color(0xFFFFFFFF)` | `:core` → `…/WahaBrandColors.kt:33` |
| `BackgroundLight` | `#FFFBF7` | `Color(0xFFFFFBF7)` | `:core` → `…/WahaBrandColors.kt:35` |
| `OnBackgroundLight` | `#191C1B` | `Color(0xFF191C1B)` | `:core` → `…/WahaBrandColors.kt:36` |
| `SurfaceLight` | `#FFFBF7` | `Color(0xFFFFFBF7)` | `:core` → `…/WahaBrandColors.kt:37` |
| `OnSurfaceLight` | `#191C1B` | `Color(0xFF191C1B)` | `:core` → `…/WahaBrandColors.kt:38` |
| `SurfaceVariantLight` | `#DDE5E2` | `Color(0xFFDDE5E2)` | `:core` → `…/WahaBrandColors.kt:39` |
| `OnSurfaceVariantLight` | `#414942` | `Color(0xFF414942)` | `:core` → `…/WahaBrandColors.kt:40` |
| `OutlineLight` | `#717975` | `Color(0xFF717975)` | `:core` → `…/WahaBrandColors.kt:41` |
| `PrimaryDark` | `#FF6600` | `Color(0xFFFF6600)` | `:core` → `…/WahaBrandColors.kt:44` |
| `OnPrimaryDark` | `#FFFFFF` | `Color(0xFFFFFFFF)` | `:core` → `…/WahaBrandColors.kt:45` |
| `PrimaryContainerDark` | `#00695C` | `Color(0xFF00695C)` | `:core` → `…/WahaBrandColors.kt:46` |
| `OnPrimaryContainerDark` | `#7FFFEB` | `Color(0xFF7FFFEB)` | `:core` → `…/WahaBrandColors.kt:47` |
| `SecondaryDark` | `#0E8539` | `Color(0xFF0E8539)` | `:core` → `…/WahaBrandColors.kt:49` |
| `OnSecondaryDark` | `#3E2723` | `Color(0xFF3E2723)` | `:core` → `…/WahaBrandColors.kt:50` |
| `SecondaryContainerDark` | `#7A5900` | `Color(0xFF7A5900)` | `:core` → `…/WahaBrandColors.kt:51` |
| `OnSecondaryContainerDark` | `#FFE08A` | `Color(0xFFFFE08A)` | `:core` → `…/WahaBrandColors.kt:52` |
| `TertiaryDark` | `#C8740C` | `Color(0xFFC8740C)` | `:core` → `…/WahaBrandColors.kt:54` |
| `OnTertiaryDark` | `#4A1300` | `Color(0xFF4A1300)` | `:core` → `…/WahaBrandColors.kt:55` |
| `TertiaryContainerDark` | `#8C3D00` | `Color(0xFF8C3D00)` | `:core` → `…/WahaBrandColors.kt:56` |
| `OnTertiaryContainerDark` | `#FFD9C8` | `Color(0xFFFFD9C8)` | `:core` → `…/WahaBrandColors.kt:57` |
| `ErrorDark` | `#FFB4AB` | `Color(0xFFFFB4AB)` | `:core` → `…/WahaBrandColors.kt:59` |
| `OnErrorDark` | `#690005` | `Color(0xFF690005)` | `:core` → `…/WahaBrandColors.kt:60` |
| `BackgroundDark` | `#000000` | `Color(0xFF000000)` | `:core` → `…/WahaBrandColors.kt:62` |
| `OnBackgroundDark` | `#E1E3DF` | `Color(0xFFE1E3DF)` | `:core` → `…/WahaBrandColors.kt:63` |
| `SurfaceDark` | `#18120E` | `Color(0xFF18120E)` | `:core` → `…/WahaBrandColors.kt:64` |
| `OnSurfaceDark` | `#E1E3DF` | `Color(0xFFE1E3DF)` | `:core` → `…/WahaBrandColors.kt:65` |
| `SurfaceVariantDark` | `#1B2820` | `Color(0xFF1B2820)` | `:core` → `…/WahaBrandColors.kt:66` |
| `OnSurfaceVariantDark` | `#A8B3AC` | `Color(0xFFA8B3AC)` | `:core` → `…/WahaBrandColors.kt:67` |
| `OutlineDark` | `#8A938C` | `Color(0xFF8A938C)` | `:core` → `…/WahaBrandColors.kt:68` |

### 1.2 `:mobile` — re-exports and semantic aliases (`Color.kt`)

`mobile/src/main/java/com/waha/ui/theme/Color.kt`. The 42 `val`s at lines 10–60 are one-to-one aliases of the `:core` tokens (same names, no literals). They are not repeated above to avoid duplicating hexes.

| Token / Variable Name | Hex Code (resolved) | Kotlin Definition | Defined In (Module & File) |
|---|---|---|---|
| `PrimaryLight` … `OutlineDark` (42 tokens) | *see §1.1* | `val X = WahaBrandColors.X` | `:mobile` → `…/ui/theme/Color.kt:10–60` |
| `WahaTeal` | `#FF6600` / `#FF6600` (resolved light/dark) | `MaterialTheme.colorScheme.primary` | `:mobile` → `…/Color.kt:62–63` |
| `WahaTealBright` | `#FF6600` (both) — **same as `WahaTeal`** | `MaterialTheme.colorScheme.primary` | `:mobile` → `…/Color.kt:65–66` |
| `WahaGold` | `#0E8539` (both) | `MaterialTheme.colorScheme.secondary` | `:mobile` → `…/Color.kt:68–69` |
| `WahaDarkBg` | `#FFFBF7` / `#000000` | `MaterialTheme.colorScheme.background` | `:mobile` → `…/Color.kt:71–72` |
| `WahaCardBg` | `#FFFBF7` / `#18120E` | `MaterialTheme.colorScheme.surface` | `:mobile` → `…/Color.kt:74–75` |
| `WahaCardBg2` | `#DDE5E2` / `#1B2820` | `MaterialTheme.colorScheme.surfaceVariant` | `:mobile` → `…/Color.kt:77–78` |
| `WahaTextWarm` | `#191C1B` / `#E1E3DF` | `MaterialTheme.colorScheme.onBackground` | `:mobile` → `…/Color.kt:80–81` |
| `WahaTextMuted` | `#414942` / `#A8B3AC` | `MaterialTheme.colorScheme.onSurfaceVariant` | `:mobile` → `…/Color.kt:83–84` |
| `WahaLine` | `#717975` @30% / `#8A938C` @30% | `MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)` | `:mobile` → `…/Color.kt:86–87` |

### 1.3 `:tv` — fixed and theme-aware tokens (`WahaTvColors`)

`tv/src/main/java/com/waha/tv/ui/theme/TvColors.kt`.

| Token / Variable Name | Hex Code | Kotlin Definition | Defined In (Module & File) |
|---|---|---|---|
| `Accent` | `#FF6600` | `MaterialTheme.colorScheme.primary` | `:tv` → `…/ui/theme/TvColors.kt:18–19` |
| `OnAccent` | `#FFFFFF` | `MaterialTheme.colorScheme.onPrimary` | `:tv` → `…/TvColors.kt:21–22` |
| `Background` | `#000000` | `MaterialTheme.colorScheme.background` | `:tv` → `…/TvColors.kt:24–25` |
| `Surface` | `#18120E` | `MaterialTheme.colorScheme.surface` | `:tv` → `…/TvColors.kt:27–28` |
| `SurfaceVariant` | `#1B2820` | `MaterialTheme.colorScheme.surfaceVariant` | `:tv` → `…/TvColors.kt:30–31` |
| `TextWarm` | `#E1E3DF` | `MaterialTheme.colorScheme.onBackground` | `:tv` → `…/TvColors.kt:33–34` |
| `TextMuted` | `#A8B3AC` | `MaterialTheme.colorScheme.onSurfaceVariant` | `:tv` → `…/TvColors.kt:36–37` |
| `Line` | `#8A938C` @30% | `MaterialTheme.colorScheme.border.copy(alpha = 0.3f)` | `:tv` → `…/TvColors.kt:40–41` |
| `OnMedia` | `#FFFFFF` | `Color.White` | `:tv` → `…/TvColors.kt:44` |
| `SavedBadge` | `#0E8539` | `MaterialTheme.colorScheme.secondary` | `:tv` → `…/TvColors.kt:47–48` |
| `PanelBackground` | `#131313` @95% (`0xF2`) | `Color(0xF2131313)` | `:tv` → `…/TvColors.kt:56` |
| `MediaScrim` | `#000000` @80% (`0xCC`) | `Color(0xCC000000)` | `:tv` → `…/TvColors.kt:62` |
| `MediaScrimSoft` | `#000000` @60% (`0x99`) | `Color(0x99000000)` | `:tv` → `…/TvColors.kt:63` |
| `ProgressTrack` | `#FFFFFF` @30% (`0x4D`) | `Color(0x4DFFFFFF)` | `:tv` → `…/TvColors.kt:64` |
| `CardPlaceholder` | `#1B2820` | `WahaBrandColors.SurfaceVariantDark` | `:tv` → `…/TvColors.kt:65` |
| `PanelInk` *(private)* | `#121212` | `Color(0xFF121212)` | `:tv` → `…/ui/screens/TvPlayerScreen.kt:923` |

### 1.4 XML resources

| Token / Variable Name | Hex Code | Kotlin/XML Definition | Defined In (Module & File) |
|---|---|---|---|
| `ic_launcher_background` | `#FF6D00` | `<color name="ic_launcher_background">#FF6D00</color>` | `:mobile` → `mobile/src/main/res/values/colors.xml:3` |
| `ic_launcher_background` | `#FF6D00` | `<color name="ic_launcher_background">#FF6D00</color>` | `:tv` → `tv/src/main/res/values/colors.xml:4` |
| `Theme.Waha` window background | `#000000` | `<item name="android:windowBackground">@android:color/black</item>` | `:mobile` → `mobile/src/main/res/values/themes.xml:7` |
| `Theme.Waha.Tv` window background | `#000000` | `<item name="android:windowBackground">@android:color/black</item>` | `:tv` → `tv/src/main/res/values/themes.xml:7` |
| `tv_banner` night sky / play glyph | `#0B0F0D` | `android:fillColor="#0B0F0D"` | `:tv` → `tv/src/main/res/drawable/tv_banner.xml:15` and `:28` |
| `tv_banner` dune (brand green) | `#0E8539` | `android:fillColor="#0E8539"` | `:tv` → `…/tv_banner.xml:20` |
| `tv_banner` sun (brand orange) | `#FF6600` | `android:fillColor="#FF6600"` | `:tv` → `…/tv_banner.xml:25` |
| `tv_banner` foreground dune | `#0A5C29` | `android:fillColor="#0A5C29"` | `:tv` → `…/tv_banner.xml:33` |

---

## 2. Theme & Semantic Mapping (`:mobile` vs `:tv`)

### 2.1 `:mobile` — `WahaTheme` (`mobile/src/main/java/com/waha/ui/theme/Theme.kt`)

Selects `WahaDarkColorScheme` when `ThemePreferenceStore.isDarkMode` is true (default `true`), else `WahaLightColorScheme`; also forces `LayoutDirection.Rtl`.

**Dark scheme** (`darkColorScheme`, lines 12–34):

| M3 role | Token | Hex |
|---|---|---|
| `primary` (L13) | `PrimaryDark` | `#FF6600` |
| `onPrimary` (L14) | `OnPrimaryDark` | `#FFFFFF` |
| `primaryContainer` (L15) | `PrimaryContainerDark` | `#00695C` |
| `onPrimaryContainer` (L16) | `OnPrimaryContainerDark` | `#7FFFEB` |
| `secondary` (L17) | `SecondaryDark` | `#0E8539` |
| `onSecondary` (L18) | `OnSecondaryDark` | `#3E2723` |
| `secondaryContainer` (L19) | `SecondaryContainerDark` | `#7A5900` |
| `onSecondaryContainer` (L20) | `OnSecondaryContainerDark` | `#FFE08A` |
| `tertiary` (L21) | `TertiaryDark` | `#C8740C` |
| `onTertiary` (L22) | `OnTertiaryDark` | `#4A1300` |
| `tertiaryContainer` (L23) | `TertiaryContainerDark` | `#8C3D00` |
| `onTertiaryContainer` (L24) | `OnTertiaryContainerDark` | `#FFD9C8` |
| `error` (L25) | `ErrorDark` | `#FFB4AB` |
| `onError` (L26) | `OnErrorDark` | `#690005` |
| `background` (L27) | `BackgroundDark` | `#000000` |
| `onBackground` (L28) | `OnBackgroundDark` | `#E1E3DF` |
| `surface` (L29) | `SurfaceDark` | `#18120E` |
| `onSurface` (L30) | `OnSurfaceDark` | `#E1E3DF` |
| `surfaceVariant` (L31) | `SurfaceVariantDark` | `#1B2820` |
| `onSurfaceVariant` (L32) | `OnSurfaceVariantDark` | `#A8B3AC` |
| `outline` (L33) | `OutlineDark` | `#8A938C` |

**Light scheme** (`lightColorScheme`, lines 36–58):

| M3 role | Token | Hex |
|---|---|---|
| `primary` (L37) | `PrimaryLight` | `#FF6600` |
| `onPrimary` (L38) | `OnPrimaryLight` | `#FFFFFF` |
| `primaryContainer` (L39) | `PrimaryContainerLight` | `#B2F5EA` |
| `onPrimaryContainer` (L40) | `OnPrimaryContainerLight` | `#002019` |
| `secondary` (L41) | `SecondaryLight` | `#0E8539` |
| `onSecondary` (L42) | `OnSecondaryLight` | `#3E2723` |
| `secondaryContainer` (L43) | `SecondaryContainerLight` | `#FFECB3` |
| `onSecondaryContainer` (L44) | `OnSecondaryContainerLight` | `#2B1B00` |
| `tertiary` (L45) | `TertiaryLight` | `#C8740C` |
| `onTertiary` (L46) | `OnTertiaryLight` | `#FFFFFF` |
| `tertiaryContainer` (L47) | `TertiaryContainerLight` | `#FFCCBC` |
| `onTertiaryContainer` (L48) | `OnTertiaryContainerLight` | `#3E1300` |
| `error` (L49) | `ErrorLight` | `#BA1A1A` |
| `onError` (L50) | `OnErrorLight` | `#FFFFFF` |
| `background` (L51) | `BackgroundLight` | `#FFFBF7` |
| `onBackground` (L52) | `OnBackgroundLight` | `#191C1B` |
| `surface` (L53) | `SurfaceLight` | `#FFFBF7` |
| `onSurface` (L54) | `OnSurfaceLight` | `#191C1B` |
| `surfaceVariant` (L55) | `SurfaceVariantLight` | `#DDE5E2` |
| `onSurfaceVariant` (L56) | `OnSurfaceVariantLight` | `#414942` |
| `outline` (L57) | `OutlineLight` | `#717975` |

**Roles never supplied (mobile):** `surfaceTint`, `outlineVariant`, `inverseSurface`, `inverseOnSurface`, `inversePrimary`, `scrim`, `surfaceBright`, `surfaceDim`, `surfaceContainer{Lowest,Low,,High,Highest}`, `errorContainer`, `onErrorContainer`, `surfaceContainer*` neutrals. These resolve to `lightColorScheme()`/`darkColorScheme()` **baseline Material values** (neutral/purple-tinted), which is why components such as `ModalBottomSheet`'s scrim and `AlertDialog`'s tonal surface can drift off-brand.

### 2.2 `:tv` — `WahaTvTheme` (`tv/src/main/java/com/waha/tv/ui/theme/TvTheme.kt`)

A single `androidx.tv.material3.darkColorScheme` (lines 15–37); no light scheme, no preference read, `LayoutDirection.Rtl` forced.

| TV M3 role | Token | Hex | Note |
|---|---|---|---|
| `primary` (L16) | `WahaBrandColors.PrimaryDark` | `#FF6600` | brand accent / focus |
| `onPrimary` (L17) | `OnPrimaryDark` | `#FFFFFF` | ink on accent |
| `primaryContainer` (L18) | `PrimaryContainerDark` | `#00695C` | unused by TV UI |
| `onPrimaryContainer` (L19) | `OnPrimaryContainerDark` | `#7FFFEB` | unused by TV UI |
| `secondary` (L20) | `SecondaryDark` | `#0E8539` | saved badge |
| `onSecondary` (L21) | `OnSecondaryDark` | `#3E2723` | unused |
| `secondaryContainer` (L22) | `SecondaryContainerDark` | `#7A5900` | unused |
| `onSecondaryContainer` (L23) | `OnSecondaryContainerDark` | `#FFE08A` | unused |
| `tertiary` (L24) | `TertiaryDark` | `#C8740C` | unused |
| `onTertiary` (L25) | `OnTertiaryDark` | `#4A1300` | unused |
| `tertiaryContainer` (L26) | `TertiaryContainerDark` | `#8C3D00` | unused |
| `onTertiaryContainer` (L27) | `OnTertiaryContainerDark` | `#FFD9C8` | unused |
| `error` (L28) | `ErrorDark` | `#FFB4AB` | unused |
| `onError` (L29) | `OnErrorDark` | `#690005` | unused |
| `background` (L30) | `BackgroundDark` | `#000000` | app shell |
| `onBackground` (L31) | `OnBackgroundDark` | `#E1E3DF` | primary text |
| `surface` (L32) | `SurfaceDark` | `#18120E` | rail, search field, keyboard |
| `onSurface` (L33) | `OnSurfaceDark` | `#E1E3DF` | unused directly |
| `surfaceVariant` (L34) | `SurfaceVariantDark` | `#1B2820` | unused directly (only via `CardPlaceholder`) |
| `onSurfaceVariant` (L35) | `OnSurfaceVariantDark` | `#A8B3AC` | secondary text |
| `border` (L36) | `OutlineDark` | `#8A938C` | **TV calls the outline role `border`** |

**Mobile vs TV divergence:**

| Concern | `:mobile` | `:tv` |
|---|---|---|
| Light mode | Yes (`WahaLightColorScheme`) | None — dark only |
| Theme source | `ThemePreferenceStore.isDarkMode` | Hardcoded |
| Outline role name | `outline` | `border` (`androidx.tv.material3`) |
| Accent aliases | `WahaTeal`, `WahaTealBright` (both `primary`) | `Accent` |
| Green/emphasis | `WahaGold` (`secondary`, unused) | `SavedBadge` (`secondary`) |
| Media/text-on-video | None — uses raw `Color.White`/`Color.Black` | `OnMedia`, `MediaScrim`, `MediaScrimSoft` |
| Divider token | `WahaLine` = `outline` @30% | `Line` = `border` @30% |
| Fixed panel color | None | `PanelBackground` + private `PanelInk` |
| Surface variants used | `surface`, `surfaceVariant`, `background` | `surface`, `background` (surfaceVariant dead) |

---

## 3. Exact Usage Map (By Color / Token)

Module/screen labels use the composable that owns the line. Paths are relative to the repository root.

### 3.1 `#FF6600` — brand orange (`primary` / `WahaTeal` / `WahaTealBright` / `WahaTvColors.Accent` / `PrimaryLight` / `PrimaryDark`)

**Theme definitions:** `mobile/…/ui/theme/Theme.kt:13`, `:37`; `tv/…/ui/theme/TvTheme.kt:16`; `core/…/brand/WahaBrandColors.kt:17`, `:44`.

| File & Line | Module & Screen/Component | UI Element & Role |
|---|---|---|
| `mobile/src/main/java/com/waha/ui/screens/WahaHomeScreen.kt:180` | `:mobile` → `WahaHomeScreen` | Loading spinner (`CircularProgressIndicator`) |
| `mobile/…/WahaHomeScreen.kt:307` | `:mobile` → `WahaTopBar` | Search icon tint when search is active |
| `mobile/…/WahaHomeScreen.kt:314` | `:mobile` → `WahaTopBar` | Settings icon tint when settings is active |
| `mobile/…/WahaHomeScreen.kt:337` | `:mobile` → `CategoryFilterBar` | Selected category chip container (`colorScheme.primary`) |
| `mobile/…/WahaHomeScreen.kt:347` | `:mobile` → `CategoryFilterBar` | Selected chip border |
| `mobile/…/WahaHomeScreen.kt:451` | `:mobile` → `ContinueWatchingCard` | Progress-bar filled portion |
| `mobile/…/VideoPlayerModal.kt:443` | `:mobile` → `VideoPlayerModal` | Loading spinner over the player |
| `mobile/…/VideoPlayerModal.kt:514` | `:mobile` → `VideoPlayerModal` | Bookmark icon tint when video is saved |
| `mobile/…/VideoPlayerModal.kt:596` | `:mobile` → `PlaybackSettingsSheet` | Selected speed chip container |
| `mobile/…/VideoPlayerModal.kt:615` | `:mobile` → `PlaybackSettingsSheet` | Selected quality chip container |
| `mobile/…/Settingsscreen.kt:90` | `:mobile` → `SettingsScreen` | Dark-mode row icon tint |
| `mobile/…/Settingsscreen.kt:101` | `:mobile` → `SettingsScreen` | Switch checked track colour |
| `mobile/…/Settingsscreen.kt:171` | `:mobile` → `SettingsSectionTitle` | Section heading text |
| `mobile/…/Settingsscreen.kt:200` | `:mobile` → `SettingsRow` | Row leading-icon tint |
| `mobile/…/Settingsscreen.kt:222` | `:mobile` → `ContentPolicyDialog` | Dialog icon tint |
| `mobile/…/Settingsscreen.kt:252` | `:mobile` → `ContentPolicyDialog` | "حسناً" confirm button text |
| `mobile/…/Searchoverlay.kt:102` | `:mobile` → `SearchOverlay` | Focused text-field border |
| `mobile/…/Searchoverlay.kt:191` | `:mobile` → `RecentSearchesSection` | "مسح الكل" action text |
| `mobile/…/Watchhistoryscreen.kt:99` | `:mobile` → `WatchHistoryScreen` | "مسح الكل" action text |
| `mobile/…/WahaHomeScreen.kt:189` | `:mobile` → `WahaHomeScreen` (Error) | "إعادة المحاولة" retry text |
| `mobile/…/WahaHomeScreen.kt:688` | `:mobile` → `BottomNavItem` | Selected nav icon tint |
| `mobile/…/WahaHomeScreen.kt:698` | `:mobile` → `BottomNavItem` | Selected nav label text |
| `mobile/…/VideoPlayerModal.kt:365` | `:mobile` → `VideoPlayerModal` | Buffering spinner over video |
| `mobile/…/VideoPlayerModal.kt:454` | `:mobile` → `VideoPlayerModal` (Error) | "إعادة المحاولة" retry text |
| `mobile/…/VideoPlayerModal.kt:690` | `:mobile` → `PlayerControls` | Seekbar thumb |
| `mobile/…/VideoPlayerModal.kt:691` | `:mobile` → `PlayerControls` | Seekbar active track |
| `tv/src/main/java/com/waha/tv/ui/screens/TvApp.kt:—` | — | *(no accent use)* |
| `tv/…/TvComponents.kt:103` | `:tv` → `tvFocusBorder` | Focus border colour for cards/keys/chips |
| `tv/…/TvComponents.kt:139` | `:tv` → `tvItemColors` | Focused container fill + selected tint base |
| `tv/…/TvComponents.kt:144` | `:tv` → `tvItemColors` | Selected (unfocused) container @22% |
| `tv/…/TvComponents.kt:261` | `:tv` → `TvVideoCard` | Play-triangle glyph painted over focused artwork |
| `tv/…/TvComponents.kt:336` | `:tv` → `TvVideoCard` | Continue-watching progress fill |
| `tv/…/TvComponents.kt:179`/`180` (via helpers) | `:tv` → `tvItemContentColor` | Focused/selected label + icon ink |
| `tv/…/TvHomeScreen.kt:101` | `:tv` → `TvHomeScreen` | Loading spinner |
| `tv/…/TvMainScreen.kt:381` | `:tv` → `TvRailItem` | Focused rail-item fill |
| `tv/…/TvMainScreen.kt:384` | `:tv` → `TvRailItem` | Focused rail-item border |
| `tv/…/TvSettingsScreen.kt:71` | `:tv` → `TvSettingsSectionTitle` | Section heading text |
| `tv/…/TvSettingsScreen.kt:128` | `:tv` → `TvSettingsRow` | Row value text when unfocused |
| `tv/…/TvSearchScreen.kt:213` | `:tv` → `TvQueryField` | Search glyph tint |
| `tv/…/TvPlayerScreen.kt:436` | `:tv` → `TvPlayerScreen` | Loading spinner |
| `tv/…/TvPlayerScreen.kt:468` | `:tv` → `TvPlayerScreen` | Buffering spinner |
| `tv/…/TvPlayerScreen.kt:671` | `:tv` → `PlaybackProgressBar` | Played portion + thumb |
| `tv/…/TvPlayerScreen.kt:938`/`940`/`976` | `:tv` → `panelControlColors` / `PanelActionButton` | Focus ring on panel controls |

### 3.2 `#FFFFFF` — white (`onPrimary`, `OnMedia`, `Color.White`, chip labels)

| File & Line | Module & Screen/Component | UI Element & Role |
|---|---|---|
| `mobile/…/WahaHomeScreen.kt:80` | `:mobile` → `DurationBadge` | Duration badge text |
| `mobile/…/WahaHomeScreen.kt:352` | `:mobile` → `CategoryFilterBar` | Selected chip label (`colorScheme.onPrimary`) |
| `mobile/…/WahaHomeScreen.kt:434` | `:mobile` → `ContinueWatchingCard` | Play glyph over artwork |
| `mobile/…/WahaHomeScreen.kt:530` | `:mobile` → `VideoCard` | Play glyph over artwork |
| `mobile/…/VideoPlayerModal.kt:340` | `:mobile` → `VideoPlayerModal` | Double-tap seek glyph |
| `mobile/…/VideoPlayerModal.kt:397` | `:mobile` → `VideoPlayerModal` | Centre play/pause glyph |
| `mobile/…/VideoPlayerModal.kt:425` | `:mobile` → `VideoPlayerModal` | Fullscreen back arrow |
| `mobile/…/VideoPlayerModal.kt:430` | `:mobile` → `VideoPlayerModal` | Fullscreen title text |
| `mobile/…/VideoPlayerModal.kt:597` | `:mobile` → `PlaybackSettingsSheet` | Selected speed chip label |
| `mobile/…/VideoPlayerModal.kt:616` | `:mobile` → `PlaybackSettingsSheet` | Selected quality chip label |
| `mobile/…/VideoPlayerModal.kt:674` | `:mobile` → `PlayerControls` | Elapsed-time label |
| `mobile/…/VideoPlayerModal.kt:699` | `:mobile` → `PlayerControls` | Duration label |
| `mobile/…/VideoPlayerModal.kt:705` | `:mobile` → `PlayerControls` | Player settings icon |
| `mobile/…/VideoPlayerModal.kt:714` | `:mobile` → `PlayerControls` | Fullscreen toggle icon |
| `mobile/…/VideoPlayerModal.kt:692` (`Color.White.copy(0.30f)`) | `:mobile` → `PlayerControls` | Inactive seekbar track |
| `tv/…/TvComponents.kt:149` | `:tv` → `tvItemColors` | Focused content colour (`onPrimary`) |
| `tv/…/TvComponents.kt:288` | `:tv` → `TvVideoCard` | Duration badge text (`OnMedia`) |
| `tv/…/TvComponents.kt:317` | `:tv` → `TvVideoCard` | Saved-badge bookmark icon (`OnMedia`) |
| `tv/…/TvComponents.kt:179` | `:tv` → `tvItemContentColor` | Focused item label/icon ink |
| `tv/…/TvHomeScreen.kt:231` | `:tv` → `TvReloadButton` | Spinner inside reload button (`onPrimary`) |
| `tv/…/TvPlayerScreen.kt:553` | `:tv` → `PlayerTopOverlay` | Video title text (`OnMedia`) |
| `tv/…/TvPlayerScreen.kt:561` | `:tv` → `PlayerTopOverlay` | Category text @78% (`OnMedia.copy`) |
| `tv/…/TvPlayerScreen.kt:593` | `:tv` → `PlayerCenterOverlay` | Play/pause glyph (`OnMedia`) |
| `tv/…/TvPlayerScreen.kt:638` | `:tv` → `PlayerSeekOverlay` | Seek glyph (`OnMedia`) |
| `tv/…/TvPlayerScreen.kt:746` | `:tv` → `PlayerBottomOverlay` | Position label (`OnMedia`) |
| `tv/…/TvPlayerScreen.kt:760` | `:tv` → `PlayerBottomOverlay` | Duration label (`OnMedia`) |
| `tv/…/TvPlayerScreen.kt:938` | `:tv` → `panelControlColors` | Selected+focused pill container |
| `tv/…/TvPlayerScreen.kt:939` | `:tv` → `panelControlColors` | Selected pill container + @35% border |
| `tv/…/TvPlayerScreen.kt:940` | `:tv` → `panelControlColors` | Focused fill @16% + content |
| `tv/…/TvPlayerScreen.kt:942` | `:tv` → `panelControlColors` | Resting fill @6% |
| `tv/…/TvPlayerScreen.kt:944` | `:tv` → `panelControlColors` | Resting border @16% |
| `tv/…/TvPlayerScreen.kt:841` | `:tv` → `PlayerPanel` | Panel hairline @10% |
| `tv/…/ui/theme/TvColors.kt:44` | `:tv` → token | `OnMedia` declaration |

### 3.3 `#000000` — black (`background`, scrims, player surface)

| File & Line | Module & Screen/Component | UI Element & Role |
|---|---|---|
| `mobile/…/WahaHomeScreen.kt:85` | `:mobile` → `DurationBadge` | Badge container @70% |
| `mobile/…/WahaHomeScreen.kt:428` | `:mobile` → `ContinueWatchingCard` | Play-button circle @35% |
| `mobile/…/WahaHomeScreen.kt:445` | `:mobile` → `ContinueWatchingCard` | Progress-bar track @45% |
| `mobile/…/WahaHomeScreen.kt:524` | `:mobile` → `VideoCard` | Play-button circle @30% |
| `mobile/…/VideoPlayerModal.kt:343` | `:mobile` → `VideoPlayerModal` | Seek glyph circle @40% |
| `mobile/…/VideoPlayerModal.kt:388` | `:mobile` → `VideoPlayerModal` | Centre button circle @45% |
| `mobile/…/VideoPlayerModal.kt:415` | `:mobile` → `VideoPlayerModal` | Fullscreen top scrim gradient (@70% → transparent) |
| `mobile/…/VideoPlayerModal.kt:667` | `:mobile` → `PlayerControls` | Bottom control scrim (transparent → @78%) |
| `mobile/…/VideoPlayerModal.kt:731` | `:mobile` → `PlayerSurface` | ExoPlayer view background (`android.graphics.Color.BLACK`) |
| `mobile/…/VideoPlayerModal.kt:732` | `:mobile` → `PlayerSurface` | ExoPlayer shutter background |
| `mobile/…/VideoPlayerModal.kt:739` | `:mobile` → `PlayerSurface` | Composable surface background |
| `mobile/…/ui/theme/Theme.kt:27` | `:mobile` → theme | `background` role = `BackgroundDark` `#000000` |
| `mobile/src/main/res/values/themes.xml:7` | `:mobile` → `Theme.Waha` | Window background before Compose draws |
| `tv/…/ui/theme/TvTheme.kt:30` | `:tv` → theme | `background` role = `BackgroundDark` `#000000` |
| `tv/…/TvPlayerScreen.kt:346` | `:tv` → `TvPlayerScreen` | Player root background |
| `tv/…/TvPlayerScreen.kt:420` | `:tv` → `TvPlayerScreen` | ExoPlayer shutter background (`android.graphics.Color.BLACK`) |
| `tv/…/TvPlayerScreen.kt:429` | `:tv` → `TvPlayerScreen` | Video surface background |
| `tv/…/TvPlayerScreen.kt:737` | `:tv` → `PlayerBottomOverlay` | Bottom scrim (transparent → @78%) |
| `tv/…/ui/theme/TvColors.kt:62` | `:tv` → token | `MediaScrim` `0xCC000000` |
| `tv/…/ui/theme/TvColors.kt:63` | `:tv` → token | `MediaScrimSoft` `0x99000000` |
| `tv/src/main/res/values/themes.xml:7` | `:tv` → `Theme.Waha.Tv` | Window background before Compose draws |

### 3.4 `#E1E3DF` — warm off-white (`OnBackgroundDark`, `TextWarm`, `WahaTextWarm`)

Declaration: `core/…/WahaBrandColors.kt:63`, `:65`; `mobile/…/Color.kt:80`; `tv/…/TvColors.kt:33`.

| File & Line | Module & Screen/Component | UI Element & Role |
|---|---|---|
| `mobile/…/WahaHomeScreen.kt:186` | `:mobile` → `WahaHomeScreen` (Error) | Error message text |
| `mobile/…/WahaHomeScreen.kt:297` | `:mobile` → `WahaTopBar` | "واحة" wordmark |
| `mobile/…/WahaHomeScreen.kt:307`/`314` | `:mobile` → `WahaTopBar` | Inactive search/settings icon tint |
| `mobile/…/WahaHomeScreen.kt:379` | `:mobile` → `ContinueWatchingRow` | Row heading |
| `mobile/…/WahaHomeScreen.kt:460` | `:mobile` → `ContinueWatchingCard` | Card title |
| `mobile/…/WahaHomeScreen.kt:546` | `:mobile` → `VideoCard` | Video title |
| `mobile/…/VideoPlayerModal.kt:451` | `:mobile` → `VideoPlayerModal` (Error) | Error message |
| `mobile/…/VideoPlayerModal.kt:483` | `:mobile` → `VideoPlayerModal` | Back arrow tint |
| `mobile/…/VideoPlayerModal.kt:492` | `:mobile` → `VideoPlayerModal` | Video title |
| `mobile/…/VideoPlayerModal.kt:525` | `:mobile` → `VideoPlayerModal` | "شاهد المزيد" heading |
| `mobile/…/VideoPlayerModal.kt:568` | `:mobile` → `PlaybackSettingsSheet` | Sheet content colour |
| `mobile/…/VideoPlayerModal.kt:579` | `:mobile` → `PlaybackSettingsSheet` | Sheet title |
| `mobile/…/VideoPlayerModal.kt:595` | `:mobile` → `PlaybackSettingsSheet` | Speed chip label (unselected) |
| `mobile/…/VideoPlayerModal.kt:614` | `:mobile` → `PlaybackSettingsSheet` | Quality chip label (unselected) |
| `mobile/…/Searchoverlay.kt:77` | `:mobile` → `SearchOverlay` | Back arrow tint |
| `mobile/…/Searchoverlay.kt:100`/`101` | `:mobile` → `SearchOverlay` | Text-field focused/unfocused text |
| `mobile/…/Searchoverlay.kt:185` | `:mobile` → `RecentSearchesSection` | "البحث الأخير" heading |
| `mobile/…/Searchoverlay.kt:229` | `:mobile` → `RecentSearchRow` | Recent query text |
| `mobile/…/Watchhistoryscreen.kt:91` | `:mobile` → `WatchHistoryScreen` | Header count text |
| `mobile/…/Settingsscreen.kt:70` | `:mobile` → `SettingsScreen` | Page title |
| `mobile/…/Settingsscreen.kt:95` | `:mobile` → `SettingsScreen` | Dark-mode row title |
| `mobile/…/Settingsscreen.kt:205` | `:mobile` → `SettingsRow` | Row title |
| `mobile/…/Settingsscreen.kt:229` | `:mobile` → `ContentPolicyDialog` | Dialog title |
| `mobile/…/Settingsscreen.kt:265` | `:mobile` → `PolicyPoint` | Policy point heading |
| `tv/…/TvComponents.kt:160`/`161` | `:tv` → `tvCardColors` | Card content + focused content |
| `tv/…/TvComponents.kt:358` | `:tv` → `TvVideoCard` | Card title |
| `tv/…/TvComponents.kt:411` | `:tv` → `TvSectionHeader` | Rail heading |
| `tv/…/TvMainScreen.kt:330` | `:tv` → `TvRailBrand` | "واحة" wordmark |
| `tv/…/TvHomeScreen.kt:284` | `:tv` → `TvErrorState` | Error heading |
| `tv/…/TvSearchScreen.kt:218` | `:tv` → `TvQueryField` | Typed query text |
| `tv/…/TvSearchScreen.kt:269` | `:tv` → `TvRecentSearches` | "البحث الأخير" heading |
| `tv/…/TvPlayerScreen.kt:448` | `:tv` → `TvPlayerScreen` (Error) | Error heading |
| `tv/…/TvPlayerScreen.kt:893` | `:tv` → `PlayerPanel` | "شاهد المزيد" heading |

### 3.5 `#A8B3AC` — muted grey-green (`OnSurfaceVariantDark`, `TextMuted`, `WahaTextMuted`)

| File & Line | Module & Screen/Component | UI Element & Role |
|---|---|---|
| `mobile/…/WahaHomeScreen.kt:224` | `:mobile` → `WahaHomeScreen` | Empty-category message |
| `mobile/…/WahaHomeScreen.kt:352` | `:mobile` → `CategoryFilterBar` | Unselected chip label |
| `mobile/…/WahaHomeScreen.kt:557` | `:mobile` → `VideoCard` | Card meta line |
| `mobile/…/WahaHomeScreen.kt:688`/`698` | `:mobile` → `BottomNavItem` | Unselected icon tint / label |
| `mobile/…/VideoPlayerModal.kt:501` | `:mobile` → `VideoPlayerModal` | Video meta line |
| `mobile/…/VideoPlayerModal.kt:514` | `:mobile` → `VideoPlayerModal` | Bookmark icon when unsaved |
| `mobile/…/VideoPlayerModal.kt:583` | `:mobile` → `PlaybackSettingsSheet` | "سرعة التشغيل" label |
| `mobile/…/VideoPlayerModal.kt:602` | `:mobile` → `PlaybackSettingsSheet` | "الجودة" label |
| `mobile/…/Searchoverlay.kt:87` | `:mobile` → `SearchOverlay` | Placeholder text |
| `mobile/…/Searchoverlay.kt:88` | `:mobile` → `SearchOverlay` | Leading search icon |
| `mobile/…/Searchoverlay.kt:92` | `:mobile` → `SearchOverlay` | Clear-query icon |
| `mobile/…/Searchoverlay.kt:115` | `:mobile` → `SearchOverlay` | Empty-state prompt |
| `mobile/…/Searchoverlay.kt:128` | `:mobile` → `SearchOverlay` | No-results message |
| `mobile/…/Searchoverlay.kt:180` | `:mobile` → `RecentSearchesSection` | History icon |
| `mobile/…/Searchoverlay.kt:224` | `:mobile` → `RecentSearchRow` | History icon |
| `mobile/…/Searchoverlay.kt:238` | `:mobile` → `RecentSearchRow` | Delete icon |
| `mobile/…/Watchhistoryscreen.kt:57` | `:mobile` → `WatchHistoryScreen` | Empty-state message |
| `mobile/…/Watchhistoryscreen.kt:86` | `:mobile` → `WatchHistoryScreen` | History header icon |
| `mobile/…/Watchhistoryscreen.kt:112` | `:mobile` → `WatchHistoryScreen` | "watched x ago" footnote |
| `mobile/…/Savedvideosscreen.kt:38` | `:mobile` → `SavedVideosScreen` | Empty-state message |
| `mobile/…/Settingsscreen.kt:96` | `:mobile` → `SettingsScreen` | Row subtitle |
| `mobile/…/Settingsscreen.kt:151` | `:mobile` → `SettingsScreen` | Version/about footer |
| `mobile/…/Settingsscreen.kt:206` | `:mobile` → `SettingsRow` | Row subtitle |
| `mobile/…/Settingsscreen.kt:270` | `:mobile` → `PolicyPoint` | Policy description |
| `tv/…/TvComponents.kt:181` | `:tv` → `tvItemContentColor` | Resting label/icon ink |
| `tv/…/TvComponents.kt:367` | `:tv` → `TvVideoCard` | Category line |
| `tv/…/TvComponents.kt:377` | `:tv` → `TvVideoCard` | Footnote ("watched x ago") |
| `tv/…/TvComponents.kt:464` | `:tv` → `TvVideoRail` | Empty-rail message |
| `tv/…/TvComponents.kt:554` | `:tv` → `TvMessage` | Default message colour |
| `tv/…/TvHomeScreen.kt:265` | `:tv` → `TvEmptyLibrary` | Empty-library message |
| `tv/…/TvSearchScreen.kt:202` | `:tv` → `TvQueryField` | Placeholder prompt |
| `tv/…/TvSearchScreen.kt:264` | `:tv` → `TvRecentSearches` | History icon |
| `tv/…/TvPlayerScreen.kt:453` | `:tv` → `TvPlayerScreen` (Error) | Error subtitle |
| `tv/…/TvPlayerScreen.kt:943` | `:tv` → `panelControlColors` | Resting control content |

### 3.6 `#18120E` — card/surface brown-black (`SurfaceDark`, `WahaCardBg`, `WahaTvColors.Surface`)

| File & Line | Module & Screen/Component | UI Element & Role |
|---|---|---|
| `mobile/…/WahaHomeScreen.kt:279` | `:mobile` → `WahaTopBar` | Floating pill top bar container |
| `mobile/…/WahaHomeScreen.kt:337` | `:mobile` → `CategoryFilterBar` | Unselected chip container |
| `mobile/…/WahaHomeScreen.kt:502` | `:mobile` → `VideoCard` | Artwork gradient end stop |
| `mobile/…/WahaHomeScreen.kt:579` | `:mobile` → `WahaBottomNavigation` | Bottom bar surface (compact) |
| `mobile/…/WahaHomeScreen.kt:625` | `:mobile` → `WahaBottomNavigation` | Side rail surface (tablet) |
| `mobile/…/VideoPlayerModal.kt:567` | `:mobile` → `PlaybackSettingsSheet` | Bottom-sheet container |
| `mobile/…/Searchoverlay.kt:104`/`105` | `:mobile` → `SearchOverlay` | Text-field focused/unfocused container |
| `tv/…/TvComponents.kt:145` | `:tv` → `tvItemColors` | Resting key/row container |
| `tv/…/TvMainScreen.kt:266` | `:tv` → `TvSideRail` | Navigation rail opaque fill |
| `tv/…/TvSearchScreen.kt:195` | `:tv` → `TvQueryField` | Search pill background |
| `tv/…/TvSearchScreen.kt:309` | `:tv` → `TvOnScreenKeyboard` | Keyboard band background |

### 3.7 `#1B2820` — green-black raised surface (`SurfaceVariantDark`, `WahaCardBg2`, `CardPlaceholder`)

| File & Line | Module & Screen/Component | UI Element & Role |
|---|---|---|
| `mobile/…/WahaHomeScreen.kt:415` | `:mobile` → `ContinueWatchingCard` | Artwork container / image placeholder |
| `mobile/…/WahaHomeScreen.kt:502` | `:mobile` → `VideoCard` | Artwork gradient start stop |
| `mobile/…/VideoPlayerModal.kt:594` | `:mobile` → `PlaybackSettingsSheet` | Speed chip container |
| `mobile/…/VideoPlayerModal.kt:613` | `:mobile` → `PlaybackSettingsSheet` | Quality chip container |
| `tv/…/TvComponents.kt:252` | `:tv` → `TvVideoCard` | Thumbnail placeholder under `AsyncImage` |
| `tv/…/ui/theme/TvColors.kt:30` | `:tv` → `WahaTvColors.SurfaceVariant` | **Declared, never consumed** (see §4.2) |

### 3.8 `#000000` background role — app shells (`WahaDarkBg`, `WahaTvColors.Background`)

| File & Line | Module & Screen/Component | UI Element & Role |
|---|---|---|
| `mobile/…/Wahaapp.kt:92` | `:mobile` → `WahaApp` | Root screen background behind all destinations |
| `mobile/…/VideoPlayerModal.kt:292` | `:mobile` → `VideoPlayerModal` | Full-screen player background |
| `mobile/…/Searchoverlay.kt:59` | `:mobile` → `SearchOverlay` | Search page background |
| `mobile/…/Settingsscreen.kt:87` | `:mobile` → `SettingsScreen` | Dark-mode icon tile background |
| `mobile/…/Settingsscreen.kt:197` | `:mobile` → `SettingsRow` | Setting icon tile background |
| `mobile/…/Settingsscreen.kt:217` | `:mobile` → `ContentPolicyDialog` | Dialog container |
| `tv/…/TvApp.kt:92` | `:tv` → `TvApp` | App shell background behind rail + content |

### 3.9 `#8A938C` @30% — hairline outline (`OutlineDark`, `WahaLine`, `WahaTvColors.Line`)

| File & Line | Module & Screen/Component | UI Element & Role |
|---|---|---|
| `mobile/…/WahaHomeScreen.kt:347` | `:mobile` → `CategoryFilterBar` | Unselected chip border |
| `mobile/…/VideoPlayerModal.kt:519` | `:mobile` → `VideoPlayerModal` | Header divider |
| `mobile/…/Searchoverlay.kt:103` | `:mobile` → `SearchOverlay` | Unfocused text-field border |
| `mobile/…/Watchhistoryscreen.kt:104` | `:mobile` → `WatchHistoryScreen` | List divider |
| `mobile/…/Settingsscreen.kt:105` | `:mobile` → `SettingsScreen` | Section divider (appearance) |
| `mobile/…/Settingsscreen.kt:123` | `:mobile` → `SettingsScreen` | Section divider (support) |
| `tv/…/TvMainScreen.kt:258` | `:tv` → `TvSideRail` | Rail edge hairline (drawn in draw phase) |
| `tv/…/TvMainScreen.kt:348` | `:tv` → `TvRailDivider` | Divider between search and sections |

### 3.10 `#0E8539` — brand green (`SecondaryLight/Dark`, `WahaGold`, `SavedBadge`)

| File & Line | Module & Screen/Component | UI Element & Role |
|---|---|---|
| `mobile/…/ui/theme/Theme.kt:17`, `:41` | `:mobile` → theme | `secondary` role (light + dark) |
| `tv/…/ui/theme/TvTheme.kt:20` | `:tv` → theme | `secondary` role |
| `tv/…/TvComponents.kt:311` | `:tv` → `TvVideoCard` | Saved-video badge circle |
| `mobile/…/Color.kt:68` | `:mobile` → token | `WahaGold` — **no consumer** |
| `tv/src/main/res/drawable/tv_banner.xml:20` | `:tv` → launcher banner | Rear dune fill |

### 3.11 `#131313` @95% / `#121212` — player panel blacks

| File & Line | Module & Screen/Component | UI Element & Role |
|---|---|---|
| `tv/…/ui/theme/TvColors.kt:56` | `:tv` → token | `PanelBackground` declaration |
| `tv/…/TvPlayerScreen.kt:802` | `:tv` → `PlayerPanel` | Control-panel backdrop |
| `tv/…/TvPlayerScreen.kt:923` | `:tv` → `TvPlayerScreen` | `PanelInk` — ink on the white selected pill |
| `tv/…/TvPlayerScreen.kt:938`/`939` | `:tv` → `panelControlColors` | Selected pill content colour |

### 3.12 Remaining accent/neutral tokens (component-level, no direct literal)

| Token | File & Line | Module & Screen/Component | UI Element & Role |
|---|---|---|---|
| `WahaTvColors.Line` | `tv/…/TvMainScreen.kt:258`, `:348` | `:tv` → `TvSideRail`, `TvRailDivider` | Hairlines (see §3.9) |
| `WahaTvColors.OnAccent` | `tv/…/TvComponents.kt:149`, `:179` | `:tv` → `tvItemColors`, `tvItemContentColor` | Ink on accent-focused keys/rows |
| `WahaTvColors.MediaScrim` | `tv/…/TvComponents.kt:295` | `:tv` → `TvVideoCard` | Duration-badge backdrop |
| `WahaTvColors.MediaScrim` | `tv/…/TvPlayerScreen.kt:545` | `:tv` → `PlayerTopOverlay` | Top scrim gradient start |
| `WahaTvColors.MediaScrimSoft` | `tv/…/TvComponents.kt:260` | `:tv` → `TvVideoCard` | Focus scrim over artwork |
| `WahaTvColors.MediaScrimSoft` | `tv/…/TvPlayerScreen.kt:587` | `:tv` → `PlayerCenterOverlay` | Centre button circle |
| `WahaTvColors.MediaScrimSoft` | `tv/…/TvPlayerScreen.kt:632` | `:tv` → `PlayerSeekOverlay` | Seek-indicator circle |
| `WahaTvColors.ProgressTrack` | `tv/…/TvComponents.kt:330` | `:tv` → `TvVideoCard` | Continue-watching track |
| `WahaTvColors.ProgressTrack` | `tv/…/TvPlayerScreen.kt:670` | `:tv` → `PlaybackProgressBar` | Player seekbar track |
| `WahaTvColors.CardPlaceholder` | `tv/…/TvComponents.kt:252` | `:tv` → `TvVideoCard` | Artwork placeholder fill |
| `WahaTvColors.OnMedia` | `tv/…/TvComponents.kt:288`, `:317`; `TvPlayerScreen.kt:553`, `:593`, `:638`, `:746`, `:760`, `:561` | `:tv` → cards + player overlays | Text/glyphs drawn on artwork |
| `WahaTvColors.SavedBadge` | `tv/…/TvComponents.kt:311` | `:tv` → `TvVideoCard` | Saved badge fill |
| `WahaTvColors.Background` | `tv/…/TvApp.kt:92` | `:tv` → `TvApp` | Shell background |
| `WahaTealBright` | `mobile/…/Searchoverlay.kt:102`, `:191` | `:mobile` → search | Focused border, "مسح الكل" |
| `WahaTealBright` | `mobile/…/Watchhistoryscreen.kt:99` | `:mobile` → history | "مسح الكل" |
| `WahaTealBright` | `mobile/…/WahaHomeScreen.kt:189`, `:688`, `:698` | `:mobile` → home/nav | Retry text, selected nav icon + label |
| `WahaTealBright` | `mobile/…/VideoPlayerModal.kt:365`, `:454`, `:690`, `:691` | `:mobile` → player | Buffering spinner, retry, seekbar thumb/active track |
| `WahaLine` | see §3.9 | `:mobile` | Outlines/dividers |
| `WahaCardBg2` | see §3.7 | `:mobile` | Raised chip/placeholder surfaces |

### 3.13 Implicit Material-role consumption (no token name at call site)

| File & Line | Module & Component | Role consumed implicitly |
|---|---|---|
| `mobile/…/WahaHomeScreen.kt:337`, `:347`, `:352` | `:mobile` → `CategoryFilterBar` | `primary`, `onPrimary` read directly via `MaterialTheme.colorScheme` |
| `mobile/…/Settingsscreen.kt:187` | `:mobile` → `SettingsRow` | `MaterialTheme.shapes.small` (no colour) |
| `mobile/…/Settingsscreen.kt:224` | `:mobile` → `ContentPolicyDialog` | `MaterialTheme.typography.titleLarge` colour defaults to `onSurface` |
| `mobile/…/WahaHomeScreen.kt:172` | `:mobile` → `PullToRefreshBox` | Refresh indicator uses `primary`/`surfaceContainerHigh` — the latter is **not themed** |
| `mobile/…/VideoPlayerModal.kt:565` | `:mobile` → `ModalBottomSheet` | Scrim (`scrim` role) and drag handle — **not themed** |
| `tv/…/TvHomeScreen.kt:231` | `:tv` → `TvReloadButton` | `onPrimary` read directly |
| `tv/…/TvComponents.kt:199` | `:tv` → `Card` | `CardDefaults.colors` fill from theme when not overridden |
| `tv/…/TvSearchScreen.kt:36` (`TvKeyButton`) | `:tv` → keys | `tvItemColors` / `tvItemContentColor` |

---

## 4. Hardcoded & Unused Colors Audit

### 4.1 Hardcoded colors bypassing the theme system

Counts: **24** `Color.*` expressions in `:mobile`, **20** in `:tv` (plus 4 XML literals and 2 `@android:color/black`).

#### `:mobile`

| File:Line | Expression | Where / Role | Suggested replacement |
|---|---|---|---|
| `mobile/…/WahaHomeScreen.kt:80` | `Color.White` | `DurationBadge` text | Add `WahaOnMedia` token (= white) in `Color.kt` |
| `mobile/…/WahaHomeScreen.kt:85` | `Color.Black.copy(alpha = 0.7f)` | Duration badge container | New `WahaMediaScrimStrong` (`0xB3000000`) |
| `mobile/…/WahaHomeScreen.kt:428` | `Color.Black.copy(alpha = 0.35f)` | Continue-watching play circle | `WahaMediaScrimSoft` (`0x59000000`) |
| `mobile/…/WahaHomeScreen.kt:434` | `Color.White` | Play glyph | `WahaOnMedia` |
| `mobile/…/WahaHomeScreen.kt:445` | `Color.Black.copy(alpha = 0.45f)` | Continue-watching progress track | `WahaProgressTrack` (`0x73000000`) |
| `mobile/…/WahaHomeScreen.kt:524` | `Color.Black.copy(alpha = 0.3f)` | VideoCard play circle | `WahaMediaScrimSoft` |
| `mobile/…/WahaHomeScreen.kt:530` | `Color.White` | VideoCard play glyph | `WahaOnMedia` |
| `mobile/…/VideoPlayerModal.kt:340` | `Color.White` | Double-tap seek glyph | `WahaOnMedia` |
| `mobile/…/VideoPlayerModal.kt:343` | `Color.Black.copy(alpha = 0.4f)` | Seek glyph circle | `WahaMediaScrimSoft` |
| `mobile/…/VideoPlayerModal.kt:388` | `Color.Black.copy(alpha = 0.45f)` | Centre button circle | `WahaMediaScrimSoft` |
| `mobile/…/VideoPlayerModal.kt:397` | `Color.White` | Centre play/pause glyph | `WahaOnMedia` |
| `mobile/…/VideoPlayerModal.kt:415` | `Color.Black.copy(alpha = 0.7f)`, `Color.Transparent` | Fullscreen top gradient | New `WahaScrimGradient` pair |
| `mobile/…/VideoPlayerModal.kt:425` | `Color.White` | Back arrow | `WahaOnMedia` |
| `mobile/…/VideoPlayerModal.kt:430` | `Color.White` | Fullscreen title | `WahaOnMedia` |
| `mobile/…/VideoPlayerModal.kt:597` | `Color.White` | Selected speed chip label | `colorScheme.onPrimary` (semantically correct: chip is `primary`) |
| `mobile/…/VideoPlayerModal.kt:616` | `Color.White` | Selected quality chip label | `colorScheme.onPrimary` |
| `mobile/…/VideoPlayerModal.kt:667` | `Color.Transparent`, `Color.Black.copy(alpha = 0.78f)` | Player control-bar gradient | New `WahaScrimGradient` pair |
| `mobile/…/VideoPlayerModal.kt:674` | `Color.White` | Elapsed time | `WahaOnMedia` |
| `mobile/…/VideoPlayerModal.kt:692` | `Color.White.copy(alpha = 0.30f)` | Inactive seekbar track | New `WahaProgressTrack` |
| `mobile/…/VideoPlayerModal.kt:699` | `Color.White` | Duration label | `WahaOnMedia` |
| `mobile/…/VideoPlayerModal.kt:705` | `Color.White` | Settings icon | `WahaOnMedia` |
| `mobile/…/VideoPlayerModal.kt:714` | `Color.White` | Fullscreen icon | `WahaOnMedia` |
| `mobile/…/VideoPlayerModal.kt:731`–`732` | `android.graphics.Color.BLACK` | ExoPlayer view/shutter | Deliberate (native surface); document as exception |
| `mobile/…/VideoPlayerModal.kt:739` | `Color.Black` | Player surface background | `WahaDarkBg` / `Color.Black` token |
| `mobile/src/main/java/com/waha/MainActivity.kt:29`, `:31` | `android.graphics.Color.TRANSPARENT` | System-bar style | Deliberate (edge-to-edge); document as exception |

#### `:tv`

| File:Line | Expression | Where / Role | Suggested replacement |
|---|---|---|---|
| `tv/…/TvComponents.kt:159` | `Color.Transparent` | `tvCardColors` container | Fine (card is transparent by design); keep, or add `WahaTvColors.CardContainer` |
| `tv/…/TvMainScreen.kt:381` | `Color.Transparent` | Rail item unfocused fill | Fine; keep |
| `tv/…/TvMainScreen.kt:387` | `Color.Transparent` | Zero-width border placeholder | Fine; keep |
| `tv/…/TvPlayerScreen.kt:346` | `Color.Black` | Player root background | `WahaTvColors.Background` (`#000000`) |
| `tv/…/TvPlayerScreen.kt:420` | `android.graphics.Color.BLACK` | ExoPlayer shutter | Deliberate (native); document |
| `tv/…/TvPlayerScreen.kt:429` | `Color.Black` | Video surface background | `WahaTvColors.Background` |
| `tv/…/TvPlayerScreen.kt:545` | `Color.Transparent` | Top scrim gradient end | Fine as gradient stop |
| `tv/…/TvPlayerScreen.kt:737` | `Color.Transparent`, `Color.Black.copy(alpha = 0.78f)` | Bottom overlay gradient | Add `WahaTvColors.PlayerBottomScrim` (`0xC7000000`) |
| `tv/…/TvPlayerScreen.kt:841` | `Color.White.copy(alpha = 0.10f)` | Panel hairline | Add `WahaTvColors.PanelDivider` (`0x1AFFFFFF`) |
| `tv/…/TvPlayerScreen.kt:923` | `Color(0xFF121212)` (`PanelInk`) | Ink on white pill | Rename/move to `WahaTvColors.PanelInk`; reconcile with `PanelBackground` `#131313` |
| `tv/…/TvPlayerScreen.kt:938` | `Color.White` ×2 | Selected pill container/content | `WahaTvColors.PanelPill` / `PanelInk` |
| `tv/…/TvPlayerScreen.kt:939` | `Color.White`, `Color.White.copy(0.35f)` | Selected pill + border | `PanelPill` / `PanelPillBorder` |
| `tv/…/TvPlayerScreen.kt:940` | `Color.White.copy(0.16f)`, `Color.White` | Focused fill + content | `PanelControlSurface` |
| `tv/…/TvPlayerScreen.kt:942` | `Color.White.copy(alpha = 0.06f)` | Resting fill | `PanelControlSurface` |
| `tv/…/TvPlayerScreen.kt:944` | `Color.White.copy(alpha = 0.16f)` | Resting border | `PanelControlBorder` |
| `tv/…/TvPlayerScreen.kt:976` | `Color.Transparent` | Border for primary button | Fine; keep |
| `tv/…/TvIcons.kt:139` | `SolidColor(Color.Black)` | Icon vector default fill (tinted at use site) | Deliberate idiom; no change needed |
| `tv/…/ui/theme/TvColors.kt:44` | `Color.White` | `OnMedia` token | Tokenized — acceptable |

#### XML literals

| File:Line | Literal | Role | Suggested replacement |
|---|---|---|---|
| `mobile/src/main/res/values/colors.xml:3` | `#FF6D00` | Adaptive-icon background | `#FF6600` (brand primary) or a documented `ic_launcher_background` token |
| `tv/src/main/res/values/colors.xml:4` | `#FF6D00` | Adaptive-icon background | Same as above (keep both files identical) |
| `mobile/src/main/res/values/themes.xml:7` | `@android:color/black` | Window background | Dark value ok; supply a `values/` + `values-night/` pair so light mode does not flash black |
| `tv/src/main/res/values/themes.xml:7` | `@android:color/black` | Window background | Fine (TV is dark-only) |
| `tv/src/main/res/drawable/tv_banner.xml:15`, `:28` | `#0B0F0D` | Banner night sky / play cut-out | Add a `brand_night` color resource; note it is **not** `BackgroundDark` `#000000` |
| `tv/src/main/res/drawable/tv_banner.xml:20` | `#0E8539` | Banner dune | Equals `SecondaryDark` — reference `@color/brand_green` if extracted |
| `tv/src/main/res/drawable/tv_banner.xml:25` | `#FF6600` | Banner sun | Equals `PrimaryDark` — reference `@color/brand_orange` |
| `tv/src/main/res/drawable/tv_banner.xml:33` | `#0A5C29` | Banner foreground dune | New `brand_green_dark` token |

### 4.2 Defined colors that are never referenced

| Token | Defined In | Evidence | Verdict |
|---|---|---|---|
| `WahaGold` | `mobile/…/ui/theme/Color.kt:68` | Only occurrence outside its declaration is `import com.waha.ui.theme.WahaGold` at `mobile/…/VideoPlayerModal.kt:77`; the import itself is unused. 0 call sites. | **Unused** — remove token + import |
| `WahaTvColors.SurfaceVariant` | `tv/…/ui/theme/TvColors.kt:30–31` | `grep "WahaTvColors.SurfaceVariant"` → 0 results | **Unused** — remove or adopt (e.g., player panel fill) |
| `WahaTvColors.OnAccent` | `tv/…/TvColors.kt:21` | 2 uses (`TvComponents.kt:149`, `:179`) — used only indirectly through helpers | Used (low) |
| `WahaTvColors.Background` | `tv/…/TvColors.kt:24` | 1 use (`TvApp.kt:92`) | Used (low) |
| `WahaBrandColors.PrimaryContainerLight` … `OutlineLight` | `core/…/WahaBrandColors.kt:19–41` | Each referenced exactly once, by `:mobile`'s `lightColorScheme` | Used, but **no screen consumes container/tertiary/error roles** — `primaryContainer`, `secondaryContainer`, `tertiaryContainer`, `error`, `errorContainer` are dead in practice |
| `WahaBrandColors.PrimaryContainerDark` … `OutlineDark` | `core/…/WahaBrandColors.kt:46–68` | Each referenced by mobile `darkColorScheme` and/or TV `darkColorScheme`; `tertiary*`, `error*`, `secondary*Container` never consumed by UI | Partially dead in practice |
| `WahaBrandColors.OnSurfaceLight` / `OnSurfaceDark` | `…:38`, `:65` | Mapped to `onSurface`, which no call site reads directly (`MediaTheme.typography` defaults aside) | Used only implicitly |
| `Theme.Waha` / `Theme.Waha.Tv` styles | `…/themes.xml:6` each | Referenced from the manifests (`android:theme`) | Used |

**Consistency notes (not dead code, but redundant):**

- `WahaTeal` and `WahaTealBright` (`Color.kt:62`, `:65`) resolve to the *same* expression; the pair implies a distinction that does not exist.
- `WahaDarkBg` vs `background`, `WahaCardBg` vs `surface`, `WahaCardBg2` vs `surfaceVariant`, `WahaTextWarm` vs `onBackground`, `WahaTextMuted` vs `onSurfaceVariant`, `WahaLine` vs `outline@30%` — six aliases that only rename M3 roles.
- `WahaTvColors.PanelBackground` (`0xF2131313`) vs `PanelInk` (`0xFF121212`) — two one-digit-apart blacks for the same panel.
- `tv_banner.xml`'s `#0B0F0D` is a fifth near-black in the project (alongside `#000000`, `#18120E`, `#131313`, `#121212`).

### 4.3 Theme roles that are never overridden (risk, not dead code)

`surfaceTint`, `outlineVariant`, `inverseSurface`, `inverseOnSurface`, `inversePrimary`, `scrim`, `surfaceBright`, `surfaceDim`, `surfaceContainer`, `surfaceContainerLow`, `surfaceContainerLowest`, `surfaceContainerHigh`, `surfaceContainerHighest`, `errorContainer`, `onErrorContainer` — absent from both `Theme.kt` schemes and the TV scheme. Concretely affected:

- `mobile/…/VideoPlayerModal.kt:565` `ModalBottomSheet` → `scrim` (baseline black, acceptable) and drag handle (`onSurfaceVariant` overridden, fine).
- `mobile/…/WahaHomeScreen.kt:172` `PullToRefreshBox` indicator → `surfaceContainerHigh` (baseline neutral, not Waha's `#1B2820`).
- `mobile/…/Settingsscreen.kt:215` `AlertDialog` → tonal elevation surface derived from `surfaceTint` (baseline primary tint, not `#FF6600`).
- Any future `Divider`, `OutlinedCard`, `NavigationBar` default → `outlineVariant` (baseline grey).
