package com.webstudio.lumagallery.ui.screens.edit.panels

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ColorPanel(
    onPreviewChange: (brightness: Float, contrast: Float, saturation: Float, warmth: Float) -> Unit,
    onApply: () -> Unit,
    onCancel: () -> Unit
) {
    var brightness by remember { mutableFloatStateOf(0f) }
    var contrast by remember { mutableFloatStateOf(1f) }
    var saturation by remember { mutableFloatStateOf(1f) }
    var warmth by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(brightness, contrast, saturation, warmth) {
        onPreviewChange(brightness, contrast, saturation, warmth)
    }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        SliderRow("Brightness", brightness, -1f..1f) { brightness = it }
        SliderRow("Contrast", contrast, 0f..2f) { contrast = it }
        SliderRow("Saturation", saturation, 0f..2f) { saturation = it }
        SliderRow("Warmth", warmth, -1f..1f) { warmth = it }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onCancel) {
                Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Cancel")
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(onClick = onApply) {
                Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Apply")
            }
        }
    }
}

@Composable
private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(96.dp))
            Slider(
                value = value,
                onValueChange = onChange,
                valueRange = range,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "%.2f".format(value),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.width(48.dp)
            )
        }
    }
}
