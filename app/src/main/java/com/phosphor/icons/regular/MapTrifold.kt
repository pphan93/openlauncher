package com.phosphor.icons.regular

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.phosphor.icons.PhIcons

val PhIcons.Regular.MapTrifold: ImageVector
    get() {
        if (_MapTrifold != null) {
            return _MapTrifold!!
        }
        _MapTrifold = ImageVector.Builder(
            name = "Regular.MapTrifold",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 256f,
            viewportHeight = 256f
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 16f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(96f, 184f)
                lineTo(96f, 40f)
            }
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 16f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(160f, 72f)
                lineTo(160f, 216f)
            }
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 16f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(96f, 184f)
                lineToRelative(-64f, 16f)
                lineToRelative(0f, -144f)
                lineToRelative(64f, -16f)
                lineToRelative(64f, 32f)
                lineToRelative(64f, -16f)
                lineToRelative(0f, 144f)
                lineToRelative(-64f, 16f)
                lineToRelative(-64f, -32f)
                close()
            }
        }.build()

        return _MapTrifold!!
    }

@Suppress("ObjectPropertyName")
private var _MapTrifold: ImageVector? = null
