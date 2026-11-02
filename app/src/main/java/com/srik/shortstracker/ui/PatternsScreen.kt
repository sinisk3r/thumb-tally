package com.srik.shortstracker.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.srik.shortstracker.data.Habit
import com.srik.shortstracker.data.Patterns
import com.srik.shortstracker.data.Stats
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun PatternsScreen(patterns: Patterns, days: Int, onDays: (Int) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { ScreenTitle("Patterns", "When you scroll", Modifier.enterRise(0)) }
        item {
            Row(Modifier.enterRise(1), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(7, 30).forEach { option ->
                    Choice("$option days", selected = option == days) { onDays(option) }
                }
            }
        }
        if (patterns.shorts == 0) {
            item {
                Text(
                    "No Shorts or Reels in the last $days days yet. Patterns show up after a day or two of tracking.",
                    modifier = Modifier.padding(top = 24.dp).enterRise(2),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@LazyColumn
        }
        item { Headline(patterns, Modifier.enterRise(2)) }
        item { StatGrid(patterns, Modifier.enterRise(3)) }
        if (patterns.habits.isNotEmpty()) item { HabitsCard(patterns.habits, Modifier.enterRise(4)) }
        item { HeatmapCard(patterns, Modifier.enterRise(4)) }
        item { HourlyCard(patterns, Modifier.enterRise(5)) }
        if (patterns.longVideoMs > 0) {
            item {
                Card(Modifier.enterRise(6)) {
                    SectionTitle("YouTube videos")
                    Text(Stats.formatDuration(patterns.longVideoMs), style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "of regular videos in the last ${patterns.days} days, on top of Shorts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun Headline(patterns: Patterns, modifier: Modifier = Modifier) {
    val parts = buildList {
        patterns.peakHour?.let { add("You scroll most around ${Stats.formatHour(it)}.") }
        patterns.busiestDay?.let { add("${it.getDisplayName(TextStyle.FULL, Locale.getDefault())}s are your heaviest day.") }
    }
    Text(parts.joinToString(" "), modifier = modifier.padding(vertical = 4.dp), style = MaterialTheme.typography.titleLarge)
}

@Composable
private fun StatGrid(patterns: Patterns, modifier: Modifier = Modifier) {
    val longest = patterns.longestSession
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                "Sittings", "${patterns.sessions}",
                "about ${"%.1f".format(patterns.sessions.toFloat() / patterns.days)} a day",
                Modifier.weight(1f),
            )
            StatTile("Typical sitting", Stats.formatDuration(patterns.avgSessionMs), "from first to last video", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                "Longest sitting",
                longest?.let { Stats.formatDuration(it.durationMs) } ?: "–",
                longest?.let {
                    Instant.ofEpochMilli(it.startedAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEE h:mm a")) +
                        " · ${it.views} videos"
                } ?: "",
                Modifier.weight(1f),
            )
            StatTile(
                "Late night",
                "${(patterns.lateNightShare * 100).roundToInt()}%",
                "of shorts after 11 PM",
                Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                "Quick skips",
                "${(patterns.quickSkipShare * 100).roundToInt()}%",
                "left within 2 seconds",
                Modifier.weight(1f),
            )
            StatTile(
                "Per Short",
                if (patterns.avgShortMs == 0L) "–" else "${(patterns.avgShortMs / 1000f).roundToInt()} s",
                "on average",
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun HabitsCard(habits: List<Habit>, modifier: Modifier = Modifier) {
    Card(modifier) {
        SectionTitle("Your habits")
        habits.forEachIndexed { index, habit ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp).enterRise(index),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(habit.emoji, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.width(48.dp))
                Column(Modifier.weight(1f)) {
                    Text(habit.title, style = MaterialTheme.typography.titleMedium)
                    Text(habit.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, note: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Eyebrow(label)
        Spacer(Modifier.height(8.dp))
        Text(value, style = MaterialTheme.typography.headlineMedium, maxLines = 1)
        Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
    }
}

@Composable
private fun HeatmapCard(patterns: Patterns, modifier: Modifier = Modifier) {
    val max = patterns.heat.maxOf { row -> row.max() }.coerceAtLeast(1)
    val accent = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceContainerHigh
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) { reveal.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessVeryLow)) }
    Card(modifier) {
        SectionTitle("Hour by weekday")
        patterns.heat.forEachIndexed { dayIndex, hours ->
            Row(Modifier.fillMaxWidth().padding(vertical = 1.5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    DayOfWeek.of(dayIndex + 1).getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    modifier = Modifier.width(18.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                hours.forEachIndexed { hour, count ->
                    val level = count.toFloat() / max
                    // Cells fill in left to right, like the day going by.
                    val shown = ((reveal.value * 30f) - hour).coerceIn(0f, 1f)
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(1.dp)
                            .graphicsLayer {
                                scaleX = shown
                                scaleY = shown
                            }
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (count == 0) empty else accent.copy(alpha = 0.18f + 0.82f * level)),
                    )
                }
            }
        }
        HourAxis(Modifier.padding(start = 18.dp, top = 4.dp))
    }
}

@Composable
private fun HourlyCard(patterns: Patterns, modifier: Modifier = Modifier) {
    val max = patterns.hourly.max().coerceAtLeast(1)
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow)) }
    Card(modifier) {
        SectionTitle("By hour")
        Row(Modifier.fillMaxWidth().height(110.dp), verticalAlignment = Alignment.Bottom) {
            patterns.hourly.forEachIndexed { hour, count ->
                val fraction by animateFloatAsState(count.toFloat() / max, bouncy(), label = "hour")
                val color by animateColorAsState(
                    if (hour == patterns.peakHour) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    label = "hourColor",
                )
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.66f)
                            .fillMaxHeight(fraction.coerceIn(0.02f, 1f))
                            .graphicsLayer {
                                scaleY = grow.value
                                transformOrigin = TransformOrigin(0.5f, 1f)
                            }
                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                            .background(color),
                    )
                }
            }
        }
        HourAxis(Modifier.padding(top = 6.dp))
        patterns.peakHour?.let {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(10.dp).height(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Peak: ${Stats.formatHour(it)}–${Stats.formatHour((it + 1) % 24)}, ${patterns.hourly[it]} shorts",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 12 AM, 6 AM, 12 PM, 6 PM under a 24-column chart. */
@Composable
private fun HourAxis(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth()) {
        listOf("12A", "6A", "12P", "6P").forEach { label ->
            Text(
                label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start,
            )
        }
    }
}

/** A selectable pill that springs when tapped. */
@Composable
fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    val container by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.inverseSurface else MaterialTheme.colorScheme.surfaceContainer,
        label = "choice",
    )
    val content by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.inverseOnSurface else MaterialTheme.colorScheme.onSurface,
        label = "choiceText",
    )
    Text(
        label,
        modifier = Modifier
            .bouncyClick(onClick)
            .clip(CircleShape)
            .background(container)
            .border(1.dp, if (selected) Color.Transparent else extraColors.line, CircleShape)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        style = MaterialTheme.typography.titleSmall,
        color = content,
    )
}
