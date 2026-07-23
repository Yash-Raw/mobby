package com.mobby.assistant.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobby.assistant.ui.theme.MobbyBlue
import com.mobby.assistant.ui.theme.MobbyError
import com.mobby.assistant.ui.theme.MobbyMuted
import com.mobby.assistant.ui.theme.MobbyNavy
import com.mobby.assistant.ui.theme.MobbySuccess

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
