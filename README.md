# Luma Gallery

A modern Android photo and video gallery built with Jetpack Compose.

## Features

- Browse photos and videos grouped by date or folder
- Favorites, hidden collection (PBKDF2-protected), and 30-day recycle bin
- Drag-to-select for bulk operations (move to bin, hide, unhide)
- Rename, copy, move, "Set as", and detail inspection of media
- Pinch-to-zoom photo viewer (Telephoto) and in-app video playback (Media3/ExoPlayer)
- Material 3 dynamic theming with dark mode support

## Tech Stack

- **Language:** Kotlin 2.2.10
- **UI:** Jetpack Compose (BOM 2024.09.00), Material 3
- **Architecture:** MVVM with a single `PhotoRepository` over `MediaStore`
- **Image/Video loading:** Coil 2.7 (+ `VideoFrameDecoder` for thumbnails)
- **Video playback:** Media3 / ExoPlayer 1.5.0
- **Persistence:** SharedPreferences (`luma_gallery_prefs`) — no Room DB; soft-delete and user state stored per-photo
- **Permissions:** Accompanist Permissions 0.37.3
- **Monetization:** Unity LevelPlay (IronSource) 8.4.0

Min SDK 24 (Android 7) · Compile/Target SDK 36 · Java 17 · AGP 9.2

## Build

```powershell
# Debug APK
.\gradlew assembleDebug

# Release APK (R8 enabled)
.\gradlew assembleRelease

# Unit tests
.\gradlew test

# Lint
.\gradlew lint
```

Or open the project in Android Studio and run the `app` configuration.

## Setup

1. Clone the repo.
2. Create a `local.properties` in the project root with your Android SDK path:
   ```
   sdk.dir=C:\\Path\\To\\Android\\Sdk
   ```
3. (Optional, for ads) Add your Unity LevelPlay app key to `local.properties`:
   ```
   IRONSOURCE_APP_KEY=your_key_here
   ```
   The app reads this via `BuildConfig.IRONSOURCE_APP_KEY` — never commit it.

## Project Layout

```
app/src/main/java/com/webstudio/lumagallery/
├── MainActivity.kt
├── data/                    # PhotoRepository, models, sealed results
├── ui/
│   ├── navigation/          # routes: gallery, photo_detail, recycle_bin, hidden_collection
│   ├── screens/             # one Composable per route
│   ├── components/          # shared UI (Effects, LiquidGlass, ...)
│   ├── theme/               # Material 3 theme
│   ├── util/                # DragSelectState
│   └── viewmodel/           # GalleryViewModel
```

See `CLAUDE.md` for architecture details and contributor conventions.

## License

All rights reserved.
