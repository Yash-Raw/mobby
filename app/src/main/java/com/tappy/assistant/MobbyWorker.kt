package com.tappy.assistant

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A background [CoroutineWorker] executed by Android's WorkManager when a scheduled
 * automation triggers.
 *
 * If Mobby's Accessibility Service is currently active, it launches the overlay and
 * dispatches the scheduled text prompt to the AI agent.
 * If the service is disabled, it posts a system notification alerting the user.
 */
class MobbyWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prompt = inputData.getString("prompt")
        if (prompt.isNullOrBlank()) {
            Log.e(TAG, "doWork: prompt input is null or empty")
            return Result.failure()
        }

        Log.i(TAG, "doWork: triggering scheduled automation: \"$prompt\"")

        val serviceInstance = TappyAccessibilityService.instance
        if (serviceInstance != null) {
            withContext(Dispatchers.Main) {
                TappyAccessibilityService.openQuickPanel()
                // Wait briefly for the panel overlay to mount before dispatching
                kotlinx.coroutines.delay(800)
                serviceInstance.commandDispatcher?.startGeminiSession(prompt)
            }
            return Result.success()
        } else {
            Log.w(TAG, "doWork: Mobby accessibility service is inactive. Posting notification.")
            showNotification(
                title = "Scheduled Automation Ready",
                text = "Tap to open Mobby and run: \"$prompt\""
            )
            return Result.success()
        }
    }

    private fun showNotification(title: String, text: String) {
        val channelId = "mobby_automations"
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Mobby Automations",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            nm.createNotificationChannel(channel)
        }

        val intent = Intent(applicationContext, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            nm.notify(System.currentTimeMillis().toInt(), notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post notification", e)
        }
    }

    companion object {
        private const val TAG = "MobbyWorker"
    }
}
