package com.srik.shortstracker.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

enum class HealthStatus { OK, BATTERY_RESTRICTED, STOPPED, OFF }

data class Health(
    /** Switched on in Accessibility settings. */
    val enabled: Boolean = false,
    /** Android actually has it running right now. */
    val running: Boolean = false,
    val batteryUnrestricted: Boolean = false,
    val connectedAt: Long = 0,
    val lastActiveAt: Long = 0,
    val cpuMsToday: Long = 0,
    /** Notifications allowed (always true before Android 13). */
    val canNotify: Boolean = true,
) {
    val status: HealthStatus
        get() = when {
            !enabled -> HealthStatus.OFF
            !running -> HealthStatus.STOPPED
            !batteryUnrestricted -> HealthStatus.BATTERY_RESTRICTED
            else -> HealthStatus.OK
        }
}

/** Phone makers whose battery savers are known to stop background apps, and where to switch that off. */
data class MakerTip(val maker: String, val steps: String, val url: String)

fun makerTip(manufacturer: String): MakerTip? = when (manufacturer.lowercase()) {
    "xiaomi", "redmi", "poco" -> MakerTip(
        "Xiaomi", "In App info, turn on Autostart, then set Battery saver to No restrictions.", "https://dontkillmyapp.com/xiaomi",
    )
    "samsung" -> MakerTip(
        "Samsung", "Settings → Battery → Background usage limits → Never sleeping apps → add Thumb Tally.", "https://dontkillmyapp.com/samsung",
    )
    "oneplus" -> MakerTip(
        "OnePlus", "App info → Battery → turn on Allow background activity.", "https://dontkillmyapp.com/oneplus",
    )
    "oppo", "realme" -> MakerTip(
        manufacturer.replaceFirstChar { it.uppercase() },
        "App info → Battery → turn on Allow background activity and Allow auto launch.",
        "https://dontkillmyapp.com/${manufacturer.lowercase()}",
    )
    "vivo", "iqoo" -> MakerTip(
        "Vivo", "Battery → Background power consumption management → Thumb Tally → Allow.", "https://dontkillmyapp.com/vivo",
    )
    "huawei", "honor" -> MakerTip(
        manufacturer.replaceFirstChar { it.uppercase() },
        "Battery → App launch → Thumb Tally → Manage manually, with all three switches on.",
        "https://dontkillmyapp.com/${manufacturer.lowercase()}",
    )
    else -> null
}

/** Asks Android to stop optimising the app's battery use (one system dialog). */
@SuppressLint("BatteryLife") // Sideloaded, and the tracker can't do its job if it's put to sleep.
fun Context.requestUnrestrictedBattery() {
    val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
    runCatching { startActivity(direct) }.onFailure { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
}

fun Context.openAppNotificationSettings() {
    runCatching {
        startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
    }
}

fun Context.openUrl(url: String) {
    runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
