package com.mobby.assistant

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import java.util.Locale

/**
 * Listens continuously in the background for the voice trigger word ("Mobby" or "Moby")
 * only when enabled by the user in settings.
 *
 * Shows a persistent status notification when active for user transparency and
 * applies a backoff delay to prevent continuous battery drain during engine retries.
 */
class WakewordDetector(
    private val context: Context,
    private val onWakewordDetected: () -> Unit
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    private val sharedPrefs = context.getSharedPreferences("mobby_prefs", Context.MODE_PRIVATE)

    fun isEnabledInSettings(): Boolean {
        return sharedPrefs.getBoolean("wakeword_enabled", false)
    }

    /** Starts continuous background listening for the wakeword if enabled in settings. */
    fun startListening() {
        if (!isEnabledInSettings()) {
            Log.d(TAG, "startListening: wakeword detection is disabled in settings")
            stopListening()
            return
        }
        if (isListening) return
        isListening = true
        Log.d(TAG, "startListening: starting background wakeword detector")
        showWakewordNotification()

        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        mainHandler.post {
            try {
                if (speechRecognizer == null) {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                        setRecognitionListener(object : RecognitionListener {
                            override fun onReadyForSpeech(params: Bundle?) {}
                            override fun onBeginningOfSpeech() {}
                            override fun onRmsChanged(rmsdB: Float) {}
                            override fun onBufferReceived(buffer: ByteArray?) {}
                            override fun onEndOfSpeech() {}

                            override fun onError(error: Int) {
                                Log.d(TAG, "onError: code=$error (wakeword listener)")
                                // Restart listening with backoff delay on speech timeouts / boundaries
                                if (isListening) {
                                    speechRecognizer?.destroy()
                                    speechRecognizer = null
                                    isListening = false
                                    mainHandler.postDelayed({
                                        if (isEnabledInSettings()) {
                                            startListening()
                                        }
                                    }, RESTART_BACKOFF_MS)
                                }
                            }

                            override fun onResults(results: Bundle?) {
                                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                if (!matches.isNullOrEmpty()) {
                                    for (match in matches) {
                                        val text = match.lowercase(Locale.ROOT)
                                        Log.d(TAG, "onResults: heard \"$text\"")
                                        if (text.contains("mobby") || text.contains("moby")) {
                                            Log.i(TAG, "Wakeword DETECTED via results!")
                                            onWakewordDetected()
                                            break
                                        }
                                    }
                                }
                                // Restart listening loop with backoff delay
                                if (isListening) {
                                    speechRecognizer?.destroy()
                                    speechRecognizer = null
                                    isListening = false
                                    mainHandler.postDelayed({
                                        if (isEnabledInSettings()) {
                                            startListening()
                                        }
                                    }, RESTART_BACKOFF_MS)
                                }
                            }

                            override fun onPartialResults(partialResults: Bundle?) {
                                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                if (!matches.isNullOrEmpty()) {
                                    for (match in matches) {
                                        val text = match.lowercase(Locale.ROOT)
                                        if (text.contains("mobby") || text.contains("moby")) {
                                            Log.i(TAG, "Wakeword DETECTED via partial results!")
                                            onWakewordDetected()
                                            break
                                        }
                                    }
                                }
                            }

                            override fun onEvent(eventType: Int, params: Bundle?) {}
                        })
                    }
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1000L)
                }
                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error starting wakeword SpeechRecognizer", e)
                isListening = false
                removeWakewordNotification()
            }
        }
    }

    /** Stops background listening and releases microphone/resources. */
    fun stopListening() {
        removeWakewordNotification()
        if (!isListening) return
        isListening = false
        Log.d(TAG, "stopListening: stopping background wakeword detector")
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.destroy()
            } catch (e: Exception) {
                Log.w(TAG, "Error destroying wakeword recognizer", e)
            }
            speechRecognizer = null
        }
    }

    private fun showWakewordNotification() {
        val channelId = "mobby_wakeword"
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Wakeword Detector",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when Mobby is actively listening for the 'Hey Mobby' voice trigger"
            }
            nm.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle("Mobby Wakeword Active")
            .setContentText("Listening for “Mobby” trigger word...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        try {
            nm.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post wakeword notification", e)
        }
    }

    private fun removeWakewordNotification() {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            nm.cancel(NOTIFICATION_ID)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cancel wakeword notification", e)
        }
    }

    companion object {
        private const val TAG = "MobbyWakeword"
        private const val NOTIFICATION_ID = 2001
        private const val RESTART_BACKOFF_MS = 1500L
    }
}

