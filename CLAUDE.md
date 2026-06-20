# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```powershell
# Build debug APK
.\gradlew assembleDebug

# Build release APK
.\gradlew assembleRelease

# Run unit tests
.\gradlew test

# Run instrumented tests (requires connected device/emulator)
.\gradlew connectedAndroidTest

# Run a single unit test class
.\gradlew testDebugUnitTest --tests "com.webstudio.lumagallery.ExampleUnitTest"

# Clean build
.\gradlew clean

# Lint check
.\gradlew lint
```

Open the project in Android Studio; use `Run > Run 'app'` to deploy to a device/emulator.

## Architecture

**MVVM + Repository** — single-module app (`com.webstudio.lumagallery`).

```
MediaStore (system)
    └── PhotoRepository          # data layer: queries, caching, persistence
            └── GalleryViewModel # state layer: StateFlow, optimistic updates
                    └── Compose screens  # UI layer: one screen per route
```

**Data layer** (`data/`):
- `PhotoRepository` owns all MediaStore queries and SharedPreferences I/O. There is **no Room database** — soft-delete (recycle bin) and user state (favorites, hidden, password hash) are persisted in `luma_gallery_prefs` SharedPreferences with keys `favorite_$photoId`, `hidden_$photoId`, `recycle_$photoId`, `deleted_at_$photoId`.
- A `Mutex`-protected, 5-second TTL in-memory cache (`cachedMedia`) avoids redundant MediaStore scans. `invalidateCache()` nulls it and resets `lastLoadTime = 0L`; the next `loadAllMedia()` call re-queries.
- `RecycleBinItem` entries expire after 30 days; `cleanupExpiredItems()` is called at ViewModel init.
- `dateFormat` and `displayDateFormat` are `ThreadLocal<SimpleDateFormat>` — do not introduce bare `SimpleDateFormat` fields shared across coroutines.
- **Delete permission flow**: `permanentlyDelete()` returns `DeleteResult` — `Success`, `Failure`, or `NeedsPermission(PendingIntent)`. On API 30+ it always returns `NeedsPermission` via `MediaStore.createDeleteRequest`. The ViewModel tracks `pendingDeleteIds`, publishes the intent to `pendingDeleteIntent: StateFlow<PendingIntent?>`, and `RecycleBinScreen` launches it via `ActivityResultLauncher<IntentSenderRequest>`. On grant, `onDeleteGranted()` calls `clearPrefsForIds(pendingDeleteIds)` to remove prefs for the deleted photos.
- **Write permission flow**: `renamePhoto()` and `movePhoto()` return `WriteResult` — `Success`, `Failure`, or `NeedsPermission(pendingIntent, operation: PendingWrite)`. `PendingWrite` is a sealed class (`Rename`, `Move`) that carries the deferred operation. The ViewModel holds `pendingWriteIntent: StateFlow<PendingIntent?>` and `PhotoDetailScreen` launches it; on grant, `onWritePermissionGranted()` replays the stored `PendingWrite`.
- **Pending intent one-shot**: `onDeleteIntentConsumed()` / `onWriteIntentConsumed()` must be called **before** launching `IntentSenderRequest` in the screen `LaunchedEffect` — calling after allows a rotation mid-dialog to re-fire the launch.

**ViewModel layer** (`ui/viewmodel/GalleryViewModel.kt`):
- Exposes `uiState: StateFlow<GalleryUiState>` (sealed: `Loading | Success | Error`), `pendingDeleteIntent: StateFlow<PendingIntent?>`, and `pendingWriteIntent: StateFlow<PendingIntent?>`.
- **Optimistic updates**: mutate in-memory lists immediately; do not trigger full MediaStore reloads for toggle operations (favorites, hide, move-to-recycle-bin). Exception: unhiding a photo requires `refresh()` because re-inserting into sorted date/folder structures is not trivial.
- `refresh()` resets to `Loading` state — never call it from a screen that stays mounted during the reload (e.g., while the user is viewing `PhotoDetailScreen`), or the screen will blank. Use `reloadGalleryInBackground()` instead for move, copy, and restore operations — it silently updates date/folder groups while keeping the current `Success` state.
- On `loadPhotos()`, a single `loadAllMedia()` warms the cache, then `dateGroups`, `folderGroups`, `recycleBinItems`, and `hiddenPhotos` are computed in parallel via `async`.
- `toggleFavorite()` rebuilds the special Favorites `FolderGroup` inline as part of the optimistic update so the Collections tab reflects the change immediately without a reload.

**UI layer** (`ui/screens/`):
- Gallery/detail/auxiliary screens are top-level `@Composable`s receiving props from `MainActivity` — no ViewModel access inside. Exception: `EditScreen` calls `viewModel()` directly because it has its own isolated `EditViewModel`.
- `GalleryScreen` hosts two tabs (Photos / Collections) and a Unity Ads banner at the bottom.
- `PhotoDetailScreen` has a `DropdownMenu` with: Details (ModalBottomSheet), Rename (AlertDialog), Set as (system chooser), Copy to / Move to (folder picker AlertDialog using `folderGroups`). A `LaunchedEffect(allPhotos.size)` navigates back when the current photo disappears from the list; it uses a `hasSeenCurrentPhoto` guard to prevent spurious back-navigation on the initial load.
- `ui/util/DragSelectState` tracks drag-selection state. `itemBounds` stores `Rect` in **root coordinate space** (via `boundsInRoot()`), not parent-relative.

**Navigation** (`ui/navigation/Navigation.kt`):
- Routes: `gallery`, `category_detail/{categoryName}`, `photo_detail/{photoId}`, `recycle_bin`, `hidden_collection`, `edit_photo/{photoId}`.
- Folder paths are URL-encoded (`Uri.encode`) before passing as nav arguments to handle `/` and special characters.

**Core data models** (`data/Photo.kt`):
- `Photo`: MediaStore fields + `isFavorite`, `isHidden`, `isInRecycleBin`, `deletedAt`, `duration`.
- `FolderGroup`: `isSpecialFolder = true` for virtual folders (e.g., Favorites) — these are excluded from copy/move destination pickers.
- `DateGroup`: groups `Photo` list by calendar date string.
- `DeleteResult`: sealed class — `Success`, `NeedsPermission(PendingIntent)`, `Failure`.
- `WriteResult` / `PendingWrite` (`data/WriteResult.kt`): parallel sealed classes for rename/move permission flows.

## Key Constraints

- **Min SDK 24** (Android 7). Use `Build.VERSION.SDK_INT` guards where needed. `java.time` APIs require desugaring; the project uses `ThreadLocal<SimpleDateFormat>` in the repository — keep new date code consistent.
- **Media permissions differ by API level**: `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO` for API 33+; `READ_EXTERNAL_STORAGE` for older.
- **Hidden collection password** is PBKDF2WithHmacSHA256 (600k iterations, 32-byte salt) stored under keys `hidden_password_hash` and `hidden_password_salt` in SharedPreferences. `verifyHiddenPassword` is a `suspend fun` (runs on `Dispatchers.Default`) and returns `false` when no password exists — it never auto-sets. Use `setHiddenPassword()` explicitly for first-time setup.
- **Photos tab grid** uses `Column { photos.chunked(3).forEach { Row { ... } } }` inside `LazyColumn` items — do **not** replace with nested `LazyVerticalGrid` (causes height-constraint clipping).
- **Unity Ads** keys in `local.properties` → `BuildConfig`: `UNITY_GAME_ID`, `UNITY_BANNER_PLACEMENT_ID`, `UNITY_REWARDED_PLACEMENT_ID`. All default to empty string — ads are silently skipped when blank. `UNITY_GAME_ID` **must be registered in the Unity dashboard for bundle ID `com.webstudio.lumagallery`** — a mismatched game ID returns HTTP 409 and init fails. `testMode = BuildConfig.DEBUG` is passed to `UnityAds.initialize`; real credentials are still required in test mode. `UnityAdState.initialized` (StateFlow) gates banner rendering — `UnityBannerAd()` returns early until `onInitializationComplete` fires.
- **AI keys live server-side**, never in the APK. The app calls a Supabase-style Deno Deploy edge function (`server/`, deployed at `BuildConfig.AI_PROXY_URL` = `https://luma-ai-proxy.webstudio.deno.net`) which holds `GEMINI_API_KEY` / `OPENROUTER_API_KEY` as runtime env vars and forwards to Gemini / OpenRouter. The client ships only `AI_PROXY_URL` + `PLAY_CLOUD_PROJECT_NUMBER` (`local.properties` → `BuildConfig`). Cloud calls attach a **Play Integrity** token (`PlayIntegrityTokenProvider`); the proxy verifies it before forwarding, and per-IP rate-limits (Deno KV, multi-window): general `RATE_PER_MIN`=5 / `RATE_PER_HOUR`=50 / `RATE_PER_DAY`=200, plus paid image ops get an extra `RATE_IMAGE_PER_DAY`=20 (checked after attestation). 429s carry a `Retry-After` header + `retry_after` JSON field. On-device background removal works with no key/proxy. OpenRouter caption model is `nvidia/nemotron-nano-12b-v2-vl:free`; result cached per photo in SharedPreferences under `caption_$photoId`. UI gates on `aiEnabled` (= `AI_PROXY_URL.isNotBlank()`), not per-provider key flags.
- **`isMinifyEnabled = true`** and **`isShrinkResources = true`** in release builds — ProGuard/R8 is active. Keep `proguard-rules.pro` current when adding new dependencies. `Log.d`/`Log.v` are stripped via `-assumenosideeffects` — do not use `Log.i`/`Log.w` for debug-only output.
- Backup rules must exclude `luma_gallery_prefs.xml` to prevent restoring another user's password hash or recycle bin state onto a new device.

## Edit Feature

**New route**: `edit_photo/{photoId}` — `EditScreen` is the only screen that instantiates its own `EditViewModel` via `viewModel()` directly (not prop-drilled from `MainActivity`).

**Data layer** (`data/edit/`):
- `BitmapEngine` — stateless `object`; every function returns a **new** bitmap. Callers own recycling; never pass a bitmap to two functions concurrently.
- `EditHistory` — bounded undo/redo stack (`maxSize = 6`). Not thread-safe; call from a single coroutine. `pushUndo(prev)` clears the redo stack. `popUndo(current)` / `popRedo(current)` take the current bitmap (pushed onto the opposite stack) and return the saved one.
- `FilterPreset` — enum; `Original` is a no-op guard. `preset.matrix()` builds a fresh `ColorMatrix` each call.
- `PhotoIO` — `loadPreviewBitmap()` decodes at reduced sampling; `saveBitmap()` writes JPEG to `MediaStore`; `savePngSticker()` saves a transparent PNG.

**AI layer** (`data/ai/`):
- `AiEditRepository` — single entry point (constructed with a `Context`). Routes: background removal → `OnDeviceBgRemover` (ML Kit, on-device, no proxy); `describePhoto()` → `AiProxyClient.describe()` (returns `AiResult.TextSuccess(text)`); cloud image edits (`editImage(op, …)` for erase/prompt-edit/generate) exist but are **currently hidden in the UI** (paid Gemini). **Upscale is on-device** now (`BitmapEngine.upscale2x`, ad-gated via `RewardedAdGate`), not the proxy. Re-enabling paid cloud edits = uncomment the Prompt/Erase buttons in `AiPanel`. `AiProxyClient` POSTs to `BuildConfig.AI_PROXY_URL` with a Play Integrity token; maps HTTP 401→`AiResult.Failure("device verification failed")`, 429→`QuotaExceeded`.
- `AiResult` sealed class: `Success(bitmap)`, `TextSuccess(text)`, `MissingApiKey`, `NetworkError`, `QuotaExceeded`, `Failure(message)`. `MissingApiKey` now means the proxy URL is unset (`!aiEnabled`).
- No API keys in the client. `EditUiState.aiEnabled` (= `AI_PROXY_URL.isNotBlank()`) drives UI hints. Server-side proxy code + tests live in `server/` (Deno/TypeScript). See `docs/superpowers/specs/2026-06-20-server-side-ai-keys-design.md`.

**EditViewModel** (`ui/viewmodel/EditViewModel.kt`):
- `EditUiState.displayBitmap` = `previewBitmap ?: bitmap` — screens always render this, not `bitmap` directly.
- Live color preview: `setColorPreview()` renders to `previewBitmap` without committing; `commitColorPreview()` pushes it to history; `discardColorPreview()` recycles it. **Always call discard or commit before any other op** — stale previews are not auto-cleared.
- `replaceBitmap()` is the shared commit path used by crop, draw-flatten, and AI results.
- Consume pattern: `consumeSavedUri()`, `consumeError()`, `consumeInfo()` must be called by the screen after acting on each one-shot state field.
- `onCleared()` recycles `previewBitmap` and calls `history.clear()` to free all snapshot bitmaps.

**Rewarded ad gate** (`ads/`):
- `RewardedAdGate` singleton gates cloud AI ops (upscale, erase, prompt-edit). Call `RewardedAdGate.configure(placementId)` once after `UnityAdState` init; call `showForReward(activity, onRewarded, onUnavailable)` before triggering a cloud op. `onUnavailable` fires if no ad is ready — show a toast; do not block the user indefinitely.
- Placement ID sourced from `BuildConfig.UNITY_REWARDED_PLACEMENT_ID` (set in `local.properties`).
- Background removal (`OnDeviceBgRemover`) does **not** require a rewarded ad — it runs on-device via ML Kit.

## SDK & Toolchain

- Compile/Target SDK: 37 | Min SDK: 24
- Kotlin 2.2.10, AGP 9.2.0, Java 17
- Compose BOM `2024.09.00`; Material 3
- Coil 2.7.0 (images + `VideoFrameDecoder` for thumbnails), Media3/ExoPlayer 1.5.0 (video playback)
- Accompanist Permissions 0.37.3, SystemUI 0.36.0
- Telephoto Zoomable 0.19.0 (pinch-to-zoom in `PhotoDetailScreen`)
- Unity Ads 4.12.4 (Maven Central — no custom repo needed)

Dependency versions **not** in `gradle/libs.versions.toml` (hardcoded in `app/build.gradle.kts`): Navigation Compose, ViewModel Compose, Lifecycle Runtime Compose, Coil, Media3, Accompanist, Lottie, Telephoto.

## graphify

This project has a graphify knowledge graph at graphify-out/.

Rules:
- Before answering architecture or codebase questions, read graphify-out/GRAPH_REPORT.md for god nodes and community structure
- If graphify-out/wiki/index.md exists, navigate it instead of reading raw files
- For cross-module "how does X relate to Y" questions, prefer `graphify query "<question>"`, `graphify path "<A>" "<B>"`, or `graphify explain "<concept>"` over grep — these traverse the graph's EXTRACTED + INFERRED edges instead of scanning files
- After modifying code files in this session, run `graphify update .` to keep the graph current (AST-only, no API cost)