package com.phosphor.icons.regular

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.phosphor.icons.PhIcons

val PhIcons.Regular.Wrench: ImageVector
    get() {
        if (_Wrench != null) {
            return _Wrench!!
        }
        _Wrench = ImageVector.Builder(
            name = "Regular.Wrench",
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
                moveTo(104f, 126.94f)
                arcToRelative(64f, 64f, 0f, isMoreThanHalf = false, isPositiveArc = true, 80f, -90.29f)
                lineTo(144f, 80f)
                lineToRelative(5.66f, 26.34f)
                lineTo(176f, 112f)
                lineToRelative(43.35f, -40f)
                arcToRelative(64f, 64f, 0f, isMoreThanHalf = false, isPositiveArc = true, -90.29f, 80f)
                lineTo(73f, 217f)
                arcTo(24f, 24f, 0f, isMoreThanHalf = false, isPositiveArc = true, 39f, 183f)
                close()
            }
        }.build()

        return _Wrench!!
    }

@Suppress("ObjectPropertyName")
private var _Wrench: ImageVector? = null
