package com.t7lab.focustime.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.t7lab.focustime.R
import com.t7lab.focustime.util.formatDuration
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private const val AUTO_DISMISS_DELAY_MS = 4000L
private const val FADE_OUT_DURATION_MS = 500

@Composable
fun SessionCompleteOverlay(
    completedDurationMs: Long,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val confettiColors = listOf(
        Color(0xFF1B6D3D),
        Color(0xFF4CAF50),
        Color(0xFFFFC107),
        Color(0xFF2196F3),
        Color(0xFFE91E63),
        Color(0xFF9C27B0),
        Color(0xFFFF5722),
    )

    val particles = remember {
        List(100) {
            ConfettiParticle(
                x = Random.nextFloat(),
                initialY = -Random.nextFloat() * 0.5f,
                speed = 0.15f + Random.nextFloat() * 0.25f,
                size = 4f + Random.nextFloat() * 8f,
                color = confettiColors[Random.nextInt(confettiColors.size)],
                drift = (Random.nextFloat() - 0.5f) * 0.2f,
                shape = ConfettiShape.entries[Random.nextInt(ConfettiShape.entries.size)],
                rotation = Random.nextFloat() * 360f,
            )
        }
    }

    // Infinite animation for continuous confetti movement
    val infiniteTransition = rememberInfiniteTransition(label = "confetti")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing)
        ),
        label = "confetti_progress"
    )

    // Track visibility for fade-out
    var isVisible by remember { mutableStateOf(true) }

    // Auto-dismiss after delay
    LaunchedEffect(Unit) {
        delay(AUTO_DISMISS_DELAY_MS)
        isVisible = false
        delay(FADE_OUT_DURATION_MS.toLong())
        onDismiss()
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(animationSpec = tween(FADE_OUT_DURATION_MS))
    ) {
        Surface(
            modifier = modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Confetti layer - continuous loop
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height

                    particles.forEach { particle ->
                        // Loop particles: when they go off screen, wrap back to top
                        val rawY = particle.initialY + progress * (1.5f + particle.speed)
                        val y = (rawY % 1.5f) - 0.2f  // Wrap and start slightly above screen

                        if (y in -0.2f..1.2f) {
                            val x = (particle.x + progress * particle.drift * 2f) % 1f
                            val alpha = when {
                                y < 0f -> (y + 0.2f) / 0.2f  // Fade in at top
                                y > 1f -> (1.2f - y) / 0.2f  // Fade out at bottom
                                else -> 1f
                            }.coerceIn(0f, 1f)

                            val center = Offset(x * canvasWidth, y * canvasHeight)
                            val particleRotation = particle.rotation + progress * 720f

                            when (particle.shape) {
                                ConfettiShape.CIRCLE -> {
                                    drawCircle(
                                        color = particle.color.copy(alpha = alpha),
                                        radius = particle.size,
                                        center = center
                                    )
                                }
                                ConfettiShape.RECT -> {
                                    rotate(particleRotation, pivot = center) {
                                        drawRect(
                                            color = particle.color.copy(alpha = alpha),
                                            topLeft = Offset(
                                                center.x - particle.size,
                                                center.y - particle.size * 0.5f
                                            ),
                                            size = Size(
                                                particle.size * 2f,
                                                particle.size
                                            )
                                        )
                                    }
                                }
                                ConfettiShape.STAR -> {
                                    rotate(particleRotation, pivot = center) {
                                        val starPath = Path().apply {
                                            val outerR = particle.size * 1.2f
                                            val innerR = particle.size * 0.5f
                                            for (i in 0 until 5) {
                                                val outerAngle =
                                                    Math.toRadians((i * 72 - 90).toDouble())
                                                val innerAngle =
                                                    Math.toRadians((i * 72 + 36 - 90).toDouble())
                                                val ox = center.x + outerR * cos(outerAngle).toFloat()
                                                val oy = center.y + outerR * sin(outerAngle).toFloat()
                                                val ix = center.x + innerR * cos(innerAngle).toFloat()
                                                val iy = center.y + innerR * sin(innerAngle).toFloat()
                                                if (i == 0) moveTo(ox, oy) else lineTo(ox, oy)
                                                lineTo(ix, iy)
                                            }
                                            close()
                                        }
                                        drawPath(
                                            path = starPath,
                                            color = particle.color.copy(alpha = alpha)
                                        )
                                    }
                                }
                                ConfettiShape.DIAMOND -> {
                                    rotate(particleRotation, pivot = center) {
                                        val diamondPath = Path().apply {
                                            moveTo(center.x, center.y - particle.size)
                                            lineTo(center.x + particle.size * 0.6f, center.y)
                                            lineTo(center.x, center.y + particle.size)
                                            lineTo(center.x - particle.size * 0.6f, center.y)
                                            close()
                                        }
                                        drawPath(
                                            path = diamondPath,
                                            color = particle.color.copy(alpha = alpha)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Content
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    AnimatedVisibility(
                        visible = true,
                        enter = scaleIn(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            )
                        ) + fadeIn()
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_achievement_star),
                            contentDescription = null,
                            tint = Color(0xFFFFC107),
                            modifier = Modifier.size(100.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = stringResource(R.string.well_done),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = stringResource(R.string.you_stayed_focused_for),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = formatDuration(completedDurationMs),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.session_complete_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

private enum class ConfettiShape {
    CIRCLE, RECT, STAR, DIAMOND
}

private data class ConfettiParticle(
    val x: Float,
    val initialY: Float,
    val speed: Float,
    val size: Float,
    val color: Color,
    val drift: Float,
    val shape: ConfettiShape = ConfettiShape.CIRCLE,
    val rotation: Float = 0f,
)
