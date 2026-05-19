package com.webstudio.lumagallery.ui.screens.edit.panels

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Rotate90DegreesCcw
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

private data class MaskStroke(
    val path: Path,
    val widthPx: Float,
    val revision: Int = 0
)

@Composable
fun AiPanel(
    hasGeminiKey: Boolean,
    hasDashscopeKey: Boolean,
    currentBitmap: Bitmap?,
    onRemoveBackground: () -> Unit,
    onUpscale: () -> Unit,
    onPromptEdit: (String) -> Unit,
    onSticker: () -> Unit,
    onCloudSticker: () -> Unit,
    onEraseObject: (mask: Bitmap, prompt: String) -> Unit,
    onRotate90: () -> Unit,
    onFlipHorizontal: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasCloud = hasGeminiKey || hasDashscopeKey
    var maskingActive by remember { mutableStateOf(false) }
    var showPromptDialog by remember { mutableStateOf(false) }
    var showStickerImprove by remember { mutableStateOf(false) }

    if (showPromptDialog) {
        var input by rememberSaveable(stateSaver = TextFieldValue.Saver) {
            mutableStateOf(TextFieldValue(""))
        }
        AlertDialog(
            onDismissRequest = { showPromptDialog = false },
            title = { Text("AI prompt edit") },
            text = {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    minLines = 3,
                    label = { Text("Describe the photo edit") },
                    placeholder = { Text("Example: make the background sunset, keep the person natural") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val prompt = input.text.trim()
                        if (prompt.isNotEmpty()) onPromptEdit(prompt)
                        showPromptDialog = false
                    }
                ) { Text("Edit") }
            },
            dismissButton = {
                TextButton(onClick = { showPromptDialog = false }) { Text("Cancel") }
            }
        )
    }

    Column(modifier = modifier.padding(8.dp)) {
        if (maskingActive && currentBitmap != null) {
            EraseObjectMaskUi(
                base = currentBitmap,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                onConfirm = { mask, prompt ->
                    onEraseObject(mask, prompt)
                    maskingActive = false
                },
                onCancel = { maskingActive = false }
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black)
                    .clip(MaterialTheme.shapes.medium),
                contentAlignment = Alignment.Center
            ) {
                if (currentBitmap != null && !currentBitmap.isRecycled) {
                    Image(
                        bitmap = currentBitmap.asImageBitmap(),
                        contentDescription = "AI edit preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        if (!hasCloud) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Cloud AI disabled - set GEMINI_API_KEY or DASHSCOPE_API_KEY in local.properties.",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(8.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
        }
        if (!maskingActive) {
            if (showStickerImprove) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Want a more accurate cutout? Use cloud AI.",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = {
                                showStickerImprove = false
                                onCloudSticker()
                            },
                            enabled = hasCloud
                        ) {
                            Text("Improve")
                        }
                    }
                }
            }
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                item {
                    AiButton(
                        icon = Icons.Default.PersonRemove,
                        label = "Bg Remove",
                        enabled = currentBitmap != null,
                        subtitle = "on-device",
                        onClick = onRemoveBackground
                    )
                }
                item {
                    AiButton(
                        icon = Icons.AutoMirrored.Filled.StickyNote2,
                        label = "Sticker",
                        enabled = currentBitmap != null,
                        subtitle = "free cutout",
                        onClick = {
                            onSticker()
                            showStickerImprove = true
                        }
                    )
                }
                item {
                    AiButton(
                        icon = Icons.Default.ZoomIn,
                        label = "Upscale",
                        enabled = currentBitmap != null && hasCloud,
                        subtitle = if (hasCloud) "cloud" else "no key",
                        onClick = onUpscale
                    )
                }
                item {
                    AiButton(
                        icon = Icons.Default.Edit,
                        label = "Prompt",
                        enabled = currentBitmap != null && hasCloud,
                        subtitle = if (hasCloud) "cloud edit" else "no key",
                        onClick = { showPromptDialog = true }
                    )
                }
                item {
                    AiButton(
                        icon = Icons.Default.AutoFixHigh,
                        label = "Erase",
                        enabled = currentBitmap != null && hasCloud,
                        subtitle = if (hasCloud) "paint mask" else "no key",
                        onClick = { maskingActive = true }
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                AiButton(
                    icon = Icons.Default.Rotate90DegreesCcw,
                    label = "Rotate",
                    enabled = currentBitmap != null,
                    subtitle = "90 deg",
                    onClick = onRotate90
                )
                AiButton(
                    icon = Icons.Default.FlipCameraAndroid,
                    label = "Flip H",
                    enabled = currentBitmap != null,
                    subtitle = "",
                    onClick = onFlipHorizontal
                )
                AiButton(
                    icon = Icons.Default.Close,
                    label = "Close",
                    enabled = true,
                    subtitle = "",
                    onClick = onClose
                )
            }
        }
    }
}

@Composable
private fun AiButton(
    icon: ImageVector,
    label: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(86.dp)
            .padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FilledTonalIconButton(onClick = onClick, enabled = enabled) {
            Icon(icon, label)
        }
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        if (subtitle.isNotEmpty()) {
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun EraseObjectMaskUi(
    base: Bitmap,
    modifier: Modifier,
    onConfirm: (mask: Bitmap, prompt: String) -> Unit,
    onCancel: () -> Unit
) {
    val strokes = remember(base) { mutableStateListOf<MaskStroke>() }
    var brushDp by remember { mutableFloatStateOf(24f) }
    var prompt by rememberSaveable {
        mutableStateOf("Remove the highlighted subject and naturally fill the area.")
    }
    val density = LocalDensity.current
    var canvasSize by remember { mutableStateOf(Size.Zero) }

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.Black)
                .clip(MaterialTheme.shapes.medium),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = base.asImageBitmap(),
                contentDescription = "Erase mask preview",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(brushDp) {
                        awaitPointerEventScope {
                            while (true) {
                                val e = awaitPointerEvent()
                                val c = e.changes.firstOrNull() ?: continue
                                if (c.pressed && !c.previousPressed) {
                                    val path = Path().apply { moveTo(c.position.x, c.position.y) }
                                    val widthPx = with(density) { brushDp.dp.toPx() }
                                    strokes.add(MaskStroke(path = path, widthPx = widthPx))
                                    c.consume()
                                } else if (c.pressed) {
                                    val last = strokes.lastOrNull() ?: continue
                                    last.path.lineTo(c.position.x, c.position.y)
                                    strokes[strokes.lastIndex] = last.copy(revision = last.revision + 1)
                                    c.consume()
                                }
                            }
                        }
                    }
            ) {
                canvasSize = size
                strokes.forEach { stroke ->
                    drawPath(
                        path = stroke.path,
                        color = Color.Red.copy(alpha = 0.52f),
                        style = Stroke(width = stroke.widthPx)
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            label = { Text("Erase prompt") },
            singleLine = false,
            minLines = 1,
            maxLines = 2,
            modifier = Modifier.fillMaxWidth()
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Brush", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(56.dp))
            Slider(
                value = brushDp,
                onValueChange = { brushDp = it },
                valueRange = 8f..64f,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row {
                IconButton(onClick = { strokes.clear() }, enabled = strokes.isNotEmpty()) {
                    Icon(Icons.Default.Refresh, "Clear mask")
                }
                TextButton(onClick = onCancel) {
                    Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Cancel")
                }
            }
            FilledTonalButton(
                onClick = {
                    if (strokes.isEmpty()) return@FilledTonalButton
                    val mask = renderMask(base, strokes, canvasSize)
                    onConfirm(mask, prompt.ifBlank { "Remove the highlighted subject and naturally fill the area." })
                },
                enabled = strokes.isNotEmpty()
            ) {
                Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Erase")
            }
        }
    }
}

private fun renderMask(
    base: Bitmap,
    strokes: List<MaskStroke>,
    canvasSize: Size
): Bitmap {
    val out = Bitmap.createBitmap(base.width, base.height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(out)
    canvas.drawColor(AndroidColor.BLACK)
    val imageRect = fittedImageRect(base, canvasSize)
    val scaleX = if (imageRect.width > 0f) base.width / imageRect.width else 1f
    val scaleY = if (imageRect.height > 0f) base.height / imageRect.height else 1f

    strokes.forEach { stroke ->
        val sp = stroke.path.toAndroidPath(imageRect, scaleX, scaleY)
        val paint = AndroidPaint().apply {
            setColor(AndroidColor.WHITE)
            strokeWidth = stroke.widthPx * maxOf(scaleX, scaleY)
            style = AndroidPaint.Style.STROKE
            strokeCap = AndroidPaint.Cap.ROUND
            strokeJoin = AndroidPaint.Join.ROUND
            isAntiAlias = true
        }
        canvas.drawPath(sp, paint)
    }
    return out
}

private fun Path.toAndroidPath(imageRect: Rect, scaleX: Float, scaleY: Float): AndroidPath {
    val out = AndroidPath()
    val pm = PathMeasure().apply { setPath(this@toAndroidPath, false) }
    var d = 0f
    var first = true
    while (d <= pm.length) {
        val p = pm.getPosition(d)
        val x = (p.x - imageRect.left) * scaleX
        val y = (p.y - imageRect.top) * scaleY
        if (first) {
            out.moveTo(x, y)
            first = false
        } else {
            out.lineTo(x, y)
        }
        d += 1f
    }
    return out
}

private fun fittedImageRect(bitmap: Bitmap, canvasSize: Size): Rect {
    if (canvasSize.width <= 0f || canvasSize.height <= 0f) {
        return Rect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
    }
    val imageAspect = bitmap.width.toFloat() / bitmap.height.toFloat()
    val canvasAspect = canvasSize.width / canvasSize.height
    return if (imageAspect > canvasAspect) {
        val height = canvasSize.width / imageAspect
        val top = (canvasSize.height - height) / 2f
        Rect(0f, top, canvasSize.width, top + height)
    } else {
        val width = canvasSize.height * imageAspect
        val left = (canvasSize.width - width) / 2f
        Rect(left, 0f, left + width, canvasSize.height)
    }
}
