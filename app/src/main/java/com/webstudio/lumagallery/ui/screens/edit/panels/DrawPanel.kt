package com.webstudio.lumagallery.ui.screens.edit.panels

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas as AndroidCanvas
import android.graphics.DashPathEffect
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlin.math.hypot
import kotlin.math.max

private enum class BrushPreset(val label: String, val alpha: Float, val widthMultiplier: Float) {
    Pen("Pen", 1f, 1f),
    Marker("Marker", 0.82f, 1.6f),
    Highlighter("Highlighter", 0.34f, 3.1f),
    Neon("Neon", 0.92f, 1.35f),
    Dashed("Dashed", 1f, 1f)
}

private enum class DrawMode { Brush, Text }

private enum class BrushControlGroup(val label: String) {
    Brush("Brush"),
    Color("Color"),
    Size("Size"),
    Opacity("Opacity")
}

private enum class TextControlGroup(val label: String) {
    Add("Add"),
    Transform("Transform"),
    Size("Size"),
    Scale("Scale"),
    Rotation("Rotation"),
    Font("Font"),
    Color("Color"),
    Style("Style")
}

private enum class TextFont(val label: String) {
    Sans("Sans"),
    Serif("Serif"),
    Mono("Mono"),
    Casual("Casual"),
    Cursive("Cursive")
}

private sealed interface DrawElement {
    data class StrokeElement(
        val path: Path,
        val color: Color,
        val widthPx: Float,
        val alpha: Float,
        val preset: BrushPreset,
        val revision: Int = 0
    ) : DrawElement

    data class TextElement(
        val id: Long,
        val text: String,
        val offset: Offset,
        val color: Color,
        val sizePx: Float,
        val alpha: Float,
        val rotationZ: Float,
        val scaleX: Float,
        val scaleY: Float,
        val bold: Boolean,
        val italic: Boolean,
        val underline: Boolean,
        val shadow: Boolean,
        val font: TextFont
    ) : DrawElement
}

private val PALETTE = listOf(
    Color(0xFFE53935),
    Color(0xFFFFB300),
    Color(0xFF43A047),
    Color(0xFF00ACC1),
    Color(0xFF1E88E5),
    Color(0xFF8E24AA),
    Color.White,
    Color.Black
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawPanel(
    baseBitmap: Bitmap,
    onApply: (Bitmap) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val elements = remember(baseBitmap) { mutableStateListOf<DrawElement>() }
    val redoStack = remember(baseBitmap) { mutableStateListOf<DrawElement>() }
    var color by remember { mutableStateOf(Color(0xFFE53935)) }
    var widthDp by remember { mutableFloatStateOf(6f) }
    var opacity by remember { mutableFloatStateOf(1f) }
    var brushPreset by remember { mutableStateOf(BrushPreset.Pen) }
    var mode by remember { mutableStateOf(DrawMode.Brush) }
    var brushControlGroup by remember { mutableStateOf(BrushControlGroup.Brush) }
    var textControlGroup by remember { mutableStateOf(TextControlGroup.Add) }
    var selectedTextId by remember { mutableStateOf<Long?>(null) }
    var showTextDialog by remember { mutableStateOf(false) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }
    val density = LocalDensity.current

    val selectedText = elements
        .filterIsInstance<DrawElement.TextElement>()
        .firstOrNull { it.id == selectedTextId }

    fun pushElement(element: DrawElement) {
        elements.add(element)
        redoStack.clear()
    }

    fun updateSelectedText(transform: (DrawElement.TextElement) -> DrawElement.TextElement) {
        val id = selectedTextId ?: return
        val index = elements.indexOfFirst { it is DrawElement.TextElement && it.id == id }
        if (index >= 0) elements[index] = transform(elements[index] as DrawElement.TextElement)
    }

    if (showTextDialog) {
        var input by rememberSaveable(stateSaver = TextFieldValue.Saver) {
            mutableStateOf(TextFieldValue(""))
        }
        AlertDialog(
            onDismissRequest = { showTextDialog = false },
            title = { Text("Add text") },
            text = {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    label = { Text("Text") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val t = input.text.trim()
                    if (t.isNotEmpty()) {
                        val center = Offset(canvasSize.width / 2f, canvasSize.height / 2f)
                        val sizePx = with(density) { 32.dp.toPx() }
                        val item = DrawElement.TextElement(
                            id = System.nanoTime(),
                            text = t,
                            offset = center,
                            color = color,
                            sizePx = sizePx,
                            alpha = opacity,
                            rotationZ = 0f,
                            scaleX = 1f,
                            scaleY = 1f,
                            bold = false,
                            italic = false,
                            underline = false,
                            shadow = false,
                            font = TextFont.Sans
                        )
                        pushElement(item)
                        selectedTextId = item.id
                        mode = DrawMode.Text
                    }
                    showTextDialog = false
                }) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showTextDialog = false }) { Text("Cancel") }
            }
        )
    }

    Column(modifier = modifier.padding(8.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .heightIn(min = 180.dp)
                .background(Color.Black)
                .clip(MaterialTheme.shapes.medium),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = baseBitmap.asImageBitmap(),
                contentDescription = "Draw preview",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(mode, elements.size) {
                        if (mode == DrawMode.Text) {
                            detectTapGestures { position ->
                                selectedTextId = elements
                                    .filterIsInstance<DrawElement.TextElement>()
                                    .minByOrNull { hypot(it.offset.x - position.x, it.offset.y - position.y) }
                                    ?.takeIf { hypot(it.offset.x - position.x, it.offset.y - position.y) < 160f }
                                    ?.id
                            }
                        }
                    }
                    .pointerInput(mode, color, widthDp, opacity, brushPreset, selectedTextId) {
                        if (mode == DrawMode.Brush) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: continue
                                    if (change.pressed && !change.previousPressed) {
                                        val path = Path().apply { moveTo(change.position.x, change.position.y) }
                                        val widthPx = with(density) {
                                            (widthDp * brushPreset.widthMultiplier).dp.toPx()
                                        }
                                        pushElement(
                                            DrawElement.StrokeElement(
                                                path = path,
                                                color = color,
                                                widthPx = widthPx,
                                                alpha = (opacity * brushPreset.alpha).coerceIn(0.05f, 1f),
                                                preset = brushPreset
                                            )
                                        )
                                        change.consume()
                                    } else if (change.pressed) {
                                        val index = elements.indexOfLast { it is DrawElement.StrokeElement }
                                        if (index >= 0) {
                                            val stroke = elements[index] as DrawElement.StrokeElement
                                            stroke.path.lineTo(change.position.x, change.position.y)
                                            elements[index] = stroke.copy(revision = stroke.revision + 1)
                                        }
                                        change.consume()
                                    }
                                }
                            }
                        } else {
                            detectTransformGestures { centroid, pan, zoom, rotation ->
                                val nearest = elements
                                    .filterIsInstance<DrawElement.TextElement>()
                                    .minByOrNull { hypot(it.offset.x - centroid.x, it.offset.y - centroid.y) }
                                if (nearest != null && hypot(nearest.offset.x - centroid.x, nearest.offset.y - centroid.y) < 160f) {
                                    selectedTextId = nearest.id
                                }
                                updateSelectedText {
                                    it.copy(
                                        offset = it.offset + pan,
                                        sizePx = (it.sizePx * zoom).coerceIn(
                                            with(density) { 10.dp.toPx() },
                                            with(density) { 120.dp.toPx() }
                                        ),
                                        rotationZ = (it.rotationZ + rotation).coerceIn(-180f, 180f)
                                    )
                                }
                            }
                        }
                    }
            ) {
                canvasSize = size
                elements.forEach { element ->
                    when (element) {
                        is DrawElement.StrokeElement -> drawPath(
                            path = element.path,
                            color = element.color.copy(alpha = element.alpha),
                            style = Stroke(width = element.widthPx)
                        )
                        is DrawElement.TextElement -> drawTextElement(element, selectedTextId == element.id)
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = mode == DrawMode.Brush,
                onClick = { mode = DrawMode.Brush },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                label = { Text("Brush") }
            )
            SegmentedButton(
                selected = mode == DrawMode.Text,
                onClick = { mode = DrawMode.Text },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                label = { Text("Text") }
            )
        }

        Spacer(Modifier.height(8.dp))
        if (mode == DrawMode.Brush) {
            BrushControls(
                selectedGroup = brushControlGroup,
                onGroupChange = { brushControlGroup = it },
                color = color,
                onColorChange = { color = it },
                widthDp = widthDp,
                onWidthChange = { widthDp = it },
                opacity = opacity,
                onOpacityChange = { opacity = it },
                brushPreset = brushPreset,
                onBrushPresetChange = { brushPreset = it }
            )
        } else {
            TextControls(
                selectedGroup = textControlGroup,
                onGroupChange = { textControlGroup = it },
                selectedText = selectedText,
                canvasSize = canvasSize,
                onAddText = { showTextDialog = true },
                onColorChange = {
                    color = it
                    updateSelectedText { text -> text.copy(color = it) }
                },
                onXChange = { x -> updateSelectedText { it.copy(offset = it.offset.copy(x = x)) } },
                onYChange = { y -> updateSelectedText { it.copy(offset = it.offset.copy(y = y)) } },
                onRotationChange = { z -> updateSelectedText { it.copy(rotationZ = z) } },
                onScaleXChange = { x -> updateSelectedText { it.copy(scaleX = x) } },
                onScaleYChange = { y -> updateSelectedText { it.copy(scaleY = y) } },
                onSizeChange = { size -> updateSelectedText { it.copy(sizePx = with(density) { size.dp.toPx() }) } },
                onBoldToggle = { updateSelectedText { it.copy(bold = !it.bold) } },
                onItalicToggle = { updateSelectedText { it.copy(italic = !it.italic) } },
                onUnderlineToggle = { updateSelectedText { it.copy(underline = !it.underline) } },
                onShadowToggle = { updateSelectedText { it.copy(shadow = !it.shadow) } },
                onFontChange = { font -> updateSelectedText { it.copy(font = font) } },
                onDelete = {
                    val id = selectedTextId ?: return@TextControls
                    val index = elements.indexOfFirst { it is DrawElement.TextElement && it.id == id }
                    if (index >= 0) {
                        redoStack.clear()
                        elements.removeAt(index)
                        selectedTextId = null
                    }
                }
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = {
                        if (elements.isNotEmpty()) redoStack.add(elements.removeAt(elements.lastIndex))
                    },
                    enabled = elements.isNotEmpty()
                ) { Icon(Icons.AutoMirrored.Filled.Undo, "Undo draw action") }
                IconButton(
                    onClick = {
                        if (redoStack.isNotEmpty()) elements.add(redoStack.removeAt(redoStack.lastIndex))
                    },
                    enabled = redoStack.isNotEmpty()
                ) { Icon(Icons.AutoMirrored.Filled.Redo, "Redo draw action") }
                IconButton(
                    onClick = {
                        elements.clear()
                        redoStack.clear()
                        selectedTextId = null
                    },
                    enabled = elements.isNotEmpty()
                ) { Icon(Icons.Default.Refresh, "Clear drawing") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onCancel) {
                    Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Cancel")
                }
                FilledTonalButton(
                    onClick = {
                        if (elements.isEmpty()) {
                            onCancel()
                        } else {
                            onApply(renderToBitmap(baseBitmap, elements, canvasSize))
                        }
                    }
                ) {
                    Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Apply")
                }
            }
        }
    }
}

@Composable
private fun BrushControls(
    selectedGroup: BrushControlGroup,
    onGroupChange: (BrushControlGroup) -> Unit,
    color: Color,
    onColorChange: (Color) -> Unit,
    widthDp: Float,
    onWidthChange: (Float) -> Unit,
    opacity: Float,
    onOpacityChange: (Float) -> Unit,
    brushPreset: BrushPreset,
    onBrushPresetChange: (BrushPreset) -> Unit
) {
    ControlRail(BrushControlGroup.entries, selectedGroup, onGroupChange) { it.label }
    Spacer(Modifier.height(6.dp))
    when (selectedGroup) {
        BrushControlGroup.Brush -> LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(BrushPreset.entries) { preset ->
                FilterChip(
                    selected = brushPreset == preset,
                    onClick = { onBrushPresetChange(preset) },
                    label = { Text(preset.label) }
                )
            }
        }
        BrushControlGroup.Color -> ColorRow(color, onColorChange)
        BrushControlGroup.Size -> SliderRow("Size", widthDp, 2f..30f, onWidthChange)
        BrushControlGroup.Opacity -> SliderRow("Opacity", opacity, 0.1f..1f, onOpacityChange)
    }
}

@Composable
private fun TextControls(
    selectedGroup: TextControlGroup,
    onGroupChange: (TextControlGroup) -> Unit,
    selectedText: DrawElement.TextElement?,
    canvasSize: Size,
    onAddText: () -> Unit,
    onColorChange: (Color) -> Unit,
    onXChange: (Float) -> Unit,
    onYChange: (Float) -> Unit,
    onRotationChange: (Float) -> Unit,
    onScaleXChange: (Float) -> Unit,
    onScaleYChange: (Float) -> Unit,
    onSizeChange: (Float) -> Unit,
    onBoldToggle: () -> Unit,
    onItalicToggle: () -> Unit,
    onUnderlineToggle: () -> Unit,
    onShadowToggle: () -> Unit,
    onFontChange: (TextFont) -> Unit,
    onDelete: () -> Unit
) {
    ControlRail(TextControlGroup.entries, selectedGroup, onGroupChange) { it.label }
    Spacer(Modifier.height(6.dp))
    when (selectedGroup) {
        TextControlGroup.Add -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(onClick = onAddText) {
                Icon(Icons.Default.TextFields, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add")
            }
            IconButton(onClick = onDelete, enabled = selectedText != null) {
                Icon(Icons.Default.Delete, "Delete selected text")
            }
            Text(
                text = selectedText?.text ?: "Tap a text layer",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        TextControlGroup.Transform -> Text(
            text = if (selectedText == null) "Tap text, then drag it on the image." else "Drag text to move. Pinch to resize. Twist to rotate.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp)
        )
        TextControlGroup.Size -> if (selectedText != null) {
            SliderRow("Size", selectedText.sizePx / LocalDensity.current.density, 12f..120f, onSizeChange)
        }
        TextControlGroup.Scale -> if (selectedText != null) {
            SliderRow("X scale", selectedText.scaleX, 0.35f..2.5f, onScaleXChange)
            SliderRow("Y scale", selectedText.scaleY, 0.35f..2.5f, onScaleYChange)
        }
        TextControlGroup.Rotation -> if (selectedText != null) {
            SliderRow("Rotate", selectedText.rotationZ, -180f..180f, onRotationChange)
        }
        TextControlGroup.Font -> if (selectedText != null) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(TextFont.entries) { font ->
                    FilterChip(
                        selected = selectedText.font == font,
                        onClick = { onFontChange(font) },
                        label = { Text(font.label) }
                    )
                }
            }
        }
        TextControlGroup.Color -> ColorRow(selectedText?.color ?: Color.White, onColorChange)
        TextControlGroup.Style -> if (selectedText != null) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    FilterChip(
                        selected = selectedText.bold,
                        onClick = onBoldToggle,
                        label = { Text("Bold") }
                    )
                }
                item {
                    FilterChip(
                        selected = selectedText.italic,
                        onClick = onItalicToggle,
                        label = { Text("Italic") }
                    )
                }
                item {
                    FilterChip(
                        selected = selectedText.underline,
                        onClick = onUnderlineToggle,
                        label = { Text("Underline") }
                    )
                }
                item {
                    FilterChip(
                        selected = selectedText.shadow,
                        onClick = onShadowToggle,
                        label = { Text("Shadow") }
                    )
                }
            }
        }
    }
}

@Composable
private fun <T> ControlRail(
    values: List<T>,
    selected: T,
    onSelected: (T) -> Unit,
    label: (T) -> String
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(values) { value ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelected(value) },
                label = { Text(label(value)) }
            )
        }
    }
}

@Composable
private fun ColorRow(selected: Color, onColorChange: (Color) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Color", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(72.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(PALETTE) { c ->
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(c)
                        .border(
                            width = if (c == selected) 2.dp else 0.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape
                        )
                        .clickable { onColorChange(c) }
                )
            }
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(72.dp))
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTextElement(
    item: DrawElement.TextElement,
    selected: Boolean
) {
    val paint = item.toTextPaint()
    drawContext.canvas.nativeCanvas.withTransformedText(item) {
        drawText(item.text, 0f, 0f, paint)
        if (selected) {
            val width = paint.measureText(item.text)
            val boundsPaint = AndroidPaint().apply {
                color = Color.White.copy(alpha = 0.72f).toArgb()
                style = AndroidPaint.Style.STROKE
                strokeWidth = 2f
                pathEffect = DashPathEffect(floatArrayOf(8f, 8f), 0f)
            }
            drawRect(0f, -item.sizePx, width, item.sizePx * 0.25f, boundsPaint)
        }
    }
}

private fun AndroidCanvas.withTransformedText(
    item: DrawElement.TextElement,
    block: AndroidCanvas.() -> Unit
) {
    save()
    translate(item.offset.x, item.offset.y)
    rotate(item.rotationZ)
    scale(item.scaleX, item.scaleY)
    block()
    restore()
}

private fun renderToBitmap(
    base: Bitmap,
    elements: List<DrawElement>,
    canvasSize: Size
): Bitmap {
    val out = base.copy(Bitmap.Config.ARGB_8888, true)
    val nativeCanvas = AndroidCanvas(out)
    val imageRect = fittedImageRect(base, canvasSize)
    val scaleX = if (imageRect.width > 0) base.width / imageRect.width else 1f
    val scaleY = if (imageRect.height > 0) base.height / imageRect.height else 1f

    nativeCanvas.save()
    nativeCanvas.clipRect(0, 0, base.width, base.height)
    elements.forEach { element ->
        when (element) {
            is DrawElement.StrokeElement -> {
                val path = element.path.toAndroidPath(imageRect, scaleX, scaleY)
                val paint = AndroidPaint().apply {
                    color = element.color.copy(alpha = element.alpha).toArgb()
                    strokeWidth = element.widthPx * max(scaleX, scaleY)
                    style = AndroidPaint.Style.STROKE
                    strokeCap = AndroidPaint.Cap.ROUND
                    strokeJoin = AndroidPaint.Join.ROUND
                    isAntiAlias = true
                    if (element.preset == BrushPreset.Dashed) {
                        pathEffect = DashPathEffect(floatArrayOf(strokeWidth * 2.2f, strokeWidth * 1.4f), 0f)
                    }
                    if (element.preset == BrushPreset.Neon) {
                        maskFilter = BlurMaskFilter(strokeWidth * 0.45f, BlurMaskFilter.Blur.NORMAL)
                    }
                }
                nativeCanvas.drawPath(path, paint)
            }
            is DrawElement.TextElement -> {
                val mapped = element.copy(
                    offset = Offset(
                        (element.offset.x - imageRect.left) * scaleX,
                        (element.offset.y - imageRect.top) * scaleY
                    ),
                    sizePx = element.sizePx * max(scaleX, scaleY)
                )
                val paint = AndroidPaint().apply {
                    set(mapped.toTextPaint())
                }
                nativeCanvas.withTransformedText(mapped) {
                    drawText(mapped.text, 0f, 0f, paint)
                }
            }
        }
    }
    nativeCanvas.restore()
    return out
}

private fun DrawElement.TextElement.toTextPaint(): AndroidPaint {
    val style = when {
        bold && italic -> Typeface.BOLD_ITALIC
        bold -> Typeface.BOLD
        italic -> Typeface.ITALIC
        else -> Typeface.NORMAL
    }
    return AndroidPaint().apply {
        color = this@toTextPaint.color.copy(alpha = this@toTextPaint.alpha).toArgb()
        textSize = sizePx
        typeface = Typeface.create(font.toTypeface(), style)
        isUnderlineText = underline
        isAntiAlias = true
        if (shadow) {
            setShadowLayer(sizePx * 0.12f, sizePx * 0.05f, sizePx * 0.05f, Color.Black.toArgb())
        }
    }
}

private fun TextFont.toTypeface(): Typeface = when (this) {
    TextFont.Sans -> Typeface.SANS_SERIF
    TextFont.Serif -> Typeface.SERIF
    TextFont.Mono -> Typeface.MONOSPACE
    TextFont.Casual -> Typeface.create("casual", Typeface.NORMAL)
    TextFont.Cursive -> Typeface.create("cursive", Typeface.NORMAL)
}

private fun Path.toAndroidPath(imageRect: Rect, scaleX: Float, scaleY: Float): AndroidPath {
    val out = AndroidPath()
    val measure = PathMeasure().apply { setPath(this@toAndroidPath, false) }
    var d = 0f
    var first = true
    while (d <= measure.length) {
        val p = measure.getPosition(d)
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
