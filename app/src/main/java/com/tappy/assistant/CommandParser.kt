package com.tappy.assistant

import android.util.Log
import java.util.Locale

/** A compact command grammar for the actions that Mobby can perform on a user's device. */
object CommandParser {

    private const val TAG = "MobbyParser"

    private val REPLY_TO_PERSON = Regex(
        """\breply\s+to\s+([\p{L}][\p{L}'-]*)(?:\s+(?:that|with|saying)\s+|\s*:\s*)(.+)$""",
        RegexOption.IGNORE_CASE
    )
    private val REPLY_IN_CURRENT_CONVERSATION = Regex(
        """\breply\s+(?:that|with|saying)\s+(.+)$""",
        RegexOption.IGNORE_CASE
    )
    private val CHECK_AND_REPLY = Regex(
        """\b(?:text|texts|message|messages).*?\bfrom\s+([\p{L}][\p{L}'-]*).*?\breply\s+to\s+(?:him|her|them)\s+(?:that|with|saying)\s+(.+)$""",
        RegexOption.IGNORE_CASE
    )
    private val CHECK_MESSAGES = Regex(
        """\b(?:check|find|show)(?:\s+if)?(?:\s+i)?(?:\s+have|\s+got)?(?:\s+any|\s+recent)?\s*(?:text|texts|message|messages).*?\bfrom\s+([\p{L}][\p{L}'-]*)""",
        RegexOption.IGNORE_CASE
    )
    private val TAP = Regex(
        """\b(?:tap|click|press|open)\s+(.+)$""",
        RegexOption.IGNORE_CASE
    )
    private val TYPE = Regex(
        """\b(?:type|write|enter)\s+(.+)$""",
        RegexOption.IGNORE_CASE
    )

    fun parse(transcript: String?): AgentCommand {
        if (transcript == null) return AgentCommand.unsupported()
        val cleaned = transcript.trim().replace(Regex("\\s+"), " ")
        val lower = cleaned.lowercase(Locale.ROOT)
        if (cleaned.isEmpty()) return AgentCommand.unsupported()

        if (lower.containsAny(
                "what's on screen", "what is on screen", "read the screen",
                "read screen", "describe the screen", "describe screen",
                "tell me what's on screen", "tell me what is on screen", "where am i"
            )
        ) return logAndReturn(AgentCommand.of(Type.DESCRIBE_SCREEN))

        if (lower.containsAny(
                "how do i use this", "guide me", "help me use this app",
                "what can i do here", "explain this screen"
            )
        ) return logAndReturn(AgentCommand.of(Type.GUIDE_SCREEN))

        if (lower.containsAny(
                "what can i tap", "list controls", "show controls",
                "what buttons are here"
            )
        ) return logAndReturn(AgentCommand.of(Type.LIST_CONTROLS))

        if (lower == "back" || lower == "go back") return logAndReturn(AgentCommand.of(Type.BACK))
        if (lower == "home" || lower == "go home") return logAndReturn(AgentCommand.of(Type.HOME))
        if (lower == "scroll down" || lower == "scroll up") {
            return logAndReturn(AgentCommand.withTarget(Type.SCROLL, if (lower.endsWith("down")) "down" else "up"))
        }
        if (lower == "send" || lower == "send message" || lower == "tap send") {
            return logAndReturn(AgentCommand.of(Type.SEND_CURRENT_MESSAGE))
        }

        CHECK_AND_REPLY.find(cleaned)?.let { match ->
            return logAndReturn(AgentCommand.withTargetAndText(
                Type.REPLY_TO_PERSON,
                match.groupValues[1],
                stripTerminalPunctuation(match.groupValues[2])
            ))
        }
        REPLY_TO_PERSON.find(cleaned)?.let { match ->
            return logAndReturn(AgentCommand.withTargetAndText(
                Type.REPLY_TO_PERSON,
                match.groupValues[1],
                stripTerminalPunctuation(match.groupValues[2])
            ))
        }
        CHECK_MESSAGES.find(cleaned)?.let { match ->
            return logAndReturn(AgentCommand.withTarget(Type.CHECK_MESSAGES_FROM, match.groupValues[1]))
        }
        REPLY_IN_CURRENT_CONVERSATION.find(cleaned)?.let { match ->
            return logAndReturn(AgentCommand.withText(
                Type.REPLY_IN_CURRENT_CONVERSATION,
                stripTerminalPunctuation(match.groupValues[1])
            ))
        }
        TYPE.find(cleaned)?.let { match ->
            return logAndReturn(AgentCommand.withText(Type.TYPE_TEXT, stripTerminalPunctuation(match.groupValues[1])))
        }
        TAP.find(cleaned)?.let { match ->
            return logAndReturn(AgentCommand.withTarget(Type.TAP, stripTerminalPunctuation(match.groupValues[1])))
        }

        Log.d(TAG, "parse: unsupported transcript=\"$cleaned\"")
        return AgentCommand(Type.UNSUPPORTED, "", cleaned)
    }

    private fun logAndReturn(command: AgentCommand): AgentCommand {
        Log.d(TAG, "parse: type=${command.type}, target=\"${command.target}\", text=\"${command.text}\"")
        return command
    }

    private fun String.containsAny(vararg options: String): Boolean =
        options.any { this.contains(it) }

    private fun stripTerminalPunctuation(value: String?): String =
        value?.trim()?.replace(Regex("[.!?]+$"), "") ?: ""

    enum class Type {
        DESCRIBE_SCREEN,
        GUIDE_SCREEN,
        LIST_CONTROLS,
        TAP,
        TYPE_TEXT,
        SCROLL,
        BACK,
        HOME,
        CHECK_MESSAGES_FROM,
        REPLY_TO_PERSON,
        REPLY_IN_CURRENT_CONVERSATION,
        SEND_CURRENT_MESSAGE,
        UNSUPPORTED
    }

    data class AgentCommand(
        val type: Type,
        val target: String,
        val text: String
    ) {
        companion object {
            fun of(type: Type) = AgentCommand(type, "", "")
            fun withTarget(type: Type, target: String) = AgentCommand(type, target, "")
            fun withText(type: Type, text: String) = AgentCommand(type, "", text)
            fun withTargetAndText(type: Type, target: String, text: String) = AgentCommand(type, target, text)
            fun unsupported() = of(Type.UNSUPPORTED)
        }
    }
}
