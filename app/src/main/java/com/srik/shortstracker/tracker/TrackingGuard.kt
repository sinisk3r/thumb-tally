package com.srik.shortstracker.tracker

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
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
    // Channel settings can't change once created, so a new id; the old one is deleted in show().
    private const val CHANNEL = "tracking_paused"
    private const val OLD_CHANNEL = "tracking_status"
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

    /**
     * Switched on *and* actually running. "On but not running" happens when a battery saver or a settings change
     * stops the service without switching it off; it counts nothing, so it gets reminders too. The service runs
     * in this process, so [ShortsAccessibilityService.running] is readable here.
     */
    fun isTrackingWorking(context: Context): Boolean = isTrackingOn(context) && ShortsAccessibilityService.running

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

    /** The person (or Android) just switched tracking off: say so right away instead of at the next check. */
    fun notifyTurnedOff(context: Context) {
        if (!TrackerSettings.get(context).state.value.remindWhenOff) return
        prefs(context).edit { putLong(KEY_LAST_REMINDER, System.currentTimeMillis()) }
        show(
            context,
            "Thumb Tally was switched off",
            "Your Shorts and Reels aren't being counted. Tap to switch it back on.",
        )
    }

    /** Tracking came back: drop the reminder and stop checking. */
    fun onTrackingOn(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
        context.getSystemService(AlarmManager::class.java)?.cancel(checkIntent(context))
    }

    /** Start the reminder checks if tracking is off. Safe to call often. */
    fun startChecks(context: Context) {
        if (isTrackingWorking(context)) return
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
        if (isTrackingWorking(context)) {
            onTrackingOn(context)
            return
        }
        val prefs = prefs(context)
        val remind = OffReminder.shouldRemind(
            now = now,
            trackingOn = false,
            remindersOn = TrackerSettings.get(context).state.value.remindWhenOff,
            lastReminderAt = prefs.getLong(KEY_LAST_REMINDER, 0),
            pausedAt = prefs.getLong(KEY_PAUSED_AT, 0),
        )
        if (!remind) return
        prefs.edit { putLong(KEY_LAST_REMINDER, now) }
        show(
            context,
            "Thumb Tally is paused",
            "Your Shorts and Reels aren't being counted right now. Tap to switch tracking back on.",
        )
    }

    private fun show(context: Context, title: String, text: String) {
        if (!canNotify(context)) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.deleteNotificationChannel(OLD_CHANNEL)
        // High importance + ongoing keeps "paused" near the top instead of sinking under chat and shopping
        // alerts. It is cleared as soon as tracking is back on (onTrackingOn).
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Tracking paused", NotificationManager.IMPORTANCE_HIGH).apply {
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
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
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
