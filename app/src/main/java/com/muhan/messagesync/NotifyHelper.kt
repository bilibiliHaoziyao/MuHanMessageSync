package com.muhan.messagesync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * 通知工具：统一管理通知渠道与通知发送。
 */
object NotifyHelper {

    const val CHANNEL_SERVICE = "sync_service"      // 前台服务常驻通知
    const val CHANNEL_STATUS = "sync_status"        // 状态提示
    const val CHANNEL_MESSAGES = "sync_messages"    // 接收到的同步消息（有声）
    const val CHANNEL_MESSAGES_SILENT = "sync_messages_silent" // 静音消息

    /**
     * 状态通知使用固定 id，新状态覆盖旧状态。
     * v1.1 修复：此前以文本 hashCode 作 id，每转发一条通知都会新增一条系统通知，造成刷屏。
     */
    private const val STATUS_ID = 2001

    private val VIBRATE_PATTERN = longArrayOf(0, 180, 120, 220)

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SERVICE, "同步服务", NotificationManager.IMPORTANCE_MIN)
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_STATUS, "同步状态", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_MESSAGES, "同步消息", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "收到同步消息时提示"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_MESSAGES_SILENT, "同步消息（静音）", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "收到同步消息时不播放提示音"
                setSound(null, null)
            }
        )
    }

    fun buildForeground(ctx: Context, text: String): Notification {
        ensureChannels(ctx)
        return NotificationCompat.Builder(ctx, CHANNEL_SERVICE)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("慕寒消息云同步")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(mainIntent(ctx))
            .build()
    }

    /** 发送一条状态通知 */
    fun postStatus(ctx: Context, channel: String, title: String, text: String) {
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return
        ensureChannels(ctx)
        val n = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(mainIntent(ctx))
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(STATUS_ID, n)
        } catch (e: SecurityException) {
            // 未授予通知权限
        }
    }

    /**
     * 发送一条还原的同步消息通知。
     * @param pkg 来源应用包名，用于点击通知时尝试打开该应用
     * @param icon 应用图标（可空）；为空时由接收端尝试用包名本地获取
     * @param priorityHigh 是否高优先级（弹窗+重要）
     * @param vibrate 是否震动
     * @param sound 是否播放提示音
     */
    fun postMessage(
        ctx: Context,
        fromDevice: String,
        app: String,
        title: String,
        text: String,
        icon: Bitmap? = null,
        priorityHigh: Boolean = false,
        vibrate: Boolean = true,
        sound: Boolean = false,
        pkg: String = ""
    ) {
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return
        ensureChannels(ctx)
        val bigText = NotificationCompat.BigTextStyle()
            .bigText(if (text.isBlank()) title else "$title\n$text")
        val channel = if (sound) CHANNEL_MESSAGES else CHANNEL_MESSAGES_SILENT
        val builder = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(if (app.isBlank()) title else "$app：$title")
            .setContentText(text.ifBlank { title })
            .setStyle(bigText)
            .setSubText("来自 $fromDevice")
            .setAutoCancel(true)
            .setContentIntent(contentIntent(ctx, pkg))
        if (icon != null) builder.setLargeIcon(icon)
        builder.priority = if (priorityHigh) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT
        if (vibrate) builder.setVibrate(VIBRATE_PATTERN)
        else builder.setVibrate(null)
        try {
            NotificationManagerCompat.from(ctx)
                .notify("msg_${fromDevice}_${app}_${title}_${text}_${System.currentTimeMillis()}".hashCode(), builder.build())
        } catch (e: SecurityException) {
        }
    }

    /**
     * 通知点击意图：若本机安装了来源应用则直接打开该应用，否则打开历史主页。
     */
    private fun contentIntent(ctx: Context, pkg: String): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        if (pkg.isNotBlank()) {
            val launch = try {
                ctx.packageManager.getLaunchIntentForPackage(pkg)
            } catch (e: Exception) {
                null
            }
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                return PendingIntent.getActivity(ctx, pkg.hashCode(), launch, flags)
            }
        }
        return mainIntent(ctx)
    }

    private fun mainIntent(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
