package com.tappy.assistant

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Manages Mobby's text-to-speech feedback and voice-based confirmations.
 * Displays an elegant, unobtrusive floating circular bubble on screen when active
 * to serve as the visual window context required by Android to perform microphone access.
 */
class OverlayManager(
    private val context: Context,
    private val windowManager: WindowManager,
    private val callbacks: Callbacks
) : TextToSpeech.OnInitListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var isQuickPanelActive = false
    private var quickPanel: View? = null

    val isShowing: Boolean get() = isQuickPanelActive

    class PendingConfirmation(
        val question: String,
        val onConfirm: suspend () -> OperationResult,
        val onCancel: (() -> Unit)?
    )

    private var pendingConfirmation: PendingConfirmation? = null

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.getDefault())
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "onInit: TTS language not supported")
            } else {
                isTtsReady = true
                Log.d(TAG, "onInit: TTS initialized successfully")
                
                tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        Log.d(TAG, "TTS onStart: $utteranceId")
                    }

                    override fun onDone(utteranceId: String?) {
                        Log.d(TAG, "TTS onDone: $utteranceId")
                        if ((utteranceId == "mobby_utterance" || utteranceId == "mobby_confirmation") && isQuickPanelActive) {
                            scope.launch {
                                callbacks.onSpeakFinished()
                            }
                        }
                    }

                    override fun onError(utteranceId: String?) {
                        Log.w(TAG, "TTS onError: $utteranceId")
                    }
                })
            }
        } else {
            Log.e(TAG, "onInit: TTS initialization failed")
        }
    }

    fun speak(text: String, utteranceId: String = "mobby_utterance") {
        if (isTtsReady) {
            Log.d(TAG, "speak: \"$text\" (id: $utteranceId)")
            val params = android.os.Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            }
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        } else {
            Log.w(TAG, "speak: TTS not ready yet, message: \"$text\"")
        }
    }

    /** Toggles the voice-control state. */
    fun toggle() {
        if (!isQuickPanelActive) show() else remove()
    }

    /** Starts the voice control session, shows a minimal status bubble, and greets the user. */
    fun show() {
        if (isQuickPanelActive) return
        isQuickPanelActive = true

        val bubble = ImageView(context).apply {
            setImageResource(R.drawable.ic_mobby)
            setPadding(dp(12), dp(12), dp(12), dp(12))

            val backgroundDrawable = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.argb(230, 255, 255, 255)) // Translucent white (glassmorphism)
                cornerRadius = dp(28).toFloat()
                setStroke(dp(2), Color.rgb(46, 107, 255)) // Blue border
            }
            background = backgroundDrawable
            elevation = dp(8).toFloat()

            setOnClickListener {
                callbacks.onSpeakPressed()
            }
        }

        val dm = context.resources.displayMetrics
        val size = dp(56)
        val params = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dm.widthPixels - size - dp(16)
            y = dm.heightPixels / 2
        }

        makeDraggable(bubble)

        try {
            windowManager.addView(bubble, params)
            quickPanel = bubble
            Log.d(TAG, "show: floating bubble added")
        } catch (e: WindowManager.BadTokenException) {
            Log.w(TAG, "show: BadTokenException", e)
            quickPanel = null
        }

        speak("Mobby is ready.")
    }

    /** Stops the voice control session and removes the bubble. */
    fun remove() {
        isQuickPanelActive = false
        pendingConfirmation = null
        quickPanel?.let {
            try {
                windowManager.removeView(it)
            } catch (_: IllegalArgumentException) {}
        }
        quickPanel = null
        callbacks.onClosePressed()
    }

    /** Speaks the message and shows a Toast. */
    fun setMessage(message: String) {
        toast(message)
        speak(message)
    }

    /** Unused in voice-only mode, but kept for compatibility. */
    fun setVoiceButtonEnabled(enabled: Boolean) {
        // No visual buttons to enable/disable
    }

    /** Shows a voice-based confirmation dialog. */
    fun showConfirmation(
        question: String,
        onConfirm: suspend () -> OperationResult
    ) {
        showConfirmation(question, onConfirm, null)
    }

    /** Shows a voice-based confirmation dialog with custom cancel handling. */
    fun showConfirmation(
        question: String,
        onConfirm: suspend () -> OperationResult,
        onCancel: (() -> Unit)?
    ) {
        speak(question, "mobby_confirmation")
        pendingConfirmation = PendingConfirmation(question, onConfirm, onCancel)
    }

    /**
     * Handles voice confirmation input.
     * Returns true if it processed a confirmation/cancellation response, false otherwise.
     */
    fun handleVoiceConfirmation(transcript: String): Boolean {
        val pending = pendingConfirmation ?: return false
        val cleanTranscript = transcript.trim().lowercase()

        val confirmWords = listOf("yes", "confirm", "ok", "sure", "yep", "do it", "yeah", "yup", "correct")
        val cancelWords = listOf("no", "cancel", "stop", "nope", "dont", "don't", "never mind", "nevermind")

        val words = cleanTranscript.split(Regex("\\s+"))
        val isConfirm = confirmWords.any { words.contains(it) }
        val isCancel = cancelWords.any { words.contains(it) }

        if (isConfirm) {
            pendingConfirmation = null
            setMessage("Working\u2026")
            scope.launch {
                val result = pending.onConfirm()
                setMessage(result.message)
            }
            return true
        } else if (isCancel) {
            pendingConfirmation = null
            setMessage("Action cancelled.")
            pending.onCancel?.invoke()
            return true
        } else {
            // Did not understand confirmation
            speak("Please say yes to confirm, or no to cancel.")
            scope.launch {
                delay(2500)
                callbacks.onSpeakPressed()
            }
            return true
        }
    }

    /** Releases all resources. Call from the service's onDestroy. */
    fun destroy() {
        scope.cancel()
        remove()
        tts?.stop()
        tts?.shutdown()
        Log.d(TAG, "destroy: overlay destroyed")
    }

    private fun toast(text: String) {
        Toast.makeText(context, text, Toast.LENGTH_LONG).show()
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
                MotionEvent.ACTION_UP -> {
                    val deltaX = Math.abs(event.rawX - initialTouchX)
                    val deltaY = Math.abs(event.rawY - initialTouchY)
                    if (deltaX < 5 && deltaY < 5) {
                        v.performClick()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun dp(value: Int): Int =
        Math.round(value * context.resources.displayMetrics.density)

    /**
     * Callbacks from the overlay panel to the orchestrating service.
     */
    interface Callbacks {
        fun onSpeakPressed()
        fun onGuidePressed()
        fun onClosePressed()
        fun onSpeakFinished()
    }

    companion object {
        private const val TAG = "MobbyOverlay"
    }
}
