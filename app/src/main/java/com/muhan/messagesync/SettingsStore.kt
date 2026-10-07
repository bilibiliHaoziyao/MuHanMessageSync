package com.muhan.messagesync

import android.content.Context
import android.os.Build
import java.util.UUID

/**
 * 全局设置存储（SharedPreferences）。
 */
object SettingsStore {

    data class Settings(
        val mode: String,              // "sender" 或 "receiver"
        val deviceName: String,
        // SMTP（服务端）
        val smtpHost: String,
        val smtpPort: Int,
        val smtpSsl: Boolean,
        val smtpUser: String,
        val smtpPassword: String,
        val smtpFrom: String,
        val recipients: List<String>,
        // IMAP（接收端）
        val imapHost: String,
        val imapPort: Int,
        val imapSsl: Boolean,
        val imapUser: String,
        val imapPassword: String,
        val imapFolder: String,
        // 同步选项（自定义）
        val forwardMode: String,        // "all" | "whitelist" | "blacklist"
        val appFilter: List<String>,    // 包名或应用名
        val skipEmptyText: Boolean,
        val skipSystemApps: Boolean,
        val pollIntervalSec: Int,
        val notifyPriority: String,     // "default" | "high"
        val historyLimit: Int,
        val autoStart: Boolean,
        val receiveVibrate: Boolean,
        val receiveSound: Boolean,
        val darkMode: String,           // "system" | "light" | "dark"
        val showAppIcon: Boolean
    ) {
        val smtpValid: Boolean
            get() = smtpHost.isNotBlank() && smtpUser.isNotBlank() &&
                    smtpPassword.isNotBlank() && recipients.isNotEmpty()
        val imapValid: Boolean
            get() = imapHost.isNotBlank() && imapUser.isNotBlank() && imapPassword.isNotBlank()
    }

    private const val PREFS = "muhan_sync_settings"

    fun defaultDeviceName(): String =
        "${Build.MODEL}-${UUID.randomUUID().toString().substring(0, 4)}"

    fun load(ctx: Context): Settings {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Settings(
            mode = p.getString("mode", "sender") ?: "sender",
            deviceName = p.getString("device_name", "")?.ifBlank { defaultDeviceName() } ?: defaultDeviceName(),
            smtpHost = p.getString("smtp_host", "") ?: "",
            smtpPort = p.getInt("smtp_port", 465),
            smtpSsl = p.getBoolean("smtp_ssl", true),
            smtpUser = p.getString("smtp_user", "") ?: "",
            smtpPassword = p.getString("smtp_password", "") ?: "",
            smtpFrom = p.getString("smtp_from", "")?.ifBlank { p.getString("smtp_user", "") ?: "" } ?: "",
            recipients = (p.getString("recipients", "") ?: "")
                .split(',', '，', ';', '；', '\n')
                .map { it.trim() }
                .filter { it.contains('@') },
            imapHost = p.getString("imap_host", "") ?: "",
            imapPort = p.getInt("imap_port", 993),
            imapSsl = p.getBoolean("imap_ssl", true),
            imapUser = p.getString("imap_user", "") ?: "",
            imapPassword = p.getString("imap_password", "") ?: "",
            imapFolder = p.getString("imap_folder", "INBOX")?.ifBlank { "INBOX" } ?: "INBOX",
            forwardMode = p.getString("forward_mode", "all") ?: "all",
            appFilter = (p.getString("app_filter", "") ?: "")
                .split(',', '，', ';', '；', '\n')
                .map { it.trim() }
                .filter { it.isNotBlank() },
            skipEmptyText = p.getBoolean("skip_empty_text", true),
            skipSystemApps = p.getBoolean("skip_system_apps", true),
            pollIntervalSec = p.getInt("poll_interval_sec", 20),
            notifyPriority = p.getString("notify_priority", "default") ?: "default",
            historyLimit = p.getInt("history_limit", 200),
            autoStart = p.getBoolean("auto_start", true),
            receiveVibrate = p.getBoolean("receive_vibrate", true),
            receiveSound = p.getBoolean("receive_sound", false),
            darkMode = p.getString("dark_mode", "system") ?: "system",
            showAppIcon = p.getBoolean("show_app_icon", true)
        )
    }

    fun save(ctx: Context, s: Settings) {
        p(ctx).edit().apply {
            putString("mode", s.mode)
            putString("device_name", s.deviceName)
            putString("smtp_host", s.smtpHost)
            putInt("smtp_port", s.smtpPort)
            putBoolean("smtp_ssl", s.smtpSsl)
            putString("smtp_user", s.smtpUser)
            putString("smtp_password", s.smtpPassword)
            putString("smtp_from", s.smtpFrom)
            putString("recipients", s.recipients.joinToString(","))
            putString("imap_host", s.imapHost)
            putInt("imap_port", s.imapPort)
            putBoolean("imap_ssl", s.imapSsl)
            putString("imap_user", s.imapUser)
            putString("imap_password", s.imapPassword)
            putString("imap_folder", s.imapFolder)
            putString("forward_mode", s.forwardMode)
            putString("app_filter", s.appFilter.joinToString(","))
            putBoolean("skip_empty_text", s.skipEmptyText)
            putBoolean("skip_system_apps", s.skipSystemApps)
            putInt("poll_interval_sec", s.pollIntervalSec)
            putString("notify_priority", s.notifyPriority)
            putInt("history_limit", s.historyLimit)
            putBoolean("auto_start", s.autoStart)
            putBoolean("receive_vibrate", s.receiveVibrate)
            putBoolean("receive_sound", s.receiveSound)
            putString("dark_mode", s.darkMode)
            putBoolean("show_app_icon", s.showAppIcon)
            apply()
        }
    }

    /** 服务是否被用户启用（用于开机自启） */
    fun isServiceEnabled(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("service_enabled", false)

    fun setServiceEnabled(ctx: Context, enabled: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("service_enabled", enabled).apply()
    }

    /** 是否已完成首次进入的引导 */
    fun isOnboarded(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("onboarded", false)

    fun setOnboarded(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("onboarded", true).apply()
    }

    private fun p(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
