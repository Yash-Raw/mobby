package com.mobby.assistant

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Data representation of a background scheduled automation.
 */
data class ScheduledTask(
    val id: String,
    val prompt: String,
    val type: String, // "INTERVAL" | "DAILY"
    val intervalMinutes: Int = 0,
    val hour: Int = 0,
    val minute: Int = 0
)

/**
 * Handles scheduling, canceling, and persistent serialization of background automation tasks.
 */
object TaskScheduler {
    private const val TAG = "MobbyScheduler"
    private const val PREFS_NAME = "mobby_scheduler_prefs"
    private const val KEY_TASKS = "scheduled_tasks"

    private fun getSharedPreferences(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Loads and returns all persistently stored scheduled tasks. */
    fun getTasks(context: Context): List<ScheduledTask> {
        val prefs = getSharedPreferences(context)
        val jsonString = prefs.getString(KEY_TASKS, "[]") ?: "[]"
        return try {
            val jsonArray = JSONArray(jsonString)
            val list = mutableListOf<ScheduledTask>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    ScheduledTask(
                        id = obj.getString("id"),
                        prompt = obj.getString("prompt"),
                        type = obj.getString("type"),
                        intervalMinutes = obj.optInt("intervalMinutes", 0),
                        hour = obj.optInt("hour", 0),
                        minute = obj.optInt("minute", 0)
                    )
                )
            }
            list
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing tasks JSON", e)
            emptyList()
        }
    }

    /** Serializes and saves a list of tasks persistently. */
    fun saveTasks(context: Context, tasks: List<ScheduledTask>) {
        val prefs = getSharedPreferences(context)
        val jsonArray = JSONArray()
        for (task in tasks) {
            val obj = JSONObject().apply {
                put("id", task.id)
                put("prompt", task.prompt)
                put("type", task.type)
                put("intervalMinutes", task.intervalMinutes)
                put("hour", task.hour)
                put("minute", task.minute)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_TASKS, jsonArray.toString()).apply()
    }

    /** Adds a new task to the schedule and registers it with WorkManager. */
    fun addTask(context: Context, task: ScheduledTask) {
        val currentTasks = getTasks(context).toMutableList()
        currentTasks.add(task)
        saveTasks(context, currentTasks)

        val workManager = WorkManager.getInstance(context)
        val data = Data.Builder()
            .putString("prompt", task.prompt)
            .build()

        val workRequest = if (task.type == "INTERVAL") {
            // Android WorkManager requires a minimum interval of 15 minutes
            val minutes = task.intervalMinutes.coerceAtLeast(15)
            PeriodicWorkRequestBuilder<MobbyWorker>(minutes.toLong(), TimeUnit.MINUTES)
                .setInputData(data)
                .build()
        } else {
            // Daily trigger: compute initial delay to target hour & minute
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, task.hour)
                set(Calendar.MINUTE, task.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (target.before(now)) {
                target.add(Calendar.DAY_OF_YEAR, 1)
            }
            val delayMs = target.timeInMillis - now.timeInMillis

            // Run daily task periodically every 24 hours with calculated initial delay
            PeriodicWorkRequestBuilder<MobbyWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .setInputData(data)
                .build()
        }

        workManager.enqueueUniquePeriodicWork(
            task.id,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
        Log.i(TAG, "Scheduled task ${task.id} with WorkManager")
    }

    /** Cancels a task with WorkManager and removes it from persistent storage. */
    fun cancelTask(context: Context, taskId: String) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(taskId)

        val currentTasks = getTasks(context).toMutableList()
        currentTasks.removeAll { it.id == taskId }
        saveTasks(context, currentTasks)
        Log.i(TAG, "Cancelled and removed task $taskId")
    }
}
