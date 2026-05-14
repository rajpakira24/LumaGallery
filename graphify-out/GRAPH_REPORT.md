# Graph Report - C:/Users/RIJU PAKIRA/AndroidStudioProjects/LumaGallery  (2026-05-07)

## Corpus Check
- 37 files · ~18,114 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 209 nodes · 333 edges · 21 communities (17 shown, 4 thin omitted)
- Extraction: 84% EXTRACTED · 16% INFERRED · 0% AMBIGUOUS · INFERRED: 52 edges (avg confidence: 0.83)
- Token cost: 28,300 input · 4,900 output

## Community Hubs (Navigation)
- [[_COMMUNITY_Core Data Models & Repository Methods|Core Data Models & Repository Methods]]
- [[_COMMUNITY_Architecture Rationale & Security Layer|Architecture Rationale & Security Layer]]
- [[_COMMUNITY_App Entrypoint & Gallery UI|App Entrypoint & Gallery UI]]
- [[_COMMUNITY_File Operations (Move, Copy, Rename, Delete)|File Operations (Move, Copy, Rename, Delete)]]
- [[_COMMUNITY_Collections Tab & Ad Integration|Collections Tab & Ad Integration]]
- [[_COMMUNITY_Navigation & Routing|Navigation & Routing]]
- [[_COMMUNITY_Shared UI Components & Security Design|Shared UI Components & Security Design]]
- [[_COMMUNITY_Drag Selection & Photos Grid|Drag Selection & Photos Grid]]
- [[_COMMUNITY_Permission Result Sealed Classes|Permission Result Sealed Classes]]
- [[_COMMUNITY_Material 3 Theme System|Material 3 Theme System]]
- [[_COMMUNITY_App Launcher Icons|App Launcher Icons]]
- [[_COMMUNITY_PBKDF2 Password Hashing|PBKDF2 Password Hashing]]
- [[_COMMUNITY_Instrumented Tests|Instrumented Tests]]
- [[_COMMUNITY_Unit Tests|Unit Tests]]
- [[_COMMUNITY_Root Build Config|Root Build Config]]
- [[_COMMUNITY_Compose UI Utilities|Compose UI Utilities]]

## God Nodes (most connected - your core abstractions)
1. `PhotoRepository` - 48 edges
2. `GalleryViewModel` - 41 edges
3. `GalleryScreen` - 20 edges
4. `MainActivity` - 16 edges
5. `PhotoDetail` - 12 edges
6. `Photo (data class)` - 8 edges
7. `CategoryDetailScreen Composable` - 8 edges
8. `LumaGalleryTheme` - 8 edges
9. `PhotoRepository.loadAllMedia()` - 7 edges
10. `DragSelectState` - 7 edges

## Surprising Connections (you probably didn't know these)
- `GalleryViewModel` --references--> `MVVM + Repository Architecture (CLAUDE.md)`  [INFERRED]
  app/src/main/java/com/webstudio/lumagallery/MainActivity.kt → CLAUDE.md
- `GalleryViewModel` --rationale_for--> `Optimistic Updates Design Rationale`  [EXTRACTED]
  app/src/main/java/com/webstudio/lumagallery/MainActivity.kt → CLAUDE.md
- `GalleryViewModel` --rationale_for--> `reloadGalleryInBackground vs refresh() Safety Rationale`  [EXTRACTED]
  app/src/main/java/com/webstudio/lumagallery/MainActivity.kt → CLAUDE.md
- `PBKDF2 Password Hash Rationale` --conceptually_related_to--> `HiddenCollectionScreen`  [INFERRED]
  CLAUDE.md → app/src/main/java/com/webstudio/lumagallery/ui/screens/HiddenCollectionScreen.kt
- `DragSelectState` --rationale_for--> `DragSelectState Root Coordinate Space Rationale`  [EXTRACTED]
  app/src/main/java/com/webstudio/lumagallery/ui/util/DragSelectState.kt → CLAUDE.md

## Hyperedges (group relationships)
- **Delete Permission Flow (permanentlyDelete -> DeleteResult.NeedsPermission -> RecycleBinScreen)** — permanently_delete, delete_result, gallery_viewmodel [EXTRACTED 0.95]
- **Write Permission Flow (renamePhoto/movePhoto -> WriteResult.NeedsPermission -> PendingWrite -> ViewModel replay)** — rename_photo, move_photo, write_result, pending_write [EXTRACTED 0.95]
- **Core Media Data Models (Photo, FolderGroup, DateGroup, RecycleBinItem)** — photo_model, folder_group_model, date_group_model, recycle_bin_item_model [INFERRED 0.95]
- **Delete Permission Flow (API 30+): RecycleBinScreen + GalleryViewModel + PendingDeleteIntent** — recyclebinscreen_RecycleBinScreen, viewmodel_GalleryViewModel, claudemd_ArchitectureDoc [EXTRACTED 1.00]
- **Write Permission Flow: PhotoDetailScreen + GalleryViewModel + PendingWrite** — photodetailscreen_PhotoDetailScreen, viewmodel_GalleryViewModel, claudemd_reloadGalleryInBackgroundRationale [EXTRACTED 1.00]
- **Luma Material 3 Theme System: Color + Typography + Shapes** — color_LumaBrandPalette, type_Typography, shape_Shapes [EXTRACTED 1.00]
- **All Square (Squircle) Launcher Icon Variants Across Densities** — ic_launcher_mdpi_square, ic_launcher_hdpi_square, ic_launcher_xhdpi_square, ic_launcher_xxhdpi_square, ic_launcher_xxxhdpi_square [EXTRACTED 1.00]
- **All Round (Circle-clipped) Launcher Icon Variants Across Densities** — ic_launcher_mdpi_round, ic_launcher_hdpi_round, ic_launcher_xhdpi_round, ic_launcher_xxhdpi_round, ic_launcher_xxxhdpi_round [EXTRACTED 1.00]
- **Complete Launcher Icon Resource Set (All Densities, Both Shapes)** — ic_launcher_mdpi_square, ic_launcher_mdpi_round, ic_launcher_hdpi_square, ic_launcher_hdpi_round, ic_launcher_xhdpi_square, ic_launcher_xhdpi_round, ic_launcher_xxhdpi_square, ic_launcher_xxhdpi_round, ic_launcher_xxxhdpi_square, ic_launcher_xxxhdpi_round [EXTRACTED 1.00]

## Communities (21 total, 4 thin omitted)

### Community 0 - "Core Data Models & Repository Methods"
Cohesion: 0.1
Nodes (6): DateGroup, FolderGroup, Photo, RecycleBinItem, PhotoRepository, PhotoRepository In-Memory Cache (Mutex, 5s TTL)

### Community 1 - "Architecture Rationale & Security Layer"
Cohesion: 0.1
Nodes (8): MVVM + Repository Architecture (CLAUDE.md), Optimistic Updates Design Rationale, reloadGalleryInBackground vs refresh() Safety Rationale, GalleryViewModel, RecycleBinScreen, Error, Loading, ViewMode

### Community 2 - "App Entrypoint & Gallery UI"
Cohesion: 0.09
Nodes (23): App Module Build Gradle, BuildConfig IRONSOURCE_APP_KEY Field, CategoryDetailScreen Composable, Media Permission by API Level Rationale, Coil ImageLoader (with VideoFrameDecoder), GalleryScreen Composable, GalleryScreenTest (Compose UI Tests), GalleryUiState (sealed: Loading/Success/Error) (+15 more)

### Community 3 - "File Operations (Move, Copy, Rename, Delete)"
Cohesion: 0.11
Nodes (20): PhotoRepository.applyMove(), PhotoRepository.applyRenameInternal(), PhotoRepository.bulkPermanentlyDelete(), PhotoRepository.cleanupExpiredItems(), PhotoRepository.copyPhoto(), PhotoRepository.copyPhotoInternal(), DateGroup (data class), DeleteResult (sealed class) (+12 more)

### Community 4 - "Collections Tab & Ad Integration"
Cohesion: 0.17
Nodes (13): IronSource App Key Hardcoded Rationale (TODO: wire BuildConfig), CollectionsTabContent, FolderCard, FolderFilter (enum), FolderTileImage, GalleryScreen, IronSourceBannerAd, PhotoGrid4 (+5 more)

### Community 5 - "Navigation & Routing"
Cohesion: 0.13
Nodes (11): CategoryDetail, Gallery, HiddenCollection, PhotoDetail, RecycleBin, Screen, ActionButton, DetailRow (+3 more)

### Community 6 - "Shared UI Components & Security Design"
Cohesion: 0.24
Nodes (10): Backup Exclusion of luma_gallery_prefs Rationale, PBKDF2 Password Hash Rationale, SelectionOverlay(), ShimmerPlaceholder(), PhotoGridItem, HiddenCollectionScreen, PasswordDialog, PhotoItem (HiddenCollection) (+2 more)

### Community 7 - "Drag Selection & Photos Grid"
Cohesion: 0.22
Nodes (4): DragSelectState Root Coordinate Space Rationale, Photos Tab Grid LazyColumn+chunked(3) Rationale, DragSelectState, PhotosTabContent

### Community 8 - "Permission Result Sealed Classes"
Cohesion: 0.32
Nodes (8): DeleteResult, Failure, Move, NeedsPermission, PendingWrite, Rename, Success, WriteResult

### Community 9 - "Material 3 Theme System"
Cohesion: 0.38
Nodes (6): Luma Brand Color Palette, Shapes (Material 3 Scale), DarkColorScheme, LightColorScheme, LumaGalleryTheme, Typography (Material 3 Scale)

### Community 10 - "App Launcher Icons"
Cohesion: 0.83
Nodes (4): Default Android Studio Placeholder Icon (Bugdroid), Green Grid Background Design Language, Launcher Icon Density Set (Adaptive Icon Resource Group), App Launcher Icon (hdpi, round)

### Community 11 - "PBKDF2 Password Hashing"
Cohesion: 1.0
Nodes (3): PhotoRepository.pbkdf2Hash() (PBKDF2WithHmacSHA256, 600k iter), PhotoRepository.setHiddenPassword() (PBKDF2), PhotoRepository.verifyHiddenPassword()

## Knowledge Gaps
- **39 isolated node(s):** `PendingWrite`, `Rename`, `Move`, `Screen`, `Gallery` (+34 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **4 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `GalleryViewModel` connect `Architecture Rationale & Security Layer` to `Core Data Models & Repository Methods`, `App Entrypoint & Gallery UI`, `Collections Tab & Ad Integration`, `Navigation & Routing`, `Shared UI Components & Security Design`, `Permission Result Sealed Classes`?**
  _High betweenness centrality (0.450) - this node is a cross-community bridge._
- **Why does `PhotoRepository` connect `Core Data Models & Repository Methods` to `Architecture Rationale & Security Layer`, `File Operations (Move, Copy, Rename, Delete)`?**
  _High betweenness centrality (0.316) - this node is a cross-community bridge._
- **Why does `GalleryScreen` connect `Collections Tab & Ad Integration` to `Material 3 Theme System`, `App Entrypoint & Gallery UI`, `Shared UI Components & Security Design`, `Drag Selection & Photos Grid`?**
  _High betweenness centrality (0.208) - this node is a cross-community bridge._
- **Are the 4 inferred relationships involving `GalleryViewModel` (e.g. with `PhotoDetail` and `RecycleBinScreen`) actually correct?**
  _`GalleryViewModel` has 4 INFERRED edges - model-reasoned connections that need verification._
- **Are the 9 inferred relationships involving `GalleryScreen` (e.g. with `.galleryScreen_loadingState_showsProgressIndicator()` and `.galleryScreen_emptyState_showsNoPhotosFound()`) actually correct?**
  _`GalleryScreen` has 9 INFERRED edges - model-reasoned connections that need verification._
- **Are the 2 inferred relationships involving `PhotoDetail` (e.g. with `.onCreate()` and `GalleryViewModel`) actually correct?**
  _`PhotoDetail` has 2 INFERRED edges - model-reasoned connections that need verification._
- **What connects `PendingWrite`, `Rename`, `Move` to the rest of the system?**
  _39 weakly-connected nodes found - possible documentation gaps or missing edges._