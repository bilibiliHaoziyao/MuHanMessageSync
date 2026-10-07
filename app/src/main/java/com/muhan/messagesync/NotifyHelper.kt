package com.muhan.messagesync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * 通知工具：统一管理通知渠道与通知发送。
 */
object NotifyHelper {

    const val CHANNEL_SERVICE = "sync_service"      // 前台服务常驻通知
    const val CHANNEL_STATUS = "sync_status"        // 状态提示
    const val CHANNEL_MESSAGES = "sync_messages"    // 接收到的同步消息（重要）

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SERVICE, "同步服务", NotificationManager.IMPORTANCE_MIN)
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_STATUS, "同步状态", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_MESSAGES, "同步消息", NotificationManager.IMPORTANCE_HIGH)
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
            NotificationManagerCompat.from(ctx).notify(text.hashCode(), n)
        } catch (e: SecurityException) {
            // 未授予通知权限
        }
    }

    /** 发送一条还原的同步消息通知 */
    fun postMessage(ctx: Context, fromDevice: String, app: String, title: String, text: String) {
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return
        ensureChannels(ctx)
        val bigText = NotificationCompat.BigTextStyle()
            .bigText(if (text.isBlank()) title else "$title\n$text")
        val n = NotificationCompat.Builder(ctx, CHANNEL_MESSAGES)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(if (app.isBlank()) title else "$app：$title")
            .setContentText(text.ifBlank { title })
            .setStyle(bigText)
            .setSubText("来自 $fromDevice")
            .setAutoCancel(true)
            .setContentIntent(mainIntent(ctx))
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify("msg_${fromDevice}_${title}_${text}".hashCode(), n)
        } catch (e: SecurityException) {
        }
    }

    private fun mainIntent(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
