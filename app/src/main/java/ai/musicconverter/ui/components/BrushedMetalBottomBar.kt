package ai.musicconverter.ui.components

import android.graphics.RuntimeShader
import android.os.Build
import android.view.HapticFeedbackConstants
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
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ── Three AGSL shader variants ─────────────────────────────────

enum class AluminumVariant { COOL, NEUTRAL, WARM }

@Suppress("ConstPropertyName")
private const val AGSL_ALUMINUM_COOL = """
    uniform float2 size;
    uniform float3 lightPos;
    uniform float ridgeY;

    float hash(float2 p) {
        return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453);
    }

    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / size;
        float grain = hash(float2(fragCoord.x * 0.03, fragCoord.y * 14.0));
        float distToLight = distance(fragCoord.x, lightPos.x * size.x);
        float sheen = exp(-0.004 * distToLight);

        // Cool blue-silver: lighter above ridge, darker below
        float3 lightAlum = float3(0.78, 0.81, 0.86);
        float3 darkAlum  = float3(0.60, 0.63, 0.68);

        // HARD split at ridgeline with narrow transition
        float blend = smoothstep(ridgeY - 0.01, ridgeY + 0.01, uv.y);
        float3 baseColor = mix(lightAlum, darkAlum, blend);
        float3 finalColor = baseColor + (grain * 0.05) + (sheen * lightPos.z);
        return half4(finalColor, 1.0);
    }
"""

@Suppress("ConstPropertyName")
private const val AGSL_ALUMINUM_NEUTRAL = """
    uniform float2 size;
    uniform float3 lightPos;
    uniform float ridgeY;

    float hash(float2 p) {
        return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453);
    }

    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / size;
        float grain = hash(float2(fragCoord.x * 0.03, fragCoord.y * 14.0));
        float distToLight = distance(fragCoord.x, lightPos.x * size.x);
        float sheen = exp(-0.004 * distToLight);

        // Pure silver: lighter above, darker below
        float3 lightAlum = float3(0.82, 0.82, 0.83);
        float3 darkAlum  = float3(0.64, 0.64, 0.66);

        float blend = smoothstep(ridgeY - 0.01, ridgeY + 0.01, uv.y);
        float3 baseColor = mix(lightAlum, darkAlum, blend);
        float3 finalColor = baseColor + (grain * 0.05) + (sheen * lightPos.z);
        return half4(finalColor, 1.0);
    }
"""

@Suppress("ConstPropertyName")
private const val AGSL_ALUMINUM_WARM = """
    uniform float2 size;
    uniform float3 lightPos;
    uniform float ridgeY;

    float hash(float2 p) {
        return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453);
    }

    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / size;
        float grain = hash(float2(fragCoord.x * 0.03, fragCoord.y * 14.0));
        float distToLight = distance(fragCoord.x, lightPos.x * size.x);
        float sheen = exp(-0.004 * distToLight);

        // Warm champagne-silver: lighter above, darker below
        float3 lightAlum = float3(0.84, 0.82, 0.79);
        float3 darkAlum  = float3(0.66, 0.64, 0.61);

        float blend = smoothstep(ridgeY - 0.01, ridgeY + 0.01, uv.y);
        float3 baseColor = mix(lightAlum, darkAlum, blend);
        float3 finalColor = baseColor + (grain * 0.05) + (sheen * lightPos.z);
        return half4(finalColor, 1.0);
    }
"""

// ── Fallback gradients per variant ─────────────────────────────

private fun fallbackGradient(variant: AluminumVariant): Brush {
    val (light, dark) = when (variant) {
        AluminumVariant.COOL -> Color(0xFFCDD2DB) to Color(0xFF9CA1AA)
        AluminumVariant.NEUTRAL -> Color(0xFFD4D4D6) to Color(0xFFA4A4A8)
        AluminumVariant.WARM -> Color(0xFFD8D4CF) to Color(0xFFA8A49F)
    }
    return Brush.verticalGradient(
        colorStops = arrayOf(
            0.0f to light,
            0.64f to light,
            0.68f to dark,
            1.0f to dark,
        )
    )
}

// ── Button gradients ───────────────────────────────────────────

private val ButtonGlossGradient = Brush.verticalGradient(
    colors = listOf(
        Color(0xFFF8F8F8), Color(0xFFEEEEEE), Color(0xFFD8D8D8),
        Color(0xFFC0C0C0), Color(0xFFB4B4B4),
    )
)

private val ButtonPressedGradient = Brush.verticalGradient(
    colors = listOf(
        Color(0xFFB0B0B0), Color(0xFFBBBBBB), Color(0xFFC5C5C5), Color(0xFFBEBEBE),
    )
)

private val ChromeBezelBrush = Brush.linearGradient(
    colors = listOf(Color.White, Color.Gray, Color.White),
    start = Offset.Zero,
    end = Offset.Infinite
)

private val ProgressBarBg = Color(0xFFFAF6E8)
private const val RIDGE_Y_FRACTION = 0.68f

// ── Reusable aluminum background modifier (for top bar too) ────

@Composable
fun aluminumBackgroundModifier(variant: AluminumVariant = AluminumVariant.NEUTRAL): Modifier {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val shaderSrc = when (variant) {
            AluminumVariant.COOL -> AGSL_ALUMINUM_COOL
            AluminumVariant.NEUTRAL -> AGSL_ALUMINUM_NEUTRAL
            AluminumVariant.WARM -> AGSL_ALUMINUM_WARM
        }
        val shader = remember(variant) { RuntimeShader(shaderSrc) }
        Modifier.drawWithCache {
            shader.setFloatUniform("size", size.width, size.height)
            shader.setFloatUniform("lightPos", 0.45f, 0.2f, 0.12f)
            shader.setFloatUniform("ridgeY", 1.0f) // no split for top bar
            val shaderBrush = ShaderBrush(shader)
            onDrawBehind {
                drawRect(brush = shaderBrush)
                drawLine(Color.White.copy(alpha = 0.7f), Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.5f)
            }
        }
    } else {
        Modifier
            .background(fallbackGradient(variant))
            .drawBehind { drawFallbackTexture() }
    }
}

// ── Notification display text (replaces snackbar/toast) ────────

/**
 * Call this to get the display text for the calculator-style progress bar.
 * When there's a notification, it overrides the elapsed time.
 */

// ── Main Bottom Bar ────────────────────────────────────────────

@Composable
fun BrushedMetalBottomBar(
    isConverting: Boolean,
    conversionProgress: Float,
    elapsedTimeText: String,
    notificationText: String? = null,
    onConvertClick: () -> Unit,
    onSearchClick: () -> Unit,
    onPreviousClick: () -> Unit = {},
    onRewindClick: () -> Unit = {},
    onFastForwardClick: () -> Unit = {},
    onNextClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val barShape = RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp)

    // Aluminum variant state - switchable via transport buttons (debug)
    var variantIndex by remember { mutableIntStateOf(1) } // 0=cool, 1=neutral, 2=warm
    val currentVariant = AluminumVariant.entries[variantIndex]

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 10.dp, shape = barShape)
            .clip(barShape)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(twoToneAluminumModifier(currentVariant))
        ) {
            // ── Progress / notification display ──
            CalculatorProgressBar(
                progress = conversionProgress,
                timeText = notificationText ?: elapsedTimeText,
                isNotification = notificationText != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .padding(top = 8.dp, bottom = 4.dp)
            )

            // ── Mini shelf ──
            MiniShelf(Modifier.fillMaxWidth().padding(horizontal = 6.dp))

            // ── Transport controls ──
            TransportControlsRow(
                isConverting = isConverting,
                onPreviousClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    variantIndex = 0 // COOL
                    onPreviousClick()
                },
                onRewindClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    onRewindClick()
                },
                onConvertClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    variantIndex = 1 // NEUTRAL
                    onConvertClick()
                },
                onFastForwardClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    onFastForwardClick()
                },
                onNextClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    variantIndex = 2 // WARM
                    onNextClick()
                },
                onSearchClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    onSearchClick()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(top = 4.dp, bottom = 6.dp)
            )

            // ── Ridgeline ──
            Canvas(Modifier.fillMaxWidth().height(8.dp)) { drawRidgeline() }

            // ── Darker zone below ridge ──
            Spacer(Modifier.fillMaxWidth().height(16.dp))
        }
    }
}

// ── Two-tone aluminum modifier ─────────────────────────────────

@Composable
private fun twoToneAluminumModifier(variant: AluminumVariant): Modifier {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val shaderSrc = when (variant) {
            AluminumVariant.COOL -> AGSL_ALUMINUM_COOL
            AluminumVariant.NEUTRAL -> AGSL_ALUMINUM_NEUTRAL
            AluminumVariant.WARM -> AGSL_ALUMINUM_WARM
        }
        val shader = remember(variant) { RuntimeShader(shaderSrc) }
        Modifier.drawWithCache {
            shader.setFloatUniform("size", size.width, size.height)
            shader.setFloatUniform("lightPos", 0.45f, 0.2f, 0.12f)
            shader.setFloatUniform("ridgeY", RIDGE_Y_FRACTION)
            val shaderBrush = ShaderBrush(shader)
            onDrawBehind {
                drawRect(brush = shaderBrush)
                drawLine(Color.White.copy(alpha = 0.7f), Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.5f)
            }
        }
    } else {
        Modifier
            .background(fallbackGradient(variant))
            .drawBehind { drawFallbackTexture() }
    }
}

// ── Mini Shelf ─────────────────────────────────────────────────

@Composable
private fun MiniShelf(modifier: Modifier = Modifier) {
    Box(modifier = modifier.height(5.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawLine(Color.Black.copy(alpha = 0.12f), Offset(8f, 0f), Offset(size.width - 8f, 0f), strokeWidth = 1f)
            drawLine(Color.White.copy(alpha = 0.5f), Offset(8f, 2f), Offset(size.width - 8f, 2f), strokeWidth = 1f)
            drawLine(Color.Black.copy(alpha = 0.06f), Offset(8f, 4f), Offset(size.width - 8f, 4f), strokeWidth = 0.5f)
        }
    }
}

private fun DrawScope.drawFallbackTexture() {
    val lineSpacing = 1.5f
    var y = 0f
    while (y < size.height) {
        val alpha = ((y / size.height) * 0.03f + 0.01f).coerceIn(0f, 0.05f)
        drawLine(Color.Black.copy(alpha = alpha), Offset(0f, y), Offset(size.width, y), strokeWidth = 0.5f)
        y += lineSpacing
    }
    drawLine(Color.White.copy(alpha = 0.7f), Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.5f)
}

// ── Ridgeline ──────────────────────────────────────────────────

private fun DrawScope.drawRidgeline() {
    val shadowPath = Path().apply {
        moveTo(0f, size.height * 0.5f)
        quadraticTo(size.width * 0.5f, -size.height * 1.5f, size.width, size.height * 0.5f)
    }
    drawPath(shadowPath, Color.Black.copy(alpha = 0.18f), style = Stroke(width = 2.5f))
    val highlightPath = Path().apply {
        moveTo(0f, size.height * 0.35f)
        quadraticTo(size.width * 0.5f, -size.height * 2.0f, size.width, size.height * 0.35f)
    }
    drawPath(highlightPath, Color.White.copy(alpha = 0.5f), style = Stroke(width = 1.5f))
}

// ── Calculator-style Progress Bar / Notification Display ───────

@Composable
private fun CalculatorProgressBar(
    progress: Float,
    timeText: String,
    isNotification: Boolean = false,
    modifier: Modifier = Modifier
) {
    var sliderPosition by remember { mutableFloatStateOf(progress) }
    if (progress != sliderPosition) sliderPosition = progress

    Box(
        modifier = modifier
            .height(32.dp)
            .shadow(2.dp, RoundedCornerShape(6.dp), ambientColor = Color(0xFF666666), spotColor = Color(0xFF444444))
            .clip(RoundedCornerShape(6.dp))
            .background(ProgressBarBg)
            .border(1.dp, Brush.linearGradient(listOf(Color(0xFF999999), Color(0xFFBBBBBB), Color(0xFF888888)), Offset.Zero, Offset.Infinite), RoundedCornerShape(6.dp))
            .drawBehind {
                drawLine(Color.Black.copy(alpha = 0.15f), Offset(4f, 1f), Offset(size.width - 4f, 1f), strokeWidth = 1.5f)
                drawLine(Color.White.copy(alpha = 0.3f), Offset(4f, size.height - 2f), Offset(size.width - 4f, size.height - 2f), strokeWidth = 0.5f)
            }
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = timeText,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = if (isNotification) Color(0xFF8B4513) else Color(0xFF222222),
                    letterSpacing = 2.sp
                ),
                maxLines = 1
            )

            if (!isNotification) {
                Canvas(modifier = Modifier.size(7.dp)) {
                    val path = Path().apply {
                        moveTo(size.width / 2, 0f); lineTo(size.width, size.height); lineTo(0f, size.height); close()
                    }
                    drawPath(path, Color(0xFF222222))
                }

                Slider(
                    value = sliderPosition,
                    onValueChange = { sliderPosition = it },
                    modifier = Modifier.weight(1f).height(18.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF555555),
                        activeTrackColor = Color(0xFF777777),
                        inactiveTrackColor = Color(0xFFCCCCCC)
                    )
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
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
    Row(modifier = modifier, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
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
            Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(20.dp), tint = Color(0xFF444444))
        }
        Spacer(Modifier.width(4.dp))
    }
}

// ── Glossy 3D Bubble Button ────────────────────────────────────

@Composable
private fun GlossyButton(onClick: () -> Unit, size: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Box(
        modifier = modifier
            .size(size.dp)
            .shadow(if (isPressed) 1.dp else 4.dp, CircleShape, ambientColor = Color(0xFF777777), spotColor = Color(0xFF555555))
            .clip(CircleShape)
            .background(if (isPressed) ButtonPressedGradient else ButtonGlossGradient)
            .border(0.5.dp, ChromeBezelBrush, CircleShape)
            .drawBehind { drawGlossyOverlay(isPressed) }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

private fun DrawScope.drawGlossyOverlay(isPressed: Boolean) {
    drawCircle(
        Brush.verticalGradient(listOf(Color(0xFFCCCCCC), Color(0xFF888888), Color(0xFFAAAAAA))),
        radius = size.minDimension / 2f, style = Stroke(width = 1.2f)
    )
    if (!isPressed) {
        val cx = size.width / 2f; val cy = size.height / 2f; val r = size.minDimension / 2f - 3f
        val highlightPath = Path().apply {
            arcTo(Rect(cx - r, cy - r, cx + r, cy + r), 195f, 150f, true)
            quadraticTo(cx, cy - r * 0.2f, cx + r * 0.7f, cy - r * 0.7f)
        }
        drawPath(highlightPath, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.85f), Color.White.copy(alpha = 0.0f)), startY = 0f, endY = size.height * 0.5f))
    }
    val cx = size.width / 2f; val cy = size.height / 2f; val r = size.minDimension / 2f - 2f
    val shadowPath = Path().apply {
        arcTo(Rect(cx - r, cy - r, cx + r, cy + r), 20f, 140f, true)
        quadraticTo(cx, cy + r * 0.5f, cx - r * 0.6f, cy + r * 0.8f)
    }
    drawPath(shadowPath, Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = if (isPressed) 0.04f else 0.12f)), startY = size.height * 0.55f, endY = size.height))
}

// ── Center Convert Button ──────────────────────────────────────

@Composable
private fun ConvertButton(isConverting: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val infiniteTransition = rememberInfiniteTransition(label = "convert")
    val rotationAngle by infiniteTransition.animateFloat(0f, 360f, infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart), label = "rot")

    Box(modifier = modifier.size(66.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(Brush.verticalGradient(listOf(Color(0xFFE0E0E0), Color(0xFF909090), Color(0xFFBBBBBB))), size.minDimension / 2f, style = Stroke(5f))
            drawCircle(Color.White.copy(alpha = 0.35f), size.minDimension / 2f + 1f, style = Stroke(0.5f))
        }
        Box(
            modifier = Modifier.size(54.dp)
                .shadow(if (isPressed) 1.dp else 5.dp, CircleShape, ambientColor = Color(0xFF777777), spotColor = Color(0xFF555555))
                .clip(CircleShape)
                .background(if (isPressed) ButtonPressedGradient else ButtonGlossGradient)
                .border(0.5.dp, ChromeBezelBrush, CircleShape)
                .drawBehind { drawGlossyOverlay(isPressed) }
                .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(38.dp)) {
                val cx = size.width / 2f; val cy = size.height / 2f
                val arrowR = size.minDimension / 2f - 3f; val ac = Color(0xFF3A3A3A)
                val sa = if (isConverting) rotationAngle else 0f

                drawArc(ac, sa - 30f, 150f, false, Offset(cx - arrowR, cy - arrowR), Size(arrowR * 2, arrowR * 2), style = Stroke(2.8f))
                val ta = Math.toRadians((sa + 120.0)); drawArrowHead(cx + arrowR * cos(ta).toFloat(), cy + arrowR * sin(ta).toFloat(), sa + 120f, ac)
                drawArc(ac, sa + 150f, 150f, false, Offset(cx - arrowR, cy - arrowR), Size(arrowR * 2, arrowR * 2), style = Stroke(2.8f))
                val ba = Math.toRadians((sa + 300.0)); drawArrowHead(cx + arrowR * cos(ba).toFloat(), cy + arrowR * sin(ba).toFloat(), sa + 300f, ac)

                val nc = Color(0xFF2A2A2A); val s = 1.3f
                drawOval(nc, Offset(cx - 6f * s, cy + 2f * s), Size(9f * s, 6f * s))
                drawOval(nc, Offset(cx + 2f * s, cy + 4f * s), Size(9f * s, 6f * s))
                drawLine(nc, Offset(cx + 1.5f * s, cy + 5f * s), Offset(cx + 1.5f * s, cy - 8f * s), 2.2f)
                drawLine(nc, Offset(cx + 10f * s, cy + 7f * s), Offset(cx + 10f * s, cy - 6f * s), 2.2f)
                drawLine(nc, Offset(cx + 1.5f * s, cy - 8f * s), Offset(cx + 10f * s, cy - 6f * s), 3f)
            }
        }
    }
}

private fun DrawScope.drawArrowHead(x: Float, y: Float, deg: Float, color: Color) {
    val a = Math.toRadians(deg.toDouble()); val l = 6f
    val path = Path().apply {
        moveTo(x, y); lineTo(x - l * cos(a - PI / 6).toFloat(), y - l * sin(a - PI / 6).toFloat())
        moveTo(x, y); lineTo(x - l * cos(a + PI / 6).toFloat(), y - l * sin(a + PI / 6).toFloat())
    }
    drawPath(path, color, style = Stroke(2.5f))
}

// ── Transport Icons ────────────────────────────────────────────

@Composable
private fun PreviousTrackIcon() {
    Canvas(Modifier.size(18.dp)) { val c = Color(0xFF3A3A3A)
        drawLine(c, Offset(3f, 4f), Offset(3f, size.height - 4f), 3f)
        drawPath(Path().apply { moveTo(size.width - 2f, 4f); lineTo(7f, size.height / 2f); lineTo(size.width - 2f, size.height - 4f); close() }, c)
    }
}

@Composable
private fun NextTrackIcon() {
    Canvas(Modifier.size(18.dp)) { val c = Color(0xFF3A3A3A)
        drawPath(Path().apply { moveTo(2f, 4f); lineTo(size.width - 7f, size.height / 2f); lineTo(2f, size.height - 4f); close() }, c)
        drawLine(c, Offset(size.width - 3f, 4f), Offset(size.width - 3f, size.height - 4f), 3f)
    }
}

@Composable
private fun RewindIcon() {
    Canvas(Modifier.size(20.dp)) { val c = Color(0xFF3A3A3A)
        drawPath(Path().apply { moveTo(size.width / 2f, 4f); lineTo(2f, size.height / 2f); lineTo(size.width / 2f, size.height - 4f); close() }, c)
        drawPath(Path().apply { moveTo(size.width - 2f, 4f); lineTo(size.width / 2f, size.height / 2f); lineTo(size.width - 2f, size.height - 4f); close() }, c)
    }
}

@Composable
private fun FastForwardIcon() {
    Canvas(Modifier.size(20.dp)) { val c = Color(0xFF3A3A3A)
        drawPath(Path().apply { moveTo(2f, 4f); lineTo(size.width / 2f, size.height / 2f); lineTo(2f, size.height - 4f); close() }, c)
        drawPath(Path().apply { moveTo(size.width / 2f, 4f); lineTo(size.width - 2f, size.height / 2f); lineTo(size.width / 2f, size.height - 4f); close() }, c)
    }
}
