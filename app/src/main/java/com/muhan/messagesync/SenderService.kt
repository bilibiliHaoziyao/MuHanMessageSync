package com.muhan.messagesync

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.LinkedHashMap
import java.util.Locale
import java.util.concurrent.Executors

/**
 * 服务端：监听本机所有通知，通过 SMTP 邮件转发到接收端。
 * 支持多设备：收件人可配置多个，同一邮件会送达所有接收端。
 */
class SenderService : NotificationListenerService() {

    private val executor = Executors.newSingleThreadExecutor()

    /** LRU 去重表，防止同一通知重复发送 */
    private val recentKeys = object : LinkedHashMap<String, Long>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean {
            return size > 256
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "服务端已连接，开始监听通知")
        notifyStatus("服务端运行中，正在监听通知…")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val s = SettingsStore.load(this)
        if (s.mode != MODE_SENDER || !s.smtpValid) return
        // 跳过自己产生的通知
        if (sbn.packageName == packageName) return

        val n = sbn.notification ?: return
        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: ""
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString())?.trim() ?: ""

        // 空通知、进度条类通知跳过
        if (title.isBlank() && text.isBlank()) return
        if (extras.containsKey(Notification.EXTRA_PROGRESS) &&
            extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0) > 0 &&
            title.contains("%")
        ) return

        val key = sbn.key ?: return
        synchronized(recentKeys) {
            if (recentKeys.containsKey(key)) return
            recentKeys[key] = System.currentTimeMillis()
        }

        val appName = try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(sbn.packageName, 0)
            ).toString()
        } catch (e: Exception) {
            sbn.packageName
        }

        val payload = MailPayload(
            device = s.deviceName,
            app = appName,
            pkg = sbn.packageName,
            title = title,
            text = text,
            time = sbn.postTime
        )

        val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date(sbn.postTime))
        val subject = "${MailPayload.SUBJECT_PREFIX}${s.deviceName} · $appName"
        val body = payload.toJson().toString(2)

        executor.submit {
            try {
                SmtpSender.send(s, subject, body)
                notifyStatus("已转发: $appName · $title ($timeStr)")
            } catch (e: Exception) {
                Log.e(TAG, "发送邮件失败", e)
                notifyStatus("发送失败: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    private fun notifyStatus(text: String) {
        NotifyHelper.postStatus(this, CHANNEL_SENDER, "服务端状态", text)
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }

    companion object {
        private const val TAG = "SenderService"
        const val MODE_SENDER = "sender"
    }
}
