package com.chloe.acechat.presentation.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * - [IDLE]: 대기 중 — 탭 가능
 * - [LISTENING]: 음성 인식 중 — 빨간색 + 펄스 애니메이션, 탭 불가 (자동 종료 대기)
 * - [DISABLED]: LLM Streaming/Loading 중 — 회색, 탭 불가
 */
enum class MicButtonState { IDLE, LISTENING, DISABLED }

private val RecordingRed = Color(0xFFE53935)

/**
 * 마이크 버튼 컴포넌트.
 *
 * @param state 버튼 동작 상태 ([MicButtonState])
 * @param hasPermission RECORD_AUDIO 권한 보유 여부. false면 잠금 아이콘 + 비활성화
 * @param onTap 버튼 탭 콜백 — [MicButtonState.IDLE]이고 [hasPermission]이 true일 때만 호출됨
 */
@Composable
fun MicButton(
    state: MicButtonState,
    hasPermission: Boolean = true,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isListening = state == MicButtonState.LISTENING
    val isEnabled = hasPermission && state == MicButtonState.IDLE

    val buttonColor = when {
        !hasPermission -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
        isListening -> RecordingRed
        state == MicButtonState.DISABLED -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
        else -> MaterialTheme.colorScheme.primary
    }
    val iconColor = when {
        !hasPermission -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        isListening -> Color.White
        state == MicButtonState.DISABLED -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        else -> MaterialTheme.colorScheme.onPrimary
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier,
    ) {
        // 펄스 링 — LISTENING 상태일 때만 합성
        if (isListening) {
            PulsingRing()
        }

        // 메인 버튼 원
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(buttonColor)
                .clickable(
                    enabled = isEnabled,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onTap,
                ),
        ) {
            Icon(
                imageVector = if (!hasPermission) Icons.Filled.Lock else Icons.Filled.Mic,
                contentDescription = when {
                    !hasPermission -> "Microphone permission required"
                    isListening -> "Listening"
                    state == MicButtonState.DISABLED -> "Mic disabled"
                    else -> "Start speaking"
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
