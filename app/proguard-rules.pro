# ── Mobby ProGuard / R8 Rules ─────────────────────────────────────────

# Keep accessibility service — Android instantiates it by class name from the manifest.
-keep class com.tappy.assistant.TappyAccessibilityService { *; }

# Keep notification listener — Android instantiates it by class name from the manifest.
-keep class com.tappy.assistant.TappyNotificationListener { *; }
-keep class com.tappy.assistant.TappyNotificationListener$MessageSnapshot { *; }
-keep class com.tappy.assistant.TappyNotificationListener$ReplyResult { *; }
-keep class com.tappy.assistant.TappyNotificationListener$ReplyResult$Sent { *; }
-keep class com.tappy.assistant.TappyNotificationListener$ReplyResult$Failed { *; }

# Keep the main activity — launcher entry point.
-keep class com.tappy.assistant.MainActivity { *; }

# Keep OperationResult data class used across module boundaries.
-keep class com.tappy.assistant.OperationResult { *; }

# Keep CommandParser types used in when-expressions and companion factories.
-keep class com.tappy.assistant.CommandParser$Type { *; }
-keep class com.tappy.assistant.CommandParser$AgentCommand { *; }

# Keep WorkManager worker and scheduled automation task model for reflection & JSON.
-keep class com.tappy.assistant.MobbyWorker { *; }
-keep class com.tappy.assistant.ScheduledTask { *; }

# Keep Gemini AI brain models for JSON parsing.
-keep class com.tappy.assistant.GeminiAction { *; }
-keep class com.tappy.assistant.GeminiSession { *; }
