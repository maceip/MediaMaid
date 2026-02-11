package ai.musicconverter.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.text.FontWeight
import androidx.glance.unit.ColorProvider

// Aluminum palette for the widget
private val WidgetBg = Color(0xFFCDCDD2)
private val WidgetText = ColorProvider(Color(0xFF333333))
private val WidgetSecondary = ColorProvider(Color(0xFF666666))
private val LcdBg = Color(0xFFE4EEE0)
private val LcdText = ColorProvider(Color(0xFF2A2A2A))
private val ButtonBg = Color(0xFFE0E0E4)
private val ButtonText = ColorProvider(Color(0xFF444444))

class MusicWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            WidgetContent()
        }
    }

    @androidx.compose.runtime.Composable
    private fun WidgetContent() {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(16.dp)
                .background(WidgetBg)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header row
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = GlanceModifier.defaultWeight()
                ) {
                    Text(
                        text = "MEDIAMAID",
                        style = TextStyle(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = WidgetSecondary
                        )
                    )
                    Text(
                        text = "Queue: Idle",
                        style = TextStyle(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = WidgetText
                        )
                    )
                }

                // Status indicator
                Box(
                    modifier = GlanceModifier
                        .height(8.dp)
                        .width(8.dp)
                        .cornerRadius(4.dp)
                        .background(Color(0xFF88AA88))
                ) {}
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            // LCD display
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .cornerRadius(6.dp)
                    .background(LcdBg)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = "\u266A  IDLE  \u266A",
                    style = TextStyle(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = LcdText
                    )
                )
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            // Transport controls row
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = GlanceModifier.defaultWeight())
                WidgetButton("\u23EE")
                Spacer(modifier = GlanceModifier.width(6.dp))
                WidgetButton("\u25B6")
                Spacer(modifier = GlanceModifier.width(6.dp))
                WidgetButton("\u23ED")
                Spacer(modifier = GlanceModifier.defaultWeight())
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun WidgetButton(label: String) {
        Box(
            modifier = GlanceModifier
                .height(30.dp)
                .width(40.dp)
                .cornerRadius(6.dp)
                .background(ButtonBg),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = ButtonText
                )
            )
        }
    }
}

class MusicWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MusicWidget()
}
