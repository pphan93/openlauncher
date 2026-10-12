package com.phosphor.icons.regular

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.phosphor.icons.PhIcons

val PhIcons.Regular.CloudMoon: ImageVector
    get() {
        if (_CloudMoon != null) {
            return _CloudMoon!!
        }
        _CloudMoon = ImageVector.Builder(
            name = "Regular.CloudMoon",
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
                moveTo(63f, 138.93f)
                arcTo(64.12f, 64.12f, 0f, isMoreThanHalf = false, isPositiveArc = true, 25.62f, 94.38f)
                horizontalLineToRelative(0f)
                arcToRelative(64.06f, 64.06f, 0f, isMoreThanHalf = false, isPositiveArc = false, 76.76f, -76.76f)
                horizontalLineToRelative(0f)
                arcTo(64f, 64f, 0f, isMoreThanHalf = false, isPositiveArc = true, 152f, 80f)
                curveToRelative(0f, 1f, 0f, 2f, -0.07f, 3f)
            }
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 16f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(104f, 144f)
                arcToRelative(68.06f, 68.06f, 0f, isMoreThanHalf = true, isPositiveArc = true, 68f, 72f)
                horizontalLineTo(92f)
                arcToRelative(44f, 44f, 0f, isMoreThanHalf = true, isPositiveArc = true, 14.2f, -85.66f)
            }
        }.build()

        return _CloudMoon!!
    }

@Suppress("ObjectPropertyName")
private var _CloudMoon: ImageVector? = null
