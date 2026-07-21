# ── Mobby ProGuard / R8 Rules ─────────────────────────────────────────

# Keep accessibility service — Android instantiates it by class name from the manifest.
-keep class com.mobby.assistant.MobbyAccessibilityService { *; }

# Keep notification listener — Android instantiates it by class name from the manifest.
-keep class com.mobby.assistant.MobbyNotificationListener { *; }
-keep class com.mobby.assistant.MobbyNotificationListener$MessageSnapshot { *; }
-keep class com.mobby.assistant.MobbyNotificationListener$ReplyResult { *; }
-keep class com.mobby.assistant.MobbyNotificationListener$ReplyResult$Sent { *; }
-keep class com.mobby.assistant.MobbyNotificationListener$ReplyResult$Failed { *; }

# Keep the main activity — launcher entry point.
-keep class com.mobby.assistant.MainActivity { *; }

# Keep OperationResult data class used across module boundaries.
-keep class com.mobby.assistant.OperationResult { *; }

# Keep CommandParser types used in when-expressions and companion factories.
-keep class com.mobby.assistant.CommandParser$Type { *; }
-keep class com.mobby.assistant.CommandParser$AgentCommand { *; }

# Keep WorkManager worker and scheduled automation task model for reflection & JSON.
-keep class com.mobby.assistant.MobbyWorker { *; }
-keep class com.mobby.assistant.ScheduledTask { *; }

# Keep Gemini AI brain models for JSON parsing.
-keep class com.mobby.assistant.GeminiAction { *; }
-keep class com.mobby.assistant.GeminiSession { *; }
