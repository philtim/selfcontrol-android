package com.t7lab.focustime.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.t7lab.focustime.ui.theme.LocalSessionColors
import com.t7lab.focustime.ui.theme.TimerTypography
import androidx.compose.ui.platform.LocalContext
import com.t7lab.focustime.util.formatDuration
import com.t7lab.focustime.util.formatEndTime

@Composable
fun FocusTimerRing(
    remainingTimeMs: Long,
    endTimeMs: Long,
    durationMs: Long,
    modifier: Modifier = Modifier,
    ringSize: Dp = 280.dp,
    strokeWidth: Dp = 12.dp,
    showEndTime: Boolean = true,
) {
    val sessionColors = LocalSessionColors.current
    val context = LocalContext.current

    val progress = if (durationMs > 0) {
        1f - ((endTimeMs - System.currentTimeMillis()).toFloat() / durationMs).coerceIn(0f, 1f)
    } else 0f

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(1000),
        label = "ring_progress"
    )

    // Breathing animation: oscillate stroke alpha 0.85-1.0 over 4s
    val infiniteTransition = rememberInfiniteTransition(label = "breathing")
    val breathingAlpha by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing_alpha"
    )

    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        Canvas(modifier = Modifier.size(ringSize)) {
            val stroke = strokeWidth.toPx()
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(stroke / 2, stroke / 2)

            // Track (muted background)
            drawArc(
                color = sessionColors.timerRingTrack,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )

            // Progress fill (grows as time elapses)
            if (animatedProgress > 0f) {
                drawArc(
                    color = sessionColors.timerRingFill.copy(alpha = breathingAlpha),
                    startAngle = -90f,
                    sweepAngle = 360f * animatedProgress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = formatDuration(remainingTimeMs),
                style = TimerTypography,
                color = sessionColors.timerText
            )
            if (showEndTime && endTimeMs > 0) {
                Text(
                    text = formatEndTime(endTimeMs, context),
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = sessionColors.timerText.copy(alpha = 0.6f)
                )
            }
        }
    }
}
