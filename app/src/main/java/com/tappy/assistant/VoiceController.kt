package com.tappy.assistant

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale

/**
 * Manages the Android SpeechRecognizer lifecycle: creation, listening, result
 * delivery, and cleanup.
 *
 * Speech results are delivered through the [Listener] callback. The controller
 * does not know about commands, overlays, or accessibility — it only converts
 * audio into a transcript string.
 */
class VoiceController(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null

    /**
     * Starts listening for a single voice command.
     * Returns an error message if preconditions aren't met, or null on success.
     */
    fun startListening(listener: Listener): String? {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "startListening: microphone permission not granted")
            return "Open Mobby once and allow microphone access before using voice controls."
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.w(TAG, "startListening: speech recognition unavailable")
            return "Voice recognition is unavailable on this phone."
        }
        if (speechRecognizer == null) {
            Log.d(TAG, "startListening: creating new SpeechRecognizer")
            speechRecognizer = createRecognizer(listener)
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            // Configure silence thresholds and timeouts to prevent early audio cutoffs.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 3000L) // Wait at least 3 seconds
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L) // Wait 2 seconds after user stops speaking
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
        }
        speechRecognizer?.startListening(intent)
        Log.d(TAG, "startListening: recognizer started")
        return null // Success — no error.
    }

    /** Releases the SpeechRecognizer. Call from the service's onDestroy. */
    fun destroy() {
        try {
            speechRecognizer?.destroy()
            Log.d(TAG, "destroy: SpeechRecognizer released")
        } catch (_: Exception) {
            // SpeechRecognizer.destroy() can throw if already disconnected.
            Log.w(TAG, "destroy: SpeechRecognizer was already disconnected")
        }
        speechRecognizer = null
    }

    private fun createRecognizer(listener: Listener): SpeechRecognizer {
        val googleServiceComponent = ComponentName.unflattenFromString(
            "com.google.android.googlequicksearchbox/com.google.android.voicesearch.serviceapi.GoogleRecognitionService"
        )
        return try {
            if (googleServiceComponent != null && isServiceAvailable(googleServiceComponent)) {
                Log.d(TAG, "createRecognizer: binding to Google Speech Service")
                SpeechRecognizer.createSpeechRecognizer(context, googleServiceComponent)
            } else {
                Log.d(TAG, "createRecognizer: Google Speech Service not found, using system default")
                SpeechRecognizer.createSpeechRecognizer(context)
            }
        } catch (e: Exception) {
            Log.w(TAG, "createRecognizer: failed to bind to Google service, using system default", e)
            SpeechRecognizer.createSpeechRecognizer(context)
        }.apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d(TAG, "onReadyForSpeech")
                }
                override fun onBeginningOfSpeech() {
                    Log.d(TAG, "onBeginningOfSpeech")
                }
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    Log.d(TAG, "onEndOfSpeech")
                }

                override fun onError(error: Int) {
                    val message = describeError(error)
                    Log.w(TAG, "onError: code=$error, message=$message")
                    listener.onVoiceError(message)
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (matches.isNullOrEmpty()) {
                        Log.w(TAG, "onResults: no matches returned")
                        listener.onVoiceError("I didn't catch a command.")
                    } else {
                        Log.d(TAG, "onResults: \"${matches[0]}\"")
                        listener.onVoiceResult(matches[0])
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    /**
     * Callback interface for speech recognition events.
     */
    interface Listener {
        /** Called with the best transcript when recognition succeeds. */
        fun onVoiceResult(transcript: String)

        /** Called with a user-facing error message when recognition fails. */
        fun onVoiceError(errorMessage: String)
    }

    companion object {
        private const val TAG = "MobbyVoice"

        /** Maps SpeechRecognizer error codes to user-friendly messages. */
        private fun describeError(error: Int): String = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "There was a microphone error. Please try again."
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Voice recognition needs a network connection."
            SpeechRecognizer.ERROR_NO_MATCH -> "I didn't catch a command. Please try again."
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "I didn't hear anything. Tap Speak and say a command."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is missing. Open Mobby to grant it."
            SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "Too many voice requests. Please wait a moment."
            SpeechRecognizer.ERROR_SERVER -> "The speech recognition server had an error. Please try again."
            SpeechRecognizer.ERROR_CLIENT -> "I couldn't hear that. Please try again."
            else -> "I couldn't hear that. Please try again."
        }
    }

    private fun isServiceAvailable(component: ComponentName): Boolean {
        return try {
            val pm = context.packageManager
            val services = pm.queryIntentServices(
                Intent("android.speech.RecognitionService"),
                PackageManager.MATCH_ALL
            )
            services.any { it.serviceInfo.packageName == component.packageName && it.serviceInfo.name == component.className }
        } catch (_: Exception) {
            false
        }
    }
}
