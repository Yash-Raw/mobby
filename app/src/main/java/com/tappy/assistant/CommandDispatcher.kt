package com.tappy.assistant

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Routes parsed voice commands to the correct handler (ScreenReader, DeviceController,
 * or TappyNotificationListener) and delivers results back to the overlay.
 *
 * This class contains no Android framework references beyond logging — it operates
 * entirely through its constructor dependencies, making it straightforward to unit-test.
 */
class CommandDispatcher(
    private val screenReader: ScreenReader,
    private val deviceController: DeviceController,
    private val overlay: OverlayManager,
    private val geminiBrain: GeminiBrain
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var currentGeminiSession: GeminiSession? = null

    /** Processes a parsed command and updates the overlay with the result. */
    fun dispatch(command: CommandParser.AgentCommand) {
        Log.d(TAG, "dispatch: type=${command.type}, target=\"${command.target}\", text=\"${command.text}\"")

        // Clear any ongoing Gemini session when a new explicit command is triggered,
        // unless it's UNSUPPORTED, which might trigger/continue a session.
        if (command.type != CommandParser.Type.UNSUPPORTED) {
            currentGeminiSession = null
        }

        when (command.type) {
            CommandParser.Type.DESCRIBE_SCREEN ->
                overlay.setMessage(screenReader.describeActiveWindow(false).message)

            CommandParser.Type.GUIDE_SCREEN ->
                overlay.setMessage(screenReader.describeActiveWindow(true).message)

            CommandParser.Type.LIST_CONTROLS ->
                overlay.setMessage(screenReader.listActiveControls().message)

            CommandParser.Type.BACK ->
                overlay.setMessage(deviceController.goBack().message)

            CommandParser.Type.HOME ->
                overlay.setMessage(deviceController.goHome().message)

            CommandParser.Type.SCROLL ->
                overlay.setMessage(deviceController.scrollActiveWindow(command.target).message)

            CommandParser.Type.TAP ->
                overlay.showConfirmation("Tap \u201C${command.target}\u201D?") {
                    deviceController.tapControl(command.target)
                }

            CommandParser.Type.TYPE_TEXT,
            CommandParser.Type.REPLY_IN_CURRENT_CONVERSATION ->
                overlay.showConfirmation("Type this into the active field?\n\u201C${command.text}\u201D") {
                    deviceController.setFocusedText(command.text)
                }

            CommandParser.Type.SEND_CURRENT_MESSAGE ->
                overlay.showConfirmation("Tap the visible Send control?") {
                    deviceController.tapControl("send")
                }

            CommandParser.Type.CHECK_MESSAGES_FROM -> {
                val message = TappyNotificationListener.findLatestMessageFrom(command.target)
                overlay.setMessage(
                    if (message == null) "No active message notification from ${command.target}."
                    else "Latest message from ${message.sender}: ${safeMessageText(message)}"
                )
            }

            CommandParser.Type.REPLY_TO_PERSON ->
                replyFromNotification(command.target, command.text)

            CommandParser.Type.UNSUPPORTED -> {
                if (geminiBrain.isEnabled()) {
                    startGeminiSession(command.text)
                } else {
                    Log.d(TAG, "dispatch: unsupported command and Gemini disabled")
                    overlay.setMessage(
                        "Try \u201Cwhat\u2019s on screen\u201D, \u201Ctap Search\u201D, " +
                                "\u201Ctype hello\u201D, \u201Cscroll down\u201D, or " +
                                "\u201Creply to Maya that I\u2019ll call later\u201D."
                    )
                }
            }
        }
    }

    fun startGeminiSession(transcript: String) {
        val session = GeminiSession(transcript)
        currentGeminiSession = session
        executeGeminiNextStep(session)
    }

    fun cancelActiveSession() {
        if (currentGeminiSession != null) {
            Log.d(TAG, "cancelActiveSession: clearing current session")
            currentGeminiSession = null
            overlay.setMessage("Session cancelled.")
        }
    }

    private fun executeGeminiNextStep(session: GeminiSession) {
        // Double check session hasn't been cleared
        if (currentGeminiSession !== session) return

        val screen = screenReader.readScreen()
        if (!screen.available) {
            overlay.setMessage("Gemini error: ${screen.error}")
            currentGeminiSession = null
            return
        }

        val screenStateString = """
            App Name: ${screen.appName}
            Screen Layout:
            ${screen.layoutXml}
        """.trimIndent()

        overlay.setMessage("Thinking...")

        scope.launch {
            val action = geminiBrain.getNextAction(session, screenStateString)
            if (currentGeminiSession === session) {
                handleGeminiAction(session, action)
            }
        }
    }

    private fun handleGeminiAction(session: GeminiSession, action: GeminiAction) {
        Log.d(TAG, "handleGeminiAction: $action")

        when (action.action) {
            "SAY" -> {
                overlay.setMessage(action.text)
                currentGeminiSession = null
            }
            "TAP" -> {
                executeAction(
                    session = session,
                    action = action,
                    runAction = { deviceController.tapControl(action.target) },
                    delayMs = 1500
                )
            }
            "TYPE" -> {
                executeAction(
                    session = session,
                    action = action,
                    runAction = { deviceController.setFocusedText(action.text) },
                    delayMs = 1000
                )
            }
            "SCROLL" -> {
                val direction = action.target.ifEmpty { "down" }
                executeAction(
                    session = session,
                    action = action,
                    runAction = { deviceController.scrollActiveWindow(direction) },
                    delayMs = 1000
                )
            }
            "BACK" -> {
                executeAction(
                    session = session,
                    action = action,
                    runAction = { deviceController.goBack() },
                    delayMs = 1000
                )
            }
            "HOME" -> {
                executeAction(
                    session = session,
                    action = action,
                    runAction = { deviceController.goHome() },
                    delayMs = 1000
                )
            }
            else -> {
                overlay.setMessage("Unknown action from Gemini: ${action.action}")
                currentGeminiSession = null
            }
        }
    }

    private fun executeAction(
        session: GeminiSession,
        action: GeminiAction,
        runAction: () -> OperationResult,
        delayMs: Long
    ) {
        val needsConfirm = action.requiresConfirmation || isCriticalAction(action)
        val thoughtPrefix = if (action.thought.isNotEmpty()) "[AI: ${action.thought}]\n" else ""

        if (needsConfirm) {
            val question = when (action.action) {
                "TAP" -> "${thoughtPrefix}Tap “${action.target}”?"
                "TYPE" -> "${thoughtPrefix}Type “${action.text}”?"
                "SCROLL" -> "${thoughtPrefix}Scroll ${action.target}?"
                "BACK" -> "${thoughtPrefix}Go back?"
                "HOME" -> "${thoughtPrefix}Go home?"
                else -> "${thoughtPrefix}Perform action?"
            }
            overlay.showConfirmation(
                question = question,
                onCancel = { currentGeminiSession = null },
                onConfirm = {
                    val result = runAction()
                    if (result.successful) {
                        scope.launch {
                            delay(delayMs)
                            executeGeminiNextStep(session)
                        }
                    } else {
                        scope.launch {
                            logFailureAndResume(session, result.message)
                        }
                    }
                    result
                }
            )
        } else {
            // Auto-execute
            scope.launch {
                val actionMessage = when (action.action) {
                    "TAP" -> "Tapping “${action.target}”\u2026"
                    "TYPE" -> "Typing text\u2026"
                    "SCROLL" -> "Scrolling ${action.target}\u2026"
                    "BACK" -> "Going back\u2026"
                    "HOME" -> "Going home\u2026"
                    else -> "Working\u2026"
                }
                overlay.setMessage(actionMessage)
                val result = withContext(Dispatchers.Default) { runAction() }
                if (result.successful) {
                    delay(delayMs)
                    executeGeminiNextStep(session)
                } else {
                    logFailureAndResume(session, result.message)
                }
            }
        }
    }

    private fun isCriticalAction(action: GeminiAction): Boolean {
        if (action.action == "TAP") {
            val target = action.target.lowercase()
            return target.contains("send") || target.contains("delete") || 
                   target.contains("confirm") || target.contains("submit") || 
                   target.contains("discard")
        }
        return false
    }

    private fun logFailureAndResume(session: GeminiSession, errorMessage: String) {
        session.addTurn(JSONObject().apply {
            put("role", "user")
            put("parts", JSONArray().apply {
                put(JSONObject().apply {
                    put("text", "Execution Failed: $errorMessage")
                })
            })
        })
        executeGeminiNextStep(session)
    }

    fun destroy() {
        Log.d(TAG, "destroy: cancelling scope")
        try {
            scope.cancel()
        } catch (e: Exception) {
            Log.w(TAG, "Scope already cancelled or destroyed", e)
        }
    }

    private fun replyFromNotification(sender: String, reply: String) {
        val message = TappyNotificationListener.findLatestMessageFrom(sender)
        if (message == null) {
            Log.d(TAG, "replyFromNotification: no notification found for \"$sender\"")
            overlay.setMessage(
                "No active message notification from $sender. Open that conversation, " +
                        "then say \u201Creply that \u2026\u201D to type in the current message box."
            )
            return
        }
        overlay.showConfirmation("Reply to ${message.sender}?\n\u201C$reply\u201D") {
            val result = TappyNotificationListener.reply(message.key, reply)
            if (result.sent) {
                Log.d(TAG, "replyFromNotification: reply sent to ${message.sender}")
                OperationResult.success("Reply sent to ${message.sender}.")
            } else {
                Log.w(TAG, "replyFromNotification: reply failed — ${result.errorMessage}")
                OperationResult.failure(result.errorMessage)
            }
        }
    }

    private fun safeMessageText(message: TappyNotificationListener.MessageSnapshot): String =
        if (message.text.isEmpty()) "the messaging app hid the message content" else message.text

    companion object {
        private const val TAG = "MobbyDispatch"
    }
}
