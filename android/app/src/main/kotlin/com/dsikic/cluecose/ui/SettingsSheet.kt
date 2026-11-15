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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dsikic.cluecose.food.FoodLLMConfig
import com.dsikic.cluecose.food.OkHttpFoodClient
import kotlinx.coroutines.launch

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
    /** Manual history wipes (iOS Settings › Data). Nothing is wiped
     * automatically. [onWipeGlucoseHistory] / [onClearFoodLog] return the
     * number of rows/meals removed so the result can be shown inline. */
    onWipeGlucoseHistory: () -> Int,
    onClearFoodLog: () -> Int,
) {
    // Confirmation state for the two manual wipes (iOS confirmationDialogs).
    var showGlucoseWipeConfirm by remember { mutableStateOf(false) }
    var showFoodWipeConfirm by remember { mutableStateOf(false) }
    var wipeGlucoseResult by remember { mutableStateOf<String?>(null) }
    var wipeFoodResult by remember { mutableStateOf<String?>(null) }

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
                // Manual history wipes — nothing is wiped automatically (the
                // iOS auto-wipe race was removed in favor of explicit taps).
                // A paired sensor re-streams fresh data over a wiped window.
                DestructiveRow(
                    text = "Wipe glucose history",
                    onClick = {
                        wipeGlucoseResult = null
                        showGlucoseWipeConfirm = true
                    },
                )
                wipeGlucoseResult?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = CluecoseColors.white(0.5f),
                    )
                }
                DestructiveRow(
                    text = "Clear food log",
                    onClick = {
                        wipeFoodResult = null
                        showFoodWipeConfirm = true
                    },
                )
                wipeFoodResult?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = CluecoseColors.white(0.5f),
                    )
                }

                // MARK: Nutrition analysis (LLM)
                NutritionSection()

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

    // Confirmation + run for the two manual wipes (iOS confirmationDialogs).
    if (showGlucoseWipeConfirm) {
        AlertDialog(
            onDismissRequest = { showGlucoseWipeConfirm = false },
            title = { Text("Wipe all glucose history?") },
            text = {
                Text(
                    "Removes every glucose reading this app has stored (any age) and " +
                        "clears the on-device chart. Your paired sensor keeps working " +
                        "and re-streams fresh readings.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showGlucoseWipeConfirm = false
                    val n = onWipeGlucoseHistory()
                    wipeGlucoseResult =
                        "Removed $n reading(s). History starts fresh from here."
                }) {
                    Text("Wipe all glucose history")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGlucoseWipeConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
    }
    if (showFoodWipeConfirm) {
        AlertDialog(
            onDismissRequest = { showFoodWipeConfirm = false },
            title = { Text("Clear the entire food log?") },
            text = {
                Text("Deletes every meal entry, including photos. This can't be undone.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showFoodWipeConfirm = false
                    val n = onClearFoodLog()
                    wipeFoodResult = "Cleared $n meal(s)."
                }) {
                    Text("Clear food log")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFoodWipeConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

/** A destructive action row (red label + trash icon), for the manual wipes. */
@Composable
private fun DestructiveRow(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Delete,
            contentDescription = null,
            tint = CluecoseColors.GlowHigh,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = CluecoseColors.GlowHigh,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
        )
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

/**
 * The "Nutrition analysis" (LLM endpoint) settings section — the Android
 * mirror of iOS `SettingsSheet.nutritionSection` (ContentView.swift:682).
 *
 * Fields: Base URL, Model, API key (obscured). Buttons: Save (persists the
 * config) and Test connection (a trivial `max_tokens:1` completion, result
 * shown green when it starts with "OK", orange otherwise). The client reads
 * its config fresh on every call, so a Save-then-Test round-trip applies
 * immediately (the iOS `FoodLLMClient.shared` per-call pattern).
 */
@Composable
private fun NutritionSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var baseURL by remember { mutableStateOf(FoodLLMConfig.load(context).baseURL) }
    var model by remember { mutableStateOf(FoodLLMConfig.load(context).model) }
    var apiKey by remember { mutableStateOf(FoodLLMConfig.load(context).apiKey) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }

    val client = remember { OkHttpFoodClient(context) }
    val keyEnabled = apiKey.trim().isNotEmpty()

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("Nutrition analysis")
        OutlinedTextField(
            value = baseURL,
            onValueChange = { baseURL = it },
            singleLine = true,
            label = { Text("Base URL", fontSize = 12.sp) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = model,
            onValueChange = { model = it },
            singleLine = true,
            label = { Text("Model", fontSize = 12.sp) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            singleLine = true,
            label = { Text("API key", fontSize = 12.sp) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Save (persists the config; clears the test result).
            Text(
                text = "Save",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = CluecoseColors.GlowInRange,
                modifier = Modifier
                    .background(CluecoseColors.GlowInRange.copy(alpha = 0.14f), RoundedCornerShape(8.dp))
                    .clickable {
                        FoodLLMConfig.save(
                            context,
                            FoodLLMConfig(
                                baseURL = baseURL.trim(),
                                model = model.trim(),
                                apiKey = apiKey,
                            ),
                        )
                        testResult = null
                    }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            )
            // Test connection (trivial completion against the just-saved config).
            Text(
                text = if (isTesting) "Testing…" else "Test connection",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isTesting) CluecoseColors.white(0.5f) else CluecoseColors.white(0.8f),
                modifier = Modifier
                    .background(
                        if (isTesting) CluecoseColors.white(0.08f) else CluecoseColors.white(0.12f),
                        RoundedCornerShape(8.dp),
                    )
                    .clickable(enabled = !isTesting && keyEnabled) {
                        // Save first so the test targets the current fields (iOS parity).
                        FoodLLMConfig.save(
                            context,
                            FoodLLMConfig(baseURL = baseURL.trim(), model = model.trim(), apiKey = apiKey),
                        )
                        isTesting = true
                        scope.launch {
                            testResult = try {
                                client.testConnection()
                            } catch (e: Exception) {
                                "Error: ${e.message}"
                            }
                            isTesting = false
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }

        testResult?.let { result ->
            Text(
                text = result,
                fontSize = 12.sp,
                color = if (result.startsWith("OK")) CluecoseColors.GlowInRange else CluecoseColors.GlowHigh,
            )
        }

        Text(
            text = "Your photos and details are sent only to the configured model endpoint for estimation; glucose stays on-device.",
            fontSize = 12.sp,
            color = CluecoseColors.white(0.4f),
        )
    }
}
