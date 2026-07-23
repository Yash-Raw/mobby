package com.mobby.assistant.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val MobbyNavy = Color(0xFF141E37)
val MobbyBlue = Color(0xFF2E6BFF)
val MobbyBg = Color(0xFFF4F6F9)
val MobbyMuted = Color(0xFF646E78)
val MobbySuccess = Color(0xFF2E7D32)
val MobbyError = Color(0xFFD32F2F)

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
