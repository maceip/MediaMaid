package ai.musicconverter.ui.components

import android.app.ActivityManager
import android.content.Context
import android.os.StatFs
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.sin

/**
 * Acrylic tube status rail showing system metrics with animated liquid levels.
 *
 * Sections (bottom to top):
 * 1. Disk space - grey sludge
 * 2. RAM usage - blue liquid
 * 3. Encode rate - green/yellow pulse
 * 4. Files remaining - purple bubbles
 */
@Composable
fun StatusTube(
    filesRemaining: Int,
    isConverting: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // System metrics state
    var diskUsedPercent by remember { mutableFloatStateOf(0f) }
    var ramUsedPercent by remember { mutableFloatStateOf(0f) }
    var encodeRate by remember { mutableFloatStateOf(0f) }
    var lastConvertedCount by remember { mutableIntStateOf(0) }
    var lastCheckTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Update system metrics periodically
    LaunchedEffect(Unit) {
        while (true) {
            diskUsedPercent = getDiskUsagePercent()
            ramUsedPercent = getRamUsagePercent(context)
            delay(1000)
        }
    }

    // Calculate encode rate (files per second)
    LaunchedEffect(filesRemaining, isConverting) {
        if (isConverting) {
            val now = System.currentTimeMillis()
            val elapsed = (now - lastCheckTime) / 1000f
            if (elapsed > 0 && lastConvertedCount > 0) {
                val converted = lastConvertedCount - filesRemaining
                if (converted > 0) {
                    encodeRate = converted / elapsed
                }
            }
            lastCheckTime = now
            lastConvertedCount = filesRemaining
        } else {
            encodeRate = 0f
            lastConvertedCount = filesRemaining
            lastCheckTime = System.currentTimeMillis()
        }
    }

    // Animated values for smooth transitions
    val animatedDisk by animateFloatAsState(
        targetValue = diskUsedPercent,
        animationSpec = tween(500),
        label = "disk"
    )
    val animatedRam by animateFloatAsState(
        targetValue = ramUsedPercent,
        animationSpec = tween(500),
        label = "ram"
    )
    val animatedRate by animateFloatAsState(
        targetValue = encodeRate.coerceIn(0f, 5f) / 5f, // Normalize to 0-1
        animationSpec = tween(300),
        label = "rate"
    )
    val animatedFiles by animateFloatAsState(
        targetValue = (filesRemaining.coerceIn(0, 100) / 100f),
        animationSpec = tween(300),
        label = "files"
    )

    // Bubble/wave animation
    val infiniteTransition = rememberInfiniteTransition(label = "tube")
    val waveOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave"
    )

    val bubbleOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "bubble"
    )

    Column(
        modifier = modifier
            .width(32.dp)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // Files remaining label
        Text(
            text = if (filesRemaining > 99) "99+" else filesRemaining.toString(),
            style = MaterialTheme.typography.labelSmall,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // The tube
        Box(
            modifier = Modifier
                .weight(1f)
                .width(24.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0x20FFFFFF),
                            Color(0x10FFFFFF),
                            Color(0x20FFFFFF)
                        )
                    )
                )
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(24.dp)
            ) {
                val tubeWidth = size.width
                val tubeHeight = size.height
                val sectionHeight = tubeHeight / 4f
                val cornerRadius = 12.dp.toPx()

                // Draw tube outline (glass effect)
                drawRoundRect(
                    color = Color(0x40FFFFFF),
                    size = Size(tubeWidth, tubeHeight),
                    cornerRadius = CornerRadius(cornerRadius, cornerRadius)
                )

                // Section 1 (bottom): Disk space - grey sludge
                drawLiquidSection(
                    yStart = tubeHeight - sectionHeight,
                    sectionHeight = sectionHeight,
                    fillPercent = animatedDisk,
                    baseColor = Color(0xFF5C5C5C),
                    highlightColor = Color(0xFF7A7A7A),
                    waveOffset = waveOffset,
                    tubeWidth = tubeWidth,
                    cornerRadius = cornerRadius,
                    isBottom = true
                )

                // Section 2: RAM - blue liquid
                drawLiquidSection(
                    yStart = tubeHeight - sectionHeight * 2,
                    sectionHeight = sectionHeight,
                    fillPercent = animatedRam,
                    baseColor = Color(0xFF2196F3),
                    highlightColor = Color(0xFF64B5F6),
                    waveOffset = waveOffset + 90f,
                    tubeWidth = tubeWidth,
                    cornerRadius = cornerRadius,
                    isBottom = false
                )

                // Section 3: Encode rate - green/yellow pulse
                drawLiquidSection(
                    yStart = tubeHeight - sectionHeight * 3,
                    sectionHeight = sectionHeight,
                    fillPercent = if (isConverting) animatedRate.coerceAtLeast(0.1f) else 0f,
                    baseColor = Color(0xFF4CAF50),
                    highlightColor = Color(0xFFFFEB3B),
                    waveOffset = waveOffset + 180f,
                    tubeWidth = tubeWidth,
                    cornerRadius = cornerRadius,
                    isBottom = false,
                    isPulsing = isConverting
                )

                // Section 4 (top): Files remaining - purple with bubbles
                drawBubbleSection(
                    yStart = 0f,
                    sectionHeight = sectionHeight,
                    fillPercent = animatedFiles,
                    baseColor = Color(0xFF9C27B0),
                    highlightColor = Color(0xFFE1BEE7),
                    bubbleOffset = bubbleOffset,
                    tubeWidth = tubeWidth,
                    cornerRadius = cornerRadius,
                    filesRemaining = filesRemaining
                )

                // Draw section dividers
                for (i in 1..3) {
                    val y = sectionHeight * i
                    drawLine(
                        color = Color(0x40FFFFFF),
                        start = Offset(2f, y),
                        end = Offset(tubeWidth - 2f, y),
                        strokeWidth = 1f
                    )
                }

                // Glass shine effect
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color(0x00FFFFFF),
                            Color(0x30FFFFFF),
                            Color(0x00FFFFFF)
                        ),
                        startX = 0f,
                        endX = tubeWidth * 0.4f
                    ),
                    size = Size(tubeWidth * 0.3f, tubeHeight),
                    topLeft = Offset(2f, 0f),
                    cornerRadius = CornerRadius(cornerRadius, cornerRadius)
                )
            }
        }

        // Rate label
        Text(
            text = if (isConverting && encodeRate > 0) {
                String.format("%.1f/s", encodeRate)
            } else {
                "—"
            },
            style = MaterialTheme.typography.labelSmall,
            fontSize = 7.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun DrawScope.drawLiquidSection(
    yStart: Float,
    sectionHeight: Float,
    fillPercent: Float,
    baseColor: Color,
    highlightColor: Color,
    waveOffset: Float,
    tubeWidth: Float,
    cornerRadius: Float,
    isBottom: Boolean,
    isPulsing: Boolean = false
) {
    val fillHeight = sectionHeight * fillPercent.coerceIn(0f, 1f)
    if (fillHeight <= 0) return

    val liquidTop = yStart + sectionHeight - fillHeight

    // Animated color for pulsing effect
    val color = if (isPulsing) {
        val pulseAmount = (sin(Math.toRadians(waveOffset.toDouble())) * 0.5 + 0.5).toFloat()
        androidx.compose.ui.graphics.lerp(baseColor, highlightColor, pulseAmount)
    } else {
        baseColor
    }

    // Draw liquid with wave effect at top
    val path = Path().apply {
        val waveAmplitude = 2f
        val startY = liquidTop + sin(Math.toRadians(waveOffset.toDouble())).toFloat() * waveAmplitude

        moveTo(2f, yStart + sectionHeight)

        // Bottom edge
        if (isBottom) {
            lineTo(2f, yStart + sectionHeight - cornerRadius)
            quadraticBezierTo(2f, yStart + sectionHeight, cornerRadius, yStart + sectionHeight)
            lineTo(tubeWidth - cornerRadius, yStart + sectionHeight)
            quadraticBezierTo(tubeWidth - 2f, yStart + sectionHeight, tubeWidth - 2f, yStart + sectionHeight - cornerRadius)
        }

        lineTo(2f, yStart + sectionHeight)
        lineTo(2f, startY)

        // Wavy top
        for (x in 0..tubeWidth.toInt() step 4) {
            val waveY = liquidTop + sin(Math.toRadians((waveOffset + x * 10).toDouble())).toFloat() * waveAmplitude
            lineTo(x.toFloat(), waveY)
        }

        lineTo(tubeWidth - 2f, liquidTop)
        lineTo(tubeWidth - 2f, yStart + sectionHeight)
        close()
    }

    drawPath(
        path = path,
        brush = Brush.verticalGradient(
            colors = listOf(highlightColor.copy(alpha = 0.7f), color),
            startY = liquidTop,
            endY = yStart + sectionHeight
        )
    )
}

private fun DrawScope.drawBubbleSection(
    yStart: Float,
    sectionHeight: Float,
    fillPercent: Float,
    baseColor: Color,
    highlightColor: Color,
    bubbleOffset: Float,
    tubeWidth: Float,
    cornerRadius: Float,
    filesRemaining: Int
) {
    val fillHeight = sectionHeight * fillPercent.coerceIn(0f, 1f)
    if (fillHeight <= 0 && filesRemaining == 0) return

    val liquidTop = yStart + sectionHeight - fillHeight.coerceAtLeast(sectionHeight * 0.1f)

    // Draw base liquid
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(highlightColor.copy(alpha = 0.5f), baseColor),
            startY = yStart,
            endY = yStart + sectionHeight
        ),
        topLeft = Offset(2f, liquidTop),
        size = Size(tubeWidth - 4f, yStart + sectionHeight - liquidTop),
        cornerRadius = CornerRadius(cornerRadius, cornerRadius)
    )

    // Draw bubbles based on files remaining
    val bubbleCount = (filesRemaining.coerceIn(0, 10)).coerceAtLeast(if (filesRemaining > 0) 1 else 0)
    for (i in 0 until bubbleCount) {
        val bubbleX = tubeWidth * (0.2f + (i % 3) * 0.3f)
        val bubbleY = yStart + sectionHeight * (0.2f + ((bubbleOffset + i * 0.2f) % 1f) * 0.6f)
        val bubbleRadius = 2f + (i % 2)

        drawCircle(
            color = highlightColor.copy(alpha = 0.6f),
            radius = bubbleRadius,
            center = Offset(bubbleX, bubbleY)
        )
    }
}

private fun getDiskUsagePercent(): Float {
    return try {
        val stat = StatFs(android.os.Environment.getExternalStorageDirectory().path)
        val total = stat.blockCountLong * stat.blockSizeLong
        val available = stat.availableBlocksLong * stat.blockSizeLong
        val used = total - available
        (used.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    } catch (e: Exception) {
        0.5f
    }
}

private fun getRamUsagePercent(context: Context): Float {
    return try {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        val used = memInfo.totalMem - memInfo.availMem
        (used.toFloat() / memInfo.totalMem.toFloat()).coerceIn(0f, 1f)
    } catch (e: Exception) {
        0.5f
    }
}
