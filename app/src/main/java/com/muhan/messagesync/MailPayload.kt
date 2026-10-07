package com.muhan.messagesync

import org.json.JSONObject

/**
 * 邮件正文的 JSON 载荷。
 */
data class MailPayload(
    val device: String,
    val app: String,
    val pkg: String,
    val title: String,
    val text: String,
    val time: Long,
    val icon: String? = null   // 应用图标的 base64 PNG（可空）
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("device", device)
        put("app", app)
        put("pkg", pkg)
        put("title", title)
        put("text", text)
        put("time", time)
        if (!icon.isNullOrBlank()) put("icon", icon)
    }

    companion object {
        const val HEADER_MARK = "X-MuHan-Sync"
        const val HEADER_DEVICE = "X-MuHan-Sync-Device"
        const val SUBJECT_PREFIX = "【慕寒同步】"

        fun parse(body: String): MailPayload? = try {
            val o = JSONObject(body)
            MailPayload(
                device = o.optString("device", ""),
                app = o.optString("app", ""),
                pkg = o.optString("pkg", ""),
                title = o.optString("title", ""),
                text = o.optString("text", ""),
                time = o.optLong("time", 0L),
                icon = o.optString("icon", "").takeIf { it.isNotBlank() }
            )
        } catch (e: Exception) {
            null
        }
    }
}
