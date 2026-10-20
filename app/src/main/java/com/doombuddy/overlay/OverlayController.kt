package com.doombuddy.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.doombuddy.character.Accessory
import com.doombuddy.character.CharacterDefinition
import com.doombuddy.character.CharacterDirector
import com.doombuddy.data.TimerMode

/**
 * Everything the overlay composable renders for one moment of buddy time.
 * The intervention is PERSISTENT: the only exits are a completed hold-dismiss
 * (via [OverlayController.onHoldComplete]) or the tracked app losing foreground.
 */
data class OverlayUiState(
    val character: CharacterDefinition? = null,
    val accessory: Accessory = Accessory.NONE,
    val moodLevel: Int = 0,
    val dialogueLine: String? = null,
    val timerMode: TimerMode = TimerMode.CYCLE,
    val sessionMs: Long = 0,
    val todayMs: Long = 0,
    val cycleSeconds: Int = 3,
    val holdDurationMs: Int = 1200,
    /** Rendered size multiplier from the user's size setting (0.7–1.6). */
    val uiScale: Float = 1f,
    val celebration: Boolean = false,
) {
    companion object {
        val idle = OverlayUiState()
    }
}

/**
 * The single authoritative overlay window + its Compose content. Repeated show()
 * calls update in place — there can never be two buddies (spec §49).
 *
 * Platform rules (docs/ANDROID_PLATFORM.md):
 *  - FLAG_NOT_FOCUSABLE implies NOT_TOUCH_MODAL: touches outside our wrap-content
 *    window pass through to the app underneath — no full-screen touch blocker, ever.
 *  - Interactive interventions remove FLAG_NOT_TOUCHABLE so ONLY the buddy's own
 *    bounds receive hold/tap; everything else stays with the host app.
 */
class OverlayController(private val context: Context) {

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    var state by mutableStateOf(OverlayUiState.idle)
        private set

    /** Tap (no hold): character poke reaction only — NEVER dismisses (spec §33). */
    var onPoke: (() -> Unit)? = null

    /** Hold started: buddy gets nervous. */
    var onHoldStart: (() -> Unit)? = null

    /** Hold released early or cancelled by dragging away: reset (spec §33). */
    var onHoldRelease: (() -> Unit)? = null

    /** Hold completed: the ONLY user-initiated dismissal (spec §32). */
    var onHoldComplete: (() -> Unit)? = null

    var director: CharacterDirector? = null

    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private var view: AbstractComposeView? = null
    private var params: WindowManager.LayoutParams? = null
    private var attached = false
    /**
     * Monotonically increasing presentation id. Delayed exits (rapid-reopen and
     * hold-dismiss) must prove they still own the presentation before detaching it;
     * otherwise an old coroutine can remove a newer persistent overlay.
     */
    private var presentationGeneration = 0L

    val isAttached: Boolean get() = attached

    val currentPresentationGeneration: Long get() = presentationGeneration

    fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(context)

    @SuppressLint("InflateParams")
    fun show(
        ui: OverlayUiState,
        xFrac: Float,
        yFrac: Float,
        interactive: Boolean,
    ) {
        if (!canDrawOverlays()) return
        presentationGeneration++
        xFraction = xFrac.coerceIn(0.05f, 0.95f)
        yFraction = yFrac.coerceIn(0.05f, 0.95f)
        state = ui
        if (!attached) {
            attach(interactive)
        } else {
            updateFlags(interactive)
            reposition()
        }
    }

    /** Live reposition when the user moves the buddy in Settings. */
    fun updatePosition(xFrac: Float, yFrac: Float) {
        if (!attached) return
        xFraction = xFrac.coerceIn(0.05f, 0.95f)
        yFraction = yFrac.coerceIn(0.05f, 0.95f)
        reposition()
    }

    private var xFraction: Float = 0.84f
    private var yFraction: Float = 0.80f

    fun update(ui: OverlayUiState) {
        if (!attached) return
        val scaleChanged = state.uiScale != ui.uiScale
        state = ui
        // The transparent character stage is intentionally fixed-size, but posting a
        // reposition keeps the overlay correct after density/configuration changes and
        // after the first composition has measured its stable bounds.
        if (scaleChanged) view?.post { reposition() }
    }

    fun hide(onDone: () -> Unit = {}) {
        presentationGeneration++
        if (!attached) {
            state = OverlayUiState.idle
            onDone()
            return
        }
        detach()
        onDone()
    }

    /** Detach only when this caller still owns the currently displayed overlay. */
    fun hideIfGeneration(generation: Long, onDone: () -> Unit = {}) {
        if (generation != presentationGeneration) return
        hide(onDone)
    }

    private fun attach(interactive: Boolean) {
        val owner = OverlayLifecycleOwner()
        val composeView = object : AbstractComposeView(createWindowContext()) {
            @Composable
            override fun Content() {
                OverlayRoot(this@OverlayController)
            }
        }.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
        }

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            // TOP|START + pixel coords computed from the user's fractional position;
            // exact centering happens in reposition() once the view is measured.
            gravity = Gravity.TOP or Gravity.START
            val topInset = statusBarHeight()
            x = (context.resources.displayMetrics.widthPixels * xFraction).toInt()
            y = topInset + ((context.resources.displayMetrics.heightPixels - topInset) * yFraction).toInt()
        }

        lifecycleOwner = owner
        view = composeView
        params = lp
        owner.onCreate()
        try {
            windowManager.addView(composeView, lp)
            attached = true
            updateFlags(interactive)
            composeView.post { reposition() }
        } catch (e: WindowManager.BadTokenException) {
            // Overlay permission revoked mid-flight or window context invalid.
            owner.onDestroy()
            attached = false
        }
    }

    @SuppressLint("NewApi")
    private fun createWindowContext(): Context =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val display = (context.getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager)
                .getDisplay(android.view.Display.DEFAULT_DISPLAY)
            context.createWindowContext(display, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
        } else context

    private fun statusBarHeight(): Int {
        val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) context.resources.getDimensionPixelSize(id) else 0
    }

    private fun updateFlags(interactive: Boolean) {
        val lp = params ?: return
        val v = view ?: return
        lp.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            if (interactive) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        try {
            windowManager.updateViewLayout(v, lp)
        } catch (_: Exception) {
        }
    }

    /**
     * Centers the measured window on the user's chosen screen point (fractions of the
     * usable area), clamped so the buddy always stays fully on screen.
     */
    private fun reposition() {
        val v = view ?: return
        val lp = params ?: return
        val dm = context.resources.displayMetrics
        val topInset = statusBarHeight()
        val usableW = dm.widthPixels
        val usableH = dm.heightPixels - topInset
        val targetX = usableW * xFraction - v.width / 2f
        val targetY = topInset + usableH * yFraction - v.height / 2f
        lp.x = targetX.toInt().coerceIn(0, (usableW - v.width).coerceAtLeast(0))
        lp.y = targetY.toInt().coerceIn(topInset, topInset + (usableH - v.height).coerceAtLeast(0))
        try {
            windowManager.updateViewLayout(v, lp)
        } catch (_: Exception) {
        }
    }

    private fun detach() {
        val v = view ?: return
        val owner = lifecycleOwner
        view = null
        lifecycleOwner = null
        params = null
        attached = false
        try {
            windowManager.removeViewImmediate(v)
        } catch (_: Exception) {
        }
        owner?.onDestroy()
        state = OverlayUiState.idle
    }

    fun hapticTick() {
        view?.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }
}
