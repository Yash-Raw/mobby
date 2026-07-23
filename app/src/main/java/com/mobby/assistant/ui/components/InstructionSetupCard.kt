package com.mobby.assistant.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobby.assistant.ui.theme.MobbyBlue
import com.mobby.assistant.ui.theme.MobbyMuted
import com.mobby.assistant.ui.theme.MobbyNavy

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
