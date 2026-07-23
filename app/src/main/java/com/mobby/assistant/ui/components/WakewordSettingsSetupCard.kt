package com.mobby.assistant.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobby.assistant.ui.theme.MobbyMuted
import com.mobby.assistant.ui.theme.MobbyNavy
import com.mobby.assistant.ui.theme.MobbySuccess

@Composable
fun WakewordSettingsSetupCard(
    wakewordEnabled: Boolean,
    onToggle: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "🎙️ Wakeword Detection (“Hey Mobby”)", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MobbyNavy)
            Text(
                text = "When enabled, Mobby listens in the background for the “Mobby” trigger word and shows an active notification. Keep disabled to save battery.",
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
                    text = if (wakewordEnabled) "Wakeword: Enabled (Listening in background)" else "Wakeword: Disabled (Saves battery)",
                    color = if (wakewordEnabled) MobbySuccess else MobbyNavy
                )
            }
        }
    }
}
