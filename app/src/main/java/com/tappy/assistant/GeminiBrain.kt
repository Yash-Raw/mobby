package com.tappy.assistant

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.net.HttpURLConnection
import java.net.URL

class GeminiBrain(private val context: Context) {

    companion object {
        private const val TAG = "MobbyGemini"
        private const val MODEL_NAME = "gemini-2.5-flash"
    }

    private val sharedPrefs = context.getSharedPreferences("mobby_prefs", Context.MODE_PRIVATE)

    val apiKey: String
        get() = sharedPrefs.getString("gemini_api_key", "") ?: ""

    fun isEnabled(): Boolean = apiKey.isNotEmpty()

    suspend fun getNextAction(
        session: GeminiSession,
        screenContents: String
    ): GeminiAction = withContext(Dispatchers.IO) {
        val key = apiKey
        if (key.isEmpty()) {
            return@withContext GeminiAction.say("Please configure your Gemini API key in Mobby app setup first.")
        }

        // Add the current screen contents as user input to the session history
        val userTurn = JSONObject().apply {
            put("role", "user")
            val parts = JSONArray().apply {
                put(JSONObject().apply {
                    put("text", "Current Screen State:\n$screenContents")
                })
            }
            put("parts", parts)
        }
        session.addTurn(userTurn)

        try {
            val url = URL("https://generativelanguage.googleapis.com/v1/models/$MODEL_NAME:generateContent?key=$key")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true

            val systemInstructionText = """
                You are the AI brain of Mobby, a privacy-respecting Android accessibility assistant.
                Your task is to help the user complete their request: "${session.userRequest}".
                Based on the user request and the current screen contents, you must decide the next single action to take.
                
                You must reply ONLY with a JSON object matching this schema:
                {
                  "thought": "Brief explanation of your reasoning (e.g. why you chose this action)",
                  "action": "TAP" | "TYPE" | "SCROLL" | "BACK" | "HOME" | "SAY",
                  "target": "For TAP: the exact label of the control to tap. For SCROLL: 'up' or 'down'",
                  "text": "For TYPE: the text to enter into the focused/editable field. For SAY: the final message to display/speak to the user when the task is complete or if you need clarification.",
                  "requires_confirmation": true | false
                }
                
                Rules:
                1. Only perform one action at a time.
                2. If you need to type text (TYPE), first ensure that the correct text field is tapped/focused in a previous step, or tap it now.
                3. If the user request is finished (e.g. email typed and send button tapped) or if you cannot proceed, return the "SAY" action with a final descriptive message in "text".
                4. If you need to generate content (like an email body or text reply), generate it yourself and use the TYPE action to enter it.
                5. Be precise with control names from the Screen Controls list.
                6. Crucial: Set "requires_confirmation" to true ONLY when you are about to perform the final critical action (like tapping a "Send", "Confirm", "Delete", or "Submit" button) that completes or irreversibly alters the user's task. For all intermediate steps (taps, typing text, scrolling), set "requires_confirmation" to false so they run automatically without asking the user.
            """.trimIndent()

            val requestBody = JSONObject().apply {
                val contentsArray = JSONArray()
                // Include chat history
                session.history.forEach { contentsArray.put(it) }
                put("contents", contentsArray)

                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", systemInstructionText) })
                    })
                })

                put("generationConfig", JSONObject().apply {
                    put("responseMimeType", "application/json")
                })
            }

            Log.d(TAG, "Request payload: ${requestBody.toString(2)}")

            BufferedOutputStream(conn.outputStream).use { os ->
                os.write(requestBody.toString().toByteArray())
                os.flush()
            }

            val responseCode = conn.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                Log.d(TAG, "Response payload: $responseText")
                val responseJson = JSONObject(responseText)
                
                val textCandidate = responseJson
                    .getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")

                // Add the model's reply to history
                val modelTurn = JSONObject().apply {
                    put("role", "model")
                    val parts = JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", textCandidate)
                        })
                    }
                    put("parts", parts)
                }
                session.addTurn(modelTurn)

                val cleanedJsonString = extractJsonString(textCandidate)
                val actionJson = JSONObject(cleanedJsonString)
                val action = actionJson.optString("action", "SAY")
                val thought = actionJson.optString("thought", "")
                val target = actionJson.optString("target", "")
                val text = actionJson.optString("text", "")
                val requiresConfirmation = actionJson.optBoolean("requires_confirmation", false)

                return@withContext GeminiAction(thought, action, target, text, requiresConfirmation)
            } else {
                val errorText = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
                Log.e(TAG, "HTTP error $responseCode: $errorText")
                return@withContext GeminiAction.say("Error calling Gemini API ($responseCode): $errorText")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception calling Gemini API", e)
            return@withContext GeminiAction.say("Error connecting to Gemini API: ${e.localizedMessage}")
        }
    }

    private fun extractJsonString(input: String): String {
        var cleaned = input.trim()
        if (cleaned.startsWith("```")) {
            val firstLineEnd = cleaned.indexOf('\n')
            cleaned = if (firstLineEnd != -1) {
                cleaned.substring(firstLineEnd).trim()
            } else {
                cleaned.substring(3).trim()
            }
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length - 3).trim()
        }
        return cleaned
    }
}

class GeminiSession(val userRequest: String) {
    val history = mutableListOf<JSONObject>()

    fun addTurn(turn: JSONObject) {
        history.add(turn)
        // Keep only the last 4 turns (8 messages maximum) to minimize token consumption and latency.
        while (history.size > 8) {
            history.removeAt(0)
            if (history.isNotEmpty()) {
                history.removeAt(0)
            }
        }
    }
}

data class GeminiAction(
    val thought: String,
    val action: String, // "TAP" | "TYPE" | "SCROLL" | "BACK" | "HOME" | "SAY"
    val target: String,
    val text: String,
    val requiresConfirmation: Boolean
) {
    companion object {
        fun say(message: String) = GeminiAction("Error/Fallback", "SAY", "", message, true)
    }
}
