package ai.musicconverter.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.random.Random

// ── Dot-Matrix Music Note Pixel Patterns ────────────────────────
// '#' = lit pixel, '.' = off

private val NOTE_EIGHTH = listOf(   // ♪ single eighth note
    "..##",
    "..#.",
    "..#.",
    "..#.",
    ".##.",
    "###.",
    ".#..",
)

private val NOTE_BEAMED = listOf(   // ♫ beamed eighth notes
    ".####",
    ".#..#",
    ".#..#",
    ".#..#",
    "##.##",
    "##.##",
)

private val NOTE_QUARTER = listOf(  // quarter note
    ".#",
    ".#",
    ".#",
    ".#",
    "##",
    "##",
)

private val NOTE_SIXTEENTH = listOf( // sixteenth note
    ".###",
    ".##.",
    ".#..",
    ".#..",
    "##..",
    "##..",
)

private val NOTE_FLAT = listOf(     // ♭ flat sign
    "#..",
    "#..",
    "##.",
    "#.#",
    "##.",
)

private val ALL_NOTES = listOf(
    NOTE_EIGHTH, NOTE_BEAMED, NOTE_QUARTER,
    NOTE_SIXTEENTH, NOTE_FLAT, NOTE_EIGHTH, NOTE_QUARTER
)

// Phosphor green matching the DigitalDisplay aesthetic
private val PhosphorGreen = Color(0xFF00FF88)
private val DisplayDark = Color(0xFF1A1A1A)
private val DisplayBorder = Color(0xFF3A3A3A)

// ── Dot-Matrix Pixel Rendering ──────────────────────────────────

private fun DrawScope.drawDotMatrixPattern(
    pattern: List<String>,
    topLeft: Offset,
    pixelSize: Float,
    color: Color
) {
    val gap = pixelSize * 0.22f
    val dotSize = pixelSize - gap

    pattern.forEachIndexed { row, line ->
        line.forEachIndexed { col, ch ->
            if (ch == '#') {
                val x = topLeft.x + col * pixelSize
                val y = topLeft.y + row * pixelSize
                // Soft glow halo
                drawRect(
                    color = color.copy(alpha = color.alpha * 0.2f),
                    topLeft = Offset(x - gap, y - gap),
                    size = Size(dotSize + gap * 2, dotSize + gap * 2)
                )
                // Lit pixel
                drawRect(
                    color = color,
                    topLeft = Offset(x, y),
                    size = Size(dotSize, dotSize)
                )
            }
        }
    }
}

// ── Rain Particle Data ──────────────────────────────────────────

private data class RainNote(
    val xFraction: Float,
    val startDelay: Float,
    val noteIndex: Int,
    val speed: Float,
    val pixelSize: Float
)

// ── Dot-Matrix Music Rain (calculator display overlay) ──────────

/**
 * Animated overlay that renders falling dot-matrix music notes
 * on a dark phosphor-green display. Supports both one-shot mode
 * (for transitions) and looping mode (for persistent bottom bar display).
 */
@Composable
fun DotMatrixMusicRain(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    loop: Boolean = false,
    onComplete: () -> Unit = {}
) {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(isPlaying, loop) {
        if (isPlaying) {
            if (loop) {
                // Loop the animation continuously while scanning
                while (true) {
                    progress.snapTo(0f)
                    progress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 2400, easing = LinearEasing)
                    )
                }
            } else {
                progress.snapTo(0f)
                progress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 1800, easing = LinearEasing)
                )
                onComplete()
            }
        } else {
            progress.snapTo(0f)
        }
    }

    if (progress.value <= 0f) return

    val notes = remember {
        List(18) { i ->
            RainNote(
                xFraction = i / 18f + Random.nextFloat() * 0.03f,
                startDelay = Random.nextFloat() * 0.35f,
                noteIndex = i % ALL_NOTES.size,
                speed = 0.7f + Random.nextFloat() * 0.6f,
                pixelSize = 1.1f + Random.nextFloat() * 0.7f
            )
        }
    }

    val p = progress.value

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Dark display background
        drawRoundRect(
            color = DisplayDark.copy(alpha = 0.94f),
            size = size,
            cornerRadius = CornerRadius(6.dp.toPx())
        )
        // Border
        drawRoundRect(
            color = DisplayBorder,
            size = size,
            cornerRadius = CornerRadius(6.dp.toPx()),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
        )

        // Falling notes
        notes.forEach { note ->
            val t = ((p - note.startDelay) / (1f - note.startDelay)).coerceIn(0f, 1f)
            if (t <= 0f) return@forEach

            val yNorm = -0.3f + t * note.speed * 2.2f
            if (yNorm > 1.6f) return@forEach

            // In loop mode, no fade-out at end (seamless wrap)
            val alpha = when {
                t < 0.06f -> t / 0.06f
                !loop && p > 0.82f -> ((1f - p) / 0.18f).coerceIn(0f, 1f)
                else -> 1f
            } * 0.88f

            val pattern = ALL_NOTES[note.noteIndex]
            val px = note.pixelSize.dp.toPx()
            val noteW = pattern.maxOf { it.length } * px
            val xPos = note.xFraction * (w - noteW)
            val yPos = yNorm * h

            drawDotMatrixPattern(
                pattern = pattern,
                topLeft = Offset(xPos, yPos),
                pixelSize = px,
                color = PhosphorGreen.copy(alpha = alpha)
            )
        }

        // CRT scanline overlay
        for (y in 0..h.toInt() step 2) {
            drawLine(
                Color.Black.copy(alpha = 0.1f),
                Offset(0f, y.toFloat()),
                Offset(w, y.toFloat()),
                0.5f
            )
        }
    }
}

// ── Pull-to-Refresh Wrapper ─────────────────────────────────────

private val PULL_THRESHOLD = 120.dp

/**
 * Wraps scrollable content (e.g. LazyColumn) to add pull-to-refresh.
 * Content shifts down with the finger for natural gesture feedback.
 * On release, snaps back with a smooth-close drawer spring (no bounce,
 * fast deceleration). No visual indicators in the list — all scanning
 * feedback is delegated to the calculator display in the bottom bar.
 */
@Composable
fun MusicPullToRefreshBox(
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val thresholdPx = with(density) { PULL_THRESHOLD.toPx() }

    var pullDistance by remember { mutableFloatStateOf(0f) }

    val connection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // When pulled down and user scrolls back up, consume to retract
                if (pullDistance > 0 && available.y < 0) {
                    val consumed = available.y.coerceAtLeast(-pullDistance)
                    pullDistance = (pullDistance + consumed).coerceAtLeast(0f)
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                // Only accumulate from direct touch, not fling momentum.
                // This prevents pull-to-refresh from triggering when the
                // user scrolls up and the list hits the top with momentum.
                if (available.y > 0 && source == NestedScrollSource.UserInput) {
                    pullDistance = (pullDistance + available.y * 0.5f)
                        .coerceAtMost(thresholdPx * 2f)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val triggered = pullDistance >= thresholdPx

                // Smooth-close drawer spring: critically damped, high stiffness.
                // No bounce, fast decisive snap-back with smooth deceleration.
                val anim = Animatable(pullDistance)
                anim.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = 1.0f,   // critically damped — no overshoot
                        stiffness = 2500f      // fast snap, smooth deceleration
                    )
                ) {
                    pullDistance = value.coerceAtLeast(0f)
                }

                if (triggered) onRefresh()
                return Velocity.Zero
            }
        }
    }

    Box(modifier = modifier.nestedScroll(connection)) {
        // Content shifts down by pull distance — natural gesture feedback
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset(y = with(density) { pullDistance.toDp() })
        ) {
            content()
        }
    }
}

