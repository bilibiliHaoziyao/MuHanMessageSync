package com.muhan.messagesync

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * 本地同步历史持久化（SharedPreferences + JSON 数组，最新在前，按上限截断）。
 */
object HistoryStore {

    data class Item(
        val id: String,
        val direction: String,   // "send" | "receive"
        val device: String,
        val app: String,
        val pkg: String,
        val title: String,
        val text: String,
        val time: Long,
        val icon: String?        // base64 PNG
    )

    private const val PREFS = "muhan_history"
    private const val KEY = "items"

    fun add(ctx: Context, item: Item, limit: Int) {
        val list = getAll(ctx).toMutableList()
        list.add(0, item)
        val trimmed = if (list.size > limit) list.subList(0, limit) else list
        save(ctx, trimmed)
    }

    fun getAll(ctx: Context): List<Item> {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            val out = mutableListOf<Item>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    Item(
                        id = o.optString("id", UUID.randomUUID().toString()),
                        direction = o.optString("d", "send"),
                        device = o.optString("device", ""),
                        app = o.optString("app", ""),
                        pkg = o.optString("pkg", ""),
                        title = o.optString("title", ""),
                        text = o.optString("text", ""),
                        time = o.optLong("time", 0L),
                        icon = o.optString("icon", "").takeIf { it.isNotBlank() }
                    )
                )
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun clear(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, "[]").apply()
    }

    private fun save(ctx: Context, items: List<Item>) {
        val arr = JSONArray()
        for (it in items) {
            arr.put(
                JSONObject().apply {
                    put("id", it.id)
                    put("d", it.direction)
                    put("device", it.device)
                    put("app", it.app)
                    put("pkg", it.pkg)
                    put("title", it.title)
                    put("text", it.text)
                    put("time", it.time)
                    if (!it.icon.isNullOrBlank()) put("icon", it.icon)
                }
            )
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
    }

    fun newId(): String = UUID.randomUUID().toString()
}
