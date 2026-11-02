package com.srik.shortstracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Every "turn on tracking" button calls this. It shows [DisclosureScreen] first unless the person already agreed,
 * then opens Accessibility settings. Provided by MainScreen.
 */
val LocalTurnOnTracking = staticCompositionLocalOf<() -> Unit> { {} }

private data class Point(val title: String, val body: String, val tone: Color? = null)

/**
 * The prominent disclosure Google Play requires before sending someone to enable an accessibility service
 * (docs/play-store.md, blocker 1), and plain honesty for every other install. Keep it in step with
 * `service_description` in strings.xml and the privacy policy whenever what's read changes.
 */
@Composable
fun DisclosureScreen(onAgree: () -> Unit, onDecline: () -> Unit) {
    val points = listOf(
        Point(
            "What it reads",
            "While YouTube, Instagram or Snapchat is open, Thumb Tally reads what's on screen to tell which video " +
                "is playing: the creator's name, the title or caption, and whether it's a Short, Reel, Spotlight or " +
                "regular video. That's how the counter goes up with each swipe.",
        ),
        Point(
            "What it doesn't read",
            "Nothing in any other app. It never reads your messages, passwords or what you type, and it doesn't " +
                "tap or scroll anything for you.",
        ),
        Point(
            "Payment apps",
            "When a UPI or bank app opens (Google Pay, PhonePe, Paytm and others), Thumb Tally switches itself " +
                "off so the payment works, and sends a notification so you can switch it back on.",
        ),
        Point(
            "Where it goes",
            "Nowhere. Everything stays on this phone. No account, no ads, no analytics, and the app has no " +
                "internet access at all.",
            tone = extraColors.good,
        ),
        Point(
            "Screenshots",
            "Off unless you turn them on in Settings. If you do, small images stay on this phone and are deleted " +
                "after the number of days you choose.",
        ),
        Point(
            "Stopping",
            "Switch Thumb Tally off in Accessibility at any time. Delete your history in Settings, or uninstall to " +
                "remove everything.",
        ),
    )

    // Shown outside the Scaffold, so it needs its own Surface for light-on-dark text in dark mode.
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenTitle("Before you turn it on", "Accessibility access", Modifier.enterRise(0))
        Text(
            "Thumb Tally needs Android's accessibility access to count videos. Here's exactly what that means.",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.enterRise(1),
        )
        Card(Modifier.enterRise(2)) {
            points.forEachIndexed { i, point ->
                if (i > 0) Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.padding(top = 6.dp)) { Dot(point.tone ?: MaterialTheme.colorScheme.onSurfaceVariant, size = 8.dp) }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(point.title, style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(2.dp))
                        Text(point.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Text(
            "Next, Android opens Accessibility settings: tap Thumb Tally and switch it on. If Android says the " +
                "setting is restricted, open App info, tap ⋮, then \"Allow restricted settings\".",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.enterRise(3),
        )
        Column(Modifier.fillMaxWidth().enterRise(4), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton(onClick = onAgree, modifier = Modifier.fillMaxWidth()) { Text("Agree and turn on") }
            GhostButton(onClick = onDecline, modifier = Modifier.fillMaxWidth()) { Text("No thanks") }
        }
        Spacer(Modifier.height(8.dp))
    }
    }
}
