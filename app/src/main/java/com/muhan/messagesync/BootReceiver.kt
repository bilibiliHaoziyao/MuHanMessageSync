package com.muhan.messagesync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 开机自启：如果用户此前启用了服务，开机后自动恢复运行。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON"
        ) return
        if (!SettingsStore.isServiceEnabled(context)) return

        val s = SettingsStore.load(context)
        // v1.1 修复：尊重用户「开机自启」开关（此前该选项完全未生效）
        if (!s.autoStart) return

        when (s.mode) {
            "sender" -> context.startForegroundService(Intent(context, SenderService::class.java))
            "receiver" -> context.startForegroundService(Intent(context, ReceiverService::class.java))
        }
    }
}
