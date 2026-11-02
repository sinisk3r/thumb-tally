@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.srik.shortstracker.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.srik.shortstracker.data.CreatorCount
import com.srik.shortstracker.data.DayBar
import com.srik.shortstracker.data.Stats
import com.srik.shortstracker.data.Summary
import com.srik.shortstracker.data.TodayLines
import com.srik.shortstracker.data.TrackerConfig
import com.srik.shortstracker.tracker.ShortsApp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun TodayScreen(summary: Summary, config: TrackerConfig, health: Health, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { TodayTitle(summary, config.dailyLimit, Modifier.enterRise(0)) }
        item { Hero(summary, config.dailyLimit, Modifier.enterRise(0)) }
        item { HealthBanner(health, Modifier.enterRise(1)) }
        item { WeekCard(summary.week, Modifier.enterRise(2)) }
        if (config.trackLongVideos) item { LongVideosCard(summary, Modifier.enterRise(3)) }
        if (summary.topCreators.isNotEmpty()) item { CreatorsCard(summary.topCreators, Modifier.enterRise(4)) }
    }
}

/** "TODAY" plus a line that gives the number its meaning; people remember the line, not the count. */
@Composable
private fun TodayTitle(summary: Summary, dailyLimit: Int, modifier: Modifier = Modifier) {
    val today = LocalDate.now()
    val line = TodayLines.pick(
        TodayLines.Context(
            today = summary.views,
            yesterday = summary.week.getOrNull(summary.week.size - 2)?.total ?: 0,
            hour = LocalTime.now().hour,
            limit = dailyLimit,
            seed = today.toEpochDay().toInt(),
        ),
    )
    Column(modifier) {
        ScreenTitle("Today", today.format(DateTimeFormatter.ofPattern("EEE d MMM")))
        Text(
            line,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun Hero(summary: Summary, dailyLimit: Int, modifier: Modifier = Modifier) {
    val over = dailyLimit in 1 until summary.views
    Column(modifier.padding(top = 12.dp, bottom = 6.dp)) {
        Eyebrow("Shorts & Reels")
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 6.dp)) {
            RollingNumber(
                summary.views,
                style = MaterialTheme.typography.displayLarge,
                color = if (over) extraColors.caution else extraColors.tally,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.padding(bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ShortsApp.entries.forEach { app ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Dot(app.color)
                        Spacer(Modifier.width(7.dp))
                        RollingNumber(summary.perApp.getValue(app).views, style = MaterialTheme.typography.labelMedium)
                        Text(" ${app.shortName}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Text(
            "${Stats.formatDuration(summary.watchedMs)} watched",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (dailyLimit > 0) {
            Spacer(Modifier.height(14.dp))
            val progress by animateFloatAsState((summary.views.toFloat() / dailyLimit).coerceAtMost(1f), bouncy(), label = "limit")
            val barColor by animateColorAsState(if (over) extraColors.caution else MaterialTheme.colorScheme.primary, label = "limitColor")
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().clip(CircleShape).background(barColor))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (over) "${summary.views - dailyLimit} over your limit of $dailyLimit"
                else "${dailyLimit - summary.views} left of your $dailyLimit limit",
                style = MaterialTheme.typography.labelMedium,
                color = if (over) extraColors.caution else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A quiet "it's working" line when all is well; a card with the fix when it isn't. */
@Composable
private fun HealthBanner(health: Health, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val turnOnTracking = LocalTurnOnTracking.current
    when (health.status) {
        HealthStatus.OK -> Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            PulsingDot(extraColors.good)
            Spacer(Modifier.width(6.dp))
            Text("Tracking", style = MaterialTheme.typography.titleSmall)
            if (health.connectedAt > 0) {
                Text(
                    "  ·  running since ${formatTime(health.connectedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HealthStatus.OFF -> Card(modifier) {
            Pill("Setup")
            Spacer(Modifier.height(10.dp))
            Text("Turn on tracking", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                "Thumb Tally uses Android's accessibility access to see when you're in Shorts, Reels, Spotlight or a YouTube video. It only wakes up while YouTube, Instagram or Snapchat is open, and nothing leaves your phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "If Android says the setting is restricted: open App info, tap ⋮, then \"Allow restricted settings\".",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(onClick = { turnOnTracking() }) { Text("Turn on") }
                GhostButton(onClick = { context.openAppInfo() }) { Text("App info") }
            }
        }
        HealthStatus.STOPPED -> Card(modifier) {
            Pill("Stopped", container = extraColors.danger.copy(alpha = 0.14f), content = extraColors.danger)
            Spacer(Modifier.height(10.dp))
            Text("Android stopped the tracker", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                "It's switched on but not running, usually because a battery saver closed it. Switch Thumb Tally off and on again in Accessibility, then allow it to run in the background.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(onClick = { turnOnTracking() }) { Text("Accessibility") }
                if (!health.batteryUnrestricted) GhostButton(onClick = { context.requestUnrestrictedBattery() }) { Text("Allow background") }
            }
        }
        HealthStatus.BATTERY_RESTRICTED -> Card(modifier) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PulsingDot(extraColors.good)
                Spacer(Modifier.width(6.dp))
                Text("Tracking", style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(8.dp))
            Text("Keep it running", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                "Android may pause the tracker to save battery, and then videos stop being counted. Allowing background use costs very little: the tracker only wakes up while YouTube, Instagram or Snapchat is open.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            PrimaryButton(onClick = { context.requestUnrestrictedBattery() }) { Text("Allow background use") }
        }
    }
}

@Composable
private fun WeekCard(week: List<DayBar>, modifier: Modifier = Modifier) {
    val max = week.maxOf { it.total }.coerceAtLeast(1)
    // Bars spring up from the baseline when the card first appears.
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow)) }
    Card(modifier) {
        SectionTitle("Last 7 days")
        Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            week.forEachIndexed { index, day ->
                val isToday = index == week.lastIndex
                val fraction by animateFloatAsState(day.total.toFloat() / max, bouncy(), label = "bar")
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (day.total > 0) "${day.total}" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                        Column(
                            Modifier
                                .fillMaxWidth(0.62f)
                                .fillMaxHeight(fraction.coerceIn(0f, 1f))
                                .graphicsLayer {
                                    scaleY = grow.value
                                    transformOrigin = TransformOrigin(0.5f, 1f)
                                }
                                .clip(RoundedCornerShape(6.dp)),
                        ) {
                            // Stacked: Instagram on top, YouTube at the base.
                            ShortsApp.entries.reversed().forEach { app ->
                                val count = day.perApp.getValue(app)
                                if (count > 0) Box(Modifier.fillMaxWidth().weight(count.toFloat()).background(app.color))
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        day.label.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isToday) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun LongVideosCard(summary: Summary, modifier: Modifier = Modifier) {
    Card(modifier) {
        SectionTitle("YouTube videos today")
        Row(verticalAlignment = Alignment.Bottom) {
            Text(Stats.formatDuration(summary.longVideos.watchedMs), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.width(10.dp))
            Text(
                "${summary.longVideos.views} ${if (summary.longVideos.views == 1) "video" else "videos"}",
                modifier = Modifier.padding(bottom = 3.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "Regular videos, counted separately from Shorts.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CreatorsCard(creators: List<CreatorCount>, modifier: Modifier = Modifier) {
    Card(modifier) {
        SectionTitle("Most watched today")
        creators.forEachIndexed { index, creator ->
            Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${index + 1}",
                    modifier = Modifier.width(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
                Dot(creator.app.color)
                Spacer(Modifier.width(10.dp))
                Text(
                    creator.name,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text("${creator.views}", style = MaterialTheme.typography.headlineSmall)
            }
        }
    }
}

fun formatTime(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("h:mm a"))
