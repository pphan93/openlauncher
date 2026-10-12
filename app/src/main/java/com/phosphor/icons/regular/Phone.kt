package com.phosphor.icons.regular

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.phosphor.icons.PhIcons

val PhIcons.Regular.Phone: ImageVector
    get() {
        if (_Phone != null) {
            return _Phone!!
        }
        _Phone = ImageVector.Builder(
            name = "Regular.Phone",
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
                moveTo(164.39f, 145.34f)
                arcToRelative(8f, 8f, 0f, isMoreThanHalf = false, isPositiveArc = true, 7.59f, -0.69f)
                lineToRelative(47.16f, 21.13f)
                arcToRelative(8f, 8f, 0f, isMoreThanHalf = false, isPositiveArc = true, 4.8f, 8.3f)
                arcTo(48.33f, 48.33f, 0f, isMoreThanHalf = false, isPositiveArc = true, 176f, 216f)
                arcTo(136f, 136f, 0f, isMoreThanHalf = false, isPositiveArc = true, 40f, 80f)
                arcTo(48.33f, 48.33f, 0f, isMoreThanHalf = false, isPositiveArc = true, 81.92f, 32.06f)
                arcToRelative(8f, 8f, 0f, isMoreThanHalf = false, isPositiveArc = true, 8.3f, 4.8f)
                lineToRelative(21.13f, 47.2f)
                arcToRelative(8f, 8f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.66f, 7.53f)
                lineTo(89.32f, 117f)
                arcToRelative(7.93f, 7.93f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.54f, 7.81f)
                curveToRelative(8.27f, 16.93f, 25.77f, 34.22f, 42.75f, 42.41f)
                arcToRelative(7.92f, 7.92f, 0f, isMoreThanHalf = false, isPositiveArc = false, 7.83f, -0.59f)
                close()
            }
        }.build()

        return _Phone!!
    }

@Suppress("ObjectPropertyName")
private var _Phone: ImageVector? = null
