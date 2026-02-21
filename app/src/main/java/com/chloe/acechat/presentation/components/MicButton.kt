package com.chloe.acechat.presentation.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

enum class MicButtonState { IDLE, RECORDING, DISABLED }

private val RecordingRed = Color(0xFFE53935)

@Composable
fun MicButton(
    state: MicButtonState,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDisabled = state == MicButtonState.DISABLED
    val isRecording = state == MicButtonState.RECORDING

    val buttonColor = when (state) {
        MicButtonState.IDLE -> MaterialTheme.colorScheme.primary
        MicButtonState.RECORDING -> RecordingRed
        MicButtonState.DISABLED -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    }
    val iconColor = when (state) {
        MicButtonState.IDLE -> MaterialTheme.colorScheme.onPrimary
        MicButtonState.RECORDING -> Color.White
        MicButtonState.DISABLED -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier,
    ) {
        // Pulsing ring — only composed when recording to avoid unnecessary animation overhead
        if (isRecording) {
            PulsingRing()
        }

        // Main button circle
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(buttonColor)
                .pointerInput(isDisabled) {
                    if (!isDisabled) {
                        awaitEachGesture {
                            awaitFirstDown()
                            onPress()
                            waitForUpOrCancellation()
                            onRelease()
                        }
                    }
                },
        ) {
            Icon(
                imageVector = Icons.Filled.Mic,
                contentDescription = when (state) {
                    MicButtonState.IDLE -> "Start speaking"
                    MicButtonState.RECORDING -> "Recording"
                    MicButtonState.DISABLED -> "Mic disabled"
                },
                tint = iconColor,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
private fun PulsingRing() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )

    Box(
        modifier = Modifier
            .size(72.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(RecordingRed.copy(alpha = 0.3f)),
    )
}
