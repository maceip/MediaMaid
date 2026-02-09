package ai.musicconverter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * shape_mini.png - Compact Mini Player for App minimization
 */
@Composable
fun MusicMiniPlayer(
    isConverting: Boolean,
    conversionProgress: Float,
    currentTrackName: String?,
    onConvertClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val barShape = RoundedCornerShape(12.dp)
    
    Box(
        modifier = modifier
            .width(280.dp)
            .height(64.dp)
            .shadow(8.dp, barShape)
            .clip(barShape)
            .then(aluminumBackgroundModifier(AluminumVariant.NEUTRAL))
            .border(0.5.dp, Color.White.copy(alpha = 0.5f), barShape)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mini Icon / Album Art placeholder
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = Color(0xFF555555)
                )
            }
            
            Spacer(Modifier.width(10.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = currentTrackName ?: "No track selected",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color(0xFF333333)
                )
                
                // Mini progress bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.Black.copy(alpha = 0.1f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(conversionProgress)
                            .height(4.dp)
                            .background(Color(0xFF00FF88))
                    )
                }
            }
            
            // Minimal Play/Stop button
            GlossyButton(
                onClick = onConvertClick,
                size = 38
            ) {
                if (isConverting) {
                    Box(Modifier.size(12.dp).background(Color(0xFF2A2A2A)))
                } else {
                    // Small triangle
                    androidx.compose.foundation.Canvas(modifier = Modifier.size(14.dp)) {
                        val path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(size.width * 0.2f, size.height * 0.15f)
                            lineTo(size.width * 0.9f, size.height * 0.5f)
                            lineTo(size.width * 0.2f, size.height * 0.85f)
                            close()
                        }
                        drawPath(path, Color(0xFF2A2A2A))
                    }
                }
            }
        }
    }
}

/**
 * shape_widget.png - Android Widget Layout using Compose logic
 */
@Composable
fun MusicWidgetPlayer(
    isConverting: Boolean,
    conversionProgress: Float,
    currentTrackName: String?,
    pendingCount: Int,
    onConvertClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val widgetShape = RoundedCornerShape(16.dp)
    
    Box(
        modifier = modifier
            .width(320.dp)
            .height(110.dp)
            .shadow(4.dp, widgetShape)
            .clip(widgetShape)
            .then(aluminumBackgroundModifier(AluminumVariant.NEUTRAL))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "MEDIAMAID",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.sp,
                            color = Color(0xFF555555),
                            letterSpacing = 1.sp
                        )
                    )
                    Text(
                        currentTrackName ?: "Queue: $pendingCount files",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = Color(0xFF222222)
                    )
                }
                
                // Status dot
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(if (isConverting) Color(0xFF00FF88) else Color(0xFF888888))
                )
            }
            
            Spacer(Modifier.height(8.dp))
            
            // Calculator style progress inside widget
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFFAF6E8))
                    .border(0.5.dp, Color(0xFF999999), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(conversionProgress)
                        .height(12.dp)
                        .background(Color(0xFF777777).copy(alpha = 0.3f))
                )
                Text(
                    text = if (isConverting) "CONVERTING... ${(conversionProgress * 100).toInt()}%" else "IDLE",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF222222)
                    )
                )
            }
            
            Spacer(Modifier.height(8.dp))
            
            // Transport controls for widget
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlossyButton(onClick = {}, size = 32) { PreviousTrackIcon() }
                GlossyButton(onClick = onConvertClick, size = 42) {
                    if (isConverting) {
                        Box(Modifier.size(14.dp).background(Color(0xFF2A2A2A)))
                    } else {
                        androidx.compose.foundation.Canvas(modifier = Modifier.size(16.dp)) {
                            val path = androidx.compose.ui.graphics.Path().apply {
                                moveTo(size.width * 0.2f, size.height * 0.15f)
                                lineTo(size.width * 0.9f, size.height * 0.5f)
                                lineTo(size.width * 0.2f, size.height * 0.85f)
                                close()
                            }
                            drawPath(path, Color(0xFF2A2A2A))
                        }
                    }
                }
                GlossyButton(onClick = {}, size = 32) { NextTrackIcon() }
            }
        }
    }
}