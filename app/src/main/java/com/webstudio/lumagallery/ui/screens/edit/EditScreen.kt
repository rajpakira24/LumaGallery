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
    onOpenStickerPacks: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val viewModel: EditViewModel = viewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val stickerPacks by viewModel.stickerPacks.collectAsStateWithLifecycle()
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

    // Upscale is now an on-device (free) op but stays behind the rewarded-ad gate.
    // Unlike cloud ops, if no ad is ready we DO NOT block the user — we run the
    // upscale anyway (after a brief toast) since there is no per-use cost.
    fun runUpscaleAfterReward(action: () -> Unit) {
        val activity = context.findActivity()
        if (activity == null) {
            action()
            return
        }
        RewardedAdGate.showForReward(
            activity = activity,
            onRewarded = action,
            onUnavailable = {
                Toast.makeText(context, "Ad unavailable — running upscale anyway.", Toast.LENGTH_SHORT).show()
                action()
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

    // Sticker studio: opened when a cutout is produced (same place stickerUri is set).
    var showStickerStudio by remember { mutableStateOf(false) }
    var showPackPicker by remember { mutableStateOf(false) }
    LaunchedEffect(state.stickerUri) {
        state.stickerUri?.let {
            showStickerStudio = true
            viewModel.consumeStickerUri()
        }
    }

    // Confirmation snackbar (with "Open Sticker Packs" action) after adding to a pack.
    LaunchedEffect(state.stickerAddedMessage) {
        state.stickerAddedMessage?.let { msg ->
            viewModel.consumeStickerAddedMessage()
            showPackPicker = false
            showStickerStudio = false
            val result = snackbarHostState.showSnackbar(
                message = msg,
                actionLabel = "Open Sticker Packs",
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                onOpenStickerPacks()
            }
        }
    }

    if (showStickerStudio) {
        StickerStudioDialog(
            stickerBitmap = state.stickerBitmap,
            onAddOutline = { viewModel.applyStickerOutline() },
            onAddText = { viewModel.applyStickerText(it) },
            onAddToPack = {
                viewModel.refreshStickerPacks()
                showPackPicker = true
            },
            onDismiss = { showStickerStudio = false }
        )
    }

    if (showPackPicker) {
        PackPickerDialog(
            packs = stickerPacks,
            onPickPack = { id -> viewModel.addStickerToPack(id) },
            onCreatePack = { name -> viewModel.createPackAndAdd(name) },
            onDismiss = { showPackPicker = false }
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
                        onUpscale = { runUpscaleAfterReward { viewModel.aiUpscale() } },
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

@Composable
private fun StickerStudioDialog(
    stickerBitmap: Bitmap?,
    onAddOutline: () -> Unit,
    onAddText: (String) -> Unit,
    onAddToPack: () -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sticker studio") },
        text = {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    if (stickerBitmap != null && !stickerBitmap.isRecycled) {
                        Image(
                            bitmap = stickerBitmap.asImageBitmap(),
                            contentDescription = "Sticker preview",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onAddOutline,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Add white outline") }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    label = { Text("Caption text") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = { onAddText(text); text = "" },
                    enabled = text.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Add text") }
            }
        },
        confirmButton = {
            TextButton(onClick = onAddToPack, enabled = stickerBitmap != null) {
                Text("Add to pack")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

@Composable
private fun PackPickerDialog(
    packs: List<com.webstudio.lumagallery.data.sticker.StickerPack>,
    onPickPack: (String) -> Unit,
    onCreatePack: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (creating) "New pack" else "Add to pack") },
        text = {
            if (creating) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    label = { Text("Pack name") },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Column {
                    if (packs.isEmpty()) {
                        Text(
                            "No packs yet. Create one below.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        packs.forEach { pack ->
                            TextButton(
                                onClick = { onPickPack(pack.identifier) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "${pack.name}  (${pack.stickers.size})",
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { creating = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("New pack") }
                }
            }
        },
        confirmButton = {
            if (creating) {
                TextButton(
                    onClick = { if (newName.isNotBlank()) onCreatePack(newName.trim()) },
                    enabled = newName.isNotBlank()
                ) { Text("Create & add") }
            } else {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
        dismissButton = {
            if (creating) {
                TextButton(onClick = { creating = false }) { Text("Back") }
            }
        }
    )
}
