package ai.musicconverter.ui.components

import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Wraps content with an Iron Man faceplate-close predictive back animation.
 *
 * Swiping back vertically compresses the app — the top bar crushes down and
 * the bottom bar crushes up until they meet in the middle, like a helmet
 * faceplate snapping shut. The home screen is revealed above and below
 * through the translucent window. Releasing springs back open; completing
 * the gesture exits the app.
 */
@Composable
fun CrtCloseWrapper(
    content: @Composable () -> Unit
) {
    val activity = LocalContext.current as? ComponentActivity
    val animatedProgress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    if (activity != null) {
        DisposableEffect(activity) {
            val callback = object : OnBackPressedCallback(true) {
                override fun handleOnBackStarted(backEvent: BackEventCompat) {}

                override fun handleOnBackProgressed(backEvent: BackEventCompat) {
                    scope.launch {
                        animatedProgress.snapTo(backEvent.progress)
                    }
                }

                override fun handleOnBackPressed() {
                    scope.launch {
                        animatedProgress.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(250)
                        )
                        activity.finish()
                    }
                }

                override fun handleOnBackCancelled() {
                    scope.launch {
                        animatedProgress.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f)
                        )
                    }
                }
            }
            activity.onBackPressedDispatcher.addCallback(callback)
            onDispose { callback.remove() }
        }
    }

    val p = animatedProgress.value

    // Vertical crush: 1.0 → 0.02 (top bar and bottom bar slam together)
    val verticalScale = (1f - p * 0.98f).coerceAtLeast(0.02f)
    // Slight horizontal compression for depth feel
    val horizontalScale = 1f - (p * 0.08f)
    // Corner radius increases as the slit narrows
    val cornerRadius = (p * 20f).dp
    // Background fades to fully transparent
    val bgAlpha = (1f - p * 1.5f).coerceIn(0f, 1f)
    // Content fades in the final stretch
    val contentAlpha = (1f - (p - 0.6f).coerceAtLeast(0f) * 2.5f).coerceIn(0f, 1f)

    val background = MaterialTheme.colorScheme.background

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = background.copy(alpha = bgAlpha)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleY = verticalScale
                    scaleX = horizontalScale
                    alpha = contentAlpha
                    // Center origin: top half crushes down, bottom half crushes up
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                    shape = RoundedCornerShape(cornerRadius)
                    clip = true
                }
        ) {
            content()
        }
    }
}
