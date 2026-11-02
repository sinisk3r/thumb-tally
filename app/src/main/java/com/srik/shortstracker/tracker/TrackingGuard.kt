package com.srik.shortstracker.tracker

import android.Manifest
import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.srik.shortstracker.R
import com.srik.shortstracker.data.TrackerSettings

/**
 * Keeps people from silently losing tracking:
 * - the service pauses itself for payment apps and says so ([notifyPaused]);
 * - while tracking is off, a light check every few minutes ([CheckReceiver]) reminds them to turn it back on,
 *   ideally only when they actually opened YouTube, Instagram or Snapchat (needs Usage access).
 *
 * The check is a non-waking inexact alarm, scheduled only while tracking is off and cancelled the moment the
 * service connects, so a working tracker costs nothing extra.
 */
object TrackingGuard {
    private const val CHANNEL = "tracking_status"
    private const val NOTIFICATION_ID = 41
    private const val CHECK_EVERY_MS = 5 * 60_000L
    private const val PREFS = "tracking_guard"
    private const val KEY_PAUSED_AT = "paused_at"
    private const val KEY_LAST_REMINDER = "last_reminder_at"
    private const val KEY_LAST_CHECK = "last_check_at"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Switched on in Accessibility settings (true even if Android hasn't started it yet). */
    fun isTrackingOn(context: Context): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val me = ComponentName(context, ShortsAccessibilityService::class.java)
        return enabled.split(':').any {
            it.equals(me.flattenToString(), ignoreCase = true) || it.equals(me.flattenToShortString(), ignoreCase = true)
        }
    }

    fun hasUsageAccess(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
        @Suppress("DEPRECATION")
        val mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Called by the service right before it switches itself off for a payment app. */
    fun notifyPaused(context: Context, paymentApp: String) {
        prefs(context).edit { putLong(KEY_PAUSED_AT, System.currentTimeMillis()) }
        show(
            context,
            "Paused so $paymentApp works",
            "Thumb Tally switched itself off for your payment. Tap to switch it back on when you're done.",
        )
        startChecks(context)
    }

    /** Tracking came back: drop the reminder and stop checking. */
    fun onTrackingOn(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
        context.getSystemService(AlarmManager::class.java)?.cancel(checkIntent(context))
    }

    /** Start the reminder checks if tracking is off. Safe to call often. */
    fun startChecks(context: Context) {
        if (isTrackingOn(context)) return
        prefs(context).edit { putLong(KEY_LAST_CHECK, System.currentTimeMillis()) }
        context.getSystemService(AlarmManager::class.java)?.setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME, // not a wake-up alarm: it waits until the phone is awake anyway
            SystemClock.elapsedRealtime() + CHECK_EVERY_MS,
            CHECK_EVERY_MS,
            checkIntent(context),
        )
    }

    internal fun check(context: Context) {
        val now = System.currentTimeMillis()
        val trackingOn = isTrackingOn(context)
        if (trackingOn) {
            onTrackingOn(context)
            return
        }
        val prefs = prefs(context)
        val lastCheck = prefs.getLong(KEY_LAST_CHECK, now - CHECK_EVERY_MS)
        prefs.edit { putLong(KEY_LAST_CHECK, now) }
        val usage = hasUsageAccess(context)
        val decision = OffReminder.decide(
            now = now,
            trackingOn = false,
            remindersOn = TrackerSettings.get(context).state.value.remindWhenOff,
            lastReminderAt = prefs.getLong(KEY_LAST_REMINDER, 0),
            pausedAt = prefs.getLong(KEY_PAUSED_AT, 0),
            hasUsageAccess = usage,
            openedSinceLastCheck = if (usage) lastWatchedAppOpened(context, lastCheck, now) else null,
        ) ?: return
        prefs.edit { putLong(KEY_LAST_REMINDER, now) }
        when (decision) {
            is Reminder.Missed -> show(
                context,
                "Thumb Tally is paused",
                "You opened ${decision.app.appName}, but nothing's being counted. Tap to switch tracking back on.",
            )
            Reminder.Off -> show(
                context,
                "Thumb Tally is paused",
                "Your Shorts and Reels aren't being counted right now. Tap to switch tracking back on.",
            )
        }
    }

    private fun lastWatchedAppOpened(context: Context, from: Long, to: Long): ShortsApp? {
        val stats = context.getSystemService(UsageStatsManager::class.java) ?: return null
        val events = runCatching { stats.queryEvents(from, to) }.getOrNull() ?: return null
        val event = UsageEvents.Event()
        var found: ShortsApp? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            @Suppress("DEPRECATION") // MOVE_TO_FOREGROUND == ACTIVITY_RESUMED, and works back to API 26.
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                ShortsApp.fromPackage(event.packageName)?.let { found = it }
            }
        }
        return found
    }

    private fun show(context: Context, title: String, text: String) {
        if (!canNotify(context)) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Tracking status", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Tells you when tracking is paused, for example for a payment app."
            },
        )
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun checkIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0, Intent(context, CheckReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** The every-few-minutes check, and restarting it after a reboot or app update. */
    class CheckReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> startChecks(context)
                else -> check(context)
            }
        }
    }
}
