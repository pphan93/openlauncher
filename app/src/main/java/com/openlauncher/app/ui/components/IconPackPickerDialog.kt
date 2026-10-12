package com.openlauncher.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import com.openlauncher.app.model.IconPackInfo
import com.openlauncher.app.ui.theme.LocalDayMode
import com.phosphor.icons.PhIcons
import com.phosphor.icons.regular.SquaresFour

@Composable
fun IconPackPickerDialog(
    packs: List<IconPackInfo>,
    selectedPackage: String,
    isLoading: Boolean,
    accent: Color,
    onSelect: (String) -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    val isDayMode = LocalDayMode.current
    val background = if (isDayMode) Color.White else Color(0xFF151515)
    val foreground = if (isDayMode) Color(0xFF111111) else Color.White
    val secondary = foreground.copy(alpha = 0.62f)

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(440.dp)
                .heightIn(max = 460.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(background)
                .padding(18.dp)
        ) {
            Text("Icon Pack", color = foreground, fontSize = 18.sp)
            Text(
                "Compatible packs installed from the Play Store appear here.",
                color = secondary,
                fontSize = 11.sp
            )
            Spacer(Modifier.height(12.dp))

            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                item(key = "system_icons") {
                    IconPackRow(
                        name = "System icons",
                        description = "Default app icons with automatic fallback",
                        selected = selectedPackage.isBlank(),
                        accent = accent,
                        icon = {
                            Icon(
                                PhIcons.Regular.SquaresFour,
                                contentDescription = null,
                                tint = foreground,
                                modifier = Modifier.size(34.dp)
                            )
                        },
                        onClick = { onSelect("") }
                    )
                }
                items(packs, key = { it.packageName }) { pack ->
                    val bitmap = remember(pack.packageName, pack.icon) {
                        runCatching { pack.icon.toBitmap(72, 72) }.getOrNull()
                    }
                    IconPackRow(
                        name = pack.displayName,
                        description = pack.packageName,
                        selected = selectedPackage == pack.packageName,
                        accent = accent,
                        icon = {
                            if (bitmap != null) {
                                Image(
                                    painter = BitmapPainter(bitmap.asImageBitmap()),
                                    contentDescription = null,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                        },
                        onClick = { onSelect(pack.packageName) }
                    )
                }
                if (!isLoading && packs.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            "No compatible icon packs found. Packs made for Nova, Lawnchair, ADW, or Apex are supported.",
                            color = secondary,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp)
                        )
                    }
                }
            }

            if (isLoading) {
                LinearProgressIndicator(
                    color = accent,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onRefresh) { Text("Refresh", color = accent) }
                TextButton(onClick = onDismiss) { Text("Done", color = accent) }
            }
        }
    }
}

@Composable
private fun IconPackRow(
    name: String,
    description: String,
    selected: Boolean,
    accent: Color,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { icon() }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(name, fontSize = 13.sp, maxLines = 1)
            Text(description, fontSize = 9.sp, color = LocalContentColor.current.copy(alpha = 0.58f), maxLines = 1)
        }
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = accent)
        )
    }
}
