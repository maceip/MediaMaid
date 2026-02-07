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

// ── AGSL Runtime Shader for high-res brushed aluminum ──────────

@Suppress("ConstPropertyName")
private const val AGSL_BRUSHED_ALUMINUM = """
    uniform float2 size;
    uniform float3 lightPos;

    float hash(float2 p) {
        return fract(sin(dot(p, float2(12.9, 78.2))) * 43758.5);
    }

    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / size;

        // High-frequency horizontal scratches (brushed grain)
        float grain = hash(float2(fragCoord.x * 0.05, fragCoord.y * 10.0));

        // Specular sheen from light hitting ridges
        float distToLight = distance(fragCoord.x, lightPos.x * size.x);
        float sheen = exp(-0.005 * distToLight);

        // Base aluminum color + grain + specular
        float3 baseColor = float3(0.75, 0.77, 0.8);
        float3 finalColor = baseColor + (grain * 0.08) + (sheen * lightPos.z);

        return half4(finalColor, 1.0);
    }
"""

// ── Fallback gradient for API < 33 ────────────────────────────

private val FallbackMetalGradient = Brush.verticalGradient(
    colors = listOf(
        Color(0xFFD4D4D4),
        Color(0xFFE0E0E0),
        Color(0xFFEEEEEE),
        Color(0xFFE8E8E8),
        Color(0xFFD8D8D8),
        Color(0xFFCCCCCC),
        Color(0xFFBBBBBB),
        Color(0xFFC4C4C4),
        Color(0xFFD0D0D0),
        Color(0xFFBBBBBB),
    )
)

// ── Button gradients ───────────────────────────────────────────

private val ButtonGlossGradient = Brush.verticalGradient(
    colors = listOf(
        Color(0xFFF0F0F0),
        Color(0xFFE8E8E8),
        Color(0xFFD0D0D0),
        Color(0xFFBBBBBB),
    )
)

private val ButtonPressedGradient = Brush.verticalGradient(
    colors = listOf(
        Color(0xFFBBBBBB),
        Color(0xFFC8C8C8),
        Color(0xFFD0D0D0),
        Color(0xFFCCCCCC),
    )
)

// ── Chrome bezel border brush ──────────────────────────────────

private val ChromeBezelBrush = Brush.linearGradient(
    colors = listOf(Color.White, Color.Gray, Color.White),
    start = Offset.Zero,
    end = Offset.Infinite
)

private val ProgressBarBg = Color(0xFFF5F0E0) // Yellowish-white like the wireframe

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
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(brushedMetalBackground())
        ) {
            // ── Top: Calculator-style progress indicator ──
            CalculatorProgressBar(
                progress = conversionProgress,
                timeText = elapsedTimeText,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )

            // ── Bottom: Transport controls ──
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
                    .padding(horizontal = 16.dp)
                    .padding(top = 2.dp, bottom = 10.dp)
            )

            // ── Swoosh curve at bottom ──
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
            ) {
                drawSwooshCurve()
            }
        }
    }
}

// ── AGSL-backed brushed metal modifier (API 33+) or fallback ──

@Composable
private fun brushedMetalBackground(): Modifier {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val shader = remember {
            RuntimeShader(AGSL_BRUSHED_ALUMINUM)
        }
        Modifier.drawWithCache {
            shader.setFloatUniform("size", size.width, size.height)
            shader.setFloatUniform("lightPos", 0.5f, 0.3f, 0.15f)
            val shaderBrush = ShaderBrush(shader)
            onDrawBehind {
                drawRect(brush = shaderBrush)
                // Top edge highlight
                drawLine(
                    color = Color.White.copy(alpha = 0.6f),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.5f
                )
                // Bottom shadow line
                drawLine(
                    color = Color.Black.copy(alpha = 0.15f),
                    start = Offset(0f, size.height - 1f),
                    end = Offset(size.width, size.height - 1f),
                    strokeWidth = 1.5f
                )
            }
        }
    } else {
        Modifier
            .background(FallbackMetalGradient)
            .drawBehind { drawFallbackMetalTexture() }
    }
}

// ── Fallback texture for pre-API 33 ───────────────────────────

private fun DrawScope.drawFallbackMetalTexture() {
    val lineSpacing = 2f
    var y = 0f
    while (y < size.height) {
        val alpha = ((y / size.height) * 0.04f + 0.01f).coerceIn(0f, 0.06f)
        drawLine(
            color = Color.Black.copy(alpha = alpha),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 0.5f
        )
        y += lineSpacing
    }
    drawLine(
        color = Color.White.copy(alpha = 0.6f),
        start = Offset(0f, 0f),
        end = Offset(size.width, 0f),
        strokeWidth = 1.5f
    )
    drawLine(
        color = Color.Black.copy(alpha = 0.15f),
        start = Offset(0f, size.height - 1f),
        end = Offset(size.width, size.height - 1f),
        strokeWidth = 1.5f
    )
}

// ── Swoosh curve at bottom of the bar ──────────────────────────

private fun DrawScope.drawSwooshCurve() {
    val path = Path().apply {
        moveTo(0f, size.height * 0.8f)
        quadraticTo(
            size.width * 0.5f, -size.height * 0.6f,
            size.width, size.height * 0.8f
        )
    }
    drawPath(
        path = path,
        color = Color.Black.copy(alpha = 0.08f),
        style = Stroke(width = 1.5f)
    )
    val path2 = Path().apply {
        moveTo(0f, size.height * 0.9f)
        quadraticTo(
            size.width * 0.5f, -size.height * 0.3f,
            size.width, size.height * 0.9f
        )
    }
    drawPath(
        path = path2,
        color = Color.White.copy(alpha = 0.3f),
        style = Stroke(width = 1f)
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
            .height(36.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.White)
            .border(
                width = 0.5.dp,
                brush = ChromeBezelBrush,
                shape = RoundedCornerShape(4.dp)
            )
            .drawBehind {
                // Inner shadow at top
                drawLine(
                    color = Color(0xFFAAAAAA).copy(alpha = 0.5f),
                    start = Offset(2f, 2f),
                    end = Offset(size.width - 2f, 2f),
                    strokeWidth = 1f
                )
            }
    ) {
        // Yellowish-white background fill
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(1.dp)
                .background(ProgressBarBg)
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Timestamp display
            Text(
                text = timeText,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color(0xFF333333),
                    letterSpacing = 1.sp
                )
            )

            // Triangle marker
            Canvas(modifier = Modifier.size(8.dp)) {
                val path = Path().apply {
                    moveTo(size.width / 2, 0f)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(path, Color(0xFF333333))
            }

            // Seek slider
            Slider(
                value = sliderPosition,
                onValueChange = { sliderPosition = it },
                modifier = Modifier
                    .weight(1f)
                    .height(20.dp),
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF555555),
                    activeTrackColor = Color(0xFF888888),
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

        // Previous Track |◄
        GlossyButton(onClick = onPreviousClick, size = 44) {
            PreviousTrackIcon()
        }

        Spacer(Modifier.width(8.dp))

        // Rewind ◄◄
        GlossyButton(onClick = onRewindClick, size = 44) {
            RewindIcon()
        }

        Spacer(Modifier.width(10.dp))

        // Center Convert Button (larger)
        ConvertButton(isConverting = isConverting, onClick = onConvertClick)

        Spacer(Modifier.width(10.dp))

        // Fast Forward ►►
        GlossyButton(onClick = onFastForwardClick, size = 44) {
            FastForwardIcon()
        }

        Spacer(Modifier.width(8.dp))

        // Next Track ►|
        GlossyButton(onClick = onNextClick, size = 44) {
            NextTrackIcon()
        }

        Spacer(Modifier.weight(1f))

        // Search button (right-aligned)
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

// ── Glossy 3D Bubble Button ────────────────────────────────────

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
                elevation = if (isPressed) 1.dp else 3.dp,
                shape = CircleShape,
                ambientColor = Color(0xFF888888),
                spotColor = Color(0xFF666666)
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
    // Top glossy highlight (half-moon specular)
    if (!isPressed) {
        val highlightPath = Path().apply {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val r = size.minDimension / 2f - 3f
            arcTo(
                rect = Rect(cx - r, cy - r, cx + r, cy + r),
                startAngleDegrees = 200f,
                sweepAngleDegrees = 140f,
                forceMoveTo = true
            )
            quadraticTo(cx, cy - r * 0.1f, cx + r * 0.64f, cy - r * 0.77f)
        }
        drawPath(
            path = highlightPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.7f),
                    Color.White.copy(alpha = 0.0f)
                ),
                startY = 0f,
                endY = size.height * 0.55f
            )
        )
    }
    // Subtle bottom shadow crescent
    val shadowPath = Path().apply {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = size.minDimension / 2f - 2f
        arcTo(
            rect = Rect(cx - r, cy - r, cx + r, cy + r),
            startAngleDegrees = 30f,
            sweepAngleDegrees = 120f,
            forceMoveTo = true
        )
        quadraticTo(cx, cy + r * 0.6f, cx - r * 0.5f, cy + r * 0.87f)
    }
    drawPath(
        path = shadowPath,
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                Color.Black.copy(alpha = 0.08f)
            ),
            startY = size.height * 0.6f,
            endY = size.height
        )
    )
}

// ── Center Convert Button (larger, with music note + arrows) ──

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
        modifier = modifier.size(62.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer chrome ring
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Outer chrome ring gradient
            drawCircle(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFDDDDDD),
                        Color(0xFF999999),
                        Color(0xFFBBBBBB),
                    )
                ),
                radius = size.minDimension / 2f,
                style = Stroke(width = 4f)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.3f),
                radius = size.minDimension / 2f + 0.5f,
                style = Stroke(width = 0.5f)
            )
        }

        // Inner glossy button
        Box(
            modifier = Modifier
                .size(52.dp)
                .shadow(
                    elevation = if (isPressed) 1.dp else 4.dp,
                    shape = CircleShape,
                    ambientColor = Color(0xFF888888),
                    spotColor = Color(0xFF666666)
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
            // Music note with circular arrows
            Canvas(modifier = Modifier.size(30.dp)) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val arrowRadius = size.minDimension / 2f - 2f
                val arrowColor = Color(0xFF444444)
                val startAngle = if (isConverting) rotationAngle else 0f

                // Top arc arrow
                drawArc(
                    color = arrowColor,
                    startAngle = startAngle - 30f,
                    sweepAngle = 150f,
                    useCenter = false,
                    topLeft = Offset(cx - arrowRadius, cy - arrowRadius),
                    size = Size(arrowRadius * 2, arrowRadius * 2),
                    style = Stroke(width = 2.5f)
                )
                val topArrowAngle = Math.toRadians((startAngle + 120.0))
                val tax = cx + arrowRadius * cos(topArrowAngle).toFloat()
                val tay = cy + arrowRadius * sin(topArrowAngle).toFloat()
                drawArrowHead(tax, tay, startAngle + 120f, arrowColor)

                // Bottom arc arrow
                drawArc(
                    color = arrowColor,
                    startAngle = startAngle + 150f,
                    sweepAngle = 150f,
                    useCenter = false,
                    topLeft = Offset(cx - arrowRadius, cy - arrowRadius),
                    size = Size(arrowRadius * 2, arrowRadius * 2),
                    style = Stroke(width = 2.5f)
                )
                val botArrowAngle = Math.toRadians((startAngle + 300.0))
                val bax = cx + arrowRadius * cos(botArrowAngle).toFloat()
                val bay = cy + arrowRadius * sin(botArrowAngle).toFloat()
                drawArrowHead(bax, bay, startAngle + 300f, arrowColor)

                // Music note (♫)
                val noteColor = Color(0xFF333333)
                drawOval(
                    color = noteColor,
                    topLeft = Offset(cx - 5f, cy + 2f),
                    size = Size(7f, 5f)
                )
                drawOval(
                    color = noteColor,
                    topLeft = Offset(cx + 3f, cy + 4f),
                    size = Size(7f, 5f)
                )
                drawLine(
                    color = noteColor,
                    start = Offset(cx + 1.5f, cy + 4f),
                    end = Offset(cx + 1.5f, cy - 7f),
                    strokeWidth = 1.8f
                )
                drawLine(
                    color = noteColor,
                    start = Offset(cx + 9.5f, cy + 6f),
                    end = Offset(cx + 9.5f, cy - 5f),
                    strokeWidth = 1.8f
                )
                drawLine(
                    color = noteColor,
                    start = Offset(cx + 1.5f, cy - 7f),
                    end = Offset(cx + 9.5f, cy - 5f),
                    strokeWidth = 2.5f
                )
            }
        }
    }
}

private fun DrawScope.drawArrowHead(x: Float, y: Float, angleDegrees: Float, color: Color) {
    val angle = Math.toRadians(angleDegrees.toDouble())
    val headLen = 5f
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
    drawPath(path, color, style = Stroke(width = 2f))
}

// ── Transport Icon Composables ─────────────────────────────────

@Composable
private fun PreviousTrackIcon() {
    Canvas(modifier = Modifier.size(18.dp)) {
        val color = Color(0xFF444444)
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
        val color = Color(0xFF444444)
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
        val color = Color(0xFF444444)
        val path1 = Path().apply {
            moveTo(size.width / 2f, 4f)
            lineTo(2f, size.height / 2f)
            lineTo(size.width / 2f, size.height - 4f)
            close()
        }
        drawPath(path1, color)
        val path2 = Path().apply {
            moveTo(size.width - 2f, 4f)
            lineTo(size.width / 2f, size.height / 2f)
            lineTo(size.width - 2f, size.height - 4f)
            close()
        }
        drawPath(path2, color)
    }
}

@Composable
private fun FastForwardIcon() {
    Canvas(modifier = Modifier.size(20.dp)) {
        val color = Color(0xFF444444)
        val path1 = Path().apply {
            moveTo(2f, 4f)
            lineTo(size.width / 2f, size.height / 2f)
            lineTo(2f, size.height - 4f)
            close()
        }
        drawPath(path1, color)
        val path2 = Path().apply {
            moveTo(size.width / 2f, 4f)
            lineTo(size.width - 2f, size.height / 2f)
            lineTo(size.width / 2f, size.height - 4f)
            close()
        }
        drawPath(path2, color)
    }
}
