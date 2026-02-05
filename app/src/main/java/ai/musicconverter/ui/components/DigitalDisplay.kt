package ai.musicconverter.ui.components

import android.app.ActivityManager
import android.content.Context
import android.os.BatteryManager
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.sin

private val DisplayBackground = Color(0xFF1A1A1A)
private val DisplayBorder = Color(0xFF3A3A3A)
private val SegmentOn = Color(0xFF00FF88)
private val SegmentDim = Color(0xFF0A2A18)
private val AmberOn = Color(0xFFFFAA00)
private val AmberDim = Color(0xFF2A1A00)
private val RedOn = Color(0xFFFF3333)
private val BlueOn = Color(0xFF33AAFF)
private val CubeOn = Color(0xFF00FF88)
private val CubeDim = Color(0xFF0A2A18)

/**
 * Retro digital instrument cluster display.
 *
 * Shows: Battery | Phone Temp | Animated Cubes | Songs In Queue (7-seg) | RAM
 */
@Composable
fun DigitalDisplay(
    songsInQueue: Int,
    isConverting: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var batteryPercent by remember { mutableIntStateOf(100) }
    var temperature by remember { mutableFloatStateOf(0f) }
    var ramPercent by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        while (true) {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            batteryPercent = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100
            temperature = try {
                // Read CPU thermal zone
                val temp = java.io.File("/sys/class/thermal/thermal_zone0/temp")
                    .readText().trim().toFloatOrNull() ?: 0f
                if (temp > 1000) temp / 1000f else temp
            } catch (_: Exception) { 0f }
            ramPercent = getRamPercent(context)
            delay(2000)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "display")
    val blinkPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "blink"
    )
    val cubePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "cube"
    )

    Box(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(DisplayBackground)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val pad = 4.dp.toPx()

            // Outer border
            drawRoundRect(
                color = DisplayBorder,
                size = Size(w, h),
                cornerRadius = CornerRadius(6.dp.toPx()),
                style = Stroke(width = 1.5.dp.toPx())
            )

            // Inner border
            drawRoundRect(
                color = DisplayBorder.copy(alpha = 0.5f),
                topLeft = Offset(2.dp.toPx(), 2.dp.toPx()),
                size = Size(w - 4.dp.toPx(), h - 4.dp.toPx()),
                cornerRadius = CornerRadius(4.dp.toPx()),
                style = Stroke(width = 0.5.dp.toPx())
            )

            val contentW = w - pad * 2
            val contentH = h - pad * 2

            // Layout: battery(15%) | temp(15%) | cubes(15%) | queue(40%) | ram(15%)
            val batteryW = contentW * 0.14f
            val tempW = contentW * 0.14f
            val cubesW = contentW * 0.14f
            val queueW = contentW * 0.40f
            val ramW = contentW * 0.14f

            var xOffset = pad

            // === Battery gauge ===
            drawBatteryGauge(
                x = xOffset, y = pad,
                width = batteryW, height = contentH,
                percent = batteryPercent
            )
            xOffset += batteryW + pad * 0.5f

            // Separator
            drawLine(DisplayBorder, Offset(xOffset, pad + 2), Offset(xOffset, pad + contentH - 2), 0.5.dp.toPx())
            xOffset += pad * 0.5f

            // === Temperature ===
            drawTemperature(
                x = xOffset, y = pad,
                width = tempW, height = contentH,
                temp = temperature
            )
            xOffset += tempW + pad * 0.5f

            drawLine(DisplayBorder, Offset(xOffset, pad + 2), Offset(xOffset, pad + contentH - 2), 0.5.dp.toPx())
            xOffset += pad * 0.5f

            // === Animated cubes ===
            drawBlinkingCubes(
                x = xOffset, y = pad,
                width = cubesW, height = contentH,
                phase = cubePhase,
                isActive = isConverting
            )
            xOffset += cubesW + pad * 0.5f

            drawLine(DisplayBorder, Offset(xOffset, pad + 2), Offset(xOffset, pad + contentH - 2), 0.5.dp.toPx())
            xOffset += pad * 0.5f

            // === Songs in queue (large 7-segment) ===
            drawQueueDisplay(
                x = xOffset, y = pad,
                width = queueW, height = contentH,
                count = songsInQueue,
                blinkPhase = blinkPhase,
                isActive = isConverting
            )
            xOffset += queueW + pad * 0.5f

            drawLine(DisplayBorder, Offset(xOffset, pad + 2), Offset(xOffset, pad + contentH - 2), 0.5.dp.toPx())
            xOffset += pad * 0.5f

            // === RAM gauge ===
            drawRamGauge(
                x = xOffset, y = pad,
                width = ramW, height = contentH,
                percent = ramPercent
            )

            // Scanline effect overlay
            for (y in 0..h.toInt() step 3) {
                drawLine(
                    Color.Black.copy(alpha = 0.08f),
                    Offset(0f, y.toFloat()),
                    Offset(w, y.toFloat()),
                    0.5f
                )
            }
        }
    }
}

// === 7-Segment Display ===

private val SEVEN_SEG_MAP = mapOf(
    0 to booleanArrayOf(true, true, true, true, true, true, false),
    1 to booleanArrayOf(false, true, true, false, false, false, false),
    2 to booleanArrayOf(true, true, false, true, true, false, true),
    3 to booleanArrayOf(true, true, true, true, false, false, true),
    4 to booleanArrayOf(false, true, true, false, false, true, true),
    5 to booleanArrayOf(true, false, true, true, false, true, true),
    6 to booleanArrayOf(true, false, true, true, true, true, true),
    7 to booleanArrayOf(true, true, true, false, false, false, false),
    8 to booleanArrayOf(true, true, true, true, true, true, true),
    9 to booleanArrayOf(true, true, true, true, false, true, true)
)

/**
 * Draw a single 7-segment digit.
 * Segments: 0=top, 1=topRight, 2=botRight, 3=bot, 4=botLeft, 5=topLeft, 6=mid
 */
private fun DrawScope.drawSevenSegDigit(
    cx: Float, cy: Float,
    digitWidth: Float, digitHeight: Float,
    digit: Int,
    onColor: Color = SegmentOn,
    offColor: Color = SegmentDim
) {
    val segments = SEVEN_SEG_MAP[digit] ?: SEVEN_SEG_MAP[8]!!
    val segW = digitWidth * 0.7f
    val segH = digitHeight * 0.08f
    val halfH = digitHeight / 2f
    val left = cx - digitWidth / 2f
    val right = cx + digitWidth / 2f
    val top = cy - digitHeight / 2f
    val mid = cy
    val bot = cy + digitHeight / 2f
    val inset = segH * 0.5f

    // Horizontal segments
    fun hSeg(x: Float, y: Float, on: Boolean) {
        drawRoundRect(
            color = if (on) onColor else offColor,
            topLeft = Offset(x - segW / 2, y - segH / 2),
            size = Size(segW, segH),
            cornerRadius = CornerRadius(segH / 2)
        )
    }

    // Vertical segments
    fun vSeg(x: Float, y: Float, segLength: Float, on: Boolean) {
        drawRoundRect(
            color = if (on) onColor else offColor,
            topLeft = Offset(x - segH / 2, y),
            size = Size(segH, segLength),
            cornerRadius = CornerRadius(segH / 2)
        )
    }

    val vLen = halfH - inset * 2

    hSeg(cx, top + segH / 2, segments[0])                      // top
    vSeg(right - segH, top + inset, vLen, segments[1])          // top right
    vSeg(right - segH, mid + inset, vLen, segments[2])          // bot right
    hSeg(cx, bot - segH / 2, segments[3])                       // bottom
    vSeg(left, mid + inset, vLen, segments[4])                  // bot left
    vSeg(left, top + inset, vLen, segments[5])                  // top left
    hSeg(cx, mid, segments[6])                                  // middle
}

private fun DrawScope.drawQueueDisplay(
    x: Float, y: Float,
    width: Float, height: Float,
    count: Int,
    blinkPhase: Float,
    isActive: Boolean
) {
    // Label "songs" at top
    val labelH = height * 0.2f
    val digitAreaH = height - labelH

    // Draw "songs" label as small dots pattern
    val labelY = y + labelH * 0.5f
    val labelColor = SegmentOn.copy(alpha = 0.5f)
    // Simple small text approximation via dots
    val dotSize = 1.5.dp.toPx()
    val labelCx = x + width / 2

    // Draw up to 3 digits for the count
    val displayCount = count.coerceIn(0, 999)
    val digits = when {
        displayCount >= 100 -> listOf(displayCount / 100, (displayCount / 10) % 10, displayCount % 10)
        displayCount >= 10 -> listOf(displayCount / 10, displayCount % 10)
        else -> listOf(displayCount)
    }

    val digitW = (width * 0.8f) / 3f
    val digitH = digitAreaH * 0.75f
    val startX = x + (width - digits.size * digitW) / 2f

    for ((i, d) in digits.withIndex()) {
        val dcx = startX + digitW * i + digitW / 2f
        val dcy = y + labelH + digitAreaH / 2f
        drawSevenSegDigit(dcx, dcy, digitW * 0.85f, digitH, d)
    }

    // "km/h" style label below digits
    val smallDot = 1.dp.toPx()
    val labelBottomY = y + height - 3.dp.toPx()
    for (i in 0..2) {
        drawCircle(
            color = SegmentOn.copy(alpha = 0.4f),
            radius = smallDot,
            center = Offset(labelCx - 4.dp.toPx() + i * 4.dp.toPx(), labelBottomY)
        )
    }

    // Colon blink when active
    if (isActive) {
        val colonAlpha = if (sin(blinkPhase.toDouble()) > 0) 0.9f else 0.15f
        val colonX = x + width * 0.9f
        val colonY1 = y + labelH + digitAreaH * 0.35f
        val colonY2 = y + labelH + digitAreaH * 0.65f
        drawCircle(SegmentOn.copy(alpha = colonAlpha), 2.dp.toPx(), Offset(colonX, colonY1))
        drawCircle(SegmentOn.copy(alpha = colonAlpha), 2.dp.toPx(), Offset(colonX, colonY2))
    }
}

private fun DrawScope.drawBatteryGauge(
    x: Float, y: Float,
    width: Float, height: Float,
    percent: Int
) {
    val cx = x + width / 2
    val gaugeW = width * 0.5f
    val gaugeH = height * 0.65f
    val gaugeTop = y + height * 0.2f

    // Battery outline
    val battLeft = cx - gaugeW / 2
    val battTop = gaugeTop + 3.dp.toPx()
    val battH = gaugeH - 3.dp.toPx()

    // Battery tip
    val tipW = gaugeW * 0.4f
    drawRoundRect(
        color = SegmentOn.copy(alpha = 0.4f),
        topLeft = Offset(cx - tipW / 2, gaugeTop),
        size = Size(tipW, 3.dp.toPx()),
        cornerRadius = CornerRadius(1.dp.toPx())
    )

    // Battery body outline
    drawRoundRect(
        color = SegmentOn.copy(alpha = 0.5f),
        topLeft = Offset(battLeft, battTop),
        size = Size(gaugeW, battH),
        cornerRadius = CornerRadius(2.dp.toPx()),
        style = Stroke(width = 1.dp.toPx())
    )

    // Fill level
    val fillPercent = percent.coerceIn(0, 100) / 100f
    val fillH = (battH - 3.dp.toPx()) * fillPercent
    val fillColor = when {
        percent > 50 -> SegmentOn
        percent > 20 -> AmberOn
        else -> RedOn
    }
    drawRoundRect(
        color = fillColor.copy(alpha = 0.8f),
        topLeft = Offset(battLeft + 1.5.dp.toPx(), battTop + battH - 1.5.dp.toPx() - fillH),
        size = Size(gaugeW - 3.dp.toPx(), fillH),
        cornerRadius = CornerRadius(1.dp.toPx())
    )

    // Percent text as tiny segments below
    val pctY = gaugeTop + gaugeH + 3.dp.toPx()
    val pctDigitW = width * 0.3f
    val pctDigitH = height * 0.18f
    val tensDigit = (percent / 10).coerceIn(0, 9)
    val onesDigit = percent % 10
    if (percent >= 10) {
        drawSevenSegDigit(cx - pctDigitW * 0.4f, pctY + pctDigitH / 2, pctDigitW * 0.8f, pctDigitH, tensDigit, SegmentOn.copy(alpha = 0.6f), SegmentDim)
    }
    drawSevenSegDigit(cx + pctDigitW * 0.4f, pctY + pctDigitH / 2, pctDigitW * 0.8f, pctDigitH, onesDigit, SegmentOn.copy(alpha = 0.6f), SegmentDim)
}

private fun DrawScope.drawTemperature(
    x: Float, y: Float,
    width: Float, height: Float,
    temp: Float
) {
    val cx = x + width / 2

    // Thermometer icon area
    val iconH = height * 0.45f
    val iconW = width * 0.25f
    val iconTop = y + height * 0.1f
    val bulbR = iconW * 0.8f

    // Thermometer tube
    drawRoundRect(
        color = AmberOn.copy(alpha = 0.4f),
        topLeft = Offset(cx - iconW / 2, iconTop),
        size = Size(iconW, iconH),
        cornerRadius = CornerRadius(iconW / 2),
        style = Stroke(width = 1.dp.toPx())
    )

    // Thermometer fill
    val fillLevel = (temp / 80f).coerceIn(0f, 1f) // Assume 80°C max
    val fillH = iconH * fillLevel
    val fillColor = when {
        temp > 50 -> RedOn
        temp > 35 -> AmberOn
        else -> SegmentOn
    }
    drawRoundRect(
        color = fillColor.copy(alpha = 0.7f),
        topLeft = Offset(cx - iconW / 2 + 1.dp.toPx(), iconTop + iconH - fillH),
        size = Size(iconW - 2.dp.toPx(), fillH),
        cornerRadius = CornerRadius(iconW / 2)
    )

    // Bulb at bottom
    drawCircle(
        color = fillColor.copy(alpha = 0.7f),
        radius = bulbR,
        center = Offset(cx, iconTop + iconH + bulbR * 0.5f)
    )

    // Temperature number
    val tempInt = temp.toInt().coerceIn(0, 99)
    val numY = y + height * 0.78f
    val digitW = width * 0.35f
    val digitH = height * 0.18f
    if (tempInt >= 10) {
        drawSevenSegDigit(cx - digitW * 0.35f, numY, digitW * 0.7f, digitH, tempInt / 10, AmberOn.copy(alpha = 0.7f), AmberDim)
    }
    drawSevenSegDigit(cx + digitW * 0.35f, numY, digitW * 0.7f, digitH, tempInt % 10, AmberOn.copy(alpha = 0.7f), AmberDim)

    // Degree symbol
    drawCircle(
        color = AmberOn.copy(alpha = 0.5f),
        radius = 1.dp.toPx(),
        center = Offset(cx + digitW * 0.8f, numY - digitH * 0.35f),
        style = Stroke(width = 0.5.dp.toPx())
    )
}

private fun DrawScope.drawBlinkingCubes(
    x: Float, y: Float,
    width: Float, height: Float,
    phase: Float,
    isActive: Boolean
) {
    val cols = 3
    val rows = 4
    val cubeSize = minOf(width / (cols + 1), height / (rows + 1)) * 0.7f
    val gapX = (width - cols * cubeSize) / (cols + 1)
    val gapY = (height - rows * cubeSize) / (rows + 1)

    for (row in 0 until rows) {
        for (col in 0 until cols) {
            val cx = x + gapX + col * (cubeSize + gapX) + cubeSize / 2
            val cy = y + gapY + row * (cubeSize + gapY) + cubeSize / 2

            val isOn = if (isActive) {
                // Cascading blink pattern
                val idx = row * cols + col
                val threshold = (phase * (cols * rows)).toInt() % (cols * rows)
                idx <= threshold
            } else {
                false
            }

            drawRoundRect(
                color = if (isOn) CubeOn else CubeDim,
                topLeft = Offset(cx - cubeSize / 2, cy - cubeSize / 2),
                size = Size(cubeSize, cubeSize),
                cornerRadius = CornerRadius(1.dp.toPx())
            )
        }
    }
}

private fun DrawScope.drawRamGauge(
    x: Float, y: Float,
    width: Float, height: Float,
    percent: Float
) {
    val cx = x + width / 2

    // Bar gauge (vertical)
    val barW = width * 0.4f
    val barH = height * 0.6f
    val barTop = y + height * 0.1f
    val barLeft = cx - barW / 2

    // Bar outline
    drawRoundRect(
        color = BlueOn.copy(alpha = 0.4f),
        topLeft = Offset(barLeft, barTop),
        size = Size(barW, barH),
        cornerRadius = CornerRadius(2.dp.toPx()),
        style = Stroke(width = 1.dp.toPx())
    )

    // Segmented fill
    val segments = 8
    val segH = (barH - 2.dp.toPx()) / segments
    val filledSegments = (segments * percent).toInt()

    for (i in 0 until segments) {
        val isOn = i < filledSegments
        val segTop = barTop + barH - 1.dp.toPx() - (i + 1) * segH
        val segColor = when {
            i >= 6 -> if (isOn) RedOn else RedOn.copy(alpha = 0.08f)
            i >= 4 -> if (isOn) AmberOn else AmberOn.copy(alpha = 0.08f)
            else -> if (isOn) BlueOn else BlueOn.copy(alpha = 0.08f)
        }
        drawRect(
            color = segColor,
            topLeft = Offset(barLeft + 1.5.dp.toPx(), segTop),
            size = Size(barW - 3.dp.toPx(), segH - 1.dp.toPx())
        )
    }

    // RAM label as small segments
    val pctInt = (percent * 100).toInt().coerceIn(0, 99)
    val numY = y + height * 0.82f
    val digitW = width * 0.35f
    val digitH = height * 0.16f
    drawSevenSegDigit(cx - digitW * 0.35f, numY, digitW * 0.7f, digitH, pctInt / 10, BlueOn.copy(alpha = 0.6f), BlueOn.copy(alpha = 0.08f))
    drawSevenSegDigit(cx + digitW * 0.35f, numY, digitW * 0.7f, digitH, pctInt % 10, BlueOn.copy(alpha = 0.6f), BlueOn.copy(alpha = 0.08f))
}

private fun getRamPercent(context: Context): Float {
    return try {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val used = memInfo.totalMem - memInfo.availMem
        (used.toFloat() / memInfo.totalMem.toFloat()).coerceIn(0f, 1f)
    } catch (_: Exception) { 0.5f }
}
