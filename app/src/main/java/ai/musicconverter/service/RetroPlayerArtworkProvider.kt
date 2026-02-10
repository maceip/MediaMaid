package ai.musicconverter.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface

/**
 * Generates a retro iTunes-era media player skin as a bitmap.
 * Used as MediaSession artwork so the system notification / lock screen
 * shows the classic brushed-aluminum player chrome around track info.
 */
object RetroPlayerArtworkProvider {

    private const val WIDTH = 480
    private const val HEIGHT = 160

    // Aluminum palette
    private const val ALUM_LIGHT = 0xFFD4D4D8.toInt()
    private const val ALUM_MID = 0xFFBCBCC0.toInt()
    private const val ALUM_DARK = 0xFFA4A4A8.toInt()
    private const val LCD_BG = 0xFFFAF6E8.toInt()
    private const val LCD_TEXT = 0xFF222222.toInt()
    private const val BUTTON_LIGHT = 0xFFF0F0F0.toInt()
    private const val BUTTON_DARK = 0xFFBBBBBB.toInt()
    private const val BEZEL = 0xFF999999.toInt()

    /**
     * Render a retro player chrome bitmap with the given track metadata.
     * This bitmap is set as the artwork on MediaMetadata so the system
     * media notification inherits the retro aesthetic.
     */
    fun render(
        context: Context,
        title: String,
        artist: String,
        positionMs: Long = 0L,
        durationMs: Long = 0L
    ): Bitmap {
        val bmp = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        drawChrome(canvas)
        drawTransportButtons(canvas)
        drawLcdPanel(canvas, title, artist, positionMs, durationMs)

        return bmp
    }

    // ── Brushed aluminum background ──────────────────────────────

    private fun drawChrome(canvas: Canvas) {
        val w = canvas.width.toFloat()
        val h = canvas.height.toFloat()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Two-tone gradient: lighter top, darker bottom
        paint.shader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(ALUM_LIGHT, ALUM_MID, ALUM_DARK),
            floatArrayOf(0f, 0.65f, 1f),
            Shader.TileMode.CLAMP
        )
        val r = 12f
        canvas.drawRoundRect(RectF(0f, 0f, w, h), r, r, paint)

        // Top edge highlight
        paint.shader = null
        paint.color = Color.WHITE
        paint.alpha = 160
        paint.strokeWidth = 2f
        paint.style = Paint.Style.STROKE
        canvas.drawLine(r, 1f, w - r, 1f, paint)

        // Ridgeline at ~68%
        val ridgeY = h * 0.68f
        paint.color = Color.BLACK
        paint.alpha = 40
        paint.strokeWidth = 1.5f
        canvas.drawLine(0f, ridgeY, w, ridgeY, paint)
        paint.color = Color.WHITE
        paint.alpha = 100
        canvas.drawLine(0f, ridgeY - 1.5f, w, ridgeY - 1.5f, paint)
        paint.style = Paint.Style.FILL
    }

    // ── Transport buttons (left cluster) ─────────────────────────

    private fun drawTransportButtons(canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val cy = canvas.height * 0.38f
        val btnR = 18f
        val startX = 34f
        val spacing = 46f

        // Three buttons: prev, play, next
        for (i in 0..2) {
            val cx = startX + i * spacing
            // Shadow
            paint.color = 0x33000000
            canvas.drawCircle(cx, cy + 2f, btnR, paint)
            // Button body gradient
            paint.shader = LinearGradient(
                cx, cy - btnR, cx, cy + btnR,
                BUTTON_LIGHT, BUTTON_DARK,
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(cx, cy, btnR, paint)
            paint.shader = null
            // Bezel ring
            paint.color = BEZEL
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f
            canvas.drawCircle(cx, cy, btnR, paint)
            paint.style = Paint.Style.FILL
            // Top highlight
            paint.color = Color.WHITE
            paint.alpha = 180
            canvas.drawCircle(cx, cy - btnR * 0.35f, btnR * 0.45f, paint)

            // Icons
            paint.color = 0xFF2A2A2A.toInt()
            paint.alpha = 255
            when (i) {
                0 -> drawRewindIcon(canvas, cx, cy, paint)
                1 -> drawPlayIcon(canvas, cx, cy, paint)
                2 -> drawFfIcon(canvas, cx, cy, paint)
            }
        }
    }

    private fun drawRewindIcon(canvas: Canvas, cx: Float, cy: Float, paint: Paint) {
        val s = 7f
        // Two left-pointing triangles
        val path = android.graphics.Path()
        path.moveTo(cx - 1f, cy - s)
        path.lineTo(cx - s - 1f, cy)
        path.lineTo(cx - 1f, cy + s)
        path.close()
        canvas.drawPath(path, paint)
        path.reset()
        path.moveTo(cx + s - 1f, cy - s)
        path.lineTo(cx - 1f, cy)
        path.lineTo(cx + s - 1f, cy + s)
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun drawPlayIcon(canvas: Canvas, cx: Float, cy: Float, paint: Paint) {
        val s = 9f
        val path = android.graphics.Path()
        path.moveTo(cx - s * 0.4f, cy - s)
        path.lineTo(cx + s * 0.7f, cy)
        path.lineTo(cx - s * 0.4f, cy + s)
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun drawFfIcon(canvas: Canvas, cx: Float, cy: Float, paint: Paint) {
        val s = 7f
        val path = android.graphics.Path()
        path.moveTo(cx + 1f, cy - s)
        path.lineTo(cx + s + 1f, cy)
        path.lineTo(cx + 1f, cy + s)
        path.close()
        canvas.drawPath(path, paint)
        path.reset()
        path.moveTo(cx - s + 1f, cy - s)
        path.lineTo(cx + 1f, cy)
        path.lineTo(cx - s + 1f, cy + s)
        path.close()
        canvas.drawPath(path, paint)
    }

    // ── LCD display panel ────────────────────────────────────────

    private fun drawLcdPanel(
        canvas: Canvas,
        title: String,
        artist: String,
        positionMs: Long,
        durationMs: Long
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val w = canvas.width.toFloat()

        // Panel bounds — right of the transport buttons
        val left = 168f
        val top = 12f
        val right = w - 16f
        val bottom = canvas.height * 0.68f - 8f
        val panelRect = RectF(left, top, right, bottom)

        // Inset shadow
        paint.color = 0x22000000
        canvas.drawRoundRect(
            RectF(left - 1f, top - 1f, right + 1f, bottom + 1f),
            6f, 6f, paint
        )
        // LCD background
        paint.color = LCD_BG
        canvas.drawRoundRect(panelRect, 5f, 5f, paint)
        // LCD border
        paint.color = BEZEL
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRoundRect(panelRect, 5f, 5f, paint)
        paint.style = Paint.Style.FILL

        // Track info text
        val displayText = if (artist.isNotBlank()) "$title \u2014 $artist" else title
        paint.color = LCD_TEXT
        paint.textSize = 16f
        paint.typeface = Typeface.MONOSPACE

        // Clip text to panel width
        val maxTextWidth = right - left - 24f
        val clipped = if (paint.measureText(displayText) > maxTextWidth) {
            var end = displayText.length
            while (end > 0 && paint.measureText(displayText, 0, end) > maxTextWidth - paint.measureText("...")) {
                end--
            }
            displayText.substring(0, end) + "..."
        } else {
            displayText
        }
        canvas.drawText(clipped, left + 12f, top + 24f, paint)

        // Time display and progress bar
        val posText = formatTime(positionMs)
        val durText = formatTime(durationMs)
        val timeY = bottom - 12f

        paint.textSize = 13f
        paint.color = LCD_TEXT
        canvas.drawText(posText, left + 12f, timeY, paint)

        val durTextWidth = paint.measureText(durText)
        canvas.drawText(durText, right - 12f - durTextWidth, timeY, paint)

        // Progress bar
        val barLeft = left + 12f + paint.measureText(posText) + 10f
        val barRight = right - 12f - durTextWidth - 10f
        val barCy = timeY - 5f
        val barH = 4f

        paint.color = 0xFFCCCCCC.toInt()
        canvas.drawRoundRect(
            RectF(barLeft, barCy - barH / 2, barRight, barCy + barH / 2),
            2f, 2f, paint
        )

        if (durationMs > 0) {
            val frac = (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
            paint.color = 0xFF777777.toInt()
            canvas.drawRoundRect(
                RectF(barLeft, barCy - barH / 2, barLeft + (barRight - barLeft) * frac, barCy + barH / 2),
                2f, 2f, paint
            )
            // Diamond thumb
            val thumbX = barLeft + (barRight - barLeft) * frac
            paint.color = 0xFF555555.toInt()
            val path = android.graphics.Path()
            val d = 5f
            path.moveTo(thumbX, barCy - d)
            path.lineTo(thumbX + d, barCy)
            path.lineTo(thumbX, barCy + d)
            path.lineTo(thumbX - d, barCy)
            path.close()
            canvas.drawPath(path, paint)
        }
    }

    private fun formatTime(ms: Long): String {
        if (ms <= 0) return "--:--"
        val totalSec = (ms / 1000).coerceAtLeast(0)
        val min = totalSec / 60
        val sec = totalSec % 60
        return String.format("%d:%02d", min, sec)
    }
}
