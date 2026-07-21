package com.mobby.assistant

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Holds active notifications in memory only. It never sends notification content to a server.
 * A reply is delivered exclusively through the messaging app's own RemoteInput action.
 */
class MobbyNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        instance = this
        val count = activeNotifications?.size ?: 0
        activeNotifications?.forEach { cache(it) }
        Log.i(TAG, "onListenerConnected: cached $count active notifications")
    }

    override fun onListenerDisconnected() {
        Log.i(TAG, "onListenerDisconnected: clearing cache")
        instance = null
        ACTIVE_MESSAGES.clear()
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn?.let {
            cache(it)
            Log.d(TAG, "onNotificationPosted: key=${it.key}, pkg=${it.packageName}")
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn?.let {
            ACTIVE_MESSAGES.remove(it.key)
            Log.d(TAG, "onNotificationRemoved: key=${it.key}")
        }
    }

    data class MessageSnapshot(
        val key: String,
        val packageName: String,
        val sender: String,
        val text: String,
        val postTime: Long
    )

    sealed class ReplyResult {
        data class Sent(val unit: Unit = Unit) : ReplyResult()
        data class Failed(val error: String) : ReplyResult()

        val sent: Boolean get() = this is Sent
        val errorMessage: String
            get() = when (this) {
                is Sent -> ""
                is Failed -> error
            }
    }

    companion object {
        private const val TAG = "MobbyNotifications"
        private val ACTIVE_MESSAGES = ConcurrentHashMap<String, MessageSnapshot>()

        @Volatile
        private var instance: MobbyNotificationListener? = null

        private fun cache(sbn: StatusBarNotification) {
            val notification = sbn.notification ?: return
            val extras = notification.extras ?: Bundle.EMPTY
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)
            var text = extras.getCharSequence(Notification.EXTRA_TEXT)
            if (text.isNullOrEmpty() && notification.tickerText != null) {
                text = notification.tickerText
            }
            ACTIVE_MESSAGES[sbn.key] = MessageSnapshot(
                key = sbn.key,
                packageName = sbn.packageName,
                sender = title?.toString() ?: "",
                text = text?.toString() ?: "",
                postTime = sbn.postTime
            )
        }

        fun findLatestMessageFrom(sender: String?): MessageSnapshot? {
            if (sender.isNullOrBlank()) return null

            instance?.activeNotifications?.forEach { cache(it) }

            val needle = sender.lowercase(Locale.ROOT)
            val result = ACTIVE_MESSAGES.values
                .filter { message ->
                    message.sender.lowercase(Locale.ROOT).contains(needle) ||
                            message.text.lowercase(Locale.ROOT).contains(needle)
                }
                .sortedByDescending { it.postTime }
                .firstOrNull()
            Log.d(TAG, "findLatestMessageFrom: sender=\"$sender\", found=${result != null}")
            return result
        }

        fun reply(notificationKey: String, message: String): ReplyResult {
            val current = instance
                ?: return ReplyResult.Failed("Message access is not connected. Re-enable it in Settings.")
            if (message.isBlank()) {
                return ReplyResult.Failed("The reply was empty.")
            }

            val target = current.activeNotifications
                ?.firstOrNull { it.key == notificationKey }
                ?: return ReplyResult.Failed("That message is no longer available to reply to.")

            val actions = target.notification.actions
                ?: return ReplyResult.Failed("This messaging app did not offer a Direct Reply action.")

            for (action in actions) {
                val inputs = action.getRemoteInputs() ?: continue
                val pendingIntent = action.actionIntent ?: continue
                if (inputs.isEmpty()) continue

                return try {
                    val replyIntent = Intent()
                    val results = Bundle()
                    for (input in inputs) {
                        results.putCharSequence(input.resultKey, message.trim())
                    }
                    RemoteInput.addResultsToIntent(inputs, replyIntent, results)
                    pendingIntent.send(current, 0, replyIntent)
                    Log.d(TAG, "reply: sent via Direct Reply for key=$notificationKey")
                    ReplyResult.Sent()
                } catch (e: PendingIntent.CanceledException) {
                    Log.w(TAG, "reply: Direct Reply action was cancelled", e)
                    ReplyResult.Failed("The messaging app cancelled its Direct Reply action.")
                }
            }
            Log.w(TAG, "reply: no Direct Reply action found for key=$notificationKey")
            return ReplyResult.Failed("This notification cannot be replied to from Mobby.")
        }
    }
}
