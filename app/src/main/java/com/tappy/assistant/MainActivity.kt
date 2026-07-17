package com.tappy.assistant

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Setup screen for Mobby's opt-in, cross-app accessibility controls. */
class MainActivity : Activity() {

    private lateinit var microphoneStatus: TextView
    private lateinit var notificationStatus: TextView
    private lateinit var accessibilityStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(createContent())
        Log.i(TAG, "onCreate: setup screen created")
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStates()
    }

    private fun createContent(): View {
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(28), dp(24), dp(32))
            setBackgroundColor(getColor(R.color.mobby_bg))
        }
        scroll.addView(page)

        page.addView(text("Mobby", 36f, getColor(R.color.mobby_navy)).apply {
            setTypeface(null, 1)
        })

        page.addView(text(
            "Your voice-controlled guide for any app on your phone.",
            17f, getColor(R.color.mobby_muted)
        ).apply {
            setPadding(0, dp(6), 0, dp(18))
        })

        page.addView(
            text(
                "You decide what Mobby can access. It reads screen text only when you ask, " +
                        "keeps it on this device, and asks for confirmation before it taps, types, or sends anything.",
                14f, getColor(R.color.mobby_navy)
            ).apply {
                setPadding(dp(16), dp(14), dp(16), dp(14))
                setBackgroundColor(Color.WHITE)
            },
            fullWidth()
        )

        addSpacing(page, 16)

        microphoneStatus = addPermissionCard(
            page,
            "1. Microphone",
            "Lets Mobby hear a command when you choose Speak. Mobby does not use an always-on microphone.",
            "Allow microphone"
        ) {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                toast("Microphone permission is already enabled.")
            } else {
                requestMicrophone()
            }
        }

        accessibilityStatus = addPermissionCard(
            page,
            "2. Device controls",
            "Android's Accessibility service lets Mobby read visible text and controls, then perform the action you explicitly confirm. Android requires you to switch this on in Settings.",
            "Open Accessibility"
        ) { openAccessibilitySettings() }

        notificationStatus = addPermissionCard(
            page,
            "3. Message access (optional)",
            "Lets Mobby check active notifications and send a reply through a messaging app's own Direct Reply action. This works across apps that support Android Direct Reply.",
            "Open message access"
        ) { openNotificationSettings() }

        addGeminiKeyCard(page)

        addInstructionCard(
            page,
            "⚡ Quick Shortcut (Volume Keys)",
            "You can quickly summon or stop Mobby from any screen by pressing and holding both Volume keys for 3 seconds.\n\nTo enable this, go to Settings ➔ Volume key shortcut ➔ select Mobby.",
            "Configure Shortcut"
        ) { openAccessibilitySettings() }

        addScheduledAutomationsCard(page)

        addSpacing(page, 4)

        page.addView(Button(this).apply {
            isAllCaps = false
            text = "Open Mobby controls over other apps"
            textSize = 18f
            setTextColor(Color.WHITE)
            setBackgroundColor(getColor(R.color.mobby_blue))
            setOnClickListener { openDeviceControls() }
        }, fullWidth())

        page.addView(text(
            "Examples: \u201CWhat\u2019s on screen?\u201D, \u201CGuide me\u201D, \u201CTap Search\u201D, " +
                    "\u201CType hello\u201D, \u201CScroll down\u201D, \u201CReply that I\u2019m running late\u201D, " +
                    "or \u201CReply to Maya that I\u2019ll call later.\u201D",
            14f, getColor(R.color.mobby_muted)
        ).apply {
            setPadding(0, dp(12), 0, 0)
        })

        return scroll
    }

    private fun addPermissionCard(
        page: LinearLayout,
        title: String,
        description: String,
        buttonText: String,
        listener: View.OnClickListener
    ): TextView {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(14))
            setBackgroundColor(Color.WHITE)
        }

        card.addView(text(title, 18f, getColor(R.color.mobby_navy)).apply {
            setTypeface(null, 1)
        })

        card.addView(text(description, 14f, getColor(R.color.mobby_muted)).apply {
            setPadding(0, dp(6), 0, dp(6))
        })

        val status = text("Not enabled", 13f, getColor(R.color.mobby_muted))
        card.addView(status)

        card.addView(Button(this).apply {
            isAllCaps = false
            text = buttonText
            setOnClickListener(listener)
        })

        page.addView(card, fullWidth())
        addSpacing(page, 12)
        return status
    }

    private fun addInstructionCard(
        page: LinearLayout,
        title: String,
        description: String,
        buttonText: String,
        listener: View.OnClickListener
    ) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(14))
            setBackgroundColor(Color.WHITE)
        }

        card.addView(text(title, 18f, getColor(R.color.mobby_navy)).apply {
            setTypeface(null, 1)
        })

        card.addView(text(description, 14f, getColor(R.color.mobby_muted)).apply {
            setPadding(0, dp(6), 0, dp(6))
        })

        card.addView(Button(this).apply {
            isAllCaps = false
            text = buttonText
            setOnClickListener(listener)
        })

        page.addView(card, fullWidth())
        addSpacing(page, 12)
    }

    private fun addScheduledAutomationsCard(page: LinearLayout) {
        val context = this
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(14))
            setBackgroundColor(Color.WHITE)
        }

        card.addView(text("⏰ Scheduled Automations", 18f, getColor(R.color.mobby_navy)).apply {
            setTypeface(null, Typeface.BOLD)
        })

        card.addView(text("Set automated tasks to execute in the background.", 14f, getColor(R.color.mobby_muted)).apply {
            setPadding(0, dp(4), 0, dp(8))
        })

        val listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        card.addView(listContainer)

        val promptInput = EditText(this).apply {
            hint = "Command (e.g., Check my emails)"
            textSize = 14f
        }
        card.addView(promptInput)

        val paramInput = EditText(this).apply {
            hint = "Interval in minutes (min 15)"
            textSize = 14f
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        card.addView(paramInput)

        var triggerMode = "INTERVAL"

        val modeButton = Button(this).apply {
            isAllCaps = false
            text = "Mode: Repeat Every X Minutes"
            textSize = 12f
            setOnClickListener {
                if (triggerMode == "INTERVAL") {
                    triggerMode = "DAILY"
                    text = "Mode: Run Daily at Time"
                    paramInput.hint = "Time of day (e.g., 08:30)"
                    paramInput.text.clear()
                    paramInput.inputType = android.text.InputType.TYPE_CLASS_TEXT
                } else {
                    triggerMode = "INTERVAL"
                    text = "Mode: Repeat Every X Minutes"
                    paramInput.hint = "Interval in minutes (min 15)"
                    paramInput.text.clear()
                    paramInput.inputType = android.text.InputType.TYPE_CLASS_NUMBER
                }
            }
        }
        card.addView(modeButton)

        fun refreshTaskList() {
            listContainer.removeAllViews()
            val tasks = TaskScheduler.getTasks(context)
            if (tasks.isEmpty()) {
                listContainer.addView(text("No active scheduled tasks.", 13f, getColor(R.color.mobby_muted)).apply {
                    setPadding(0, dp(4), 0, dp(8))
                })
            } else {
                for (task in tasks) {
                    val taskRow = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(0, dp(4), 0, dp(4))
                    }
                    val triggerDesc = if (task.type == "INTERVAL") "every ${task.intervalMinutes}m" else "daily at ${String.format("%02d:%02d", task.hour, task.minute)}"

                    val infoText = TextView(context).apply {
                        text = "• \"${task.prompt}\" ($triggerDesc)"
                        textSize = 13f
                        setTextColor(getColor(R.color.mobby_navy))
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    }
                    taskRow.addView(infoText)

                    val deleteBtn = Button(context).apply {
                        isAllCaps = false
                        text = "Delete"
                        textSize = 11f
                        setPadding(dp(6), 0, dp(6), 0)
                        setOnClickListener {
                            TaskScheduler.cancelTask(context, task.id)
                            refreshTaskList()
                            Toast.makeText(context, "Task cancelled", Toast.LENGTH_SHORT).show()
                        }
                    }
                    taskRow.addView(deleteBtn)
                    listContainer.addView(taskRow)
                }
            }
        }

        refreshTaskList()

        val addBtn = Button(this).apply {
            isAllCaps = false
            text = "Schedule Automation"
            setOnClickListener {
                val prompt = promptInput.text.toString().trim()
                val param = paramInput.text.toString().trim()

                if (prompt.isEmpty() || param.isEmpty()) {
                    Toast.makeText(context, "Please enter both prompt and trigger parameters", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val taskId = "mobby_task_${System.currentTimeMillis()}"
                val task = if (triggerMode == "INTERVAL") {
                    val minutes = param.toIntOrNull() ?: 60
                    ScheduledTask(id = taskId, prompt = prompt, type = "INTERVAL", intervalMinutes = minutes)
                } else {
                    val parts = param.split(":")
                    if (parts.size != 2) {
                        Toast.makeText(context, "Please use HH:mm format (e.g., 08:30)", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    val hour = parts[0].toIntOrNull() ?: 8
                    val minute = parts[1].toIntOrNull() ?: 0
                    ScheduledTask(id = taskId, prompt = prompt, type = "DAILY", hour = hour, minute = minute)
                }

                TaskScheduler.addTask(context, task)
                promptInput.text.clear()
                paramInput.text.clear()
                refreshTaskList()
                Toast.makeText(context, "Task scheduled successfully", Toast.LENGTH_SHORT).show()
            }
        }
        card.addView(addBtn)

        page.addView(card, fullWidth())
        addSpacing(page, 12)
    }

    private fun addGeminiKeyCard(page: LinearLayout) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(14))
            setBackgroundColor(Color.WHITE)
        }

        card.addView(text("Gemini Brain (Optional)", 18f, getColor(R.color.mobby_navy)).apply {
            setTypeface(null, 1)
        })

        card.addView(text("Enter your Gemini API Key to enable advanced AI actions like writing emails and complex tasks.", 14f, getColor(R.color.mobby_muted)).apply {
            setPadding(0, dp(6), 0, dp(6))
        })

        val sharedPreferences = getSharedPreferences("mobby_prefs", MODE_PRIVATE)
        val savedKey = sharedPreferences.getString("gemini_api_key", "") ?: ""

        val input = EditText(this).apply {
            hint = "AIzaSy..."
            setText(savedKey)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        card.addView(input)

        card.addView(Button(this).apply {
            isAllCaps = false
            text = "Save API Key"
            setOnClickListener {
                val key = input.text.toString().trim()
                sharedPreferences.edit().putString("gemini_api_key", key).apply()
                Toast.makeText(this@MainActivity, if (key.isEmpty()) "API Key cleared" else "API Key saved", Toast.LENGTH_SHORT).show()
            }
        })

        page.addView(card, fullWidth())
        addSpacing(page, 12)
    }

    private fun openDeviceControls() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestMicrophone()
            return
        }
        if (!isAccessibilityAccessEnabled()) {
            toast("Enable Mobby controls in Android Accessibility Settings first.")
            openAccessibilitySettings()
            return
        }
        if (!TappyAccessibilityService.openQuickPanel()) {
            toast("Android is still connecting Mobby controls. Please try again in a moment.")
            return
        }
        Log.i(TAG, "openDeviceControls: overlay opened, moving to background")
        moveTaskToBack(true)
    }

    private fun requestMicrophone() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "requestMicrophone: requesting RECORD_AUDIO permission")
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MICROPHONE)
        }
    }

    private fun openNotificationSettings() {
        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        updatePermissionStates()
        if (requestCode == REQUEST_MICROPHONE && grantResults.isNotEmpty()
            && grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            Log.i(TAG, "onRequestPermissionsResult: microphone granted")
            toast("Microphone enabled. Tap Open Mobby controls when you are ready.")
        }
    }

    private fun updatePermissionStates() {
        if (!::microphoneStatus.isInitialized) return
        val mic = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val notification = isNotificationAccessEnabled()
        val accessibility = isAccessibilityAccessEnabled()
        setStatus(microphoneStatus, mic)
        setStatus(notificationStatus, notification)
        setStatus(accessibilityStatus, accessibility)
        Log.i(TAG, "updatePermissionStates: mic=$mic, accessibility=$accessibility, notification=$notification")
    }

    private fun setStatus(view: TextView, enabled: Boolean) {
        view.text = if (enabled) "Enabled" else "Not enabled"
        view.setTextColor(getColor(if (enabled) R.color.mobby_success else R.color.mobby_muted))
    }

    private fun isNotificationAccessEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return isComponentEnabled(enabled, ComponentName(this, TappyNotificationListener::class.java))
    }

    private fun isAccessibilityAccessEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return isComponentEnabled(enabled, ComponentName(this, TappyAccessibilityService::class.java))
    }

    private fun isComponentEnabled(setting: String?, target: ComponentName): Boolean {
        if (setting.isNullOrEmpty()) return false
        return setting.split(":").any { target == ComponentName.unflattenFromString(it) }
    }

    private fun text(value: String, size: Float, color: Int): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            setLineSpacing(0f, 1.1f)
        }

    private fun fullWidth(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

    private fun addSpacing(parent: LinearLayout, height: Int) {
        parent.addView(View(this), LinearLayout.LayoutParams(1, dp(height)))
    }

    private fun dp(value: Int): Int =
        Math.round(value * resources.displayMetrics.density)

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    companion object {
        private const val TAG = "MobbyMain"
        private const val REQUEST_MICROPHONE = 101
    }
}
