package com.mobby.assistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale

/**
 * Listens continuously in the background for the voice trigger word ("Mobby" or "Moby").
 * When heard, triggers the [onWakewordDetected] callback.
 *
 * It uses the phone's native [SpeechRecognizer] to avoid external library size overhead.
 * Handles automatic restart loops when the speech engine times out or reaches boundaries.
 */
class WakewordDetector(
    private val context: Context,
    private val onWakewordDetected: () -> Unit
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    /** Starts continuous background listening for the wakeword. */
    fun startListening() {
        if (isListening) return
        isListening = true
        Log.d(TAG, "startListening: starting background wakeword detector")

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
                                // Restart listening on typical speech timeouts / boundaries
                                if (isListening) {
                                    speechRecognizer?.destroy()
                                    speechRecognizer = null
                                    isListening = false
                                    startListening()
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
                                // Restart listening loop
                                if (isListening) {
                                    speechRecognizer?.destroy()
                                    speechRecognizer = null
                                    isListening = false
                                    startListening()
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
            }
        }
    }

    /** Stops background listening and releases microphone/resources. */
    fun stopListening() {
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

    companion object {
        private const val TAG = "MobbyWakeword"
    }
}
