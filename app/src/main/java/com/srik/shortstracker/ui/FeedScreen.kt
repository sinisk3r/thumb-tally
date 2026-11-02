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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.srik.shortstracker.data.Stats
import com.srik.shortstracker.data.ViewEvent
import com.srik.shortstracker.tracker.ShortsApp
import com.srik.shortstracker.tracker.ViewKind
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun FeedScreen(events: List<ViewEvent>, modifier: Modifier = Modifier) {
    var preview by remember { mutableStateOf<String?>(null) }
    val zone = ZoneId.systemDefault()
    val byDay = remember(events) { events.groupBy { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() } }

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item { ScreenTitle("Feed", "Everything you watched", Modifier.enterRise(0)) }
        if (events.isEmpty()) {
            item {
                Text(
                    "Nothing yet. Watch a few Shorts or Reels and they'll show up here.",
                    modifier = Modifier.fillMaxWidth().padding(top = 48.dp).enterRise(1),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        byDay.forEach { (day, dayEvents) ->
            item(key = day.toString()) { DayHeader(day, dayEvents, Modifier.animateItem()) }
            items(dayEvents, key = { it.id }) { event ->
                FeedRow(event, zone, onImage = { preview = it }, modifier = Modifier.animateItem())
            }
        }
    }

    preview?.let { path ->
        Dialog(onDismissRequest = { preview = null }) {
            AsyncImage(
                model = File(path),
                contentDescription = "Screenshot",
                modifier = Modifier.clip(RoundedCornerShape(18.dp)).bouncyClick { preview = null },
            )
        }
    }
}

@Composable
private fun DayHeader(day: LocalDate, events: List<ViewEvent>, modifier: Modifier = Modifier) {
    val today = LocalDate.now()
    val label = when (day) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> day.format(DateTimeFormatter.ofPattern("EEE d MMM"))
    }
    val shorts = events.count { it.kind == ViewKind.SHORT.key }
    val videos = events.size - shorts
    Row(modifier.fillMaxWidth().padding(top = 22.dp, bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
        Text(label.uppercase(), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
        Eyebrow(listOfNotNull("$shorts shorts", if (videos > 0) "$videos videos" else null).joinToString(" · "))
    }
}

@Composable
private fun FeedRow(event: ViewEvent, zone: ZoneId, onImage: (String) -> Unit, modifier: Modifier = Modifier) {
    val app = ShortsApp.fromPackage(event.app)
    val isVideo = event.kind == ViewKind.VIDEO.key
    val time = Instant.ofEpochMilli(event.startedAt).atZone(zone).format(DateTimeFormatter.ofPattern("h:mm a"))
    Row(modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val path = event.screenshotPath
        if (path != null) {
            AsyncImage(
                model = File(path),
                contentDescription = "Screenshot",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 48.dp, height = 84.dp)
                    .bouncyClick { onImage(path) }
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            )
        } else {
            Box(Modifier.width(48.dp).padding(top = 7.dp), contentAlignment = Alignment.TopCenter) {
                if (isVideo) {
                    // Regular videos get a wide mark instead of a dot.
                    Box(Modifier.size(width = 18.dp, height = 11.dp).clip(RoundedCornerShape(3.dp)).background(app?.color ?: extraColors.line))
                } else {
                    Dot(app?.color ?: extraColors.line, size = 10.dp)
                }
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                event.creator ?: "Unknown creator",
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (event.creator == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            event.caption?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(3.dp))
            Text(
                listOfNotNull(
                    app?.shortName?.let { if (isVideo) "$it video" else it },
                    time,
                    Stats.formatClip(event.durationMs).takeIf { event.durationMs > 0 },
                ).joinToString("  ·  "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
