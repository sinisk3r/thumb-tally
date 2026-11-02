package com.srik.shortstracker.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** The app's one spring: quick, with a small overshoot so things land with a bit of bounce. */
fun <T> bouncy() = spring<T>(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)

/** Squashes a little while pressed and springs back on release. */
fun Modifier.pressBounce(interactionSource: MutableInteractionSource): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.94f else 1f,
        spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** Rises into place the first time it's shown; [index] staggers a list of cards. */
fun Modifier.enterRise(index: Int = 0): Modifier = composed {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * 55L)
        progress.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow))
    }
    graphicsLayer {
        alpha = progress.value.coerceIn(0f, 1f)
        translationY = (1f - progress.value) * 36.dp.toPx()
    }
}

/** A number that rolls up (or down) to its new value. */
@Composable
fun RollingNumber(value: Int, style: TextStyle, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    AnimatedContent(
        targetState = value,
        modifier = modifier,
        transitionSpec = {
            val up = targetState > initialState
            (slideInVertically(bouncy()) { if (up) it else -it } + fadeIn(tween(120))) togetherWith
                (slideOutVertically(tween(160)) { if (up) -it else it } + fadeOut(tween(120))) using
                SizeTransform(clip = false)
        },
        label = "number",
    ) { Text("$it", style = style, color = color) }
}

/** A status dot with a ring that keeps pulsing outward: "this is live". */
@Composable
fun PulsingDot(color: Color) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val ring by transition.animateFloat(1f, 2.6f, infiniteRepeatable(tween(1600), RepeatMode.Restart), label = "ring")
    val fade by transition.animateFloat(0.55f, 0f, infiniteRepeatable(tween(1600), RepeatMode.Restart), label = "fade")
    Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(8.dp)
                .graphicsLayer {
                    scaleX = ring
                    scaleY = ring
                    alpha = fade
                }
                .background(color, CircleShape),
        )
        Box(Modifier.size(8.dp).background(color, CircleShape))
    }
}
