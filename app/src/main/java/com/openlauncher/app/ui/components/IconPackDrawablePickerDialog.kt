package com.openlauncher.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import com.openlauncher.app.util.IconPackManager

@Composable
fun IconPackDrawablePickerDialog(
    title: String,
    iconPackPackage: String,
    drawableNames: List<String>,
    isLoading: Boolean,
    accent: Color,
    onSelect: (String) -> Unit,
    onUseDefault: () -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filteredNames = remember(drawableNames, query) {
        if (query.isBlank()) drawableNames
        else drawableNames.filter { it.contains(query.trim(), ignoreCase = true) }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.width(600.dp).heightIn(min = 330.dp, max = 520.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(title, fontSize = 18.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text("Search icons") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))

                when {
                    isLoading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = accent)
                    }
                    filteredNames.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            if (drawableNames.isEmpty()) "This icon pack does not expose an icon catalog."
                            else "No matching icons",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                            fontSize = 12.sp
                        )
                    }
                    else -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(76.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filteredNames, key = { it }) { drawableName ->
                            PackDrawableTile(
                                iconPackPackage = iconPackPackage,
                                drawableName = drawableName,
                                onClick = { onSelect(drawableName) }
                            )
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onUseDefault) { Text("Use default", color = accent) }
                    TextButton(onClick = onDismiss) { Text("Cancel", color = accent) }
                }
            }
        }
    }
}

@Composable
private fun PackDrawableTile(
    iconPackPackage: String,
    drawableName: String,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val bitmap = remember(iconPackPackage, drawableName) {
        IconPackManager.loadDrawable(context, iconPackPackage, drawableName)
            ?.let { drawable -> runCatching { drawable.toBitmap(80, 80) }.getOrNull() }
    }
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (bitmap != null) {
            Image(
                painter = BitmapPainter(bitmap.asImageBitmap()),
                contentDescription = drawableName,
                modifier = Modifier.size(42.dp)
            )
        } else {
            Box(Modifier.size(42.dp).background(Color.Gray.copy(alpha = 0.15f)))
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = drawableName.replace('_', ' '),
            fontSize = 7.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
