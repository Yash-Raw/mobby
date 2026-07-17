package com.tappy.assistant

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Button
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
 * The visual overlay control panel has been removed to make Mobby completely voice-based.
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

    val isShowing: Boolean get() = isQuickPanelActive

    class PendingConfirmation(
        val question: String,
        val onConfirm: () -> OperationResult,
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
            }
        } else {
            Log.e(TAG, "onInit: TTS initialization failed")
        }
    }

    fun speak(text: String) {
        if (isTtsReady) {
            Log.d(TAG, "speak: \"$text\"")
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        } else {
            Log.w(TAG, "speak: TTS not ready yet, message: \"$text\"")
        }
    }

    /** Toggles the voice-control state. */
    fun toggle() {
        if (!isQuickPanelActive) show() else remove()
    }

    /** Starts the voice control session and greets the user. */
    fun show() {
        if (isQuickPanelActive) return
        isQuickPanelActive = true
        speak("Mobby is ready. Say a command.")
        scope.launch {
            delay(1500)
            callbacks.onSpeakPressed()
        }
    }

    /** Stops the voice control session. */
    fun remove() {
        isQuickPanelActive = false
        pendingConfirmation = null
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
        onConfirm: () -> OperationResult
    ) {
        showConfirmation(question, onConfirm, null)
    }

    /** Shows a voice-based confirmation dialog with custom cancel handling. */
    fun showConfirmation(
        question: String,
        onConfirm: () -> OperationResult,
        onCancel: (() -> Unit)?
    ) {
        speak(question)
        pendingConfirmation = PendingConfirmation(question, onConfirm, onCancel)
        scope.launch {
            delay(2000)
            callbacks.onSpeakPressed()
        }
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
                val result = withContext(Dispatchers.Default) { pending.onConfirm() }
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

    /**
     * Callbacks from the overlay panel to the orchestrating service.
     */
    interface Callbacks {
        fun onSpeakPressed()
        fun onGuidePressed()
        fun onClosePressed()
    }

    companion object {
        private const val TAG = "MobbyOverlay"
    }
}
