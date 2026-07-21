package com.mobby.assistant

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── Color Palette ────────────────────────────────────────────────────────
private val MobbyNavy = Color(0xFF141E37)
private val MobbyBlue = Color(0xFF2E6BFF)
private val MobbyBg = Color(0xFFF4F6F9)
private val MobbyMuted = Color(0xFF646E78)
private val MobbySuccess = Color(0xFF2E7D32)
private val MobbyError = Color(0xFFD32F2F)

/** Setup screen for Mobby's opt-in, cross-app accessibility controls, built with Jetpack Compose. */
class MainActivity : ComponentActivity() {

    private var micEnabledState = mutableStateOf(false)
    private var accessibilityEnabledState = mutableStateOf(false)
    private var notificationEnabledState = mutableStateOf(false)
    private var geminiApiKeyState = mutableStateOf("")
    private var requireConfirmationState = mutableStateOf(true)
    private var scheduledTasksState = mutableStateOf<List<ScheduledTask>>(emptyList())

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        updatePermissionStates()
        if (isGranted) {
            Log.i(TAG, "requestPermissionLauncher: microphone granted")
            toast("Microphone enabled. Tap Start Mobby Voice Assistant when ready.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "onCreate: Jetpack Compose setup screen created")
        loadPreferences()

        setContent {
            MobbyTheme {
                MobbySetupScreen(
                    micEnabled = micEnabledState.value,
                    accessibilityEnabled = accessibilityEnabledState.value,
                    notificationEnabled = notificationEnabledState.value,
                    geminiApiKey = geminiApiKeyState.value,
                    requireConfirmation = requireConfirmationState.value,
                    scheduledTasks = scheduledTasksState.value,
                    onAllowMic = { requestMicrophone() },
                    onOpenAccessibility = { openAccessibilitySettings() },
                    onOpenNotification = { openNotificationSettings() },
                    onSaveGeminiKey = { key ->
                        getSharedPreferences("mobby_prefs", MODE_PRIVATE)
                            .edit().putString("gemini_api_key", key).apply()
                        geminiApiKeyState.value = key
                        toast(if (key.isEmpty()) "API Key cleared" else "API Key saved")
                    },
                    onToggleConfirmation = {
                        val newVal = !requireConfirmationState.value
                        getSharedPreferences("mobby_prefs", MODE_PRIVATE)
                            .edit().putBoolean("require_confirmation", newVal).apply()
                        requireConfirmationState.value = newVal
                        toast(if (newVal) "Confirmation required for all actions" else "Confirmation disabled for non-critical actions")
                    },
                    onAddTask = { task ->
                        TaskScheduler.addTask(this, task)
                        refreshTasks()
                        toast("Task scheduled successfully")
                    },
                    onDeleteTask = { taskId ->
                        TaskScheduler.cancelTask(this, taskId)
                        refreshTasks()
                        toast("Task cancelled")
                    },
                    onStartAssistant = { openDeviceControls() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStates()
        refreshTasks()
    }

    private fun loadPreferences() {
        val prefs = getSharedPreferences("mobby_prefs", MODE_PRIVATE)
        geminiApiKeyState.value = prefs.getString("gemini_api_key", "") ?: ""
        requireConfirmationState.value = prefs.getBoolean("require_confirmation", true)
    }

    private fun updatePermissionStates() {
        micEnabledState.value = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        accessibilityEnabledState.value = isAccessibilityAccessEnabled()
        notificationEnabledState.value = isNotificationAccessEnabled()
        Log.i(TAG, "updatePermissionStates: mic=${micEnabledState.value}, accessibility=${accessibilityEnabledState.value}, notification=${notificationEnabledState.value}")
    }

    private fun refreshTasks() {
        scheduledTasksState.value = TaskScheduler.getTasks(this)
    }

    private fun openDeviceControls() {
        if (!micEnabledState.value) {
            requestMicrophone()
            return
        }
        if (!accessibilityEnabledState.value) {
            toast("Enable Mobby in Android Accessibility Settings first.")
            openAccessibilitySettings()
            return
        }
        if (!MobbyAccessibilityService.openQuickPanel()) {
            toast("Android is still connecting Mobby. Please try again in a moment.")
            return
        }
        Log.i(TAG, "openDeviceControls: overlay opened, moving to background")
        moveTaskToBack(true)
    }

    private fun requestMicrophone() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "requestMicrophone: requesting RECORD_AUDIO permission")
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            toast("Microphone permission is already enabled.")
        }
    }

    private fun openNotificationSettings() {
        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun isNotificationAccessEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return isComponentEnabled(enabled, ComponentName(this, MobbyNotificationListener::class.java))
    }

    private fun isAccessibilityAccessEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return isComponentEnabled(enabled, ComponentName(this, MobbyAccessibilityService::class.java))
    }

    private fun isComponentEnabled(setting: String?, target: ComponentName): Boolean {
        if (setting.isNullOrEmpty()) return false
        return setting.split(":").any { target == ComponentName.unflattenFromString(it) }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val TAG = "MobbyMain"
    }
}

// ── Jetpack Compose Theme & UI Components ───────────────────────────

@Composable
fun MobbyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            primary = MobbyBlue,
            background = MobbyBg,
            surface = Color.White
        ),
        content = content
    )
}

@Composable
fun MobbySetupScreen(
    micEnabled: Boolean,
    accessibilityEnabled: Boolean,
    notificationEnabled: Boolean,
    geminiApiKey: String,
    requireConfirmation: Boolean,
    scheduledTasks: List<ScheduledTask>,
    onAllowMic: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenNotification: () -> Unit,
    onSaveGeminiKey: (String) -> Unit,
    onToggleConfirmation: () -> Unit,
    onAddTask: (ScheduledTask) -> Unit,
    onDeleteTask: (String) -> Unit,
    onStartAssistant: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MobbyBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            // Header Title
            Text(
                text = "Mobby",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = MobbyNavy
            )
            Text(
                text = "Your voice-controlled guide for any app on your phone.",
                fontSize = 16.sp,
                color = MobbyMuted,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )

            // Privacy Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "You decide what Mobby can access. It reads screen text only when you ask, keeps it on this device, and asks for confirmation before it taps, types, or sends anything.",
                    fontSize = 14.sp,
                    color = MobbyNavy,
                    modifier = Modifier.padding(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Permission Card 1: Microphone
            PermissionSetupCard(
                title = "1. Microphone",
                description = "Lets Mobby hear a command when you choose Speak. Mobby does not use an always-on microphone.",
                buttonText = "Allow microphone",
                enabled = micEnabled,
                onButtonClick = onAllowMic
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Permission Card 2: Accessibility
            PermissionSetupCard(
                title = "2. Device controls",
                description = "Android's Accessibility service lets Mobby read visible text and controls, then perform the action you explicitly confirm. Android requires you to switch this on in Settings.",
                buttonText = "Open Accessibility",
                enabled = accessibilityEnabled,
                onButtonClick = onOpenAccessibility
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Permission Card 3: Notifications
            PermissionSetupCard(
                title = "3. Message access (optional)",
                description = "Lets Mobby check active notifications and send a reply through a messaging app's own Direct Reply action. This works across apps that support Android Direct Reply.",
                buttonText = "Open message access",
                enabled = notificationEnabled,
                onButtonClick = onOpenNotification
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Gemini Key Card
            GeminiKeySetupCard(
                savedKey = geminiApiKey,
                onSaveKey = onSaveGeminiKey
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Confirmation Settings Card
            ConfirmationSettingsSetupCard(
                requireConfirmation = requireConfirmation,
                onToggle = onToggleConfirmation
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Quick Shortcut Card
            InstructionSetupCard(
                title = "⚡ Quick Shortcut (Volume Keys)",
                description = "You can quickly summon or stop Mobby from any screen by pressing and holding both Volume keys for 3 seconds.\n\nTo enable this, go to Settings ➔ Volume key shortcut ➔ select Mobby.",
                buttonText = "Configure Shortcut",
                onButtonClick = onOpenAccessibility
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Scheduled Automations Card
            ScheduledAutomationsSetupCard(
                tasks = scheduledTasks,
                onAddTask = onAddTask,
                onDeleteTask = onDeleteTask
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Start Assistant Primary Button
            Button(
                onClick = onStartAssistant,
                colors = ButtonDefaults.buttonColors(containerColor = MobbyBlue),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    text = "Start Mobby Voice Assistant",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            // Footer Examples
            Text(
                text = "Examples: “What’s on screen?”, “Guide me”, “Tap Search”, “Type hello”, “Scroll down”, “Reply that I’m running late”, or “Reply to Maya that I’ll call later.”",
                fontSize = 13.sp,
                color = MobbyMuted,
                modifier = Modifier.padding(top = 12.dp, bottom = 24.dp)
            )
        }
    }
}

@Composable
fun PermissionSetupCard(
    title: String,
    description: String,
    buttonText: String,
    enabled: Boolean,
    onButtonClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MobbyNavy)
            Text(text = description, fontSize = 14.sp, color = MobbyMuted, modifier = Modifier.padding(vertical = 6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Text(
                    text = if (enabled) "Enabled" else "Not enabled",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled) MobbySuccess else MobbyError
                )
            }
            Button(
                onClick = onButtonClick,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (enabled) MobbyMuted else MobbyBlue)
            ) {
                Text(text = buttonText, color = Color.White)
            }
        }
    }
}

@Composable
fun GeminiKeySetupCard(
    savedKey: String,
    onSaveKey: (String) -> Unit
) {
    var keyInput by remember(savedKey) { mutableStateOf(savedKey) }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Gemini Brain (Optional)", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MobbyNavy)
            Text(
                text = "Enter your Gemini API Key to enable advanced AI actions like writing emails and complex tasks.",
                fontSize = 14.sp,
                color = MobbyMuted,
                modifier = Modifier.padding(vertical = 6.dp)
            )
            OutlinedTextField(
                value = keyInput,
                onValueChange = { keyInput = it },
                label = { Text("API Key (AIzaSy...)") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )
            Button(
                onClick = { onSaveKey(keyInput.trim()) },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MobbyBlue)
            ) {
                Text(text = "Save API Key", color = Color.White)
            }
        }
    }
}

@Composable
fun ConfirmationSettingsSetupCard(
    requireConfirmation: Boolean,
    onToggle: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Confirmation Settings", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MobbyNavy)
            Text(
                text = "By default, Mobby asks for confirmation before performing any action. Turn this off to execute non-critical actions automatically.",
                fontSize = 14.sp,
                color = MobbyMuted,
                modifier = Modifier.padding(vertical = 6.dp)
            )
            OutlinedButton(
                onClick = onToggle,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (requireConfirmation) "Confirmation: Required" else "Confirmation: Disabled (Auto-execute)",
                    color = if (requireConfirmation) MobbyNavy else MobbySuccess
                )
            }
        }
    }
}

@Composable
fun InstructionSetupCard(
    title: String,
    description: String,
    buttonText: String,
    onButtonClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MobbyNavy)
            Text(text = description, fontSize = 14.sp, color = MobbyMuted, modifier = Modifier.padding(vertical = 6.dp))
            Button(
                onClick = onButtonClick,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MobbyBlue)
            ) {
                Text(text = buttonText, color = Color.White)
            }
        }
    }
}

@Composable
fun ScheduledAutomationsSetupCard(
    tasks: List<ScheduledTask>,
    onAddTask: (ScheduledTask) -> Unit,
    onDeleteTask: (String) -> Unit
) {
    var promptInput by remember { mutableStateOf("") }
    var paramInput by remember { mutableStateOf("") }
    var triggerMode by remember { mutableStateOf("INTERVAL") } // "INTERVAL" | "DAILY"
    val context = LocalContext.current

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "⏰ Scheduled Automations", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MobbyNavy)
            Text(
                text = "Set automated tasks to execute in the background.",
                fontSize = 14.sp,
                color = MobbyMuted,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            // Task List
            if (tasks.isEmpty()) {
                Text(text = "No active scheduled tasks.", fontSize = 13.sp, color = MobbyMuted, modifier = Modifier.padding(vertical = 8.dp))
            } else {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    for (task in tasks) {
                        val triggerDesc = if (task.type == "INTERVAL") "every ${task.intervalMinutes}m" else "daily at ${String.format("%02d:%02d", task.hour, task.minute)}"
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = "• \"${task.prompt}\" ($triggerDesc)",
                                fontSize = 13.sp,
                                color = MobbyNavy,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { onDeleteTask(task.id) }) {
                                Text(text = "Delete", fontSize = 12.sp, color = MobbyError)
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = promptInput,
                onValueChange = { promptInput = it },
                label = { Text("Command (e.g. Check my emails)") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            )

            OutlinedTextField(
                value = paramInput,
                onValueChange = { paramInput = it },
                label = { Text(if (triggerMode == "INTERVAL") "Interval in minutes (min 15)" else "Time of day (HH:mm, e.g. 08:30)") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )

            TextButton(
                onClick = {
                    triggerMode = if (triggerMode == "INTERVAL") "DAILY" else "INTERVAL"
                    paramInput = ""
                },
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Text(
                    text = if (triggerMode == "INTERVAL") "Mode: Repeat Every X Minutes (Tap to change)" else "Mode: Run Daily at Time (Tap to change)",
                    fontSize = 12.sp,
                    color = MobbyBlue
                )
            }

            Button(
                onClick = {
                    val prompt = promptInput.trim()
                    val param = paramInput.trim()
                    if (prompt.isEmpty() || param.isEmpty()) {
                        Toast.makeText(context, "Please enter both prompt and trigger parameters", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val taskId = "mobby_task_${System.currentTimeMillis()}"
                    val task = if (triggerMode == "INTERVAL") {
                        val minutes = param.toIntOrNull() ?: 60
                        ScheduledTask(id = taskId, prompt = prompt, type = "INTERVAL", intervalMinutes = minutes)
                    } else {
                        val parts = param.split(":")
                        if (parts.size != 2) {
                            Toast.makeText(context, "Please use HH:mm format (e.g., 08:30)", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val hour = parts[0].toIntOrNull() ?: 8
                        val minute = parts[1].toIntOrNull() ?: 0
                        ScheduledTask(id = taskId, prompt = prompt, type = "DAILY", hour = hour, minute = minute)
                    }
                    onAddTask(task)
                    promptInput = ""
                    paramInput = ""
                },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MobbyBlue),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Schedule Automation", color = Color.White)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MobbySetupScreenPreview() {
    MobbyTheme {
        MobbySetupScreen(
            micEnabled = true,
            accessibilityEnabled = false,
            notificationEnabled = true,
            geminiApiKey = "AIzaSy...",
            requireConfirmation = true,
            scheduledTasks = listOf(
                ScheduledTask("1", "Check WhatsApp messages", "INTERVAL", intervalMinutes = 30)
            ),
            onAllowMic = {},
            onOpenAccessibility = {},
            onOpenNotification = {},
            onSaveGeminiKey = {},
            onToggleConfirmation = {},
            onAddTask = {},
            onDeleteTask = {},
            onStartAssistant = {}
        )
    }
}
