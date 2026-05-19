package com.webstudio.lumagallery.ui.screens.edit.panels

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.webstudio.lumagallery.data.edit.BitmapEngine
import com.webstudio.lumagallery.data.edit.FilterPreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun FilterPanel(
    bitmap: Bitmap,
    onPick: (FilterPreset) -> Unit,
    onClose: () -> Unit
) {
    val thumb by produceState<Bitmap?>(initialValue = null, bitmap) {
        value = withContext(Dispatchers.Default) {
            val scale = (96f / maxOf(bitmap.width, bitmap.height)).coerceAtMost(1f)
            val w = (bitmap.width * scale).toInt().coerceAtLeast(1)
            val h = (bitmap.height * scale).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(bitmap, w, h, true)
        }
    }

    Column(modifier = Modifier.padding(8.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(FilterPreset.values()) { preset ->
                FilterThumb(preset = preset, thumb = thumb, onClick = { onPick(preset) })
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onClose) {
                Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Close")
            }
        }
    }
}

@Composable
private fun FilterThumb(preset: FilterPreset, thumb: Bitmap?, onClick: () -> Unit) {
    val processed by produceState<Bitmap?>(initialValue = null, preset, thumb) {
        val src = thumb ?: return@produceState
        value = withContext(Dispatchers.Default) {
            if (preset == FilterPreset.Original) src
            else BitmapEngine.applyColorMatrix(src, preset.matrix())
        }
    }

    Column(
        modifier = Modifier
            .width(80.dp)
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            processed?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = preset.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            preset.displayName,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}
