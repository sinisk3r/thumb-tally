@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.srik.shortstracker.ui

import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.srik.shortstracker.BuildConfig
import com.srik.shortstracker.data.OverlayColor
import com.srik.shortstracker.data.OverlayMode
import com.srik.shortstracker.data.OverlayStyle
import com.srik.shortstracker.tracker.ShortsAccessibilityService
import com.srik.shortstracker.data.OverlaySpot
import com.srik.shortstracker.data.TrackerConfig

@Composable
fun SettingsScreen(config: TrackerConfig, health: Health, viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val turnOnTracking = LocalTurnOnTracking.current
    var confirmDelete by remember { mutableStateOf(false) }
    val update = viewModel::updateConfig

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { ScreenTitle("Settings", "Thumb Tally ${BuildConfig.VERSION_NAME}", Modifier.enterRise(0)) }
        item {
            Card(Modifier.enterRise(1)) {
                SectionTitle("Battery & reliability")
                StatusRow(
                    title = "Tracker",
                    value = when (health.status) {
                        HealthStatus.OFF -> "Off"
                        HealthStatus.STOPPED -> "Stopped by Android"
                        else -> "Running"
                    },
                    good = health.enabled && health.running,
                    action = if (health.enabled && health.running) null else ("Fix" to { turnOnTracking() }),
                )
                StatusRow(
                    title = "Background use",
                    value = if (health.batteryUnrestricted) "Allowed" else "Restricted",
                    good = health.batteryUnrestricted,
                    action = if (health.batteryUnrestricted) null else ("Allow" to { context.requestUnrestrictedBattery() }),
                )
                StatusRow(
                    title = "Processing today",
                    value = formatCpu(health.cpuMsToday),
                    good = true,
                    action = null,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "The tracker only wakes up while YouTube, Instagram or Snapchat is open, and checks the screen about once a second. Everywhere else it's asleep.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                makerTip(Build.MANUFACTURER)?.let { tip ->
                    Spacer(Modifier.height(12.dp))
                    Eyebrow("On ${tip.maker} phones")
                    Spacer(Modifier.height(4.dp))
                    Text(tip.steps, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { context.openUrl(tip.url) }, contentPadding = PaddingValues(0.dp)) {
                        Text("Step-by-step guide")
                    }
                }
            }
        }
        item {
            Card(Modifier.enterRise(2)) {
                SectionTitle("Payments & reminders")
                ToggleRow(
                    title = "Pause for payment apps",
                    subtitle = "Many UPI and bank apps won't pay while tracking is on. Thumb Tally switches itself off when one opens and tells you.",
                    checked = config.pauseForPayments,
                    onChange = { on -> update { it.copy(pauseForPayments = on) } },
                )
                ToggleRow(
                    title = "Remind me when it's paused",
                    subtitle = "A notification to switch tracking back on.",
                    checked = config.remindWhenOff,
                    onChange = { on -> update { it.copy(remindWhenOff = on) } },
                )
                if (!health.canNotify) {
                    StatusRow(title = "Notifications", value = "Off", good = false, action = "Allow" to { context.openAppNotificationSettings() })
                }
            }
        }
        item {
            Card(Modifier.enterRise(3)) {
                SectionTitle("Pop-up counter")
                ToggleRow(
                    title = "Pop up on each new Short",
                    subtitle = "Bounces in when a new Short or Reel starts, then pops away by itself.",
                    checked = config.overlayEnabled,
                    onChange = { on -> update { it.copy(overlayEnabled = on) } },
                )
                if (config.overlayEnabled) {
                    ChoiceRow("Animation", OverlayStyle.entries, config.overlayStyle, { it.label }) { style ->
                        update { it.copy(overlayStyle = style) }
                    }
                    ChoiceRow("Colour", OverlayColor.entries, config.overlayColor, { it.label }) { color ->
                        update { it.copy(overlayColor = color) }
                    }
                    ChoiceRow("Count", OverlayMode.entries, config.overlayMode, { it.label }) { mode ->
                        update { it.copy(overlayMode = mode) }
                    }
                    // "From the camera" always sits under the camera hole.
                    if (config.overlayStyle != OverlayStyle.CAMERA) {
                        ChoiceRow("Where", OverlaySpot.entries, config.overlaySpot, { it.label }) { spot ->
                            update { it.copy(overlaySpot = spot) }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                ToggleRow(
                    title = "Nudges",
                    subtitle = "Cheeky messages at milestones, late at night, after a long break and around your limit.",
                    checked = config.nudgesEnabled,
                    onChange = { on -> update { it.copy(nudgesEnabled = on) } },
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val preview = { withNudge: Boolean ->
                        val tracker = ShortsAccessibilityService.instance
                        if (tracker == null) {
                            Toast.makeText(context, "Turn on tracking first.", Toast.LENGTH_SHORT).show()
                        } else {
                            tracker.preview(withNudge)
                        }
                    }
                    PrimaryButton(onClick = { preview(false) }) { Text("Try it") }
                    GhostButton(onClick = { preview(true) }) { Text("Try a nudge") }
                }
            }
        }
        item {
            Card(Modifier.enterRise(4)) {
                SectionTitle("What to track")
                ToggleRow(
                    title = "Regular YouTube videos",
                    subtitle = "Counted separately from Shorts, so they don't change your Shorts numbers.",
                    checked = config.trackLongVideos,
                    onChange = { on -> update { it.copy(trackLongVideos = on) } },
                )
                ChoiceRow("Daily limit", listOf(0, 30, 50, 100, 200), config.dailyLimit, { if (it == 0) "Off" else "$it" }) { limit ->
                    update { it.copy(dailyLimit = limit) }
                }
                Text(
                    "Shorts, Reels and Spotlight together; regular YouTube videos don't count. Nothing gets blocked: " +
                        "the pop-up nudges you at the limit and Today's bar turns amber.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Card(Modifier.enterRise(5)) {
                SectionTitle("Screenshots")
                ToggleRow(
                    title = "Save screenshots",
                    subtitle = "A small private image of each video, shown in the Feed. Android 11 or newer.",
                    checked = config.screenshotsEnabled,
                    onChange = { on -> update { it.copy(screenshotsEnabled = on) } },
                )
                if (config.screenshotsEnabled) {
                    ChoiceRow("Take one every", listOf(1, 3, 5, 10), config.screenshotEvery, { if (it == 1) "video" else "$it videos" }) { n ->
                        update { it.copy(screenshotEvery = n) }
                    }
                    ChoiceRow("Keep them for", listOf(7, 30, 90), config.retentionDays, { "$it days" }) { days ->
                        update { it.copy(retentionDays = days) }
                    }
                }
            }
        }
        item {
            Card(Modifier.enterRise(6)) {
                SectionTitle("Troubleshooting")
                ToggleRow(
                    title = "Save screen dumps",
                    subtitle = "If videos aren't being counted, turn this on, watch a few, then share the dump.",
                    checked = config.debugDumps,
                    onChange = { on -> update { it.copy(debugDumps = on) } },
                )
                if (config.debugDumps) {
                    TextButton(onClick = {
                        val dump = viewModel.readDumps()
                        if (dump == null) {
                            Toast.makeText(context, "No dump yet. Open Shorts or Reels first.", Toast.LENGTH_SHORT).show()
                        } else {
                            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, dump)
                            context.startActivity(Intent.createChooser(send, "Share screen dump"))
                        }
                    }, contentPadding = PaddingValues(0.dp)) { Text("Share screen dump") }
                }
            }
        }
        item {
            Card(Modifier.enterRise(7)) {
                SectionTitle("Your data")
                Text(
                    "Everything is stored only on this phone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { confirmDelete = true }, contentPadding = PaddingValues(0.dp)) {
                    Text("Delete all history and screenshots", color = extraColors.danger)
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete everything?") },
            text = { Text("This removes all tracked videos and screenshots. It can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAll()
                    confirmDelete = false
                }) { Text("Delete", color = extraColors.danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

private fun formatCpu(ms: Long): String = when {
    ms < 1_000 -> "under 1 second"
    ms < 60_000 -> "%.1f seconds".format(ms / 1000f)
    else -> "%.1f minutes".format(ms / 60_000f)
}

@Composable
private fun StatusRow(title: String, value: String, good: Boolean, action: Pair<String, () -> Unit>?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Dot(if (good) extraColors.good else extraColors.danger, size = 8.dp)
        Spacer(Modifier.width(10.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (action != null) {
            GhostButton(onClick = action.second) { Text(action.first) }
        } else {
            Text(value, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.colorScheme.inverseSurface,
                checkedThumbColor = MaterialTheme.colorScheme.inverseOnSurface,
                uncheckedBorderColor = Color.Transparent,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
        )
    }
}

@Composable
private fun <T> ChoiceRow(title: String, options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Column(Modifier.padding(top = 12.dp)) {
        Text(title, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                Choice(label(option), selected = option == selected) { onSelect(option) }
            }
        }
    }
}
