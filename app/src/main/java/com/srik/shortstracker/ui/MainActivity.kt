package com.srik.shortstracker.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.srik.shortstracker.tracker.TrackingGuard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { ShortsTheme { MainScreen(viewModel) } }
        // Needed for "paused for a payment app" and "tracking is off" notifications (Android 13+). Asked once;
        // after a "no", Settings shows an Allow button instead.
        if (!TrackingGuard.canNotify(this) && savedInstanceState == null) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.refresh() }
                .launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        // Keep the tracker status fresh while the app is in front (it changes when you come back
        // from Accessibility or battery settings, and Android can take a moment to start the tracker).
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    viewModel.refresh()
                    delay(REFRESH_MS)
                }
            }
        }
    }

    private companion object {
        const val REFRESH_MS = 15_000L
    }
}

private enum class Tab(val title: String, val icon: ImageVector) {
    TODAY("Today", Icons.Filled.Home),
    PATTERNS("Patterns", Icons.Filled.DateRange),
    FEED("Feed", Icons.AutoMirrored.Filled.List),
    SETTINGS("Settings", Icons.Filled.Settings),
}

@Composable
private fun MainScreen(viewModel: MainViewModel) {
    var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val patterns by viewModel.patterns.collectAsStateWithLifecycle()
    val patternDays by viewModel.patternDays.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()
    val config by viewModel.config.collectAsStateWithLifecycle()
    val health by viewModel.health.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                Tab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.title, style = MaterialTheme.typography.titleSmall) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.secondaryContainer),
                    )
                }
            }
        },
    ) { padding ->
        AnimatedContent(
            targetState = tab,
            modifier = Modifier.padding(padding).fillMaxSize(),
            transitionSpec = { (fadeIn(tween(220)) + scaleIn(bouncy(), initialScale = 0.97f)) togetherWith fadeOut(tween(90)) },
            label = "tab",
        ) { current ->
            when (current) {
                Tab.TODAY -> TodayScreen(summary, config, health)
                Tab.PATTERNS -> PatternsScreen(patterns, patternDays, viewModel::setPatternDays)
                Tab.FEED -> FeedScreen(events)
                Tab.SETTINGS -> SettingsScreen(config, health, viewModel)
            }
        }
    }
}

fun Context.openAccessibilitySettings() {
    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
}

/** App info page, where "Allow restricted settings" lives in its ⋮ menu (Android 13+ sideloads). */
fun Context.openAppInfo() {
    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
}
