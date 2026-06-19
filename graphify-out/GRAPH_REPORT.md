# Graph Report - C:/Users/RIJU PAKIRA/AndroidStudioProjects/LumaGallery  (2026-05-19)

## Corpus Check
- 57 files · ~29,987 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 351 nodes · 515 edges · 25 communities (15 shown, 10 thin omitted)
- Extraction: 94% EXTRACTED · 6% INFERRED · 0% AMBIGUOUS · INFERRED: 29 edges (avg confidence: 0.82)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- [[_COMMUNITY_PhotoRepository|PhotoRepository]]
- [[_COMMUNITY_GalleryViewModel|GalleryViewModel]]
- [[_COMMUNITY_PhotoRepository|PhotoRepository]]
- [[_COMMUNITY_GalleryScreen.kt|GalleryScreen.kt]]
- [[_COMMUNITY_PhotoDetailScreen()|PhotoDetailScreen()]]
- [[_COMMUNITY_EditViewModel|EditViewModel]]
- [[_COMMUNITY_DrawPanel.kt|DrawPanel.kt]]
- [[_COMMUNITY_AiPanel.kt|AiPanel.kt]]
- [[_COMMUNITY_.editWithPrompt()|.editWithPrompt()]]
- [[_COMMUNITY_QwenImageClient.kt|QwenImageClient.kt]]
- [[_COMMUNITY_EditViewModel|EditViewModel]]
- [[_COMMUNITY_Navigation.kt|Navigation.kt]]
- [[_COMMUNITY_AiEditRepository|AiEditRepository]]
- [[_COMMUNITY_RewardedAdGate|RewardedAdGate]]
- [[_COMMUNITY_AiResult.kt|AiResult.kt]]
- [[_COMMUNITY_BitmapEngine|BitmapEngine]]
- [[_COMMUNITY_EditHistory|EditHistory]]
- [[_COMMUNITY_PhotoIO|PhotoIO]]
- [[_COMMUNITY_OnDeviceBgRemover|OnDeviceBgRemover]]
- [[_COMMUNITY_UnityAdState|UnityAdState]]
- [[_COMMUNITY_FilterPreset.kt|FilterPreset.kt]]

## God Nodes (most connected - your core abstractions)
1. `PhotoRepository` - 38 edges
2. `GalleryViewModel` - 30 edges
3. `EditViewModel` - 23 edges
4. `GalleryScreen()` - 12 edges
5. `PhotoDetailScreen()` - 10 edges
6. `DrawPanel()` - 8 edges
7. `PhotoRepository` - 8 edges
8. `GalleryScreenTest` - 7 edges
9. `AiEditRepository` - 7 edges
10. `GeminiImageClient` - 7 edges

## Surprising Connections (you probably didn't know these)
- `LumaGallery README` --references--> `Coil 2.7.0 Image Loader`  [EXTRACTED]
  README.md → CLAUDE.md
- `LumaGallery README` --references--> `Media3/ExoPlayer 1.5.0`  [EXTRACTED]
  README.md → CLAUDE.md
- `LumaGallery README` --references--> `Accompanist Permissions 0.37.3`  [EXTRACTED]
  README.md → CLAUDE.md
- `LumaGallery CLAUDE.md` --references--> `LumaGallery README`  [EXTRACTED]
  CLAUDE.md → README.md
- `LumaGallery README` --references--> `MVVM + Repository Architecture`  [EXTRACTED]
  README.md → CLAUDE.md

## Hyperedges (group relationships)
- **Data Layer Components** — photorepo, cachedmedia, sharedprefs_luma, mediastore, recyclebinitem, deleteresult, writeresult [EXTRACTED 1.00]
- **Edit Feature Components** — editscreen, editviewmodel, bitmapengine, edithistory, filterpreset, photoio, aieditrepository, airesult, ondevicebgremover, rewardedadgate [EXTRACTED 1.00]
- **AI Provider Fallback Chain** — aieditrepository, gemini_api, qwen_dashscope, ondevicebgremover [EXTRACTED 1.00]
- **Navigation Routes** — navigation_kt, galleryscreen, photodetailscreen, recyclebinscreen, editscreen [EXTRACTED 1.00]
- **Permission Intent Flows** — photorepo, galleryviewmodel, recyclebinscreen, photodetailscreen, deleteresult, writeresult, pending_intent_oneshot [EXTRACTED 1.00]

## Communities (25 total, 10 thin omitted)

### Community 1 - "GalleryViewModel"
Cohesion: 0.08
Nodes (6): Error, GalleryUiState, GalleryViewModel, Loading, Success, ViewMode

### Community 2 - "PhotoRepository"
Cohesion: 0.08
Nodes (35): Accompanist Permissions 0.37.3, MVVM + Repository Architecture, Backup Exclusion of luma_gallery_prefs, cachedMedia In-Memory Cache, LumaGallery CLAUDE.md, Coil 2.7.0 Image Loader, Compose Screens (UI Layer), DateGroup (+27 more)

### Community 3 - "GalleryScreen.kt"
Cohesion: 0.11
Nodes (14): CollectionsTabContent(), findActivity(), FolderCard(), FolderFilter, FolderTileImage(), GalleryScreen(), PhotoGrid4(), PhotoGridItem() (+6 more)

### Community 4 - "PhotoDetailScreen()"
Cohesion: 0.12
Nodes (18): MainActivity, CategoryDetailScreen(), PhotoItem(), HiddenCollectionScreen(), PasswordDialog(), PhotoItem(), PermissionScreen(), ActionButton() (+10 more)

### Community 6 - "DrawPanel.kt"
Cohesion: 0.16
Nodes (21): BrushControlGroup, BrushControls(), BrushPreset, ColorRow(), ControlRail(), DrawElement, DrawMode, DrawPanel() (+13 more)

### Community 7 - "AiPanel.kt"
Cohesion: 0.13
Nodes (16): EditScreen(), EditTool, AiButton(), AiPanel(), EraseObjectMaskUi(), fittedImageRect(), MaskStroke, renderMask() (+8 more)

### Community 8 - ".editWithPrompt()"
Cohesion: 0.16
Nodes (12): Candidate, Content, GeminiImageClient, GenerateRequest, GenerateResponse, GenerationConfig, InlineData, Part (+4 more)

### Community 9 - "QwenImageClient.kt"
Cohesion: 0.23
Nodes (10): Choice, ContentPart, Input, Message, MultimodalRequest, MultimodalResponse, Output, OutputMessage (+2 more)

### Community 10 - "EditViewModel"
Cohesion: 0.17
Nodes (12): AiEditRepository, AiResult Sealed Class, BitmapEngine, EditHistory Undo/Redo Stack, EditUiState, EditViewModel, FilterPreset Enum, Gemini 2.0 Flash API (+4 more)

### Community 11 - "Navigation.kt"
Cohesion: 0.18
Nodes (7): CategoryDetail, EditPhoto, Gallery, HiddenCollection, PhotoDetail, RecycleBin, Screen

### Community 14 - "AiResult.kt"
Cohesion: 0.29
Nodes (6): AiResult, Failure, MissingApiKey, NetworkError, QuotaExceeded, Success

## Knowledge Gaps
- **50 isolated node(s):** `AiResult`, `Success`, `MissingApiKey`, `NetworkError`, `QuotaExceeded` (+45 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **10 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.