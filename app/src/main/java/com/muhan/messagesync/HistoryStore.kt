package com.muhan.messagesync

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * 本地同步历史持久化。
 *
 * v1.1 修复：此前把含 base64 图标的整份历史塞进 SharedPreferences 的单个 key，
 * 数百条即可达到数 MB（读取全量入内存、写入全量序列化），存在性能与 OOM 隐患。
 * 现改为：索引 JSON 写入 filesDir/history.json，图标以 PNG 单独存放于 filesDir/icons/。
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
        val iconPath: String?    // 本地图标文件路径（不再存放 base64）
    )

    private const val FILE = "history.json"

    private fun file(ctx: Context): File = File(ctx.filesDir, FILE)

    fun newId(): String = UUID.randomUUID().toString()

    /**
     * 新增一条历史。
     * @param iconBase64 应用图标的 base64（可空），内部落盘为 PNG 文件。
     */
    fun add(ctx: Context, item: Item, limit: Int, iconBase64: String?) {
        val path = IconUtil.saveIconFile(ctx, item.id, iconBase64)
        val list = getAll(ctx).toMutableList()
        list.add(0, item.copy(iconPath = path))
        save(ctx, prune(list, limit, SettingsStore.load(ctx).historyDays))
    }

    fun getAll(ctx: Context): List<Item> {
        val f = file(ctx)
        if (!f.exists()) return emptyList()
        return try {
            val arr = JSONArray(f.readText())
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
                        iconPath = o.optString("icon", "").takeIf { it.isNotBlank() }
                    )
                )
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 按当前设置清理过期/超限条目（同时删除对应图标文件） */
    fun prune(ctx: Context) {
        val s = SettingsStore.load(ctx)
        save(ctx, prune(getAll(ctx).toMutableList(), s.historyLimit, s.historyDays))
    }

    fun delete(ctx: Context, id: String) {
        val list = getAll(ctx).toMutableList()
        val target = list.firstOrNull { it.id == id } ?: return
        IconUtil.deleteIconFile(target.iconPath)
        list.removeAll { it.id == id }
        save(ctx, list)
    }

    fun clear(ctx: Context) {
        getAll(ctx).forEach { IconUtil.deleteIconFile(it.iconPath) }
        try {
            file(ctx).delete()
        } catch (e: Exception) {
        }
    }

    /** 截断到上限、丢弃过期项，并回收被丢弃条目的图标文件 */
    private fun prune(list: MutableList<Item>, limit: Int, days: Int): List<Item> {
        var kept: List<Item> = list.toList()
        if (days > 0) {
            val cut = System.currentTimeMillis() - days * 24L * 3600L * 1000L
            val old = kept.filter { it.time > 0L && it.time < cut }
            old.forEach { IconUtil.deleteIconFile(it.iconPath) }
            kept = kept.filter { it.time >= cut }
        }
        if (kept.size > limit) {
            kept.subList(limit, kept.size).forEach { IconUtil.deleteIconFile(it.iconPath) }
            kept = kept.subList(0, limit)
        }
        return kept
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
                    if (!it.iconPath.isNullOrBlank()) put("icon", it.iconPath)
                }
            )
        }
        try {
            file(ctx).writeText(arr.toString())
        } catch (e: Exception) {
            // 写入失败忽略，下次再写
        }
    }
}
