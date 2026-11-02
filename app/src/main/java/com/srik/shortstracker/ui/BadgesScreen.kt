package com.srik.shortstracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.srik.shortstracker.data.BadgeState
import com.srik.shortstracker.data.BadgeTone

/** A small emoji in a tinted circle: the icon on stat tiles and badges. */
@Composable
fun IconBubble(emoji: String, size: Int = 28, dim: Boolean = false) {
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .alpha(if (dim) 0.35f else 1f),
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, fontSize = (size * 0.52f).sp)
    }
}

/**
 * Every badge: the ones you have, the good ones worth chasing, and the cheeky ones you may want to avoid.
 * Badges follow the Patterns range, so they can be lost again when a habit changes.
 */
@Composable
fun BadgesScreen(badges: List<BadgeState>, days: Int, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val mine = badges.filter { it.earned }
    val goodLocked = badges.filter { !it.earned && it.badge.tone == BadgeTone.GOOD }
    val cheekyLocked = badges.filter { !it.earned && it.badge.tone == BadgeTone.CHEEKY }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) { Text("‹ Patterns") }
            ScreenTitle("Badges", "${mine.size} of ${badges.size} · last $days days", Modifier.enterRise(0))
            Text(
                if (mine.isEmpty()) "Nothing yet. Every legend starts somewhere."
                else "Some you earned. Some earned you.",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 6.dp).enterRise(1),
            )
        }
        if (mine.isNotEmpty()) item { BadgeGroup("Yours right now", mine, Modifier.enterRise(2)) }
        if (goodLocked.isNotEmpty()) item { BadgeGroup("Good ones to chase", goodLocked, Modifier.enterRise(3)) }
        if (cheekyLocked.isNotEmpty()) item { BadgeGroup("Cheeky ones (avoid… or don't)", cheekyLocked, Modifier.enterRise(4)) }
        item {
            Text(
                "Badges look at the last $days days, so they come and go as your habits change.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.enterRise(5),
            )
        }
    }
}

@Composable
private fun BadgeGroup(title: String, items: List<BadgeState>, modifier: Modifier = Modifier) {
    Card(modifier) {
        SectionTitle(title)
        items.forEachIndexed { index, state ->
            if (index > 0) Spacer(Modifier.size(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconBubble(state.badge.emoji, size = 44, dim = !state.earned)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            state.badge.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (state.earned) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                        if (state.earned) {
                            Pill(
                                if (state.badge.tone == BadgeTone.GOOD) "Nice" else "Cheeky",
                                container = (if (state.badge.tone == BadgeTone.GOOD) extraColors.good else extraColors.caution).copy(alpha = 0.16f),
                                content = if (state.badge.tone == BadgeTone.GOOD) extraColors.good else extraColors.caution,
                            )
                        } else {
                            Text("🔒", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    Text(state.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
