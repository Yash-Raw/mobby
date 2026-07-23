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
