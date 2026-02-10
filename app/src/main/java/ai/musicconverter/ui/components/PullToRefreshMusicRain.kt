package ai.musicconverter.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import kotlin.math.abs
import kotlin.math.sin
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
 * on a dark phosphor-green display. Designed to overlay the
 * CalculatorProgressBar when a pull-to-refresh is triggered.
 */
@Composable
fun DotMatrixMusicRain(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    onComplete: () -> Unit = {}
) {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 1800, easing = LinearEasing)
            )
            onComplete()
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

            // Fade in at start, fade out near end
            val alpha = when {
                t < 0.06f -> t / 0.06f
                p > 0.82f -> ((1f - p) / 0.18f).coerceIn(0f, 1f)
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

private val PULL_THRESHOLD = 72.dp

/**
 * Wraps scrollable content (e.g. LazyColumn) to add pull-to-refresh
 * behavior. Shows a retro dot-matrix music note indicator while
 * pulling, and triggers [onRefresh] when released past the threshold.
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
                // Consume upward scroll to reduce pull indicator
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
                // Capture overscroll when content is at top
                if (available.y > 0) {
                    pullDistance = (pullDistance + available.y * 0.5f)
                        .coerceAtMost(thresholdPx * 2f)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val triggered = pullDistance >= thresholdPx
                // Smooth snap-back
                val anim = Animatable(pullDistance)
                anim.animateTo(0f, tween(220)) { pullDistance = value }
                if (triggered) onRefresh()
                return Velocity.Zero
            }
        }
    }

    val pullFraction = (pullDistance / thresholdPx).coerceIn(0f, 1.5f)

    Box(modifier = modifier.nestedScroll(connection)) {
        // Content shifts down by pull distance
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset(y = with(density) { pullDistance.toDp() })
        ) {
            content()
        }

        // Pull indicator overlay
        if (pullDistance > 0f) {
            PullMusicIndicator(
                pullFraction = pullFraction,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(with(density) { pullDistance.toDp() })
                    .align(Alignment.TopCenter)
            )
        }
    }
}

// ── Pull Indicator (retro display strip) ────────────────────────

@Composable
private fun PullMusicIndicator(
    pullFraction: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pullIndicator")
    val wobble by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wobble"
    )

    val pastThreshold = pullFraction >= 1f

    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
            .background(DisplayDark)
    ) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val noteAlpha = (pullFraction * 0.85f).coerceIn(0f, 0.92f)

        // Scale the central note with pull distance
        val scale = pullFraction.coerceIn(0f, 1.2f)
        val px = 3.dp.toPx() * scale
        if (px < 0.5f) return@Canvas

        val wiggle = if (pastThreshold) sin(wobble.toDouble()).toFloat() * 3.dp.toPx() else 0f

        // Central eighth note
        val pattern = NOTE_EIGHTH
        val noteW = pattern.maxOf { it.length } * px
        val noteH = pattern.size * px

        drawDotMatrixPattern(
            pattern = pattern,
            topLeft = Offset(cx - noteW / 2 + wiggle, cy - noteH / 2),
            pixelSize = px,
            color = PhosphorGreen.copy(alpha = noteAlpha)
        )

        // Flanking quarter notes (appear after 40% pull)
        if (pullFraction > 0.4f) {
            val sideAlpha = ((pullFraction - 0.4f) / 0.6f).coerceIn(0f, 0.65f)
            val sidePx = px * 0.6f
            val sidePattern = NOTE_QUARTER
            val sideH = sidePattern.size * sidePx
            val sideWiggle = if (pastThreshold) -wiggle * 0.6f else 0f

            drawDotMatrixPattern(
                pattern = sidePattern,
                topLeft = Offset(cx - noteW * 2f + sideWiggle, cy - sideH / 2),
                pixelSize = sidePx,
                color = PhosphorGreen.copy(alpha = sideAlpha)
            )
            drawDotMatrixPattern(
                pattern = sidePattern,
                topLeft = Offset(cx + noteW * 1.3f - sideWiggle, cy - sideH / 2),
                pixelSize = sidePx,
                color = PhosphorGreen.copy(alpha = sideAlpha)
            )
        }

        // "Release to refresh" bounce dots when past threshold
        if (pastThreshold) {
            val dotY = cy + noteH / 2f + 5.dp.toPx()
            val dotR = 1.5.dp.toPx()
            val bounce = sin(wobble.toDouble() * 2).toFloat() * 2.dp.toPx()
            for (i in -1..1) {
                drawCircle(
                    color = PhosphorGreen.copy(alpha = 0.55f),
                    radius = dotR,
                    center = Offset(
                        cx + i * 5.dp.toPx(),
                        dotY + abs(i) * 2.dp.toPx() + bounce
                    )
                )
            }
        }

        // Scanlines
        for (y in 0..size.height.toInt() step 2) {
            drawLine(
                Color.Black.copy(alpha = 0.08f),
                Offset(0f, y.toFloat()),
                Offset(size.width, y.toFloat()),
                0.5f
            )
        }
    }
}
