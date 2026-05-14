package com.webstudio.lumagallery.ui.screens

import android.app.Activity
import android.app.PendingIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.webstudio.lumagallery.data.RecycleBinItem
import com.webstudio.lumagallery.ui.components.LiquidGlassTokens
import com.webstudio.lumagallery.ui.components.SelectionOverlay
import com.webstudio.lumagallery.ui.components.ShimmerPlaceholder
import com.webstudio.lumagallery.ui.components.liquidGlass
import com.webstudio.lumagallery.ui.components.pressScale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RecycleBinScreen(
    items: List<RecycleBinItem>,
    onNavigateBack: () -> Unit,
    onRestore: (RecycleBinItem) -> Unit,
    onPermanentDelete: (RecycleBinItem) -> Unit,
    onBulkRestore: (List<RecycleBinItem>) -> Unit,
    onBulkDelete: (List<RecycleBinItem>) -> Unit,
    modifier: Modifier = Modifier,
    pendingDeleteIntent: PendingIntent? = null,
    onDeleteIntentConsumed: () -> Unit = {},
    onDeleteGranted: () -> Unit = {}
) {
    var selectedItems by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<RecycleBinItem?>(null) }
    var showBulkDeleteDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var itemToRestore by remember { mutableStateOf<RecycleBinItem?>(null) }
    var showBulkRestoreDialog by remember { mutableStateOf(false) }
    val isSelectionMode = selectedItems.isNotEmpty()

    val deletePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) onDeleteGranted()
        onDeleteIntentConsumed()
    }

    LaunchedEffect(pendingDeleteIntent) {
        pendingDeleteIntent?.let {
            onDeleteIntentConsumed()
            deletePermissionLauncher.launch(IntentSenderRequest.Builder(it.intentSender).build())
        }
    }

    if (showRestoreDialog && itemToRestore != null) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            title = { Text("Restore Photo") },
            text = { Text("Restore \"${itemToRestore!!.photo.displayName}\" to your gallery?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        itemToRestore?.let { onRestore(it) }
                        showRestoreDialog = false
                        itemToRestore = null
                    }
                ) { Text("Restore") }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showBulkRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showBulkRestoreDialog = false },
            title = { Text("Restore ${selectedItems.size} Items") },
            text = { Text("Restore ${selectedItems.size} items to your gallery?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val itemsToRestore = items.filter { it.photo.id in selectedItems }
                        onBulkRestore(itemsToRestore)
                        selectedItems = emptySet()
                        showBulkRestoreDialog = false
                    }
                ) { Text("Restore") }
            },
            dismissButton = {
                TextButton(onClick = { showBulkRestoreDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showDeleteDialog && itemToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Permanently Delete") },
            text = { Text("Are you sure you want to permanently delete this photo? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        itemToDelete?.let { onPermanentDelete(it) }
                        showDeleteDialog = false
                        itemToDelete = null
                    }
                ) {
                    Text("Delete Forever", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showBulkDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showBulkDeleteDialog = false },
            title = { Text("Permanently Delete ${selectedItems.size} Items") },
            text = { Text("Are you sure you want to permanently delete ${selectedItems.size} items? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val itemsToDelete = items.filter { it.photo.id in selectedItems }
                        onBulkDelete(itemsToDelete)
                        selectedItems = emptySet()
                        showBulkDeleteDialog = false
                    }
                ) {
                    Text("Delete Forever", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        state = rememberTopAppBarState()
    )

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        topBar = {
            LargeTopAppBar(
                title = {
                    if (isSelectionMode) {
                        Text("${selectedItems.size} selected", fontWeight = FontWeight.Bold)
                    } else {
                        Column {
                            Text(
                                "Recently Deleted",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.headlineMedium
                            )
                            if (items.isNotEmpty()) {
                                Text(
                                    "${items.size} items",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isSelectionMode) {
                            selectedItems = emptySet()
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(
                            if (isSelectionMode) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            if (isSelectionMode) "Clear selection" else "Back"
                        )
                    }
                },
                actions = {
                    if (items.isNotEmpty() && !isSelectionMode) {
                        TextButton(
                            onClick = {
                                selectedItems = items.map { it.photo.id }.toSet()
                            }
                        ) {
                            Text("Select All")
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.68f),
                    scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)
                )
            )
        },
        bottomBar = {
            if (isSelectionMode) {
                Surface(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .liquidGlass(shape = LiquidGlassTokens.PanelShape, alpha = 0.72f),
                    color = Color.Transparent,
                    shape = LiquidGlassTokens.PanelShape,
                    tonalElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        FilledTonalButton(
                            onClick = { showBulkRestoreDialog = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.Restore,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Restore")
                        }

                        Spacer(Modifier.width(12.dp))

                        Button(
                            onClick = { showBulkDeleteDialog = true },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) {
                            Icon(
                                Icons.Default.DeleteForever,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Delete")
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (items.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Recycle bin is empty",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Pictures and videos deleted from Gallery stay in the Recycle Bin for 30 days before being permanently deleted.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            Column(modifier = Modifier.padding(padding)) {
                Surface(
                    color = Color.Transparent,
                    tonalElevation = 2.dp,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .liquidGlass(shape = RoundedCornerShape(16.dp), alpha = 0.7f, elevation = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Items will be permanently deleted after 30 days",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(110.dp),
                    modifier = modifier.fillMaxSize(),
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(
                        items = items,
                        key = { it.photo.id }
                    ) { item ->
                        RecycleBinItemCard(
                            modifier = Modifier.animateItem(
                                fadeInSpec = tween(220),
                                fadeOutSpec = tween(160),
                                placementSpec = tween(220)
                            ),
                            item = item,
                            isSelected = item.photo.id in selectedItems,
                            onSelect = {
                                selectedItems = if (item.photo.id in selectedItems) {
                                    selectedItems - item.photo.id
                                } else {
                                    selectedItems + item.photo.id
                                }
                            },
                            onRestore = {
                                itemToRestore = item
                                showRestoreDialog = true
                            },
                            onDelete = {
                                itemToDelete = item
                                showDeleteDialog = true
                            },
                            isSelectionMode = isSelectionMode
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecycleBinItemCard(
    item: RecycleBinItem,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    isSelectionMode: Boolean,
    modifier: Modifier = Modifier
) {
    val daysRemaining = item.daysRemaining
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(10.dp)

    val badgeBg by animateColorAsState(
        targetValue = when {
            daysRemaining <= 3 -> MaterialTheme.colorScheme.errorContainer
            daysRemaining <= 10 -> MaterialTheme.colorScheme.tertiaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerHighest
        },
        animationSpec = tween(220),
        label = "badgeBg"
    )
    val badgeFg by animateColorAsState(
        targetValue = when {
            daysRemaining <= 3 -> MaterialTheme.colorScheme.onErrorContainer
            daysRemaining <= 10 -> MaterialTheme.colorScheme.onTertiaryContainer
            else -> MaterialTheme.colorScheme.onSurface
        },
        animationSpec = tween(220),
        label = "badgeFg"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .liquidGlass(shape = shape, alpha = 0.36f, elevation = 4.dp)
            .pressScale(interactionSource)
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onSelect
        ),
        shape = shape,
        color = Color.Transparent,
        tonalElevation = 2.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(item.photo.uri)
                    .apply {
                        if (item.photo.isVideo) videoFrameMillis(1000)
                    }
                    .crossfade(300)
                    .size(300)
                    .build(),
                contentDescription = item.photo.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = { ShimmerPlaceholder(modifier = Modifier.fillMaxSize(), shape = shape) },
                error = {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.BrokenImage,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.12f))
            )

            Surface(
                color = badgeBg,
                contentColor = badgeFg,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
            ) {
                Text(
                    text = "$daysRemaining d",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }

            if (item.photo.isVideo) {
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

            if (!isSelectionMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    IconButton(
                        onClick = onRestore,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Restore,
                            contentDescription = "Restore",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.DeleteForever,
                            contentDescription = "Delete Forever",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            SelectionOverlay(selected = isSelected, shape = shape)
        }
    }
}
