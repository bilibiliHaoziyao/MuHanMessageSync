package com.muhan.messagesync

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.sun.mail.imap.IMAPFolder
import com.sun.mail.imap.IdleManager
import java.util.Properties
import javax.mail.Folder
import javax.mail.Message
import javax.mail.Multipart
import javax.mail.Session
import javax.mail.event.MessageCountAdapter
import javax.mail.event.MessageCountEvent
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart
import java.util.concurrent.Executors
import kotlin.concurrent.thread

/**
 * 接收端：通过 IMAP 实时收信（优先 IDLE 长连接，不支持时退化为轮询），
 * 将服务端发来的邮件还原为系统通知。
 *
 * 多设备说明：多个接收端可共用同一邮箱，各自用本地 Message-ID 去重，
 * 不会因已读标记而互相干扰。
 */
class ReceiverService : Service() {

    @Volatile
    private var running = false

    private var worker: Thread? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        NotifyHelper.ensureChannels(this)
        startForeground(FOREGROUND_ID, NotifyHelper.buildForeground(this, "接收端正在实时监听邮箱…"))
        if (!running) {
            running = true
            worker = thread(name = "muhan-imap") { runLoop() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        worker?.interrupt()
        super.onDestroy()
    }

    private fun updateStatus(text: String) {
        mainHandler.post {
            try {
                startForeground(FOREGROUND_ID, NotifyHelper.buildForeground(this, text))
            } catch (e: Exception) {
                Log.w(TAG, "更新前台通知失败", e)
            }
        }
    }

    private fun runLoop() {
        while (running) {
            try {
                connectCycle()
            } catch (e: InterruptedException) {
                return
            } catch (e: Exception) {
                Log.e(TAG, "IMAP 连接异常", e)
                updateStatus("连接异常：${e.message ?: e.javaClass.simpleName}，10 秒后重连")
                try {
                    Thread.sleep(10_000)
                } catch (ie: InterruptedException) {
                    return
                }
            }
        }
    }

    private fun connectCycle() {
        val s = SettingsStore.load(this)
        if (s.mode != "receiver" || !s.imapValid) {
            updateStatus("请先在主界面配置 IMAP 接收设置")
            Thread.sleep(5_000)
            return
        }

        val props = Properties().apply {
            put("mail.store.protocol", if (s.imapSsl) "imaps" else "imap")
            put("mail.imaps.host", s.imapHost)
            put("mail.imaps.port", s.imapPort.toString())
            put("mail.imap.host", s.imapHost)
            put("mail.imap.port", s.imapPort.toString())
            put("mail.imaps.connectiontimeout", "15000")
            put("mail.imaps.timeout", "345000")
            put("mail.imap.connectiontimeout", "15000")
            put("mail.imap.timeout", "345000")
            put("mail.imaps.ssl.checkserveridentity", "true")
            put("mail.imap.ssl.checkserveridentity", "true")
        }

        val session = Session.getInstance(props, null)
        val store = session.getStore(if (s.imapSsl) "imaps" else "imap")
        store.connect(s.imapHost, s.imapPort, s.imapUser, s.imapPassword)

        val folder = store.getFolder(s.imapFolder) as IMAPFolder
        folder.open(Folder.READ_WRITE)

        val processed = MessageDedup(this)
        val listener = object : MessageCountAdapter() {
            override fun messagesAdded(e: MessageCountEvent) {
                e.messages.forEach { processMessage(it, s, processed) }
            }
        }
        folder.addMessageCountListener(listener)

        // 首次扫描最近的邮件（防止离线期间漏收）
        scanRecent(folder, s, processed)

        // 尝试 IDLE 实时监听
        var idleManager: IdleManager? = null
        var useIdle = false
        try {
            idleManager = IdleManager(Executors.newSingleThreadExecutor(), session)
            idleManager.watch(folder)
            useIdle = true
        } catch (e: Exception) {
            Log.w(TAG, "服务器不支持 IDLE，退化为轮询模式", e)
        }

        if (useIdle) {
            updateStatus("已连接 ${s.imapHost}（IMAP IDLE 实时接收中）")
            while (running && folder.isOpen) {
                Thread.sleep(1_000)
            }
        } else {
            updateStatus("已连接 ${s.imapHost}（轮询模式，每 20 秒）")
            while (running && folder.isOpen) {
                Thread.sleep(20_000)
                scanRecent(folder, s, processed)
            }
        }

        try {
            idleManager?.stop()
        } catch (e: Exception) {
        }
        try {
            folder.close(false)
            store.close()
        } catch (e: Exception) {
        }
    }

    /** 扫描最近 N 封邮件（去重后处理） */
    private fun scanRecent(folder: IMAPFolder, s: SettingsStore.Settings, processed: MessageDedup) {
        try {
            val count = folder.messageCount
            if (count <= 0) return
            val from = maxOf(1, count - SCAN_WINDOW + 1)
            folder.getMessages(from, count).forEach { processMessage(it, s, processed) }
        } catch (e: Exception) {
            Log.w(TAG, "扫描邮件失败", e)
        }
    }

    private fun processMessage(msg: Message, s: SettingsStore.Settings, processed: MessageDedup) {
        try {
            // 只处理慕寒同步标记的邮件
            val marks = msg.getHeader(MailPayload.HEADER_MARK) ?: return
            if (marks.none { it.trim() == "1" }) return

            // 跳过本机发出的（防回环）
            val fromDevice = msg.getHeader(MailPayload.HEADER_DEVICE)?.firstOrNull()?.trim() ?: ""
            if (fromDevice == s.deviceName) return

            val messageId = (msg as? MimeMessage)?.messageID ?: "${msg.subject}-${msg.sentDate?.time}"
            if (messageId != null && processed.contains(messageId)) return

            val body = extractText(msg)
            val payload = MailPayload.parse(body)
            if (payload != null) {
                mainHandler.post {
                    NotifyHelper.postMessage(
                        this, payload.device, payload.app, payload.title, payload.text
                    )
                }
            } else if (msg.subject?.startsWith(MailPayload.SUBJECT_PREFIX) == true) {
                mainHandler.post {
                    NotifyHelper.postMessage(
                        this, fromDevice, "", msg.subject ?: "同步消息", body.take(500)
                    )
                }
            }
            if (messageId != null) processed.mark(messageId)
        } catch (e: Exception) {
            Log.w(TAG, "处理邮件失败", e)
        }
    }

    private fun extractText(msg: Message): String = try {
        when (val c = msg.content) {
            is String -> c
            is Multipart -> findText(c) ?: ""
            else -> c.toString()
        }
    } catch (e: Exception) {
        ""
    }

    private fun findText(mp: Multipart): String? {
        for (i in 0 until mp.count) {
            val part = mp.getBodyPart(i)
            if (part.isMimeType("text/plain")) return part.content as? String
            if (part.content is Multipart) {
                findText(part.content as Multipart)?.let { return it }
            }
        }
        return null
    }

    /** 本地消息去重（基于 Message-ID） */
    class MessageDedup(ctx: Context) {
        private val prefs = ctx.getSharedPreferences("muhan_dedup", Context.MODE_PRIVATE)

        @Volatile
        private var cache: MutableSet<String> = HashSet(prefs.getStringSet("ids", emptySet()) ?: emptySet())

        fun contains(id: String): Boolean = synchronized(cache) { cache.contains(id) }

        fun mark(id: String) {
            synchronized(cache) {
                cache.add(id)
                if (cache.size > MAX) {
                    cache = cache.drop(cache.size - MAX).toMutableSet()
                }
                prefs.edit().putStringSet("ids", cache).apply()
            }
        }

        companion object {
            private const val MAX = 500
        }
    }

    companion object {
        private const val TAG = "ReceiverService"
        private const val FOREGROUND_ID = 1001
        private const val SCAN_WINDOW = 50
    }
}
