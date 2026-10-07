package com.muhan.messagesync

import android.app.Notification
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
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
        if (sbn.packageName == packageName) return   // 跳过自身

        // 系统应用过滤
        if (s.skipSystemApps && IconUtil.isSystemApp(this, sbn.packageName)) return

        val n = sbn.notification ?: return
        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: ""
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString())?.trim() ?: ""

        // 无文字通知过滤
        if (title.isBlank() && text.isBlank()) {
            if (s.skipEmptyText) return
        }

        val appName = try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(sbn.packageName, 0)
            ).toString()
        } catch (e: Exception) {
            sbn.packageName
        }

        // 白名单 / 黑名单
        if (s.forwardMode == "whitelist" && !matchFilter(s.appFilter, appName, sbn.packageName)) return
        if (s.forwardMode == "blacklist" && matchFilter(s.appFilter, appName, sbn.packageName)) return

        val key = sbn.key ?: return
        synchronized(recentKeys) {
            if (recentKeys.containsKey(key)) return
            recentKeys[key] = System.currentTimeMillis()
        }

        // 应用图标 -> base64，随邮件传输（接收端跨设备也能看到图标）
        val iconB64: String? = IconUtil.appIconBitmap(this, sbn.packageName)?.let { bmp ->
            IconUtil.bitmapToBase64(bmp)
        } ?: IconUtil.iconToBitmap(n.smallIcon)?.let { IconUtil.bitmapToBase64(it) }

        val payload = MailPayload(
            device = s.deviceName,
            app = appName,
            pkg = sbn.packageName,
            title = title,
            text = text,
            time = sbn.postTime,
            icon = iconB64
        )

        val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date(sbn.postTime))
        val subject = "${MailPayload.SUBJECT_PREFIX}${s.deviceName} · $appName"
        val body = payload.toJson().toString(2)

        executor.submit {
            try {
                SmtpSender.send(s, subject, body)
                notifyStatus("已转发: $appName · $title ($timeStr)")
                HistoryStore.add(
                    this,
                    HistoryStore.Item(
                        id = HistoryStore.newId(),
                        direction = "send",
                        device = s.deviceName,
                        app = appName,
                        pkg = sbn.packageName,
                        title = title,
                        text = text,
                        time = sbn.postTime,
                        icon = iconB64
                    ),
                    s.historyLimit
                )
            } catch (e: Exception) {
                Log.e(TAG, "发送邮件失败", e)
                notifyStatus("发送失败: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    /** 匹配应用名单（包名或应用名，忽略大小写包含） */
    private fun matchFilter(filter: List<String>, appName: String, pkg: String): Boolean {
        if (filter.isEmpty()) return false
        val a = appName.lowercase(Locale.CHINA)
        val p = pkg.lowercase(Locale.CHINA)
        return filter.any { it.lowercase(Locale.CHINA).let { kw -> a.contains(kw) || p.contains(kw) } }
    }

    private fun notifyStatus(text: String) {
        NotifyHelper.postStatus(this, NotifyHelper.CHANNEL_STATUS, "服务端状态", text)
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
