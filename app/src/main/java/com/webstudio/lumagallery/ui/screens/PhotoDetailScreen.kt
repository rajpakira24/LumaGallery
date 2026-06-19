package com.webstudio.lumagallery.ui.screens

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.webstudio.lumagallery.data.FolderGroup
import com.webstudio.lumagallery.data.Photo
import com.webstudio.lumagallery.ui.viewmodel.CaptionState
import kotlinx.coroutines.delay
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.zoomable

private const val TAG = "PhotoDetailScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoDetailScreen(
    photo: Photo,
    allPhotos: List<Photo>,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    onToggleFavorite: (Long) -> Unit = {},
    onToggleHidden: (Long) -> Unit = {},
    onSetHiddenPassword: (String) -> Unit = {},
    onDelete: (Long) -> Unit = {},
    hasHiddenPassword: Boolean = false,
    onRenamePhoto: (Long, Uri, String) -> Unit = { _, _, _ -> },
    onCopyPhoto: (Photo, String) -> Unit = { _, _ -> },
    onMovePhoto: (Photo, String) -> Unit = { _, _ -> },
    onEditPhoto: (Long) -> Unit = {},
    folderGroups: List<FolderGroup> = emptyList(),
    pendingWriteIntent: PendingIntent? = null,
    onWriteIntentConsumed: () -> Unit = {},
    onWriteGranted: () -> Unit = {},
    onWriteDenied: () -> Unit = {},
    captionState: CaptionState = CaptionState.None,
    hasOpenRouterKey: Boolean = false,
    onLoadCaption: (Long) -> Unit = {},
    onGenerateCaption: (Photo) -> Unit = {}
) {
    val context = LocalContext.current

    val writePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) onWriteGranted() else onWriteDenied()
        onWriteIntentConsumed()
    }

    LaunchedEffect(pendingWriteIntent) {
        pendingWriteIntent?.let {
            onWriteIntentConsumed()
            writePermissionLauncher.launch(IntentSenderRequest.Builder(it.intentSender).build())
        }
    }
    // All state declarations first — required before any conditional logic
    var isUiVisible by remember { mutableStateOf(true) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showSetPasswordDialog by remember { mutableStateOf(false) }
    var pendingHidePhotoId by remember { mutableStateOf<Long?>(null) }
    var showDetailsSheet by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showFolderPickerForCopy by remember { mutableStateOf(false) }
    var showFolderPickerForMove by remember { mutableStateOf(false) }

    val initialPage = allPhotos.indexOfFirst { it.id == photo.id }.coerceAtLeast(0)
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { allPhotos.size.coerceAtLeast(1) }
    )

    val safePageIndex = pagerState.currentPage.coerceIn(0, maxOf(0, allPhotos.size - 1))
    val currentPhoto = allPhotos.getOrNull(safePageIndex)

    // Auto-hide UI after 3 seconds of visibility
    LaunchedEffect(isUiVisible) {
        if (isUiVisible) {
            delay(3000)
            isUiVisible = false
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        Log.d(TAG, "Viewing page ${pagerState.currentPage}")
    }

    // Navigate back when the current photo is removed (hidden/deleted).
    // Track whether we've seen the photo first to avoid spurious back-nav on initial load.
    var hasSeenCurrentPhoto by remember { mutableStateOf(false) }
    LaunchedEffect(allPhotos.size) {
        if (allPhotos.any { it.id == photo.id }) {
            hasSeenCurrentPhoto = true
        } else if (hasSeenCurrentPhoto) {
            onNavigateBack()
        }
    }

    // Set password dialog — shown before first hide
    if (showSetPasswordDialog && pendingHidePhotoId != null) {
        SetPasswordDialog(
            onConfirm = { password ->
                onSetHiddenPassword(password)
                pendingHidePhotoId?.let { onToggleHidden(it) }
                pendingHidePhotoId = null
                showSetPasswordDialog = false
            },
            onDismiss = {
                pendingHidePhotoId = null
                showSetPasswordDialog = false
            }
        )
    }

    if (showDeleteDialog && currentPhoto != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Move to Recycle Bin") },
            text = { Text("This photo will be moved to the recycle bin for 30 days before being permanently deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(currentPhoto.id)
                    showDeleteDialog = false
                    onNavigateBack()
                }) {
                    Text("Move to Recycle Bin", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showRenameDialog && currentPhoto != null) {
        var newName by remember(currentPhoto.displayName) { mutableStateOf(currentPhoto.displayName) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("File name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotBlank() && newName.trim() != currentPhoto.displayName) {
                        onRenamePhoto(currentPhoto.id, currentPhoto.uri, newName.trim())
                    }
                    showRenameDialog = false
                }) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
            }
        )
    }

    val nonSpecialFolders = remember(folderGroups) { folderGroups.filter { !it.isSpecialFolder } }

    if (showFolderPickerForCopy && currentPhoto != null) {
        AlertDialog(
            onDismissRequest = { showFolderPickerForCopy = false },
            title = { Text("Copy to folder") },
            text = {
                LazyColumn {
                    items(nonSpecialFolders) { folder ->
                        ListItem(
                            headlineContent = { Text(folder.folderName) },
                            supportingContent = {
                                Text(folder.folderPath, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            },
                            modifier = Modifier.clickable {
                                onCopyPhoto(currentPhoto, folder.folderPath)
                                showFolderPickerForCopy = false
                            }
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showFolderPickerForCopy = false }) { Text("Cancel") }
            }
        )
    }

    if (showFolderPickerForMove && currentPhoto != null) {
        AlertDialog(
            onDismissRequest = { showFolderPickerForMove = false },
            title = { Text("Move to folder") },
            text = {
                LazyColumn {
                    items(nonSpecialFolders) { folder ->
                        ListItem(
                            headlineContent = { Text(folder.folderName) },
                            supportingContent = {
                                Text(folder.folderPath, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            },
                            modifier = Modifier.clickable {
                                onMovePhoto(currentPhoto, folder.folderPath)
                                showFolderPickerForMove = false
                            }
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showFolderPickerForMove = false }) { Text("Cancel") }
            }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Show nothing until navigation fires when photo is gone
        if (currentPhoto == null) return@Box

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val pagePhoto = allPhotos.getOrNull(page) ?: return@HorizontalPager

            if (pagePhoto.isVideo) {
                VideoPlayer(
                    uri = pagePhoto.uri.toString(),
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                val zoomState = rememberZoomableState()
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(pagePhoto.uri)
                        .crossfade(true)
                        .memoryCacheKey(pagePhoto.id.toString())
                        .diskCacheKey(pagePhoto.id.toString())
                        .build(),
                    contentDescription = pagePhoto.displayName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        // onClick on zoomable so tap is not consumed by zoom gesture handling
                        .zoomable(
                            state = zoomState,
                            onClick = { isUiVisible = !isUiVisible }
                        )
                )
            }
        }

        // Top bar
        AnimatedVisibility(
            visible = isUiVisible,
            enter = fadeIn(tween(300)) + slideInVertically(initialOffsetY = { -it }, animationSpec = tween(300)),
            exit = fadeOut(tween(300)) + slideOutVertically(targetOffsetY = { -it }, animationSpec = tween(300))
        ) {
            TopAppBar(
                title = {
                    Text(
                        currentPhoto.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black.copy(alpha = 0.6f)),
                modifier = Modifier.statusBarsPadding()
            )
        }

        // Bottom action bar
        AnimatedVisibility(
            visible = isUiVisible,
            enter = fadeIn(tween(300)) + slideInVertically(initialOffsetY = { it }, animationSpec = tween(300)),
            exit = fadeOut(tween(300)) + slideOutVertically(targetOffsetY = { it }, animationSpec = tween(300)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ActionButton(
                        icon = Icons.Default.Share,
                        label = "Share",
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = if (currentPhoto.isVideo) "video/*" else "image/*"
                                putExtra(Intent.EXTRA_STREAM, currentPhoto.uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share via"))
                        }
                    )
                    AnimatedContent(
                        targetState = currentPhoto.isFavorite,
                        transitionSpec = {
                            (scaleIn(tween(180)) + fadeIn(tween(180))) togetherWith
                                    (scaleOut(tween(140)) + fadeOut(tween(140)))
                        },
                        label = "favoriteSwap"
                    ) { fav ->
                        ActionButton(
                            icon = if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            label = if (fav) "Unfavorite" else "Favorite",
                            onClick = { onToggleFavorite(currentPhoto.id) },
                            tint = if (fav) Color.Red else Color.White
                        )
                    }
                    ActionButton(
                        icon = if (currentPhoto.isHidden) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        label = if (currentPhoto.isHidden) "Unhide" else "Hide",
                        onClick = {
                            if (currentPhoto.isHidden || hasHiddenPassword) {
                                onToggleHidden(currentPhoto.id)
                            } else {
                                pendingHidePhotoId = currentPhoto.id
                                showSetPasswordDialog = true
                            }
                        }
                    )
                    ActionButton(
                        icon = Icons.Default.Delete,
                        label = "Delete",
                        onClick = { showDeleteDialog = true }
                    )
                    Box {
                        ActionButton(
                            icon = Icons.Default.MoreVert,
                            label = "More",
                            onClick = { showMoreMenu = true }
                        )
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Details") },
                                onClick = { showMoreMenu = false; showDetailsSheet = true },
                                leadingIcon = { Icon(Icons.Default.Info, null) }
                            )
                            if (!currentPhoto.isVideo) {
                                DropdownMenuItem(
                                    text = { Text("Edit") },
                                    onClick = {
                                        showMoreMenu = false
                                        onEditPhoto(currentPhoto.id)
                                    },
                                    leadingIcon = { Icon(Icons.Default.Tune, null) }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Rename") },
                                onClick = { showMoreMenu = false; showRenameDialog = true },
                                leadingIcon = { Icon(Icons.Default.Edit, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Set as") },
                                onClick = {
                                    showMoreMenu = false
                                    currentPhoto?.let { launchSetAs(context, it.uri, it.mimeType) }
                                },
                                leadingIcon = { Icon(Icons.Default.Wallpaper, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Copy to") },
                                onClick = { showMoreMenu = false; showFolderPickerForCopy = true },
                                leadingIcon = { Icon(Icons.Default.FileCopy, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Move to") },
                                onClick = { showMoreMenu = false; showFolderPickerForMove = true },
                                leadingIcon = { Icon(Icons.Default.DriveFileMove, null) }
                            )
                        }
                    }
                }
            }
        }

        // Page indicator
        if (allPhotos.size > 1) {
            AnimatedVisibility(
                visible = isUiVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 100.dp)
            ) {
                Card(
                    shape = CircleShape,
                    colors = CardDefaults.cardColors(
                        containerColor = Color.Black.copy(alpha = 0.55f),
                        contentColor = Color.White
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Text(
                        text = "${pagerState.currentPage + 1} / ${allPhotos.size}",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }

    LaunchedEffect(showDetailsSheet, currentPhoto?.id) {
        if (showDetailsSheet && currentPhoto != null) onLoadCaption(currentPhoto.id)
    }

    if (showDetailsSheet && currentPhoto != null) {
        ModalBottomSheet(
            onDismissRequest = { showDetailsSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "Details",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
                DetailRow("File name", currentPhoto.displayName)
                DetailRow("Date", formatDate(currentPhoto.dateTaken))
                DetailRow("Size", formatFileSize(currentPhoto.size))
                if (!currentPhoto.isVideo) {
                    DetailRow("Dimensions", "${currentPhoto.width} × ${currentPhoto.height}")
                }
                if (currentPhoto.isVideo && currentPhoto.duration > 0) {
                    DetailRow("Duration", formatDuration(currentPhoto.duration))
                }
                DetailRow("Path", currentPhoto.folderPath)
                DetailRow("Type", currentPhoto.mimeType)
                if (hasOpenRouterKey || captionState is CaptionState.Loaded) {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "AI Description",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                        if (captionState is CaptionState.Loaded) {
                            TextButton(onClick = { onGenerateCaption(currentPhoto) }) {
                                Text("Regenerate", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    when (captionState) {
                        CaptionState.None -> {
                            OutlinedButton(
                                onClick = { onGenerateCaption(currentPhoto) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Describe with AI")
                            }
                        }
                        CaptionState.Loading -> {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                        is CaptionState.Loaded -> {
                            Text(
                                captionState.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        is CaptionState.Error -> {
                            Text(
                                captionState.msg,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            TextButton(onClick = { onGenerateCaption(currentPhoto) }) {
                                Text("Retry")
                            }
                        }
                    }
                }
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun VideoPlayer(uri: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val exoPlayer = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            try {
                setMediaItem(MediaItem.fromUri(uri))
                prepare()
                playWhenReady = true
                repeatMode = Player.REPEAT_MODE_ONE
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing video player", e)
            }
        }
    }
    DisposableEffect(uri) {
        onDispose { exoPlayer.release() }
    }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = true
                controllerShowTimeoutMs = 3000
                controllerHideOnTouch = true
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        modifier = modifier
    )
}

@Composable
private fun SetPasswordDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Hidden Collection Password") },
        text = {
            Column {
                Text("Create a password to protect your hidden photos.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; error = "" },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null
                            )
                        }
                    }
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it; error = "" },
                    label = { Text("Confirm Password") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
                if (error.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    password.length < 4 -> error = "Password must be at least 4 characters"
                    password != confirmPassword -> error = "Passwords do not match"
                    else -> onConfirm(password)
                }
            }) { Text("Set & Hide") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.White
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.clickable { onClick() }
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(4.dp))
        Text(text = label, color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            value.ifEmpty { "—" },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.6f),
            textAlign = TextAlign.End
        )
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1_024 -> "%.1f KB".format(bytes / 1_024.0)
    else -> "$bytes B"
}

private fun formatDate(timestampMs: Long): String =
    if (timestampMs > 0)
        java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(timestampMs))
    else "Unknown"

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

private fun launchSetAs(context: Context, uri: Uri, mimeType: String) {
    val intent = Intent(Intent.ACTION_ATTACH_DATA).apply {
        setDataAndType(uri, mimeType)
        putExtra("mimeType", mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Set as"))
}
