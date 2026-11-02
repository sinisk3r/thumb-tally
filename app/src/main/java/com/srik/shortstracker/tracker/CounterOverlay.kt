package com.srik.shortstracker.tracker

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.AnticipateInterpolator
import android.view.animation.BounceInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.srik.shortstracker.R
import com.srik.shortstracker.data.OverlayColor
import com.srik.shortstracker.data.OverlayMode
import com.srik.shortstracker.data.OverlaySpot
import com.srik.shortstracker.data.OverlayStyle
import com.srik.shortstracker.data.TrackerConfig

/**
 * The pill that pops up over Shorts/Reels when a new video starts, then goes away by itself.
 * It ignores touches, so it never gets in the way of the app underneath.
 *
 * Uses the accessibility overlay window type, which needs no extra permission and is removed
 * automatically if the tracker is switched off.
 */
class CounterOverlay(private val service: AccessibilityService) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val density = service.resources.displayMetrics.density
    private val handler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { hide() }

    /** The window: a transparent frame with room around the pill, so its bounce and shadow aren't cut off. */
    private var frame: FrameLayout? = null
    private var pill: LinearLayout? = null
    /** A pill still playing its exit animation. */
    private var leaving: FrameLayout? = null
    private var spot = OverlaySpot.TOP_RIGHT
    private var style = OverlayStyle.POP
    private lateinit var content: List<View>
    private lateinit var dot: View
    private lateinit var countText: TextView
    private lateinit var labelText: TextView
    private lateinit var nudgeText: TextView
    private lateinit var barTrack: FrameLayout
    private lateinit var barFill: View

    /** Pops the pill in (or bounces it if it's already up). [showCount] false shows only the nudge. */
    fun pop(app: ShortsApp, session: Int, today: Int, nudge: Nudge?, config: TrackerConfig, showCount: Boolean) {
        if (!showCount && nudge == null) return
        if (pill != null && (spot != config.overlaySpot || style != config.overlayStyle)) removeNow()
        removeLeaving()
        val fresh = pill == null
        val view = pill ?: create(config.overlaySpot, config.overlayStyle).also { pill = it }

        val (count, label) = when (config.overlayMode) {
            OverlayMode.SESSION -> "$session" to "this sitting"
            OverlayMode.TODAY -> "$today" to "today"
            OverlayMode.BOTH -> "$session" to "now · $today today"
        }
        roll(count, animate = !fresh)
        labelText.text = label
        dot.visibility = if (showCount) View.VISIBLE else View.GONE
        countText.visibility = dot.visibility
        updateBar(today, config.dailyLimit, showCount, fresh)
        labelText.visibility = dot.visibility

        nudgeText.text = nudge?.text
        nudgeText.visibility = if (nudge == null) View.GONE else View.VISIBLE
        // Going over the limit is said in words (the nudge), never with an alarming red pill.
        val background = when {
            config.overlayColor == OverlayColor.APP -> app.color.withAlpha(0xF0)
            // Pure black blends into the camera hole.
            config.overlayColor == OverlayColor.MIDNIGHT && style == OverlayStyle.CAMERA -> Color.BLACK
            else -> config.overlayColor.argb
        }
        (view.background as GradientDrawable).setColor(background)
        // Snapchat yellow needs dark text; everything else is dark enough for white.
        val dark = config.overlayColor == OverlayColor.APP && app == ShortsApp.SNAPCHAT
        countText.setTextColor(if (dark) INK else Color.WHITE)
        labelText.setTextColor(if (dark) INK_SOFT else 0xB3FFFFFF.toInt())
        (dot.background as GradientDrawable).setColor(if (config.overlayColor == OverlayColor.APP) Color.WHITE else app.color)
        val tinted = config.overlayColor == OverlayColor.MIDNIGHT
        nudgeText.setTextColor(
            when {
                dark -> INK
                tinted -> nudge?.tone?.textColor ?: Color.WHITE
                else -> Color.WHITE
            },
        )

        if (fresh) view.post { enter(view) } else bounce(view)
        handler.removeCallbacks(hideRunnable)
        handler.postDelayed(hideRunnable, if (nudge != null) NUDGE_MS else COUNT_MS)
    }

    /** Sends the pill away, e.g. when you leave the app. */
    fun hide() {
        handler.removeCallbacks(hideRunnable)
        val view = pill ?: return
        val window = frame
        pill = null
        frame = null
        leaving = window
        exit(view) { removeLeaving(window) }
    }

    private fun removeNow() {
        handler.removeCallbacks(hideRunnable)
        frame?.let { runCatching { windowManager.removeView(it) } }
        frame = null
        pill = null
    }

    private fun removeLeaving(window: FrameLayout? = leaving) {
        window ?: return
        runCatching { windowManager.removeView(window) }
        if (leaving === window) leaving = null
    }

    // region Animations

    private fun enter(view: View) {
        view.animate().cancel()
        view.animate().startDelay = 0
        when (style) {
            OverlayStyle.CAMERA -> fromCamera(view)
            OverlayStyle.POP -> {
                view.set(alpha = 0f, scale = 0.4f, dy = enterOffset())
                view.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f)
                    .setDuration(480).setInterpolator(OvershootInterpolator(1.8f))
            }
            OverlayStyle.DROP -> {
                view.set(alpha = 0f, dy = -dp(ROOM_DP).toFloat())
                view.animate().alpha(1f).translationY(0f).setDuration(650).setInterpolator(BounceInterpolator())
            }
            OverlayStyle.SLIDE -> {
                view.set(alpha = 0f, dx = slideOffset())
                view.animate().alpha(1f).translationX(0f).setDuration(520).setInterpolator(OvershootInterpolator(1.4f))
            }
            OverlayStyle.GENTLE -> {
                view.set(alpha = 0f, scale = 0.94f)
                view.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(DecelerateInterpolator())
            }
        }
    }

    private fun exit(view: View, then: () -> Unit) {
        view.animate().cancel()
        view.animate().startDelay = 0
        content.forEach { it.animate().cancel() }
        when (style) {
            OverlayStyle.CAMERA -> intoCamera(view, then)
            OverlayStyle.POP -> view.animate().alpha(0f).scaleX(0.3f).scaleY(0.3f).translationY(enterOffset())
                .setDuration(280).setInterpolator(AnticipateInterpolator(1.4f)).withEndAction(then)
            OverlayStyle.DROP -> view.animate().alpha(0f).translationY(-dp(ROOM_DP).toFloat())
                .setDuration(260).setInterpolator(AnticipateInterpolator(1.2f)).withEndAction(then)
            OverlayStyle.SLIDE -> view.animate().alpha(0f).translationX(slideOffset())
                .setDuration(260).setInterpolator(AccelerateInterpolator()).withEndAction(then)
            OverlayStyle.GENTLE -> view.animate().alpha(0f).scaleX(0.94f).scaleY(0.94f)
                .setDuration(220).setInterpolator(AccelerateInterpolator()).withEndAction(then)
        }
    }

    /** Already showing: a quick squash and spring back, starting from wherever the last animation is. */
    private fun bounce(view: View) {
        view.animate().cancel()
        val peak = if (style == OverlayStyle.CAMERA || style == OverlayStyle.GENTLE) 1.06f else 1.12f
        view.animate().alpha(1f).translationX(0f).translationY(0f).scaleX(peak).scaleY(peak)
            .setStartDelay(0).setDuration(110).setInterpolator(DecelerateInterpolator()).withEndAction {
                view.animate().scaleX(1f).scaleY(1f).setDuration(380).setInterpolator(OvershootInterpolator(2.5f))
            }
        content.forEach { it.animate().alpha(1f).setStartDelay(0).setDuration(120) }
    }

    /**
     * Starts as a black dot exactly over the camera hole, stretches into the pill as it drops into place,
     * then the text fades in. Leaving plays it backwards.
     */
    private fun fromCamera(view: View) {
        val start = cameraStart(view)
        view.set(alpha = 1f, scaleX = start.scaleX, scaleY = start.scaleY, dx = start.dx, dy = start.dy)
        content.forEach { it.alpha = 0f }
        view.animate().scaleX(1f).scaleY(1f).translationX(0f).translationY(0f)
            .setDuration(520).setInterpolator(SPRINGY)
        content.forEach { it.animate().alpha(1f).setStartDelay(200).setDuration(200) }
    }

    private fun intoCamera(view: View, then: () -> Unit) {
        val end = cameraStart(view)
        content.forEach { it.animate().alpha(0f).setStartDelay(0).setDuration(120) }
        view.animate().scaleX(end.scaleX).scaleY(end.scaleY).translationX(end.dx).translationY(end.dy)
            .setStartDelay(90).setDuration(360).setInterpolator(PathInterpolator(0.5f, 0f, 0.2f, 1f))
            .withEndAction {
                // Gone into the hole; fade the last few pixels so nothing lingers.
                view.animate().alpha(0f).setStartDelay(0).setDuration(80).withEndAction(then)
            }
    }

    /** Old number rolls up and out, the new one rolls in from below. */
    private fun roll(to: String, animate: Boolean) {
        if (!animate || countText.text == to || countText.text.isEmpty()) {
            countText.text = to
            return
        }
        val h = countText.height.takeIf { it > 0 }?.toFloat() ?: dp(26).toFloat()
        countText.animate().cancel()
        countText.animate().translationY(-h).alpha(0f).setStartDelay(0).setDuration(130)
            .setInterpolator(AccelerateInterpolator()).withEndAction {
                countText.text = to
                countText.translationY = h
                countText.animate().translationY(0f).alpha(1f).setDuration(320).setInterpolator(OvershootInterpolator(1.6f))
            }
    }

    /** A thin bar under the count that fills towards the daily limit; amber once you're past it. */
    private fun updateBar(today: Int, limit: Int, showCount: Boolean, fresh: Boolean) {
        val show = showCount && limit > 0
        barTrack.visibility = if (show) View.VISIBLE else View.GONE
        if (!show) return
        val progress = (today.toFloat() / limit).coerceIn(0f, 1f)
        (barFill.background as GradientDrawable).setColor(if (today > limit) 0xFFFFC46B.toInt() else Color.WHITE)
        if (fresh) barFill.scaleX = ((today - 1).toFloat() / limit).coerceIn(0f, 1f)
        barFill.animate().cancel()
        barFill.animate().scaleX(progress).setStartDelay(if (fresh) 380 else 120).setDuration(520)
            .setInterpolator(DecelerateInterpolator())
    }

    private data class Transform(val scaleX: Float, val scaleY: Float, val dx: Float, val dy: Float)

    /** The transform that shrinks the pill onto the camera hole (or a dot in the status bar if there's none). */
    private fun cameraStart(view: View): Transform {
        val hole = cameraHole()
        val at = IntArray(2).also(view::getLocationOnScreen)
        // Location includes the current translation; undo it to get the resting spot.
        val centerX = at[0] - view.translationX + view.width / 2f
        val centerY = at[1] - view.translationY + view.height / 2f
        val w = view.width.coerceAtLeast(1)
        val h = view.height.coerceAtLeast(1)
        return Transform(
            scaleX = hole.width() / w.toFloat(),
            scaleY = hole.height() / h.toFloat(),
            dx = hole.exactCenterX() - centerX,
            dy = hole.exactCenterY() - centerY,
        )
    }

    private fun cameraHole(): Rect {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val cutout = windowManager.currentWindowMetrics.windowInsets.displayCutout
            cutout?.boundingRectTop?.takeIf { !it.isEmpty }?.let { return it }
        }
        val screenWidth = service.resources.displayMetrics.widthPixels
        val size = dp(26)
        val top = (statusBarHeight() - size) / 2
        return Rect(screenWidth / 2 - size / 2, top, screenWidth / 2 + size / 2, top + size)
    }

    private fun View.set(
        alpha: Float = 1f, scale: Float = 1f, scaleX: Float = scale, scaleY: Float = scale, dx: Float = 0f, dy: Float = 0f,
    ) {
        this.alpha = alpha
        this.scaleX = scaleX
        this.scaleY = scaleY
        translationX = dx
        translationY = dy
    }

    /** Comes in from (and leaves towards) the nearest screen edge. */
    private fun enterOffset() = dp(if (spot == OverlaySpot.BOTTOM) 28 else -28).toFloat()

    private fun slideOffset() = dp(if (spot == OverlaySpot.TOP_RIGHT) ROOM_DP else -ROOM_DP).toFloat()

    // endregion

    private fun create(where: OverlaySpot, how: OverlayStyle): LinearLayout {
        spot = where
        style = how
        dot = View(service).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL }
            layoutParams = LinearLayout.LayoutParams(dp(9), dp(9)).apply { marginEnd = dp(9) }
        }
        countText = TextView(service).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            typeface = font(R.font.big_shoulders_black, Typeface.DEFAULT_BOLD)
            fontFeatureSettings = "tnum"
            includeFontPadding = false
        }
        labelText = TextView(service).apply {
            setTextColor(0xB3FFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = font(R.font.figtree_medium, Typeface.DEFAULT)
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(7) }
        }
        // The number rolls up like an odometer, so it's clipped to its own box.
        val roller = FrameLayout(service).apply {
            clipChildren = true
            addView(countText)
        }
        val row = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(dot)
            addView(roller)
            addView(labelText)
        }
        barFill = View(service).apply {
            background = GradientDrawable().apply { cornerRadius = dp(2).toFloat(); setColor(Color.WHITE) }
            pivotX = 0f
            scaleX = 0f
        }
        barTrack = FrameLayout(service).apply {
            background = GradientDrawable().apply { cornerRadius = dp(2).toFloat(); setColor(0x33FFFFFF) }
            addView(barFill, FrameLayout.LayoutParams(-1, -1))
            layoutParams = LinearLayout.LayoutParams(-1, dp(3)).apply { topMargin = dp(6); bottomMargin = dp(1) }
        }
        nudgeText = TextView(service).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = font(R.font.figtree_semibold, Typeface.DEFAULT_BOLD)
            maxWidth = dp(280)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(2) }
        }
        content = listOf(row, barTrack, nudgeText)
        val camera = how == OverlayStyle.CAMERA
        val view = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(16), dp(9), dp(16), dp(10))
            background = GradientDrawable().apply { cornerRadius = dp(24).toFloat(); setColor(BACKGROUND) }
            elevation = if (camera) 0f else dp(6).toFloat()
            addView(row)
            addView(barTrack)
            addView(nudgeText)
            // Grow from the corner it sits in (the camera style works out its own centre).
            addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                v.pivotX = if (!camera && where == OverlaySpot.TOP_RIGHT) v.width.toFloat() else v.width / 2f
                v.pivotY = when {
                    camera -> v.height / 2f
                    where == OverlaySpot.BOTTOM -> v.height.toFloat()
                    else -> 0f
                }
            }
        }
        val room = dp(ROOM_DP)
        val window = FrameLayout(service).apply {
            // The camera style starts up in the status bar, so its window reaches the top of the screen.
            if (camera) setPadding(room, statusBarHeight() + dp(8), room, room) else setPadding(room, room, room, room)
            clipChildren = false
            clipToPadding = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            addView(view, FrameLayout.LayoutParams(-2, -2))
        }
        val height = service.resources.displayMetrics.heightPixels
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            if (camera) {
                gravity = Gravity.TOP or Gravity.START
                // Centre the window on the camera hole, which isn't always in the middle.
                val screenWidth = service.resources.displayMetrics.widthPixels
                width = screenWidth
                x = 0
                y = 0
                (view.layoutParams as FrameLayout.LayoutParams).gravity = Gravity.CENTER_HORIZONTAL
                val holeX = cameraHole().exactCenterX()
                window.setPadding(
                    (2 * holeX - screenWidth).coerceAtLeast(0f).toInt() + room,
                    window.paddingTop,
                    (screenWidth - 2 * holeX).coerceAtLeast(0f).toInt() + room,
                    room,
                )
            } else {
                // Top: just under the app's search bar. Bottom: above the caption and the tab bar.
                gravity = when (where) {
                    OverlaySpot.TOP_RIGHT -> Gravity.TOP or Gravity.END
                    OverlaySpot.TOP -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    OverlaySpot.BOTTOM -> Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                }
                x = if (where == OverlaySpot.TOP_RIGHT) dp(14) - room else 0
                y = (if (where == OverlaySpot.BOTTOM) (height * 0.2).toInt() else (height * 0.12).toInt()) - room
            }
        }
        // Invisible until its first frame is laid out and the entrance starts.
        view.alpha = 0f
        windowManager.addView(window, params)
        frame = window
        return view
    }

    private fun statusBarHeight(): Int {
        val id = service.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) service.resources.getDimensionPixelSize(id) else dp(28)
    }

    private fun font(id: Int, fallback: Typeface) = runCatching { ResourcesCompat.getFont(service, id) }.getOrNull() ?: fallback

    private fun dp(value: Int) = (value * density).toInt()

    private val ShortsApp.color: Int
        get() = when (this) {
            ShortsApp.YOUTUBE -> 0xFFFF4E45.toInt()
            ShortsApp.INSTAGRAM -> 0xFFD62976.toInt()
            ShortsApp.SNAPCHAT -> 0xFFF5C400.toInt()
        }

    private val Nudge.Tone.textColor: Int
        get() = when (this) {
            Nudge.Tone.CHEER -> 0xFF7BE3A6.toInt()
            Nudge.Tone.TEASE -> 0xFFC9BEFF.toInt()
            Nudge.Tone.WARN -> 0xFFFFC46B.toInt()
        }

    private fun Int.withAlpha(alpha: Int) = (this and 0x00FFFFFF) or (alpha shl 24)

    private companion object {
        const val BACKGROUND = 0xEB141219.toInt()
        const val INK = 0xFF17151F.toInt()
        const val INK_SOFT = 0xB317151F.toInt()
        const val COUNT_MS = 1_800L
        const val NUDGE_MS = 4_500L
        /** Space around the pill inside its window, for the overshoot, slide and shadow. */
        const val ROOM_DP = 32
        /** Fast out, a little overshoot, settles softly: like the pill is stretching out of the hole. */
        val SPRINGY = OvershootInterpolator(1.15f)
    }
}
