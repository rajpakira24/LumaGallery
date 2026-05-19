package com.webstudio.lumagallery.ui.screens.edit.panels

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.PhotoSizeSelectLarge
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.yalantis.ucrop.UCrop
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

private data class CropAspect(
    val label: String,
    val x: Float? = null,
    val y: Float? = null
)

private val ASPECTS = listOf(
    CropAspect("Free"),
    CropAspect("Original", -1f, -1f),
    CropAspect("1:1", 1f, 1f),
    CropAspect("4:5", 4f, 5f),
    CropAspect("3:4", 3f, 4f),
    CropAspect("16:9", 16f, 9f),
    CropAspect("9:16", 9f, 16f)
)

@Composable
fun CropPanel(
    bitmap: Bitmap,
    onCropped: (Bitmap) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    var pendingDestUri by remember { mutableStateOf<Uri?>(null) }
    var selectedAspect by remember { mutableStateOf(ASPECTS.first()) }
    var quality by remember { mutableFloatStateOf(95f) }
    var maxEdge by remember { mutableFloatStateOf(2048f) }
    var launching by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            val out = UCrop.getOutput(data) ?: pendingDestUri
            if (out != null) {
                val bmp = context.contentResolver.openInputStream(out)?.use {
                    BitmapFactory.decodeStream(it)
                }
                if (bmp != null) onCropped(bmp) else onCancel()
            } else onCancel()
        } else onCancel()
        pendingDestUri = null
        launching = false
    }

    fun launchCrop() {
        if (launching) return
        launching = true
        val srcFile = File(context.cacheDir, "ucrop_src_${System.currentTimeMillis()}.jpg")
        FileOutputStream(srcFile).use {
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality.roundToInt(), it)
        }
        val destFile = File(context.cacheDir, "ucrop_dst_${System.currentTimeMillis()}.jpg")
        val srcUri = Uri.fromFile(srcFile)
        val destUri = Uri.fromFile(destFile)
        pendingDestUri = destUri

        val options = UCrop.Options().apply {
            setCompressionFormat(Bitmap.CompressFormat.JPEG)
            setCompressionQuality(quality.roundToInt())
            setFreeStyleCropEnabled(selectedAspect.x == null)
            setHideBottomControls(false)
        }

        val crop = UCrop.of(srcUri, destUri)
            .withOptions(options)
            .withMaxResultSize(maxEdge.roundToInt(), maxEdge.roundToInt())

        val aspectX = selectedAspect.x
        val aspectY = selectedAspect.y
        val configured = when {
            selectedAspect.label == "Original" -> crop.withAspectRatio(
                bitmap.width.toFloat(),
                bitmap.height.toFloat()
            )
            aspectX != null && aspectY != null -> crop.withAspectRatio(
                aspectX,
                aspectY
            )
            else -> crop
        }

        launcher.launch(configured.getIntent(context))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            "Crop setup",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(ASPECTS) { aspect ->
                FilterChip(
                    selected = selectedAspect == aspect,
                    onClick = { selectedAspect = aspect },
                    label = { Text(aspect.label) }
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        SliderRow(
            label = "Quality",
            value = quality,
            valueRange = 70f..100f,
            displayValue = "${quality.roundToInt()}%",
            onChange = { quality = it }
        )
        SliderRow(
            label = "Max edge",
            value = maxEdge,
            valueRange = 720f..4096f,
            displayValue = "${maxEdge.roundToInt()} px",
            onChange = { maxEdge = it }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onCancel, enabled = !launching) {
                Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Cancel")
            }
            FilledTonalButton(onClick = ::launchCrop, enabled = !launching) {
                Icon(Icons.Default.Crop, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (launching) "Opening..." else "Open crop")
            }
        }

        Row(
            modifier = Modifier.padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.OpenInFull, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                if (selectedAspect.x == null) "Free crop allows resizing the crop box freely."
                else "Selected aspect ratio is locked in the crop tool.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    displayValue: String,
    onChange: (Float) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.PhotoSizeSelectLarge, null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(72.dp))
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = valueRange,
            modifier = Modifier.weight(1f)
        )
        Text(
            displayValue,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(58.dp)
        )
    }
}
