package ai.musicconverter.ui.components

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ── AGSL: Two-tone brushed aluminum with ridgeline ─────────────

@Suppress("ConstPropertyName")
private const val AGSL_TWO_TONE_ALUMINUM = """
    uniform float2 size;
    uniform float3 lightPos;
    uniform float ridgeY;         // 0..1 fraction where ridge sits

    float hash(float2 p) {
        return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453);
    }

    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / size;

        // Fine-grained horizontal scratches
        float grain = hash(float2(fragCoord.x * 0.03, fragCoord.y * 12.0));

        // Specular sheen
        float distToLight = distance(fragCoord.x, lightPos.x * size.x);
        float sheen = exp(-0.004 * distToLight);

        // Two-tone: lighter above ridgeY, darker below
        float3 lightAlum = float3(0.80, 0.82, 0.84);
        float3 darkAlum  = float3(0.68, 0.70, 0.73);

        // Smooth blend at the ridge boundary
        float blend = smoothstep(ridgeY - 0.03, ridgeY + 0.03, uv.y);
        float3 baseColor = mix(lightAlum, darkAlum, blend);

        float3 finalColor = baseColor + (grain * 0.06) + (sheen * lightPos.z);

        return half4(finalColor, 1.0);
    }
"""

// ── Fallback two-tone gradient for API < 33 ───────────────────

private val FallbackTwoToneGradient = Brush.verticalGradient(
    colorStops = arrayOf(
        0.0f to Color(0xFFD8D8DA),
        0.15f to Color(0xFFE2E2E4),
        0.45f to Color(0xFFDCDCDE),
        0.65f to Color(0xFFD2D2D4),  // ridge transition
        0.72f to Color(0xFFBBBBBE),
        0.85f to Color(0xFFB0B0B4),
        1.0f to Color(0xFFA8A8AC),
    )
)

// ── Button gradients ───────────────────────────────────────────

private val ButtonGlossGradient = Brush.verticalGradient(
    colors = listOf(
        Color(0xFFF8F8F8),
        Color(0xFFEEEEEE),
        Color(0xFFD8D8D8),
        Color(0xFFC0C0C0),
        Color(0xFFB4B4B4),
    )
)

private val ButtonPressedGradient = Brush.verticalGradient(
    colors = listOf(
        Color(0xFFB0B0B0),
        Color(0xFFBBBBBB),
        Color(0xFFC5C5C5),
        Color(0xFFBEBEBE),
    )
)

// ── Chrome bezel border brush ──────────────────────────────────

private val ChromeBezelBrush = Brush.linearGradient(
    colors = listOf(Color.White, Color.Gray, Color.White),
    start = Offset.Zero,
    end = Offset.Infinite
)

private val ProgressBarBg = Color(0xFFFAF6E8) // Warm cream/ivory like the wireframe

// Ridgeline sits at ~65% of total bar height (buttons above, darker below)
private const val RIDGE_Y_FRACTION = 0.68f

// ── Main Bottom Bar ────────────────────────────────────────────

@Composable
fun BrushedMetalBottomBar(
    isConverting: Boolean,
    conversionProgress: Float, // 0f..1f
    elapsedTimeText: String,   // "00:00:00"
    onConvertClick: () -> Unit,
    onSearchClick: () -> Unit,
    onPreviousClick: () -> Unit = {},
    onRewindClick: () -> Unit = {},
    onFastForwardClick: () -> Unit = {},
    onNextClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val barShape = RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 10.dp, shape = barShape)
            .clip(barShape)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(twoToneAluminumBackground())
        ) {
            // ── Progress indicator ──
            CalculatorProgressBar(
                progress = conversionProgress,
                timeText = elapsedTimeText,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .padding(top = 8.dp, bottom = 4.dp)
            )

            // ── Mini shelf: tiny recessed ledge ──
            MiniShelf(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp)
            )

            // ── Transport controls (lighter aluminum zone) ──
            TransportControlsRow(
                isConverting = isConverting,
                onPreviousClick = onPreviousClick,
                onRewindClick = onRewindClick,
                onConvertClick = onConvertClick,
                onFastForwardClick = onFastForwardClick,
                onNextClick = onNextClick,
                onSearchClick = onSearchClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(top = 4.dp, bottom = 6.dp)
            )

            // ── Ridgeline: curved ridge separating tones ──
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
            ) {
                drawRidgeline()
            }

            // ── Darker aluminum zone below ridge ──
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp)
            )
        }
    }
}

// ── Mini Shelf: recessed ledge between display and buttons ─────

@Composable
private fun MiniShelf(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.height(5.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Top shadow line (inset)
            drawLine(
                color = Color.Black.copy(alpha = 0.12f),
                start = Offset(8f, 0f),
                end = Offset(size.width - 8f, 0f),
                strokeWidth = 1f
            )
            // Bright edge below it (highlight from light hitting ledge)
            drawLine(
                color = Color.White.copy(alpha = 0.5f),
                start = Offset(8f, 2f),
                end = Offset(size.width - 8f, 2f),
                strokeWidth = 1f
            )
            // Bottom shadow of shelf
            drawLine(
                color = Color.Black.copy(alpha = 0.06f),
                start = Offset(8f, 4f),
                end = Offset(size.width - 8f, 4f),
                strokeWidth = 0.5f
            )
        }
    }
}

// ── Two-tone AGSL aluminum (API 33+) or fallback ──────────────

@Composable
private fun twoToneAluminumBackground(): Modifier {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val shader = remember {
            RuntimeShader(AGSL_TWO_TONE_ALUMINUM)
        }
        Modifier.drawWithCache {
            shader.setFloatUniform("size", size.width, size.height)
            shader.setFloatUniform("lightPos", 0.45f, 0.2f, 0.12f)
            shader.setFloatUniform("ridgeY", RIDGE_Y_FRACTION)
            val shaderBrush = ShaderBrush(shader)
            onDrawBehind {
                drawRect(brush = shaderBrush)
                // Top edge highlight
                drawLine(
                    color = Color.White.copy(alpha = 0.7f),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.5f
                )
            }
        }
    } else {
        Modifier
            .background(FallbackTwoToneGradient)
            .drawBehind { drawFallbackTexture() }
    }
}

private fun DrawScope.drawFallbackTexture() {
    // Horizontal grain lines
    val lineSpacing = 1.5f
    var y = 0f
    while (y < size.height) {
        val alpha = ((y / size.height) * 0.03f + 0.01f).coerceIn(0f, 0.05f)
        drawLine(
            color = Color.Black.copy(alpha = alpha),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 0.5f
        )
        y += lineSpacing
    }
    drawLine(
        color = Color.White.copy(alpha = 0.7f),
        start = Offset(0f, 0f),
        end = Offset(size.width, 0f),
        strokeWidth = 1.5f
    )
}

// ── Ridgeline: curved ridge between lighter/darker zones ──────

private fun DrawScope.drawRidgeline() {
    // Shadow below the ridge (darker side)
    val shadowPath = Path().apply {
        moveTo(0f, size.height * 0.5f)
        quadraticTo(
            size.width * 0.5f, -size.height * 1.5f,
            size.width, size.height * 0.5f
        )
    }
    drawPath(
        path = shadowPath,
        color = Color.Black.copy(alpha = 0.15f),
        style = Stroke(width = 2f)
    )
    // Bright highlight on top of ridge (light catching the edge)
    val highlightPath = Path().apply {
        moveTo(0f, size.height * 0.4f)
        quadraticTo(
            size.width * 0.5f, -size.height * 1.8f,
            size.width, size.height * 0.4f
        )
    }
    drawPath(
        path = highlightPath,
        color = Color.White.copy(alpha = 0.45f),
        style = Stroke(width = 1.5f)
    )
}

// ── Calculator-style Progress Bar ──────────────────────────────

@Composable
private fun CalculatorProgressBar(
    progress: Float,
    timeText: String,
    modifier: Modifier = Modifier
) {
    var sliderPosition by remember { mutableFloatStateOf(progress) }
    if (progress != sliderPosition) {
        sliderPosition = progress
    }

    Box(
        modifier = modifier
            .height(32.dp)
            .shadow(
                elevation = 2.dp,
                shape = RoundedCornerShape(6.dp),
                ambientColor = Color(0xFF666666),
                spotColor = Color(0xFF444444)
            )
            .clip(RoundedCornerShape(6.dp))
            .background(ProgressBarBg)
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF999999),
                        Color(0xFFBBBBBB),
                        Color(0xFF888888)
                    ),
                    start = Offset.Zero,
                    end = Offset.Infinite
                ),
                shape = RoundedCornerShape(6.dp)
            )
            .drawBehind {
                // Inset shadow at top
                drawLine(
                    color = Color.Black.copy(alpha = 0.15f),
                    start = Offset(4f, 1f),
                    end = Offset(size.width - 4f, 1f),
                    strokeWidth = 1.5f
                )
                // Inner highlight at bottom
                drawLine(
                    color = Color.White.copy(alpha = 0.3f),
                    start = Offset(4f, size.height - 2f),
                    end = Offset(size.width - 4f, size.height - 2f),
                    strokeWidth = 0.5f
                )
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // LCD timestamp
            Text(
                text = timeText,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color(0xFF222222),
                    letterSpacing = 2.sp
                )
            )

            // Triangle marker
            Canvas(modifier = Modifier.size(7.dp)) {
                val path = Path().apply {
                    moveTo(size.width / 2, 0f)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(path, Color(0xFF222222))
            }

            // Seek slider
            Slider(
                value = sliderPosition,
                onValueChange = { sliderPosition = it },
                modifier = Modifier
                    .weight(1f)
                    .height(18.dp),
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF555555),
                    activeTrackColor = Color(0xFF777777),
                    inactiveTrackColor = Color(0xFFCCCCCC)
                )
            )
        }
    }
}

// ── Transport Controls Row ─────────────────────────────────────

@Composable
private fun TransportControlsRow(
    isConverting: Boolean,
    onPreviousClick: () -> Unit,
    onRewindClick: () -> Unit,
    onConvertClick: () -> Unit,
    onFastForwardClick: () -> Unit,
    onNextClick: () -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.weight(1f))

        GlossyButton(onClick = onPreviousClick, size = 44) { PreviousTrackIcon() }
        Spacer(Modifier.width(8.dp))
        GlossyButton(onClick = onRewindClick, size = 44) { RewindIcon() }
        Spacer(Modifier.width(10.dp))
        ConvertButton(isConverting = isConverting, onClick = onConvertClick)
        Spacer(Modifier.width(10.dp))
        GlossyButton(onClick = onFastForwardClick, size = 44) { FastForwardIcon() }
        Spacer(Modifier.width(8.dp))
        GlossyButton(onClick = onNextClick, size = 44) { NextTrackIcon() }

        Spacer(Modifier.weight(1f))

        GlossyButton(onClick = onSearchClick, size = 40) {
            Icon(
                Icons.Default.Search,
                contentDescription = "Search",
                modifier = Modifier.size(20.dp),
                tint = Color(0xFF444444)
            )
        }
        Spacer(Modifier.width(4.dp))
    }
}

// ── Glossy 3D Bubble Button (improved material) ────────────────

@Composable
private fun GlossyButton(
    onClick: () -> Unit,
    size: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Box(
        modifier = modifier
            .size(size.dp)
            .shadow(
                elevation = if (isPressed) 1.dp else 4.dp,
                shape = CircleShape,
                ambientColor = Color(0xFF777777),
                spotColor = Color(0xFF555555)
            )
            .clip(CircleShape)
            .background(if (isPressed) ButtonPressedGradient else ButtonGlossGradient)
            .border(
                width = 0.5.dp,
                brush = ChromeBezelBrush,
                shape = CircleShape
            )
            .drawBehind { drawGlossyOverlay(isPressed) }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

private fun DrawScope.drawGlossyOverlay(isPressed: Boolean) {
    // Outer chrome ring
    drawCircle(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color(0xFFCCCCCC),
                Color(0xFF888888),
                Color(0xFFAAAAAA),
            )
        ),
        radius = size.minDimension / 2f,
        style = Stroke(width = 1.2f)
    )

    // Top glossy specular highlight (half-moon)
    if (!isPressed) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = size.minDimension / 2f - 3f

        val highlightPath = Path().apply {
            arcTo(
                rect = Rect(cx - r, cy - r, cx + r, cy + r),
                startAngleDegrees = 195f,
                sweepAngleDegrees = 150f,
                forceMoveTo = true
            )
            quadraticTo(cx, cy - r * 0.2f, cx + r * 0.7f, cy - r * 0.7f)
        }
        drawPath(
            path = highlightPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.85f),
                    Color.White.copy(alpha = 0.0f)
                ),
                startY = 0f,
                endY = size.height * 0.5f
            )
        )
    }

    // Bottom shadow crescent for 3D convexity
    val cx = size.width / 2f
    val cy = size.height / 2f
    val r = size.minDimension / 2f - 2f
    val shadowPath = Path().apply {
        arcTo(
            rect = Rect(cx - r, cy - r, cx + r, cy + r),
            startAngleDegrees = 20f,
            sweepAngleDegrees = 140f,
            forceMoveTo = true
        )
        quadraticTo(cx, cy + r * 0.5f, cx - r * 0.6f, cy + r * 0.8f)
    }
    drawPath(
        path = shadowPath,
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                Color.Black.copy(alpha = if (isPressed) 0.04f else 0.12f)
            ),
            startY = size.height * 0.55f,
            endY = size.height
        )
    )
}

// ── Center Convert Button (larger icon) ────────────────────────

@Composable
private fun ConvertButton(
    isConverting: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val infiniteTransition = rememberInfiniteTransition(label = "convert")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "convertRotation"
    )

    Box(
        modifier = modifier.size(66.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer chrome ring
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFE0E0E0),
                        Color(0xFF909090),
                        Color(0xFFBBBBBB),
                    )
                ),
                radius = size.minDimension / 2f,
                style = Stroke(width = 5f)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.35f),
                radius = size.minDimension / 2f + 1f,
                style = Stroke(width = 0.5f)
            )
        }

        // Inner glossy button
        Box(
            modifier = Modifier
                .size(54.dp)
                .shadow(
                    elevation = if (isPressed) 1.dp else 5.dp,
                    shape = CircleShape,
                    ambientColor = Color(0xFF777777),
                    spotColor = Color(0xFF555555)
                )
                .clip(CircleShape)
                .background(if (isPressed) ButtonPressedGradient else ButtonGlossGradient)
                .border(
                    width = 0.5.dp,
                    brush = ChromeBezelBrush,
                    shape = CircleShape
                )
                .drawBehind { drawGlossyOverlay(isPressed) }
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            // Music note + circular arrows - LARGER canvas
            Canvas(modifier = Modifier.size(38.dp)) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val arrowRadius = size.minDimension / 2f - 3f
                val arrowColor = Color(0xFF3A3A3A)
                val startAngle = if (isConverting) rotationAngle else 0f

                // Top arc arrow
                drawArc(
                    color = arrowColor,
                    startAngle = startAngle - 30f,
                    sweepAngle = 150f,
                    useCenter = false,
                    topLeft = Offset(cx - arrowRadius, cy - arrowRadius),
                    size = Size(arrowRadius * 2, arrowRadius * 2),
                    style = Stroke(width = 2.8f)
                )
                val topAngleRad = Math.toRadians((startAngle + 120.0))
                val tax = cx + arrowRadius * cos(topAngleRad).toFloat()
                val tay = cy + arrowRadius * sin(topAngleRad).toFloat()
                drawArrowHead(tax, tay, startAngle + 120f, arrowColor)

                // Bottom arc arrow
                drawArc(
                    color = arrowColor,
                    startAngle = startAngle + 150f,
                    sweepAngle = 150f,
                    useCenter = false,
                    topLeft = Offset(cx - arrowRadius, cy - arrowRadius),
                    size = Size(arrowRadius * 2, arrowRadius * 2),
                    style = Stroke(width = 2.8f)
                )
                val botAngleRad = Math.toRadians((startAngle + 300.0))
                val bax = cx + arrowRadius * cos(botAngleRad).toFloat()
                val bay = cy + arrowRadius * sin(botAngleRad).toFloat()
                drawArrowHead(bax, bay, startAngle + 300f, arrowColor)

                // Music note (♫) - scaled up
                val noteColor = Color(0xFF2A2A2A)
                val s = 1.3f // scale factor
                // Note head 1
                drawOval(
                    color = noteColor,
                    topLeft = Offset(cx - 6f * s, cy + 2f * s),
                    size = Size(9f * s, 6f * s)
                )
                // Note head 2
                drawOval(
                    color = noteColor,
                    topLeft = Offset(cx + 2f * s, cy + 4f * s),
                    size = Size(9f * s, 6f * s)
                )
                // Stem 1
                drawLine(
                    color = noteColor,
                    start = Offset(cx + 1.5f * s, cy + 5f * s),
                    end = Offset(cx + 1.5f * s, cy - 8f * s),
                    strokeWidth = 2.2f
                )
                // Stem 2
                drawLine(
                    color = noteColor,
                    start = Offset(cx + 10f * s, cy + 7f * s),
                    end = Offset(cx + 10f * s, cy - 6f * s),
                    strokeWidth = 2.2f
                )
                // Beam
                drawLine(
                    color = noteColor,
                    start = Offset(cx + 1.5f * s, cy - 8f * s),
                    end = Offset(cx + 10f * s, cy - 6f * s),
                    strokeWidth = 3f
                )
            }
        }
    }
}

private fun DrawScope.drawArrowHead(x: Float, y: Float, angleDegrees: Float, color: Color) {
    val angle = Math.toRadians(angleDegrees.toDouble())
    val headLen = 6f
    val path = Path().apply {
        moveTo(x, y)
        lineTo(
            x - headLen * cos(angle - PI / 6).toFloat(),
            y - headLen * sin(angle - PI / 6).toFloat()
        )
        moveTo(x, y)
        lineTo(
            x - headLen * cos(angle + PI / 6).toFloat(),
            y - headLen * sin(angle + PI / 6).toFloat()
        )
    }
    drawPath(path, color, style = Stroke(width = 2.5f))
}

// ── Transport Icon Composables ─────────────────────────────────

@Composable
private fun PreviousTrackIcon() {
    Canvas(modifier = Modifier.size(18.dp)) {
        val color = Color(0xFF3A3A3A)
        drawLine(color, Offset(3f, 4f), Offset(3f, size.height - 4f), strokeWidth = 3f)
        val path = Path().apply {
            moveTo(size.width - 2f, 4f)
            lineTo(7f, size.height / 2f)
            lineTo(size.width - 2f, size.height - 4f)
            close()
        }
        drawPath(path, color)
    }
}

@Composable
private fun NextTrackIcon() {
    Canvas(modifier = Modifier.size(18.dp)) {
        val color = Color(0xFF3A3A3A)
        val path = Path().apply {
            moveTo(2f, 4f)
            lineTo(size.width - 7f, size.height / 2f)
            lineTo(2f, size.height - 4f)
            close()
        }
        drawPath(path, color)
        drawLine(color, Offset(size.width - 3f, 4f), Offset(size.width - 3f, size.height - 4f), strokeWidth = 3f)
    }
}

@Composable
private fun RewindIcon() {
    Canvas(modifier = Modifier.size(20.dp)) {
        val color = Color(0xFF3A3A3A)
        val path1 = Path().apply {
            moveTo(size.width / 2f, 4f); lineTo(2f, size.height / 2f); lineTo(size.width / 2f, size.height - 4f); close()
        }
        drawPath(path1, color)
        val path2 = Path().apply {
            moveTo(size.width - 2f, 4f); lineTo(size.width / 2f, size.height / 2f); lineTo(size.width - 2f, size.height - 4f); close()
        }
        drawPath(path2, color)
    }
}

@Composable
private fun FastForwardIcon() {
    Canvas(modifier = Modifier.size(20.dp)) {
        val color = Color(0xFF3A3A3A)
        val path1 = Path().apply {
            moveTo(2f, 4f); lineTo(size.width / 2f, size.height / 2f); lineTo(2f, size.height - 4f); close()
        }
        drawPath(path1, color)
        val path2 = Path().apply {
            moveTo(size.width / 2f, 4f); lineTo(size.width - 2f, size.height / 2f); lineTo(size.width / 2f, size.height - 4f); close()
        }
        drawPath(path2, color)
    }
}
