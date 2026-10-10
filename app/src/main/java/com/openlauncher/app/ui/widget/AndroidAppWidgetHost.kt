package com.openlauncher.app.ui.widget

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.widget.TextView as AndroidTextView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

const val OPEN_LAUNCHER_APP_WIDGET_HOST_ID = 0x4F4C

@Composable
fun HostedAndroidWidget(
    appWidgetId: Int,
    host: AppWidgetHost,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val manager = remember(context) { AppWidgetManager.getInstance(context) }
    val density = LocalDensity.current
    var measuredSize by remember(appWidgetId) { mutableStateOf(IntSize.Zero) }

    AndroidView(
        modifier = modifier.onSizeChanged { measuredSize = it },
        factory = { viewContext ->
            val provider = manager.getAppWidgetInfo(appWidgetId)
            if (provider != null) {
                host.createView(viewContext, appWidgetId, provider).apply {
                    setAppWidget(appWidgetId, provider)
                }
            } else {
                AndroidTextView(viewContext).apply {
                    text = "Widget unavailable"
                    gravity = android.view.Gravity.CENTER
                    setTextColor(android.graphics.Color.GRAY)
                }
            }
        },
        update = { view: View ->
            if (view is AppWidgetHostView && measuredSize != IntSize.Zero) {
                val widthDp = with(density) { measuredSize.width.toDp().value.toInt() }
                val heightDp = with(density) { measuredSize.height.toDp().value.toInt() }
                updateHostedWidgetSize(view, widthDp, heightDp)
            }
        }
    )
}

@Suppress("DEPRECATION")
private fun updateHostedWidgetSize(view: AppWidgetHostView, widthDp: Int, heightDp: Int) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        view.updateAppWidgetSize(
            Bundle(),
            listOf(SizeF(widthDp.toFloat(), heightDp.toFloat()))
        )
    } else {
        view.updateAppWidgetSize(null, widthDp, heightDp, widthDp, heightDp)
    }
}

@Composable
fun AndroidWidgetEditPlaceholder(
    label: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Widgets,
            contentDescription = null,
            tint = accent.copy(alpha = 0.75f),
            modifier = Modifier.size(34.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "ANDROID WIDGET · TAP TO EDIT",
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.38f),
            fontSize = 7.sp,
            letterSpacing = 1.sp
        )
    }
}

@Composable
fun AndroidWidgetPickerDialog(
    providers: List<AppWidgetProviderInfo>,
    accent: Color,
    isDayMode: Boolean,
    onSelect: (AppWidgetProviderInfo) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val packageManager = context.packageManager
    val bg = if (isDayMode) Color(0xFFF4F4F4) else Color(0xFF0C0C0C)
    val border = if (isDayMode) Color(0xFFD0D0D0) else Color(0xFF242424)
    val primary = if (isDayMode) Color(0xFF171717) else Color.White
    val secondary = if (isDayMode) Color(0xFF666666) else Color.White.copy(alpha = 0.48f)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 380.dp, max = 620.dp)
                .fillMaxWidth(0.72f)
                .height(430.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(bg)
                .border(1.dp, border, RoundedCornerShape(6.dp))
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Widgets, null, tint = accent, modifier = Modifier.size(19.dp))
                Spacer(Modifier.size(9.dp))
                Column {
                    Text(
                        "ANDROID WIDGETS",
                        color = primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.4.sp
                    )
                    Text(
                        "Choose a widget supplied by an installed app",
                        color = secondary,
                        fontSize = 8.sp
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Default.Close, "Close", tint = secondary, modifier = Modifier.size(17.dp))
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = border)

            if (providers.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("NO COMPATIBLE WIDGETS FOUND", color = secondary, fontSize = 9.sp)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(providers, key = { it.provider.flattenToString() }) { provider ->
                        val widgetLabel = remember(provider.provider) {
                            provider.loadLabel(packageManager).toString()
                        }
                        val appLabel = remember(provider.provider) {
                            runCatching {
                                val appInfo = packageManager.getApplicationInfo(provider.provider.packageName, 0)
                                packageManager.getApplicationLabel(appInfo).toString()
                            }.getOrDefault(provider.provider.packageName)
                        }
                        val iconBitmap = remember(provider.provider) {
                            runCatching {
                                packageManager.getApplicationIcon(provider.provider.packageName)
                                    .toBitmap(64, 64)
                            }.getOrNull()
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(provider) }
                                .padding(horizontal = 8.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (iconBitmap != null) {
                                Image(
                                    bitmap = iconBitmap.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.size(36.dp)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(accent.copy(alpha = 0.12f), RoundedCornerShape(5.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Widgets, null, tint = accent, modifier = Modifier.size(20.dp))
                                }
                            }
                            Spacer(Modifier.size(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    widgetLabel,
                                    color = primary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    appLabel,
                                    color = secondary,
                                    fontSize = 8.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                "${provider.minWidth} × ${provider.minHeight}",
                                color = secondary,
                                fontSize = 7.sp
                            )
                        }
                        HorizontalDivider(color = border.copy(alpha = 0.65f))
                    }
                }
            }
        }
    }
}
