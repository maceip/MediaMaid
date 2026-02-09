package ai.musicconverter.ui.components

import android.app.Activity
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * Wraps content with a CRT television close effect on predictive back gesture.
 *
 * Phase 1 (Slam): The screen compresses vertically downward, like the top half
 * of a CRT monitor slamming shut onto the bottom bar.
 *
 * Phase 2 (Shrink): The flattened view scales down uniformly toward center
 * and fades away, like an old CRT turning off — ending with the classic
 * bright horizontal line that fades to a dot.
 */
@Composable
fun CrtCloseWrapper(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val animatedProgress = remember { Animatable(0f) }
    var isGestureActive by remember { mutableStateOf(false) }

    PredictiveBackHandler(enabled = true) { backEvents ->
        isGestureActive = true
        try {
            backEvents.collect { event ->
                animatedProgress.snapTo(event.progress)
            }
            // User committed — animate to full close
            animatedProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(300, easing = FastOutSlowInEasing)
            )
            // Finish the activity
            (context as? Activity)?.finish()
        } catch (_: CancellationException) {
            // User cancelled — spring back
            animatedProgress.animateTo(
                targetValue = 0f,
                animationSpec = tween(250, easing = FastOutSlowInEasing)
            )
            isGestureActive = false
        }
    }

    val p = animatedProgress.value

    Box(modifier = Modifier.fillMaxSize()) {
        // Main content with CRT slam + shrink transform
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (p > 0f) {
                        // Phase 1: Slam (progress 0.0 → 0.5)
                        // Content compresses vertically, bottom edge stays anchored
                        val slamFraction = (p * 2f).coerceIn(0f, 1f)

                        // Phase 2: Shrink (progress 0.5 → 1.0)
                        // Flattened view scales down and fades like CRT off
                        val shrinkFraction = ((p - 0.5f) * 2f).coerceIn(0f, 1f)

                        // Slam: compress Y from 1.0 down to 0.08 (92% crush)
                        val slamScaleY = 1f - (slamFraction * 0.92f)
                        // Shrink: uniform scale from 1.0 to 0.0
                        val shrinkScale = 1f - shrinkFraction

                        scaleY = slamScaleY * shrinkScale
                        scaleX = if (shrinkFraction > 0f) {
                            // During shrink, X also compresses
                            1f - (shrinkFraction * 0.85f)
                        } else {
                            // During slam, X stays at 1.0 (only vertical crush)
                            1f
                        }

                        // Keep bottom anchored during slam by shifting transform origin down
                        transformOrigin = TransformOrigin(
                            0.5f,
                            if (shrinkFraction > 0f) 0.5f else 0.85f
                        )

                        // Fade during shrink phase
                        alpha = if (shrinkFraction > 0f) {
                            (1f - shrinkFraction * 0.95f).coerceIn(0f, 1f)
                        } else {
                            1f
                        }

                        // Subtle perspective tilt during slam
                        rotationX = slamFraction * 3f * (1f - shrinkFraction)
                        cameraDistance = 16f * density
                    }
                }
        ) {
            content()
        }

        // CRT scanline overlay — visible during gesture
        if (p > 0.05f) {
            Canvas(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = (p * 2f).coerceIn(0f, 0.6f) }) {
                // Horizontal scanlines across the screen
                val lineSpacing = 4f
                var y = 0f
                while (y < size.height) {
                    drawLine(
                        Color.Black.copy(alpha = 0.12f),
                        Offset(0f, y),
                        Offset(size.width, y),
                        strokeWidth = 1f
                    )
                    y += lineSpacing
                }
            }
        }

        // CRT bright line / dot at the end of shrink phase
        if (p > 0.7f) {
            val lineProgress = ((p - 0.7f) / 0.3f).coerceIn(0f, 1f)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cy = size.height / 2f
                val cx = size.width / 2f

                // Bright horizontal line that shrinks to a dot
                val lineWidth = size.width * (1f - lineProgress * 0.95f)
                val lineHeight = 3f - lineProgress * 2f

                drawLine(
                    Color.White.copy(alpha = (0.8f * (1f - lineProgress * 0.5f))),
                    Offset(cx - lineWidth / 2f, cy),
                    Offset(cx + lineWidth / 2f, cy),
                    strokeWidth = lineHeight.coerceAtLeast(1f)
                )

                // Bright center dot that persists and fades
                if (lineProgress > 0.5f) {
                    val dotAlpha = ((1f - lineProgress) * 2f).coerceIn(0f, 1f)
                    drawCircle(
                        Color.White.copy(alpha = dotAlpha * 0.9f),
                        radius = 4f * (1f - lineProgress * 0.5f),
                        center = Offset(cx, cy)
                    )
                    // Glow around dot
                    drawCircle(
                        Color.White.copy(alpha = dotAlpha * 0.3f),
                        radius = 12f * (1f - lineProgress * 0.3f),
                        center = Offset(cx, cy)
                    )
                }
            }
        }
    }
}
