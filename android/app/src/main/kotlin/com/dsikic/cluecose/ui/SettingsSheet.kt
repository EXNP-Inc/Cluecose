package com.dsikic.cluecose.ui

import android.os.Build
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Settings dialog mirroring iOS `SettingsSheet` (ContentView.swift:578):
 * Sensor (paired / age / re-pair), Data (link status, Health app), and the
 * destructive "Forget sensor" action. The Health app is Health Connect
 * (API 28+); the Live Activity section is iOS-only (Dynamic Island /
 * Lock Screen / Watch) and has no Android equivalent, so it is omitted.
 */
@Composable
fun SettingsSheet(
    state: UiState,
    ownerReceiverId: String,
    onOwnerReceiverIdChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onRePair: () -> Unit,
    onForgetSensor: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .background(Color(0xF20B2530), shape = RoundedCornerShape(24.dp))
                .border(1.dp, CluecoseColors.white(0.12f), shape = RoundedCornerShape(24.dp))
                .padding(20.dp),
        ) {
            // Title + Done (iOS inline nav title + confirmationAction).
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Settings",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "Done",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = CluecoseColors.GlowInRange,
                    modifier = Modifier
                        .background(CluecoseColors.GlowInRange.copy(alpha = 0.14f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                        .clickable(onClick = onDismiss),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .height(330.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // MARK: Sensor
                SectionLabel("Sensor")
                SettingRow(
                    label = "Paired sensor",
                    value = state.sensorName ?: "Not paired",
                    modifier = Modifier.fillMaxWidth(),
                )
                SettingRow(
                    label = "Sensor age",
                    value = state.sensorAgeText ?: "—",
                    modifier = Modifier.fillMaxWidth(),
                )
                ActionRow(
                    text = "Re-pair with NFC tap",
                    onClick = onRePair,
                )
                ReceiverIdField(
                    value = ownerReceiverId,
                    onValueChange = onOwnerReceiverIdChange,
                )

                // MARK: Data
                SectionLabel("Data")
                SettingRow(
                    label = "Direct Bluetooth link",
                    value = when (state.connectionState) {
                        ConnectionState.CONNECTED -> "Connected"
                        ConnectionState.RECONNECTING -> "Reconnecting"
                        else -> "Searching"
                    },
                    valueColor = if (state.connectionState == ConnectionState.CONNECTED) {
                        CluecoseColors.GlowInRange
                    } else {
                        CluecoseColors.white(0.7f)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                SettingRow(
                    label = "Health app",
                    value = if (Build.VERSION.SDK_INT >= 28) "Not set up" else "Unavailable",
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    text = "Not a medical device.",
                    style = MaterialTheme.typography.labelSmall,
                    color = CluecoseColors.white(0.35f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // iOS safeAreaInset(edge: .bottom) destructive button.
            Text(
                text = "Forget sensor",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = CluecoseColors.GlowHigh,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CluecoseColors.GlowHigh.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                    .clickable(onClick = onForgetSensor)
                    .padding(vertical = 12.dp),
            )
        }
    }
}

/** iOS `Section("…")` header, small-caps style. */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = CluecoseColors.white(0.45f),
    )
}

/** iOS `LabeledContent` row: label left, value right. */
@Composable
private fun SettingRow(
    label: String,
    value: String,
    valueColor: Color = CluecoseColors.white(0.8f),
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = CluecoseColors.white(0.75f),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            fontSize = 14.sp,
            color = valueColor,
        )
    }
}

/**
 * Free-form receiver ID field (little-endian hex, 8 digits) for the "sensor
 * bound to another install" case — the Android mirror of iOS's LibreView
 * Account ID field (GlucoseModel.needsReceiverID). The value is presented
 * FIRST by the NFC candidate ladder before this install's own accountless ID.
 */
@Composable
private fun ReceiverIdField(value: String, onValueChange: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Other install's receiver ID",
                fontSize = 14.sp,
                color = CluecoseColors.white(0.75f),
                modifier = Modifier.weight(1f),
            )
            if (value.isNotEmpty()) {
                Text(
                    text = "clear",
                    fontSize = 13.sp,
                    color = CluecoseColors.GlowInRange,
                    modifier = Modifier
                        .padding(start = 10.dp)
                        .clickable { onValueChange("") },
                )
            }
        }
        OutlinedTextField(
            value = value,
            onValueChange = { if (it.length <= 16) onValueChange(it) },
            singleLine = true,
            label = { Text("e.g. d82eec8f", fontSize = 12.sp) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
        )
        Text(
            text = "Only needed when the sensor was paired on another phone/install. 8 hex digits, little-endian.",
            style = MaterialTheme.typography.labelSmall,
            color = CluecoseColors.white(0.35f),
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** iOS `Button("…")` list row: label + chevron. */
@Composable
private fun ActionRow(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = CluecoseColors.GlowInRange,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.Filled.ArrowForward,
            contentDescription = null,
            tint = CluecoseColors.white(0.4f),
            modifier = Modifier.size(14.dp),
        )
    }
}
