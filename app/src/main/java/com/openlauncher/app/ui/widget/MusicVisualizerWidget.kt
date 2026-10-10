package com.openlauncher.app.ui.widget

import android.Manifest
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.openlauncher.app.data.MusicVisualizerStyle
import com.openlauncher.app.model.NowPlayingState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Live FFT visualization of the device output mix. The Visualizer API requires
 * RECORD_AUDIO even though this widget never records or stores audio.
 */
@Composable
fun MusicVisualizerWidget(
    state: NowPlayingState?,
    accent: Color,
    isDayMode: Boolean = false,
    style: MusicVisualizerStyle = MusicVisualizerStyle.MIRRORED_BARS,
    isEditing: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isPlaying = state?.isPlaying == true
    val spectrumBandCount = 64
    val levels = remember {
        mutableStateListOf<Float>().apply { repeat(spectrumBandCount) { add(0.025f) } }
    }
    val waveform = remember {
        mutableStateListOf<Float>().apply { repeat(96) { add(0f) } }
    }
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var captureAvailable by remember { mutableStateOf(true) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
    }

    DisposableEffect(hasAudioPermission) {
        if (!hasAudioPermission) {
            levels.indices.forEach { levels[it] = 0.025f }
            waveform.indices.forEach { waveform[it] = 0f }
            onDispose { }
        } else {
            val mainHandler = Handler(Looper.getMainLooper())
            var visualizer: Visualizer? = null
            var disposed = false

            try {
                visualizer = Visualizer(0).apply {
                    enabled = false
                    captureSize = Visualizer.getCaptureSizeRange().last()
                    scalingMode = Visualizer.SCALING_MODE_NORMALIZED
                    setDataCaptureListener(
                        object : Visualizer.OnDataCaptureListener {
                            override fun onWaveFormDataCapture(
                                visualizer: Visualizer?,
                                waveformData: ByteArray?,
                                samplingRate: Int
                            ) {
                                if (waveformData == null || waveformData.isEmpty() || disposed) return
                                val next = waveformToPoints(waveformData, waveform.size)
                                mainHandler.post {
                                    if (disposed) return@post
                                    next.forEachIndexed { index, value ->
                                        waveform[index] = waveform[index] * 0.3f + value * 0.7f
                                    }
                                }
                            }

                            override fun onFftDataCapture(
                                visualizer: Visualizer?,
                                fft: ByteArray?,
                                samplingRate: Int
                            ) {
                                if (fft == null || fft.size < 8 || disposed) return
                                val next = fftToLevels(fft, spectrumBandCount)
                                mainHandler.post {
                                    if (disposed) return@post
                                    next.forEachIndexed { index, value ->
                                        val old = levels[index]
                                        levels[index] = if (value >= old) {
                                            old * 0.25f + value * 0.75f
                                        } else {
                                            old * 0.72f + value * 0.28f
                                        }
                                    }
                                }
                            }
                        },
                        Visualizer.getMaxCaptureRate() / 2,
                        true,
                        true
                    )
                    enabled = true
                }
                captureAvailable = true
            } catch (_: RuntimeException) {
                captureAvailable = false
                visualizer?.release()
                visualizer = null
            }

            onDispose {
                disposed = true
                mainHandler.removeCallbacksAndMessages(null)
                runCatching { visualizer?.enabled = false }
                runCatching { visualizer?.release() }
            }
        }
    }

    val primaryText = if (isDayMode) Color(0xFF161616) else MaterialTheme.colorScheme.onBackground
    val secondaryText = if (isDayMode) Color(0xFF666666) else primaryText.copy(alpha = 0.42f)
    val track = if (isDayMode) Color.Black.copy(alpha = 0.08f) else primaryText.copy(alpha = 0.07f)
    val status = when {
        !hasAudioPermission -> "TAP TO ENABLE LIVE AUDIO"
        !captureAvailable -> "AUDIO CAPTURE UNAVAILABLE"
        state == null || state.title.isBlank() -> "LISTENING FOR AUDIO"
        isPlaying -> "PLAYING · LIVE SPECTRUM"
        else -> "PAUSED"
    }

    Column(
        modifier = modifier
            .then(
                if (!hasAudioPermission && !isEditing) {
                    Modifier.clickable {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                } else {
                    Modifier
                }
            )
            .padding(start = 14.dp, end = 14.dp, top = 24.dp, bottom = 9.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            val linearBarCount = 30
            val slotWidth = size.width / linearBarCount
            val strokeWidth = (slotWidth * 0.62f).coerceAtLeast(2.dp.toPx())
            val centerY = size.height / 2f
            val maximumHalfHeight = (size.height * 0.43f).coerceAtLeast(strokeWidth)
            val minimumHalfHeight = strokeWidth * 0.55f

            fun linearLevel(index: Int): Float {
                val sourceIndex = (index.toFloat() / (linearBarCount - 1) * (levels.size - 1))
                    .roundToInt()
                    .coerceIn(0, levels.lastIndex)
                return levels[sourceIndex].coerceIn(0f, 1f)
            }

            when (style) {
                MusicVisualizerStyle.CLASSIC_BARS -> {
                    val baseline = size.height * 0.94f
                    val maximumHeight = size.height * 0.84f
                    repeat(linearBarCount) { index ->
                        val level = linearLevel(index)
                        val x = slotWidth * (index + 0.5f)
                        val barColor = neonGradient(index.toFloat() / (linearBarCount - 1))
                        drawLine(
                            color = track,
                            start = Offset(x, baseline - maximumHeight),
                            end = Offset(x, baseline),
                            strokeWidth = strokeWidth,
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            color = barColor.copy(alpha = 0.72f + level * 0.28f),
                            start = Offset(x, baseline),
                            end = Offset(x, baseline - maximumHeight * level.coerceAtLeast(0.035f)),
                            strokeWidth = strokeWidth,
                            cap = StrokeCap.Round
                        )
                    }
                }

                MusicVisualizerStyle.MIRRORED_BARS -> repeat(linearBarCount) { index ->
                    val level = linearLevel(index)
                    val halfHeight = (minimumHalfHeight + maximumHalfHeight * level)
                        .coerceAtMost(maximumHalfHeight)
                    val x = slotWidth * (index + 0.5f)
                    val barColor = neonGradient(index.toFloat() / (linearBarCount - 1))
                    drawLine(
                        color = track,
                        start = Offset(x, centerY - maximumHalfHeight),
                        end = Offset(x, centerY + maximumHalfHeight),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = barColor.copy(alpha = 0.72f + level * 0.28f),
                        start = Offset(x, centerY - halfHeight),
                        end = Offset(x, centerY + halfHeight),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                }

                MusicVisualizerStyle.CIRCULAR_SPECTRUM,
                MusicVisualizerStyle.RING_OF_FIRE -> {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val minimumDimension = minOf(size.width, size.height)
                    val bass = levels.take(5).average().toFloat().coerceIn(0f, 1f)
                    val ringRadius = minimumDimension * (0.19f + bass * 0.025f)
                    val maximumSpike = minimumDimension *
                        if (style == MusicVisualizerStyle.RING_OF_FIRE) 0.27f else 0.22f
                    // The captured FFT only needs 64 bands, but drawing those bands directly
                    // around a circle leaves obvious gaps at widget sizes. Interpolate a denser
                    // set of spokes so the circular edge remains crisp on large displays.
                    val spokeCount = 128
                    val spokeSpacing = (2f * PI.toFloat() * ringRadius) / spokeCount
                    val radialStroke = (spokeSpacing * 0.48f).coerceAtLeast(0.75.dp.toPx())

                    fun circularLevel(spoke: Int): Float {
                        val source = spoke.toFloat() * levels.size / spokeCount
                        val lowerIndex = source.toInt().coerceIn(0, levels.lastIndex)
                        val upperIndex = (lowerIndex + 1).coerceAtMost(levels.lastIndex)
                        val mix = source - lowerIndex
                        val interpolated = levels[lowerIndex] * (1f - mix) + levels[upperIndex] * mix
                        val previous = levels[(lowerIndex - 1).coerceAtLeast(0)]
                        val next = levels[(upperIndex + 1).coerceAtMost(levels.lastIndex)]
                        return (interpolated * 0.72f + (previous + next) * 0.14f)
                            .coerceIn(0f, 1f)
                    }

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                VisualizerCyan.copy(alpha = 0.95f),
                                VisualizerMagenta.copy(alpha = 0.50f),
                                VisualizerViolet.copy(alpha = 0.12f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = ringRadius * 0.78f
                        ),
                        radius = ringRadius * 0.78f,
                        center = center
                    )

                    if (style == MusicVisualizerStyle.RING_OF_FIRE) {
                        drawCircle(
                            color = VisualizerViolet.copy(alpha = 0.11f + bass * 0.14f),
                            radius = ringRadius + radialStroke * 2.5f,
                            center = center,
                            style = Stroke(width = radialStroke * 4f)
                        )
                        drawCircle(
                            color = VisualizerCyan.copy(alpha = 0.82f),
                            radius = ringRadius,
                            center = center,
                            style = Stroke(width = radialStroke * 0.72f)
                        )
                    }

                    repeat(spokeCount) { index ->
                        val level = circularLevel(index)
                        val fraction = index.toFloat() / spokeCount
                        val angle = -PI.toFloat() / 2f + (2f * PI.toFloat() * fraction)
                        val inner = ringRadius + radialStroke
                        val length = maximumSpike * (0.10f + level * 0.90f)
                        val spikeColor = if (style == MusicVisualizerStyle.CIRCULAR_SPECTRUM) {
                            Color.hsv((fraction * 360f + 175f) % 360f, 0.72f, 0.95f)
                        } else {
                            neonGradient(fraction)
                        }
                        val start = Offset(
                            center.x + cos(angle) * inner,
                            center.y + sin(angle) * inner
                        )
                        val end = Offset(
                            center.x + cos(angle) * (inner + length),
                            center.y + sin(angle) * (inner + length)
                        )
                        if (style == MusicVisualizerStyle.RING_OF_FIRE) {
                            drawLine(
                                color = spikeColor.copy(alpha = 0.10f + level * 0.17f),
                                start = start,
                                end = end,
                                strokeWidth = radialStroke * 3f,
                                cap = StrokeCap.Round
                            )
                        }
                        drawLine(
                            color = spikeColor.copy(alpha = 0.60f + level * 0.40f),
                            start = start,
                            end = end,
                            strokeWidth = radialStroke,
                            cap = StrokeCap.Round
                        )
                    }
                }

                MusicVisualizerStyle.WAVEFORM -> {
                    val path = Path()
                    val amplitude = size.height * 0.42f
                    waveform.forEachIndexed { index, sample ->
                        val x = size.width * index / (waveform.size - 1).coerceAtLeast(1)
                        val y = centerY - sample.coerceIn(-1f, 1f) * amplitude
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawLine(
                        color = track,
                        start = Offset(0f, centerY),
                        end = Offset(size.width, centerY),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawPath(
                        path = path,
                        brush = Brush.horizontalGradient(
                            listOf(VisualizerCyan, VisualizerBlue, VisualizerViolet, VisualizerMagenta)
                        ),
                        style = Stroke(
                            width = 2.2.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
            }
        }

        Spacer(Modifier.height(7.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Canvas(modifier = Modifier.size(6.dp)) {
                drawCircle(if (captureAvailable && hasAudioPermission) accent else secondaryText.copy(alpha = 0.5f))
            }
            Spacer(Modifier.size(7.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state?.title?.takeIf { it.isNotBlank() } ?: "Music visualizer",
                    color = primaryText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = status,
                    color = if (isPlaying && captureAvailable) accent.copy(alpha = 0.8f) else secondaryText,
                    fontSize = 6.sp,
                    letterSpacing = 1.2.sp,
                    maxLines = 1
                )
            }
        }
    }
}

/** Converts Android's interleaved complex FFT bytes into logarithmic bands. */
private fun fftToLevels(fft: ByteArray, barCount: Int): FloatArray {
    val complexBins = fft.size / 2
    val highestUsefulBin = (complexBins * 0.72f).roundToInt().coerceAtLeast(2)

    return FloatArray(barCount) { band ->
        // Quadratic spacing gives bass and mid frequencies more visual resolution.
        val startRatio = band.toFloat() / barCount
        val endRatio = (band + 1f) / barCount
        val startBin = (1 + startRatio * startRatio * (highestUsefulBin - 1))
            .roundToInt().coerceIn(1, complexBins - 1)
        val endBin = (1 + endRatio * endRatio * (highestUsefulBin - 1))
            .roundToInt().coerceIn(startBin + 1, complexBins)

        var magnitudeTotal = 0f
        var samples = 0
        for (bin in startBin until endBin) {
            val real = fft[bin * 2].toInt().toFloat()
            val imaginary = fft[bin * 2 + 1].toInt().toFloat()
            magnitudeTotal += sqrt(real * real + imaginary * imaginary)
            samples++
        }

        val averageMagnitude = if (samples > 0) magnitudeTotal / samples else 0f
        (ln(1f + averageMagnitude) / ln(182f) * 1.28f).coerceIn(0.025f, 1f)
    }
}

private fun waveformToPoints(waveform: ByteArray, pointCount: Int): FloatArray {
    if (pointCount <= 0) return FloatArray(0)
    return FloatArray(pointCount) { index ->
        val sourceIndex = (index.toFloat() / (pointCount - 1).coerceAtLeast(1) * (waveform.size - 1))
            .roundToInt()
            .coerceIn(0, waveform.lastIndex)
        (((waveform[sourceIndex].toInt() and 0xFF) - 128) / 128f).coerceIn(-1f, 1f)
    }
}

private val VisualizerCyan = Color(0xFF62E7F2)
private val VisualizerBlue = Color(0xFF5B8FF7)
private val VisualizerViolet = Color(0xFF6747FF)
private val VisualizerMagenta = Color(0xFFE117F4)

private fun neonGradient(fraction: Float): Color {
    val value = fraction.coerceIn(0f, 1f)
    return when {
        value < 0.34f -> lerp(VisualizerCyan, VisualizerBlue, value / 0.34f)
        value < 0.67f -> lerp(VisualizerBlue, VisualizerViolet, (value - 0.34f) / 0.33f)
        else -> lerp(VisualizerViolet, VisualizerMagenta, (value - 0.67f) / 0.33f)
    }
}
