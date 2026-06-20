package com.webstudio.lumagallery.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import com.unity3d.services.banners.BannerErrorInfo
import com.unity3d.services.banners.BannerView
import com.unity3d.services.banners.UnityBannerSize
import com.webstudio.lumagallery.BuildConfig
import com.webstudio.lumagallery.ads.UnityAdState
import com.webstudio.lumagallery.data.DateGroup
import com.webstudio.lumagallery.data.FolderGroup
import com.webstudio.lumagallery.data.Photo
import com.webstudio.lumagallery.ui.components.LiquidGlassTokens
import com.webstudio.lumagallery.ui.components.SelectionOverlay
import com.webstudio.lumagallery.ui.components.ShimmerPlaceholder
import com.webstudio.lumagallery.ui.components.liquidGlass
import com.webstudio.lumagallery.ui.components.pressScale
import com.webstudio.lumagallery.ui.util.DragSelectState
import com.webstudio.lumagallery.ui.viewmodel.GalleryUiState
import com.webstudio.lumagallery.ui.viewmodel.ViewMode

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun GalleryScreen(
    uiState: GalleryUiState,
    modifier: Modifier = Modifier,
    onFolderClick: (String) -> Unit = {},
    onPhotoClick: (Long) -> Unit = {},
    onSearchQueryChange: (String) -> Unit = {},
    onViewModeChange: (ViewMode) -> Unit = {},
    onRecentlyDeletedClick: () -> Unit = {},
    onHiddenCollectionClick: () -> Unit = {},
    onStickerPacksClick: () -> Unit = {},
    onBulkMoveToRecycleBin: (Set<Long>) -> Unit = {},
    onBulkToggleHidden: (Set<Long>) -> Unit = {},
    onRefresh: () -> Unit = {},
    aiEnabled: Boolean = false,
    onGenerateImage: (String) -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var showSearchBar by remember { mutableStateOf(false) }
    var selectedPhotoIds by remember { mutableStateOf(emptySet<Long>()) }
    var isRefreshing by remember { mutableStateOf(false) }
    var showGenerateDialog by remember { mutableStateOf(false) }
    val isSelectionMode = selectedPhotoIds.isNotEmpty()
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        state = rememberTopAppBarState()
    )

    LaunchedEffect(uiState) {
        if (uiState is GalleryUiState.Success && isRefreshing) {
            isRefreshing = false
        }
    }

    BackHandler(enabled = isSelectionMode) {
        selectedPhotoIds = emptySet()
    }

    val photoCount = (uiState as? GalleryUiState.Success)?.allPhotos?.size ?: 0

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        bottomBar = {
            if (isSelectionMode) {
                SelectionActionBar(
                    count = selectedPhotoIds.size,
                    onDelete = {
                        onBulkMoveToRecycleBin(selectedPhotoIds)
                        selectedPhotoIds = emptySet()
                    },
                    onHide = {
                        onBulkToggleHidden(selectedPhotoIds)
                        selectedPhotoIds = emptySet()
                    },
                    onCancel = { selectedPhotoIds = emptySet() }
                )
            } else {
                UnityBannerAd()
            }
        },
        topBar = {
            Column {
                LargeTopAppBar(
                    title = {
                        if (showSearchBar) {
                            TextField(
                                value = searchQuery,
                                onValueChange = {
                                    searchQuery = it
                                    onSearchQueryChange(it)
                                },
                                placeholder = { Text("Search photos...") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                leadingIcon = { Icon(Icons.Default.Search, null) },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                )
                            )
                        } else {
                            Column {
                                    Text(
                                        "Luma",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.headlineMedium
                                    )
                                if (photoCount > 0) {
                                    Text(
                                        "$photoCount items",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        if (showSearchBar) {
                            IconButton(onClick = {
                                showSearchBar = false
                                searchQuery = ""
                                onSearchQueryChange("")
                            }) {
                                Icon(Icons.Default.Close, "Close search")
                            }
                        } else {
                            IconButton(onClick = { showSearchBar = true }) {
                                Icon(Icons.Default.Search, "Search")
                            }
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.largeTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.68f),
                        scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)
                    )
                )

                if (uiState is GalleryUiState.Success && !showSearchBar) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .liquidGlass(
                                shape = LiquidGlassTokens.PillShape,
                                alpha = 0.62f,
                                elevation = 4.dp
                            )
                    ) {
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            SegmentedButton(
                                selected = uiState.viewMode == ViewMode.PHOTOS,
                                onClick = { onViewModeChange(ViewMode.PHOTOS) },
                                shape = SegmentedButtonDefaults.itemShape(0, 2),
                                icon = {
                                    Icon(
                                        Icons.Default.PhotoLibrary,
                                        contentDescription = null,
                                        modifier = Modifier.size(SegmentedButtonDefaults.IconSize)
                                    )
                                },
                                label = { Text("Photos") }
                            )
                            SegmentedButton(
                                selected = uiState.viewMode == ViewMode.COLLECTIONS,
                                onClick = { onViewModeChange(ViewMode.COLLECTIONS) },
                                shape = SegmentedButtonDefaults.itemShape(1, 2),
                                icon = {
                                    Icon(
                                        Icons.Default.Folder,
                                        contentDescription = null,
                                        modifier = Modifier.size(SegmentedButtonDefaults.IconSize)
                                    )
                                },
                                label = { Text("Collections") }
                            )
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = isSelectionMode,
                enter = fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 2 },
                exit = fadeOut(tween(160)) + slideOutVertically(tween(160)) { it / 2 }
            ) {
                ExtendedFloatingActionButton(
                    onClick = {
                        val uris = (uiState as? GalleryUiState.Success)
                            ?.allPhotos
                            ?.filter { it.id in selectedPhotoIds }
                            ?.map { it.uri }
                            ?: emptyList()
                        if (uris.isNotEmpty()) {
                            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                                type = "*/*"
                                putParcelableArrayListExtra(
                                    Intent.EXTRA_STREAM,
                                    ArrayList(uris)
                                )
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share"))
                            selectedPhotoIds = emptySet()
                        }
                    },
                    icon = { Icon(Icons.Default.Share, null) },
                    text = { Text("Share ${selectedPhotoIds.size}") },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            if (!isSelectionMode && aiEnabled) {
                FloatingActionButton(
                    onClick = { showGenerateDialog = true },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = "Create with AI")
                }
            }
        }
    ) { padding ->
        when (uiState) {
            is GalleryUiState.Loading -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text("Loading photos...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            is GalleryUiState.Success -> {
                val displayedDateGroups = remember(uiState.searchQuery, uiState.filteredPhotos, uiState.dateGroups) {
                    if (uiState.searchQuery.isEmpty()) {
                        uiState.dateGroups
                    } else {
                        val filteredIds = uiState.filteredPhotos.map { it.id }.toHashSet()
                        uiState.dateGroups
                            .map { dg -> dg.copy(photos = dg.photos.filter { it.id in filteredIds }) }
                            .filter { it.photos.isNotEmpty() }
                    }
                }
                AnimatedContent(
                    targetState = uiState.viewMode,
                    transitionSpec = {
                        (fadeIn(tween(220)) togetherWith fadeOut(tween(180)))
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    label = "viewModeSwitch"
                ) { mode ->
                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = {
                            isRefreshing = true
                            onRefresh()
                        },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        when (mode) {
                            ViewMode.PHOTOS -> {
                                PhotosTabContent(
                                    dateGroups = displayedDateGroups,
                                    onPhotoClick = { id ->
                                        if (isSelectionMode) {
                                            selectedPhotoIds =
                                                if (id in selectedPhotoIds)
                                                    selectedPhotoIds - id
                                                else
                                                    selectedPhotoIds + id
                                        } else {
                                            onPhotoClick(id)
                                        }
                                    },
                                    selectedPhotoIds = selectedPhotoIds,
                                    onDragSelectionChange = { ids -> selectedPhotoIds = ids },
                                    modifier = modifier
                                )
                            }
                            ViewMode.COLLECTIONS -> {
                                CollectionsTabContent(
                                    folderGroups = uiState.folderGroups,
                                    recycleBinCount = uiState.recycleBinItems.size,
                                    hiddenCount = uiState.hiddenPhotos.size,
                                    onFolderClick = onFolderClick,
                                    onRecentlyDeletedClick = onRecentlyDeletedClick,
                                    onHiddenCollectionClick = onHiddenCollectionClick,
                                    onStickerPacksClick = onStickerPacksClick,
                                    modifier = modifier
                                )
                            }
                        }
                    }
                }
            }
            is GalleryUiState.Error -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            uiState.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
    }

    if (showGenerateDialog) {
        GenerateImageDialog(
            onGenerate = { prompt -> onGenerateImage(prompt) },
            onDismiss = { showGenerateDialog = false }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotosTabContent(
    dateGroups: List<DateGroup>,
    onPhotoClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    selectedPhotoIds: Set<Long> = emptySet(),
    onDragSelectionChange: (Set<Long>) -> Unit = {}
) {
    val dragSelectState = remember { DragSelectState() }
    val flatPhotos = remember(dateGroups) { dateGroups.flatMap { it.photos } }
    val groupOffsets = remember(dateGroups) {
        dateGroups.runningFold(0) { acc, group -> acc + group.photos.size }
    }
    var containerRootOffset by remember { mutableStateOf(Offset.Zero) }

    if (dateGroups.isEmpty()) {
        Box(
            modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Outlined.PhotoLibrary,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "No photos found",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Pull down to refresh",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        Box(
            modifier = modifier
                .fillMaxSize()
                .onGloballyPositioned { coords ->
                    val bounds = coords.boundsInRoot()
                    containerRootOffset = Offset(bounds.left, bounds.top)
                }
                .pointerInput(flatPhotos) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            val rx = offset.x + containerRootOffset.x
                            val ry = offset.y + containerRootOffset.y
                            val idx = dragSelectState.findIndexAt(rx, ry)
                            if (idx >= 0) {
                                dragSelectState.startDrag(idx)
                                onDragSelectionChange(setOf(flatPhotos[idx].id))
                            }
                        },
                        onDrag = { change, _ ->
                            val rx = change.position.x + containerRootOffset.x
                            val ry = change.position.y + containerRootOffset.y
                            val idx = dragSelectState.findIndexAt(rx, ry)
                            if (idx >= 0) {
                                dragSelectState.updateDrag(idx)
                                val range = dragSelectState.selectedRange()
                                if (!range.isEmpty()) {
                                    onDragSelectionChange(
                                        flatPhotos.slice(range).map { it.id }.toSet()
                                    )
                                }
                            }
                        },
                        onDragEnd = { dragSelectState.endDrag() },
                        onDragCancel = { dragSelectState.endDrag() }
                    )
                }
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                dateGroups.forEachIndexed { groupIdx, dateGroup ->
                    val groupStartIdx = groupOffsets[groupIdx]

                    stickyHeader(key = "header_${dateGroup.date}") {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            tonalElevation = 1.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = dateGroup.displayDate,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                            )
                        }
                    }

                    dateGroup.photos.chunked(3).forEachIndexed { rowIdx, rowPhotos ->
                        item(key = "row_${dateGroup.date}_$rowIdx") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                    .animateItem(
                                        fadeInSpec = tween(200),
                                        fadeOutSpec = tween(160),
                                        placementSpec = tween(220)
                                    ),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                rowPhotos.forEachIndexed { colIdx, photo ->
                                    val globalIdx = groupStartIdx + rowIdx * 3 + colIdx
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .onGloballyPositioned { coords ->
                                                dragSelectState.itemBounds[globalIdx] =
                                                    coords.boundsInRoot()
                                            }
                                    ) {
                                        PhotoGridItem(
                                            photo = photo,
                                            isSelected = photo.id in selectedPhotoIds,
                                            onClick = { onPhotoClick(photo.id) }
                                        )
                                    }
                                }
                                repeat(3 - rowPhotos.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    item(key = "spacer_${dateGroup.date}") { Spacer(Modifier.height(12.dp)) }
                }
            }
        }
    }
}

private enum class FolderFilter(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    ALL("All", Icons.Default.Apps),
    PHOTOS("Photos", Icons.Default.Image),
    VIDEOS("Videos", Icons.Default.Videocam),
    FAVORITES("Favorites", Icons.Default.Favorite)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun CollectionsTabContent(
    folderGroups: List<FolderGroup>,
    recycleBinCount: Int,
    hiddenCount: Int,
    onFolderClick: (String) -> Unit,
    onRecentlyDeletedClick: () -> Unit,
    onHiddenCollectionClick: () -> Unit,
    onStickerPacksClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var filter by remember { mutableStateOf(FolderFilter.ALL) }

    val filteredFolders = remember(folderGroups, filter) {
        when (filter) {
            FolderFilter.ALL -> folderGroups
            FolderFilter.PHOTOS -> folderGroups.filter { fg -> fg.photos.any { !it.isVideo } }
            FolderFilter.VIDEOS -> folderGroups.filter { fg -> fg.photos.any { it.isVideo } }
            FolderFilter.FAVORITES -> folderGroups.filter { fg -> fg.photos.any { it.isFavorite } }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "filter_chips") {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FolderFilter.values().forEach { f ->
                    FilterChip(
                        selected = filter == f,
                        onClick = { filter = f },
                        label = { Text(f.label) },
                        leadingIcon = {
                            Icon(
                                f.icon,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                            )
                        }
                    )
                }
            }
        }

        if (filteredFolders.isEmpty()) {
            item(key = "empty_folders") {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.FolderOff,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "No collections found",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            val chunkedFolders = filteredFolders.chunked(2)
            chunkedFolders.forEachIndexed { rowIdx, rowFolders ->
                item(key = "folder_row_${rowFolders.first().folderPath}_$rowIdx") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem(
                                fadeInSpec = tween(220),
                                fadeOutSpec = tween(160),
                                placementSpec = tween(220)
                            ),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        rowFolders.forEach { folder ->
                            Box(modifier = Modifier.weight(1f)) {
                                FolderCard(
                                    folderGroup = folder,
                                    onClick = { onFolderClick(folder.folderPath) }
                                )
                            }
                        }
                        if (rowFolders.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        item(key = "recently_deleted") {
            SpecialFolderCard(
                title = "Recently Deleted",
                count = recycleBinCount,
                icon = Icons.Default.Delete,
                onClick = onRecentlyDeletedClick
            )
        }

        item(key = "hidden_collection") {
            SpecialFolderCard(
                title = "Hidden Collection",
                count = hiddenCount,
                icon = Icons.Default.VisibilityOff,
                onClick = onHiddenCollectionClick
            )
        }

        item(key = "sticker_packs") {
            SpecialFolderCard(
                title = "Sticker Packs",
                count = 0,
                icon = Icons.AutoMirrored.Filled.StickyNote2,
                onClick = onStickerPacksClick,
                showCount = false
            )
        }
    }
}

@Composable
private fun FolderCard(
    folderGroup: FolderGroup,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .liquidGlass(shape = RoundedCornerShape(20.dp), alpha = 0.42f)
            .pressScale(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick
        ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
    ) {
        Box {
            PhotoGrid4(folderGroup.photos.take(4))

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.75f)
                            ),
                            startY = 100f
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (folderGroup.isSpecialFolder)
                            Icons.Default.Favorite else Icons.Default.Folder,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        folderGroup.folderName,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    "${folderGroup.count} items",
                    color = Color.White.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun SpecialFolderCard(
    title: String,
    count: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    showCount: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isDark = isSystemInDarkTheme()
    val shape = RoundedCornerShape(18.dp)
    val cardBrush = Brush.linearGradient(
        colors = if (isDark) {
            listOf(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.62f),
                MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.48f),
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.16f)
            )
        } else {
            listOf(
                Color.White.copy(alpha = 0.58f),
                MaterialTheme.colorScheme.surface.copy(alpha = 0.46f),
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
            )
        },
        start = Offset.Zero,
        end = Offset(620f, 160f)
    )
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.48f else 0.56f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(82.dp)
            .clip(shape)
            .background(cardBrush)
            .border(width = 1.dp, color = borderColor, shape = shape)
            .pressScale(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.74f))
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (showCount) "$count items" else "Manage WhatsApp stickers",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PhotoGridItem(
    photo: Photo,
    isSelected: Boolean = false,
    onClick: () -> Unit = {}
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(10.dp)
    Surface(
        modifier = Modifier
            .aspectRatio(1f)
            .liquidGlass(shape = shape, alpha = 0.36f, elevation = 4.dp)
            .pressScale(interactionSource)
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick
            ),
        shape = shape,
        color = Color.Transparent,
        tonalElevation = 2.dp
    ) {
        Box {
            val context = LocalContext.current
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(photo.uri)
                    .apply {
                        if (photo.isVideo) videoFrameMillis(1000)
                    }
                    .crossfade(300)
                    .memoryCacheKey(photo.id.toString())
                    .diskCacheKey(photo.id.toString())
                    .build(),
                contentDescription = photo.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    ShimmerPlaceholder(
                        modifier = Modifier.fillMaxSize(),
                        shape = shape
                    )
                },
                error = {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Icon(
                            Icons.Default.BrokenImage,
                            contentDescription = null,
                            modifier = Modifier
                                .size(32.dp)
                                .align(Alignment.Center),
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            )

            if (photo.isVideo) {
                Surface(
                    color = Color.Black.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Video",
                        tint = Color.White,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            if (photo.isFavorite) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "Favorite",
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .size(16.dp)
                )
            }

            SelectionOverlay(selected = isSelected, shape = shape)
        }
    }
}

@Composable
private fun PhotoGrid4(photos: List<Photo>) {
    val context = LocalContext.current
    when (photos.size) {
        0 -> Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        )
        1 -> {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(photos[0].uri)
                    .apply { if (photos[0].isVideo) videoFrameMillis(1000) }
                    .crossfade(300)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    ShimmerPlaceholder(modifier = Modifier.fillMaxSize())
                },
                error = {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.errorContainer)
                    )
                }
            )
        }
        else -> {
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f)) {
                    photos.getOrNull(0)?.let { photo ->
                        Box(
                            Modifier
                                .weight(1f)
                                .padding(1.dp)
                        ) {
                            FolderTileImage(photo)
                        }
                    }
                    photos.getOrNull(2)?.let { photo ->
                        Box(
                            Modifier
                                .weight(1f)
                                .padding(1.dp)
                        ) {
                            FolderTileImage(photo)
                        }
                    }
                }
                Column(Modifier.weight(1f)) {
                    photos.getOrNull(1)?.let { photo ->
                        Box(
                            Modifier
                                .weight(1f)
                                .padding(1.dp)
                        ) {
                            FolderTileImage(photo)
                        }
                    }
                    photos.getOrNull(3)?.let { photo ->
                        Box(
                            Modifier
                                .weight(1f)
                                .padding(1.dp)
                        ) {
                            FolderTileImage(photo)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderTileImage(photo: Photo) {
    val context = LocalContext.current
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(context)
            .data(photo.uri)
            .apply { if (photo.isVideo) videoFrameMillis(1000) }
            .crossfade(300)
            .build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
        loading = { ShimmerPlaceholder(modifier = Modifier.fillMaxSize()) }
    )
    if (photo.isVideo) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                Icons.Default.PlayArrow,
                null,
                Modifier.size(20.dp),
                tint = Color.White
            )
        }
    }
}

@Composable
private fun GenerateImageDialog(
    onGenerate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var prompt by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.AutoAwesome, null) },
        title = { Text("Create with AI") },
        text = {
            Column {
                Text(
                    "Describe an image and AI will generate it for you.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("Image description") },
                    placeholder = { Text("e.g. sunset over mountains, photorealistic") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onGenerate(prompt.trim()); onDismiss() },
                enabled = prompt.isNotBlank()
            ) { Text("Generate") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun SelectionActionBar(
    count: Int,
    onDelete: () -> Unit,
    onHide: () -> Unit,
    onCancel: () -> Unit
) {
    Surface(
        modifier = Modifier
            .navigationBarsPadding()
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .liquidGlass(shape = LiquidGlassTokens.PanelShape, alpha = 0.72f),
        color = Color.Transparent,
        tonalElevation = 8.dp,
        shadowElevation = 0.dp,
        shape = LiquidGlassTokens.PanelShape
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Close, contentDescription = "Cancel selection")
            }
            Text(
                text = "$count selected",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onHide) {
                Icon(Icons.Default.VisibilityOff, contentDescription = "Hide")
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun UnityBannerAd() {
    val context = LocalContext.current
    val initialized by UnityAdState.initialized.collectAsStateWithLifecycle()
    val placementId = BuildConfig.UNITY_BANNER_PLACEMENT_ID
    if (!initialized || placementId.isBlank()) return

    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        factory = { ctx ->
            val activity = ctx.findActivity()
            if (activity == null) {
                android.util.Log.d("UnityAds", "findActivity() returned null — banner skipped")
                android.view.View(ctx)
            } else {
                BannerView(activity, placementId, UnityBannerSize(320, 50)).apply {
                    listener = object : BannerView.IListener {
                        override fun onBannerLoaded(bannerAdView: BannerView) = Unit
                        override fun onBannerShown(bannerAdView: BannerView) = Unit
                        override fun onBannerFailedToLoad(bannerAdView: BannerView, errorInfo: BannerErrorInfo) {
                            android.util.Log.d("UnityAds", "Banner failed: ${errorInfo.errorCode} — ${errorInfo.errorMessage}")
                        }
                        override fun onBannerClick(bannerAdView: BannerView) = Unit
                        override fun onBannerLeftApplication(bannerAdView: BannerView) = Unit
                    }
                    load()
                }
            }
        },
        onRelease = { view -> (view as? BannerView)?.destroy() }
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
