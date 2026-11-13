package com.dsikic.cluecose.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The not-connected state (iOS `listeningCard` + the NFC pairing prompt).
 * Pulsing radar rings around a central icon, the "Listening for your Libre
 * sensor" copy, the NFC hold-to-pair prompt, and the Start scanning action.
 */
@Composable
fun ListeningScreen(
    state: UiState,
    onStart: () -> Unit,
    onNfc: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulse by transition.animateFloat(
        initialValue = 0.72f,
        targetValue = 1.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )
    val ringAlpha by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 0.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
        ),
        label = "ringFade",
    )

    GlassPanel(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 34.dp,
        contentPadding = androidx.compose.ui.unit.Dp(26f),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Radar rings + central icon.
            Box(
                modifier = Modifier.size(120.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .scale(pulse)
                        .drawBehind {
                            drawCircle(
                                color = CluecoseColors.GlowInRange.copy(alpha = ringAlpha),
                                radius = size.minDimension / 2,
                                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                            )
                        },
                )
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .drawBehind {
                            drawCircle(
                                color = CluecoseColors.white(0.15f),
                                radius = size.minDimension / 2,
                                style = Stroke(width = 1.5.dp.toPx()),
                            )
                        }
                        .background(CluecoseColors.white(0.05f), CircleShape),
                ) {
                    Icon(
                        imageVector = Icons.Filled.WifiTethering,
                        contentDescription = null,
                        tint = CluecoseColors.white(0.9f),
                        modifier = Modifier.size(34.dp),
                    )
                }
            }

            Text(
                text = "Listening for your Libre sensor",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = CluecoseColors.TextPrimary,
                fontSize = 17.sp,
            )
            Text(
                text = "First readings should appear within a minute or two.",
                style = MaterialTheme.typography.bodySmall,
                color = CluecoseColors.white(0.6f),
                textAlign = TextAlign.Center,
            )

            if (state.status.isNotEmpty()) {
                Text(
                    text = state.status,
                    style = MaterialTheme.typography.labelSmall,
                    color = CluecoseColors.white(0.4f),
                    textAlign = TextAlign.Center,
                )
            }

            // NFC hold-to-pair prompt (iOS pairCard copy).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        CluecoseColors.GlowInRange.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(16.dp),
                    )
                    .border(
                        width = 1.dp,
                        color = CluecoseColors.GlowInRange.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(16.dp),
                    )
                    .padding(14.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    imageVector = Icons.Filled.Nfc,
                    contentDescription = null,
                    tint = CluecoseColors.GlowInRange,
                    modifier = Modifier
                        .size(28.dp)
                        .align(Alignment.Top),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    // Android's reader mode is armed in-process (no system
                    // sheet), so while awaiting a tag the card says so.
                    Text(
                        text = if (state.nfcAwaiting) {
                            "Hold the top of the phone to the sensor now…"
                        } else {
                            "Hold the top of the phone to the sensor"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = CluecoseColors.TextPrimary,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (state.nfcAwaiting) {
                            "The reader is armed — the exchange starts the moment the sensor is in the field."
                        } else {
                            "Hold the sensor against the back of the phone's NFC area to pair — this links Cluecose directly to it over Bluetooth so readings update the moment the sensor broadcasts — no account, no cloud."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = CluecoseColors.white(0.55f),
                    )
                }
            }

            // Primary action. Unpaired, the only first-pair path is the NFC
            // tap — a bare BLE scan can't pair without the 4-byte PIN the tag
            // provides (iOS: the unpaired CTA is "Pair sensor" → model.pair()).
            // So the primary green button IS the NFC pairing; "Start scanning"
            // (stored-MAC reconnect) only exists once a sensor is paired.
            val paired = state.sensorName != null
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        CluecoseColors.Action,
                        shape = RoundedCornerShape(16.dp),
                    )
                    .padding(vertical = 12.dp)
                    .clickable(onClickLabel = null) {
                        if (paired) onStart() else onNfc()
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (paired) {
                    Text(
                        text = "Start scanning",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = Color(0xFF04150C),
                    )
                } else {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Nfc,
                            contentDescription = null,
                            tint = Color(0xFF04150C),
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Pair with NFC tap",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = Color(0xFF04150C),
                        )
                    }
                }
            }

            // Paired but not connected: keep the NFC path as the secondary
            // "re-pair" (iOS Settings → "Re-pair with NFC tap").
            if (paired) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            CluecoseColors.white(0.12f),
                            shape = RoundedCornerShape(100.dp),
                        )
                        .padding(vertical = 9.dp)
                        .clickable(onClickLabel = null) { onNfc() },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Nfc,
                            contentDescription = null,
                            tint = CluecoseColors.TextPrimary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Re-pair with NFC tap",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = CluecoseColors.TextPrimary,
                        )
                    }
                }
            }
        }
    }
}
