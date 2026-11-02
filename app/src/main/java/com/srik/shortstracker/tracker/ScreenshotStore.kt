package com.srik.shortstracker.tracker

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.view.Display
import androidx.annotation.RequiresApi
import androidx.core.graphics.scale
import com.srik.shortstracker.data.ViewDao
import java.io.File
import java.util.concurrent.TimeUnit

/** Small JPEG screenshots in app-private storage (never in the phone's gallery). */
class ScreenshotStore(context: Context) {
    private val dir = File(context.filesDir, "shots")

    /** Needs Android 11+; does nothing on older phones. */
    fun capture(service: AccessibilityService, onBitmap: (Bitmap) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) captureR(service, onBitmap)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun captureR(service: AccessibilityService, onBitmap: (Bitmap) -> Unit) {
        service.takeScreenshot(
            Display.DEFAULT_DISPLAY,
            service.mainExecutor,
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    val buffer = result.hardwareBuffer
                    // Hardware bitmaps can't be scaled or compressed, so copy into normal memory first.
                    val bitmap = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                        ?.copy(Bitmap.Config.ARGB_8888, false)
                    buffer.close()
                    if (bitmap != null) onBitmap(bitmap)
                }

                override fun onFailure(errorCode: Int) {
                    Log.w(TAG, "Screenshot failed with code $errorCode")
                }
            },
        )
    }

    /** Scales down to [WIDTH]px wide and saves as JPEG; returns the file path. */
    fun save(id: Long, bitmap: Bitmap): String {
        dir.mkdirs()
        val height = bitmap.height * WIDTH / bitmap.width
        val small = bitmap.scale(WIDTH, height)
        val file = File(dir, "$id.jpg")
        file.outputStream().use { small.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
        if (small !== bitmap) small.recycle()
        bitmap.recycle()
        return file.absolutePath
    }

    suspend fun prune(dao: ViewDao, retentionDays: Int) {
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(retentionDays.toLong())
        dao.withScreenshotsBefore(cutoff).forEach { event ->
            event.screenshotPath?.let { File(it).delete() }
            dao.setScreenshot(event.id, null)
        }
    }

    fun deleteAll() {
        dir.deleteRecursively()
    }

    private companion object {
        const val TAG = "ScreenshotStore"
        const val WIDTH = 540
        const val QUALITY = 70
    }
}
