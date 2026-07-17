package com.tappy.assistant

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Manages the floating accessibility overlay panel that appears over other apps.
 * Handles panel construction, visibility toggling, message display, and confirmation dialogs.
 *
 * The overlay uses [WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY] which is
 * automatically granted when the accessibility service is enabled — no separate
 * SYSTEM_ALERT_WINDOW permission is needed.
 */
class OverlayManager(
    private val context: Context,
    private val windowManager: WindowManager,
    private val callbacks: Callbacks
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var quickPanel: View? = null
    private var panelMessage: TextView? = null
    private var voiceButton: Button? = null

    /** Whether the overlay panel is currently showing. */
    val isShowing: Boolean get() = quickPanel != null

    /** Toggles the quick panel on or off. */
    fun toggle() {
        if (quickPanel == null) show() else remove()
    }

    /** Shows the quick panel at the bottom of the screen. */
    fun show() {
        if (quickPanel != null) return

        val backgroundDrawable = android.graphics.drawable.GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = dp(12).toFloat()
            setStroke(dp(1), Color.rgb(220, 224, 230))
        }

        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = backgroundDrawable
            elevation = dp(8).toFloat()
        }

        panel.addView(TextView(context).apply {
            text = "Mobby controls"
            textSize = 18f
            setTextColor(Color.rgb(16, 32, 68))
        })

        panelMessage = TextView(context).apply {
            text = "Say a command, or ask what is on this screen."
            textSize = 14f
            setTextColor(Color.rgb(74, 84, 110))
            setPadding(0, dp(4), 0, dp(6))
        }
        panel.addView(panelMessage)

        val controls = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        voiceButton = overlayButton("Speak").apply {
            setOnClickListener { callbacks.onSpeakPressed() }
        }
        controls.addView(voiceButton)
        controls.addView(overlayButton("Guide screen").apply {
            setOnClickListener { callbacks.onGuidePressed() }
        })
        controls.addView(overlayButton("Close").apply {
            setOnClickListener {
                callbacks.onClosePressed()
                remove()
            }
        })
        panel.addView(controls)

        val dm = context.resources.displayMetrics
        val cardWidth = dp(340).coerceAtMost(dm.widthPixels - dp(32))
        val params = overlayLayoutParams(cardWidth).apply {
            x = (dm.widthPixels - cardWidth) / 2
            y = dm.heightPixels - dp(180)
        }
        makeDraggable(panel)

        try {
            windowManager.addView(panel, params)
            quickPanel = panel
            Log.d(TAG, "show: panel added")
        } catch (e: WindowManager.BadTokenException) {
            // Service may have been disconnected between the call and the addView.
            Log.w(TAG, "show: BadTokenException, service likely disconnected", e)
            quickPanel = null
        }
    }

    /** Removes the quick panel from the screen. */
    fun remove() {
        quickPanel?.let {
            try {
                windowManager.removeView(it)
            } catch (_: IllegalArgumentException) {
                // View was already detached.
            }
        }
        quickPanel = null
        panelMessage = null
        voiceButton = null
        Log.d(TAG, "remove: panel removed")
    }

    /** Updates the message text shown in the panel, or shows a Toast if the panel isn't visible. */
    fun setMessage(message: String) {
        val view = panelMessage
        if (view != null) view.text = message else toast(message)
    }

    /** Enables or disables the voice button. */
    fun setVoiceButtonEnabled(enabled: Boolean) {
        voiceButton?.isEnabled = enabled
    }

    /**
     * Shows a confirmation dialog in place of the quick panel.
     * The [onConfirm] callback runs off the main thread and its result is posted back
     * to the overlay UI, preventing ANRs from slow accessibility tree traversals or IPC.
     */
    fun showConfirmation(
        question: String,
        onConfirm: () -> OperationResult
    ) {
        showConfirmation(question, onConfirm, null)
    }

    fun showConfirmation(
        question: String,
        onConfirm: () -> OperationResult,
        onCancel: (() -> Unit)?
    ) {
        if (quickPanel == null) {
            toast(question)
            return
        }
        remove()

        val backgroundDrawable = android.graphics.drawable.GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = dp(12).toFloat()
            setStroke(dp(1), Color.rgb(220, 224, 230))
        }

        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = backgroundDrawable
            elevation = dp(8).toFloat()
        }

        panel.addView(TextView(context).apply {
            text = question
            textSize = 16f
            setTextColor(Color.rgb(16, 32, 68))
        })

        val buttons = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        buttons.addView(overlayButton("Cancel").apply {
            setOnClickListener {
                Log.d(TAG, "showConfirmation: cancelled")
                onCancel?.invoke()
                remove()
                show()
            }
        })

        val confirmButton = overlayButton("Confirm")
        confirmButton.setOnClickListener {
            Log.d(TAG, "showConfirmation: confirmed, running off-thread")
            confirmButton.isEnabled = false
            setMessage("Working\u2026")
            scope.launch {
                val result = withContext(Dispatchers.Default) { onConfirm() }
                remove()
                show()
                setMessage(result.message)
            }
        }
        buttons.addView(confirmButton)
        panel.addView(buttons)

        val dm = context.resources.displayMetrics
        val cardWidth = dp(340).coerceAtMost(dm.widthPixels - dp(32))
        val params = overlayLayoutParams(cardWidth).apply {
            x = (dm.widthPixels - cardWidth) / 2
            y = dm.heightPixels - dp(180)
        }
        makeDraggable(panel)

        try {
            windowManager.addView(panel, params)
            quickPanel = panel
            Log.d(TAG, "showConfirmation: dialog shown")
        } catch (e: WindowManager.BadTokenException) {
            Log.w(TAG, "showConfirmation: BadTokenException", e)
            quickPanel = null
        }
    }

    /** Releases all resources. Call from the service's onDestroy. */
    fun destroy() {
        scope.cancel()
        remove()
        Log.d(TAG, "destroy: overlay destroyed")
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun overlayButton(label: String): Button =
        Button(context).apply {
            isAllCaps = false
            text = label
            textSize = 13f
        }

    private fun overlayLayoutParams(width: Int): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

    private fun makeDraggable(view: View) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        view.setOnTouchListener { v, event ->
            val layoutParams = v.layoutParams as? WindowManager.LayoutParams ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - initialTouchX).toInt()
                    val deltaY = (event.rawY - initialTouchY).toInt()
                    layoutParams.x = initialX + deltaX
                    layoutParams.y = initialY + deltaY
                    try {
                        windowManager.updateViewLayout(v, layoutParams)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to update drag layout", e)
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun dp(value: Int): Int =
        Math.round(value * context.resources.displayMetrics.density)

    private fun toast(text: String) {
        Toast.makeText(context, text, Toast.LENGTH_LONG).show()
    }

    /**
     * Callbacks from the overlay panel to the orchestrating service.
     * This keeps OverlayManager free of any speech or command logic.
     */
    interface Callbacks {
        /** Called when the user taps the "Speak" button. */
        fun onSpeakPressed()

        /** Called when the user taps the "Guide screen" button. */
        fun onGuidePressed()

        /** Called when the user taps the "Close" button. */
        fun onClosePressed()
    }

    companion object {
        private const val TAG = "MobbyOverlay"
    }
}
