package com.webstudio.lumagallery.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.webstudio.lumagallery.data.sticker.StickerPack
import com.webstudio.lumagallery.data.sticker.StickerPackRepository
import com.webstudio.lumagallery.stickers.WhatsAppStickerHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StickerPackScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repo = remember { StickerPackRepository(context) }
    val scope = rememberCoroutineScope()

    var packs by remember { mutableStateOf<List<StickerPack>>(emptyList()) }
    // bumped to force a reload from disk after mutations
    var reloadTick by remember { mutableStateOf(0) }

    LaunchedEffect(reloadTick) {
        packs = withContext(Dispatchers.IO) { repo.loadPacks() }
    }

    fun reload() { reloadTick++ }

    var packToRename by remember { mutableStateOf<StickerPack?>(null) }
    var renameText by remember { mutableStateOf("") }
    var packToDelete by remember { mutableStateOf<StickerPack?>(null) }

    if (packToRename != null) {
        AlertDialog(
            onDismissRequest = { packToRename = null },
            title = { Text("Rename pack") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text("Pack name") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = packToRename!!.identifier
                        val name = renameText.trim()
                        packToRename = null
                        if (name.isNotEmpty()) {
                            scope.launch {
                                withContext(Dispatchers.IO) { repo.renamePack(id, name) }
                                reload()
                            }
                        }
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { packToRename = null }) { Text("Cancel") }
            }
        )
    }

    if (packToDelete != null) {
        AlertDialog(
            onDismissRequest = { packToDelete = null },
            title = { Text("Delete pack") },
            text = { Text("Delete \"${packToDelete!!.name}\" and all its stickers? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = packToDelete!!.identifier
                        packToDelete = null
                        scope.launch {
                            withContext(Dispatchers.IO) { repo.deletePack(id) }
                            reload()
                        }
                    }
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { packToDelete = null }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Sticker Packs") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (packs.isEmpty()) {
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
                        Icons.AutoMirrored.Filled.StickyNote2,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "No sticker packs yet",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Create a sticker in the photo editor (AI > Sticker), then add it to a pack to see it here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items = packs, key = { it.identifier }) { pack ->
                    StickerPackCard(
                        pack = pack,
                        assetFile = { fileName -> repo.assetFile(pack.identifier, fileName) },
                        onRemoveSticker = { fileName ->
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    repo.removeSticker(pack.identifier, fileName)
                                }
                                reload()
                            }
                        },
                        onRename = {
                            renameText = pack.name
                            packToRename = pack
                        },
                        onDelete = { packToDelete = pack },
                        onAddToWhatsApp = {
                            val activity = context.findActivity()
                            if (activity != null) {
                                WhatsAppStickerHelper.addToWhatsApp(activity, pack)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun StickerPackCard(
    pack: StickerPack,
    assetFile: (String) -> java.io.File,
    onRemoveSticker: (String) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onAddToWhatsApp: () -> Unit
) {
    val validationError = WhatsAppStickerHelper.validate(pack)

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        pack.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        "${pack.stickers.size} sticker(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onRename) {
                    Icon(Icons.Default.Edit, contentDescription = "Rename pack")
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete pack",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            if (pack.stickers.isEmpty()) {
                Text(
                    "This pack has no stickers.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(items = pack.stickers, key = { it.fileName }) { sticker ->
                        Box {
                            AsyncImage(
                                model = assetFile(sticker.fileName),
                                contentDescription = "Sticker",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            )
                            Surface(
                                color = Color.Black.copy(alpha = 0.55f),
                                shape = RoundedCornerShape(bottomStart = 10.dp),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                            ) {
                                IconButton(
                                    onClick = { onRemoveSticker(sticker.fileName) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove sticker",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            FilledTonalButton(
                onClick = onAddToWhatsApp,
                enabled = validationError == null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Add to WhatsApp")
            }
            if (validationError != null) {
                Text(
                    validationError,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
