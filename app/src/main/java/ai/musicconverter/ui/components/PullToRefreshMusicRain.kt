package ai.musicconverter.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
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

// ── Aluminum-style gradient for pull reveal ──────────────────────

private val PullAluminumGradient = Brush.verticalGradient(
    colorStops = arrayOf(
        0.0f to Color(0xFFD4D4D6),   // light aluminum top
        0.5f to Color(0xFFCBCBCE),   // mid
        0.85f to Color(0xFFB8B8BC),  // darker below
        1.0f to Color(0xFFA4A4A8),   // dark aluminum bottom edge
    )
)

// ── Pull-to-Refresh Wrapper ─────────────────────────────────────

private val PULL_THRESHOLD = 72.dp

/**
 * Wraps scrollable content (e.g. LazyColumn) to add pull-to-refresh
 * behavior. Reveals a brushed-aluminum surface while pulling, and
 * snaps back with a soft-close drawer animation when released.
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
                // Soft-close drawer: high stiffness spring with
                // moderate damping for a firm, cushioned snap-back
                val anim = Animatable(pullDistance)
                anim.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) { pullDistance = value.coerceAtLeast(0f) }
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

        // Aluminum reveal behind the list
        if (pullDistance > 0f) {
            PullAluminumReveal(
                pullFraction = pullFraction,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(with(density) { pullDistance.toDp() })
                    .align(Alignment.TopCenter)
            )
        }
    }
}

// ── Pull Indicator (aluminum reveal strip) ──────────────────────

/**
 * Brushed aluminum surface revealed when pulling down, matching
 * the bottom bar material. Shows a subtle refresh arrow that
 * intensifies as the user approaches the threshold.
 */
@Composable
private fun PullAluminumReveal(
    pullFraction: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pullIndicator")
    val shimmer by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )

    val pastThreshold = pullFraction >= 1f

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
            .background(PullAluminumGradient)
            .drawBehind {
                // Brushed-metal horizontal grain lines (matches bottom bar)
                val lineSpacing = 1.5f
                var y = 0f
                while (y < size.height) {
                    val alpha = ((y / size.height) * 0.03f + 0.01f).coerceIn(0f, 0.05f)
                    drawLine(Color.Black.copy(alpha = alpha), Offset(0f, y), Offset(size.width, y), strokeWidth = 0.5f)
                    y += lineSpacing
                }
                // Top edge highlight
                drawLine(Color.White.copy(alpha = 0.7f), Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.5f)
                // Bottom shadow edge (where content meets)
                drawLine(Color.Black.copy(alpha = 0.15f), Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), strokeWidth = 1.5f)
            }
    ) {
        // Refresh arrow indicator drawn on the aluminum surface
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f

            // Circular refresh arrow (aluminum-embossed style)
            val arrowAlpha = (pullFraction * 0.6f).coerceIn(0f, 0.7f)
            val arrowColor = Color(0xFF555555).copy(alpha = arrowAlpha)
            val arrowRadius = 10.dp.toPx() * pullFraction.coerceIn(0.3f, 1f)
            val rotation = if (pastThreshold) shimmer * 57.3f else pullFraction * 270f

            // Draw arc
            drawArc(
                color = arrowColor,
                startAngle = rotation,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = Offset(cx - arrowRadius, cy - arrowRadius),
                size = Size(arrowRadius * 2, arrowRadius * 2),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
            )

            // Arrowhead at end of arc
            val headAngle = Math.toRadians((rotation + 270.0))
            val headX = cx + arrowRadius * kotlin.math.cos(headAngle).toFloat()
            val headY = cy + arrowRadius * kotlin.math.sin(headAngle).toFloat()
            drawCircle(
                color = arrowColor,
                radius = 2.5.dp.toPx(),
                center = Offset(headX, headY)
            )

            // Three bounce dots below arrow when past threshold
            if (pastThreshold) {
                val dotY = cy + arrowRadius + 8.dp.toPx()
                val dotR = 1.5.dp.toPx()
                val bounce = sin(shimmer.toDouble() * 2).toFloat() * 2.dp.toPx()
                for (i in -1..1) {
                    drawCircle(
                        color = Color(0xFF555555).copy(alpha = 0.5f),
                        radius = dotR,
                        center = Offset(
                            cx + i * 6.dp.toPx(),
                            dotY + abs(i) * 2.dp.toPx() + bounce
                        )
                    )
                }
            }
        }
    }
}
