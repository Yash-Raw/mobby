package com.mobby.assistant

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import com.mobby.assistant.ui.screens.MobbySetupScreen
import com.mobby.assistant.ui.theme.MobbyTheme

/** Setup screen for Mobby's opt-in, cross-app accessibility controls, built with Jetpack Compose. */
class MainActivity : ComponentActivity() {

    private var micEnabledState = mutableStateOf(false)
    private var accessibilityEnabledState = mutableStateOf(false)
    private var notificationEnabledState = mutableStateOf(false)
    private var geminiApiKeyState = mutableStateOf("")
    private var requireConfirmationState = mutableStateOf(true)
    private var wakewordEnabledState = mutableStateOf(false)
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
                    wakewordEnabled = wakewordEnabledState.value,
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
                    onToggleWakeword = {
                        val newVal = !wakewordEnabledState.value
                        getSharedPreferences("mobby_prefs", MODE_PRIVATE)
                            .edit().putBoolean("wakeword_enabled", newVal).apply()
                        wakewordEnabledState.value = newVal
                        toast(if (newVal) "Wakeword listener enabled" else "Wakeword listener disabled")
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
        wakewordEnabledState.value = prefs.getBoolean("wakeword_enabled", false)
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
