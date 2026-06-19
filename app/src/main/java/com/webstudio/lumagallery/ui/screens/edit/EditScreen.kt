package com.webstudio.lumagallery.ui.screens.edit

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.PhotoFilter
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.webstudio.lumagallery.ads.RewardedAdGate
import com.webstudio.lumagallery.ui.screens.edit.panels.AiPanel
import com.webstudio.lumagallery.ui.screens.edit.panels.ColorPanel
import com.webstudio.lumagallery.ui.screens.edit.panels.CropPanel
import com.webstudio.lumagallery.ui.screens.edit.panels.DrawPanel
import com.webstudio.lumagallery.ui.screens.edit.panels.FilterPanel
import com.webstudio.lumagallery.ui.viewmodel.EditViewModel

private enum class EditTool(val label: String, val icon: ImageVector) {
    Crop("Crop", Icons.Filled.Crop),
    Color("Color", Icons.Filled.Tune),
    Filter("Filter", Icons.Outlined.PhotoFilter),
    Draw("Draw", Icons.Filled.Brush),
    Ai("AI", Icons.Filled.AutoFixHigh)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(
    photoUri: Uri,
    onNavigateBack: () -> Unit,
    onSaved: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: EditViewModel = viewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    fun runCloudEditAfterReward(action: () -> Unit) {
        val activity = context.findActivity()
        if (activity == null) {
            Toast.makeText(context, "Rewarded ad is not available right now. Please try again later.", Toast.LENGTH_SHORT).show()
            return
        }
        RewardedAdGate.showForReward(
            activity = activity,
            onRewarded = action,
            onUnavailable = {
                Toast.makeText(context, "Rewarded ad is not available right now. Please try again later.", Toast.LENGTH_SHORT).show()
            }
        )
    }

    LaunchedEffect(photoUri) { viewModel.loadPhoto(photoUri) }

    LaunchedEffect(state.savedUri) {
        state.savedUri?.let {
            Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
            viewModel.consumeSavedUri()
            onSaved(it)
        }
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeError()
        }
    }

    LaunchedEffect(state.infoMessage) {
        state.infoMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeInfo()
        }
    }

    var showStickerShareDialog by remember { mutableStateOf(false) }
    var pendingStickerUri by remember { mutableStateOf<Uri?>(null) }
    LaunchedEffect(state.stickerUri) {
        state.stickerUri?.let { uri ->
            pendingStickerUri = uri
            showStickerShareDialog = true
            viewModel.consumeStickerUri()
        }
    }

    if (showStickerShareDialog && pendingStickerUri != null) {
        AlertDialog(
            onDismissRequest = { showStickerShareDialog = false },
            title = { Text("Sticker saved!") },
            text = { Text("Add this sticker to WhatsApp?") },
            confirmButton = {
                TextButton(onClick = {
                    shareToWhatsApp(context, pendingStickerUri!!)
                    showStickerShareDialog = false
                }) { Text("Add to WhatsApp") }
            },
            dismissButton = {
                TextButton(onClick = { showStickerShareDialog = false }) { Text("Done") }
            }
        )
    }

    var selectedTool by remember { mutableStateOf<EditTool?>(null) }
    var showExitDialog by remember { mutableStateOf(false) }

    val handleBack: () -> Unit = {
        if (state.isModified) showExitDialog = true else onNavigateBack()
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Discard changes?") },
            text = { Text("You have unsaved edits. Discard them and exit?") },
            confirmButton = {
                TextButton(onClick = { showExitDialog = false; onNavigateBack() }) {
                    Text("Discard", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Edit") },
                navigationIcon = {
                    IconButton(onClick = handleBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::undo, enabled = state.canUndo && !state.isBusy) {
                        Icon(Icons.AutoMirrored.Filled.Undo, "Undo")
                    }
                    IconButton(onClick = viewModel::redo, enabled = state.canRedo && !state.isBusy) {
                        Icon(Icons.AutoMirrored.Filled.Redo, "Redo")
                    }
                    IconButton(onClick = viewModel::save, enabled = state.isModified && !state.isBusy) {
                        Icon(Icons.Filled.Save, "Save")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                val bmp: Bitmap? = state.displayBitmap
                val editBitmap = state.bitmap
                if (editBitmap != null && !editBitmap.isRecycled && selectedTool == EditTool.Draw) {
                    DrawPanel(
                        baseBitmap = editBitmap,
                        onApply = { overlay ->
                            viewModel.replaceBitmap(overlay)
                            selectedTool = null
                        },
                        onCancel = { selectedTool = null },
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (editBitmap != null && !editBitmap.isRecycled && selectedTool == EditTool.Ai) {
                    AiPanel(
                        aiEnabled = state.aiEnabled,
                        currentBitmap = editBitmap,
                        onRemoveBackground = { viewModel.aiRemoveBackground() },
                        onUpscale = { runCloudEditAfterReward { viewModel.aiUpscale() } },
                        onPromptEdit = { prompt ->
                            runCloudEditAfterReward { viewModel.aiPromptEdit(prompt) }
                        },
                        onSticker = { viewModel.createOnDeviceSticker() },
                        onCloudSticker = {
                            runCloudEditAfterReward {
                                viewModel.aiPromptEdit(
                                    "Create a clean sticker cutout from this image. Remove the background completely, keep the main subject sharp and natural, and return only the subject on a transparent or plain clean background."
                                )
                            }
                        },
                        onEraseObject = { mask, prompt ->
                            runCloudEditAfterReward { viewModel.aiEraseObject(mask, prompt) }
                        },
                        onRotate90 = { viewModel.rotate(90f) },
                        onFlipHorizontal = { viewModel.flip(true) },
                        onClose = { selectedTool = null },
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (bmp != null && !bmp.isRecycled) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Editing preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (state.isLoading) {
                    CircularProgressIndicator(color = Color.White)
                }

                state.processingLabel?.let { label ->
                    Surface(
                        color = Color.Black.copy(alpha = 0.55f),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(label, color = Color.White)
                        }
                    }
                }
            }

            Surface(
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    val bmp = state.bitmap
                    when (selectedTool) {
                        EditTool.Crop -> if (bmp != null) CropPanel(
                            bitmap = bmp,
                            onCropped = { viewModel.replaceBitmap(it); selectedTool = null },
                            onCancel = { selectedTool = null }
                        )
                        EditTool.Color -> if (bmp != null) ColorPanel(
                            onPreviewChange = { b, c, s, w -> viewModel.setColorPreview(b, c, s, w) },
                            onApply = { viewModel.commitColorPreview(); selectedTool = null },
                            onCancel = { viewModel.discardColorPreview(); selectedTool = null }
                        )
                        EditTool.Filter -> if (bmp != null) FilterPanel(
                            bitmap = bmp,
                            onPick = { preset ->
                                viewModel.applyFilter(preset)
                                selectedTool = null
                            },
                            onClose = { selectedTool = null }
                        )
                        EditTool.Draw -> Spacer(Modifier.height(8.dp))
                        EditTool.Ai -> Spacer(Modifier.height(8.dp))
                        null -> Spacer(Modifier.height(8.dp))
                    }

                    NavigationBar(modifier = Modifier.fillMaxWidth()) {
                        EditTool.values().forEach { tool ->
                            NavigationBarItem(
                                selected = selectedTool == tool,
                                onClick = {
                                    if (selectedTool == EditTool.Color && tool != EditTool.Color) {
                                        viewModel.discardColorPreview()
                                    }
                                    selectedTool = if (selectedTool == tool) null else tool
                                },
                                icon = { Icon(tool.icon, tool.label) },
                                label = { Text(tool.label) },
                                enabled = !state.isBusy
                            )
                        }
                    }
                }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun shareToWhatsApp(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        setPackage("com.whatsapp")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(intent)
    } catch (e: android.content.ActivityNotFoundException) {
        val fallback = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(fallback, "Share sticker via"))
    }
}
