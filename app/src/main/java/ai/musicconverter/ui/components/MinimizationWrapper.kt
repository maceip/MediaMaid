package ai.musicconverter.ui.components

import android.app.Activity
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.zIndex
import kotlin.coroutines.cancellation.CancellationException

/**
 * MinimizationWrapper - Clean perspective shifted UI minimization.
 * 
 * 3 Animation States:
 * 1. Garage Door: App Bar slides down to meet Bottom Bar, hiding content.
 * 2. Morph: Transition into MusicMiniPlayer / MusicWidgetPlayer.
 * 3. Collapsing: Uniform scale down.
 */
@Composable
fun MinimizationWrapper(
    isConverting: Boolean = false,
    conversionProgress: Float = 0f,
    currentTrackName: String? = "Syncing Master...",
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val animatedProgress = remember { Animatable(0f) }
    var isGestureActive by remember { mutableStateOf(false) }

    PredictiveBackHandler(enabled = true) { backEvents ->
        isGestureActive = true
        try {
            backEvents.collect { event ->
                animatedProgress.snapTo(event.progress)
            }
            // Committed
            animatedProgress.animateTo(1f, tween(400, easing = FastOutSlowInEasing))
            (context as? Activity)?.finish()
        } catch (_: CancellationException) {
            animatedProgress.animateTo(0f, tween(250))
            isGestureActive = false
        }
    }

    val p = animatedProgress.value

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        
        // Phase 1: Garage Door Close (0.0 -> 0.4)
        // Phase 2: Morph to Mini (0.4 -> 0.7)
        // Phase 3: Collapse (0.7 -> 1.0)
        
        val garageProgress = (p / 0.4f).coerceIn(0f, 1f)
        val morphProgress = ((p - 0.4f) / 0.3f).coerceIn(0f, 1f)
        val collapseProgress = ((p - 0.7f) / 0.3f).coerceIn(0f, 1f)

        // Main Content UI
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Garage Door: Scale Y down while shifting origin to bottom
                    // But we want a "Perspective Shift"
                    
                    val scaleYFactor = 1f - (garageProgress * 0.85f)
                    scaleY = scaleYFactor
                    
                    // Perspective Tilt
                    rotationX = garageProgress * 15f
                    cameraDistance = 12f * density
                    
                    // Anchor at bottom
                    transformOrigin = TransformOrigin(0.5f, 1.0f)
                    
                    // Fade out as it morphs
                    alpha = 1f - morphProgress
                    
                    // Scale down further during collapse
                    val scaleFactor = 1f - (collapseProgress * 0.5f)
                    scaleX *= scaleFactor
                    scaleY *= scaleFactor
                }
                .zIndex(1f)
        ) {
            content()
        }

        // Mini Player UI (Fades in during morph)
        if (p > 0.3f) {
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        alpha = morphProgress
                        scaleX = 0.8f + (morphProgress * 0.2f)
                        scaleY = 0.8f + (morphProgress * 0.2f)
                        
                        // Collapse Phase
                        val scaleFactor = 1f - (collapseProgress * 0.8f)
                        scaleX *= scaleFactor
                        scaleY *= scaleFactor
                        
                        // Slide up slightly
                        translationY = (1f - morphProgress) * 100f
                    }
                    .zIndex(2f),
                contentAlignment = Alignment.Center
            ) {
                MusicMiniPlayer(
                    isConverting = isConverting,
                    conversionProgress = conversionProgress,
                    currentTrackName = currentTrackName,
                    onConvertClick = {}
                )
            }
        }
        
        // Widget Layout (Alternative state or final collapse target)
        if (p > 0.8f) {
             Box(
                modifier = Modifier
                    .graphicsLayer {
                        alpha = (p - 0.8f) / 0.2f
                        scaleX = 0.5f + (alpha * 0.5f)
                        scaleY = 0.5f + (alpha * 0.5f)
                    }
                    .zIndex(3f)
            ) {
                MusicWidgetPlayer(
                    isConverting = isConverting,
                    conversionProgress = conversionProgress,
                    currentTrackName = currentTrackName,
                    pendingCount = 0,
                    onConvertClick = {}
                )
            }
        }
    }
}
