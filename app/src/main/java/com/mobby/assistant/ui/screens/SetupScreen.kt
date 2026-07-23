package com.mobby.assistant.ui.screens

import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobby.assistant.ScheduledTask
import com.mobby.assistant.ui.components.ConfirmationSettingsSetupCard
import com.mobby.assistant.ui.components.GeminiKeySetupCard
import com.mobby.assistant.ui.components.InstructionSetupCard
import com.mobby.assistant.ui.components.PermissionSetupCard
import com.mobby.assistant.ui.components.ScheduledAutomationsSetupCard
import com.mobby.assistant.ui.components.WakewordSettingsSetupCard
import com.mobby.assistant.ui.theme.MobbyBg
import com.mobby.assistant.ui.theme.MobbyBlue
import com.mobby.assistant.ui.theme.MobbyMuted
import com.mobby.assistant.ui.theme.MobbyNavy
import com.mobby.assistant.ui.theme.MobbyTheme

@Composable
fun MobbySetupScreen(
    micEnabled: Boolean,
    accessibilityEnabled: Boolean,
    notificationEnabled: Boolean,
    geminiApiKey: String,
    requireConfirmation: Boolean,
    wakewordEnabled: Boolean,
    scheduledTasks: List<ScheduledTask>,
    onAllowMic: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenNotification: () -> Unit,
    onSaveGeminiKey: (String) -> Unit,
    onToggleConfirmation: () -> Unit,
    onToggleWakeword: () -> Unit,
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
                description = "Lets Mobby hear a command when you choose Speak. Mobby does not use an always-on microphone unless Wakeword is explicitly enabled below.",
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

            // Wakeword Settings Card
            WakewordSettingsSetupCard(
                wakewordEnabled = wakewordEnabled,
                onToggle = onToggleWakeword
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
            wakewordEnabled = false,
            scheduledTasks = listOf(
                ScheduledTask("1", "Check WhatsApp messages", "INTERVAL", intervalMinutes = 30)
            ),
            onAllowMic = {},
            onOpenAccessibility = {},
            onOpenNotification = {},
            onSaveGeminiKey = {},
            onToggleConfirmation = {},
            onToggleWakeword = {},
            onAddTask = {},
            onDeleteTask = {},
            onStartAssistant = {}
        )
    }
}
