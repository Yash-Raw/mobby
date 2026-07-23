package com.mobby.assistant.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobby.assistant.ScheduledTask
import com.mobby.assistant.ui.theme.MobbyBlue
import com.mobby.assistant.ui.theme.MobbyError
import com.mobby.assistant.ui.theme.MobbyMuted
import com.mobby.assistant.ui.theme.MobbyNavy

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
