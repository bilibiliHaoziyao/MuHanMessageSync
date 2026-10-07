package com.muhan.messagesync

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.TextInputEditText
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 首页：以历史列表形式展示服务端转发与接收端收到的通知（带应用图标）。
 *
 * v1.1 新增：统计、搜索、收发筛选、点击复制内容、长按删除单条。
 */
class MainActivity : BaseActivity() {

    private lateinit var rvHistory: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var tvStats: TextView
    private lateinit var etSearch: TextInputEditText
    private lateinit var filterToggle: MaterialButtonToggleGroup
    private lateinit var adapter: HistoryAdapter

    private var allItems: List<HistoryStore.Item> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        rvHistory = findViewById(R.id.rvHistory)
        tvEmpty = findViewById(R.id.tvEmpty)
        tvStats = findViewById(R.id.tvStats)
        etSearch = findViewById(R.id.etSearch)
        filterToggle = findViewById(R.id.filterToggle)

        rvHistory.layoutManager = LinearLayoutManager(this)
        adapter = HistoryAdapter(
            emptyList(),
            onClick = { item -> copyItem(item) },
            onLongClick = { item -> confirmDelete(item) }
        )
        rvHistory.adapter = adapter

        findViewById<View>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<View>(R.id.btnClear).setOnClickListener {
            HistoryStore.clear(this)
            loadHistory()
            Toast.makeText(this, getString(R.string.history_cleared), Toast.LENGTH_SHORT).show()
        }

        filterToggle.check(R.id.btnFilterAll)
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                applyFilter()
            }
        })
        filterToggle.addOnButtonCheckedListener { _, _, _ -> applyFilter() }
    }

    override fun onResume() {
        super.onResume()
        loadHistory()
    }

    private fun loadHistory() {
        HistoryStore.prune(this)
        allItems = HistoryStore.getAll(this)
        updateStats()
        applyFilter()
    }

    private fun updateStats() {
        val today = allItems.count { isToday(it.time) }
        tvStats.text = getString(R.string.history_stats, today, allItems.size)
    }

    private fun isToday(time: Long): Boolean {
        if (time <= 0L) return false
        val cal = Calendar.getInstance()
        val target = Calendar.getInstance().apply { this.timeInMillis = time }
        return cal.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
            cal.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
    }

    /** 按关键字与收发方向过滤后刷新列表 */
    private fun applyFilter() {
        val kw = etSearch.text?.toString()?.trim().orEmpty()
        val dirOnly = when (filterToggle.checkedButtonId) {
            R.id.btnFilterSend -> "send"
            R.id.btnFilterRecv -> "receive"
            else -> null
        }
        val filtered = allItems.filter { item ->
            val dirOk = dirOnly == null || item.direction == dirOnly
            val kwOk = kw.isEmpty() ||
                item.app.contains(kw, true) ||
                item.pkg.contains(kw, true) ||
                item.title.contains(kw, true) ||
                item.text.contains(kw, true) ||
                item.device.contains(kw, true)
            dirOk && kwOk
        }
        adapter.update(filtered)
        tvEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    /** 点击条目：复制通知内容到剪贴板 */
    private fun copyItem(item: HistoryStore.Item) {
        // 用最保守的写法拼接，避免依赖较新版本的 stdlib 函数
        val parts = ArrayList<String>()
        if (item.app.isNotBlank()) parts.add(item.app)
        if (item.title.isNotBlank()) parts.add(item.title)
        if (item.text.isNotBlank()) parts.add(item.text)
        val content = parts.joinToString("\n").trim().ifBlank { item.pkg }
        val cm = getSystemService(CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        cm?.setPrimaryClip(android.content.ClipData.newPlainText("muhan_sync", content))
        Toast.makeText(this, getString(R.string.history_copied), Toast.LENGTH_SHORT).show()
    }

    /** 长按条目：删除该条记录 */
    private fun confirmDelete(item: HistoryStore.Item) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.history_delete_confirm))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                HistoryStore.delete(this, item.id)
                loadHistory()
            }
            .setNegativeButton(getString(android.R.string.cancel), null)
            .show()
    }

    class HistoryAdapter(
        private var items: List<HistoryStore.Item>,
        private val onClick: (HistoryStore.Item) -> Unit,
        private val onLongClick: (HistoryStore.Item) -> Unit
    ) : RecyclerView.Adapter<HistoryAdapter.VH>() {

        fun update(list: List<HistoryStore.Item>) {
            items = list
            notifyDataSetChanged()
        }

        override fun getItemCount(): Int = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_history, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val it = items[position]
            val ctx = holder.itemView.context

            val appName = it.app.ifBlank { it.pkg.ifBlank { "未知应用" } }
            holder.tvApp.text = appName

            val isSend = it.direction == "send"
            holder.tvDir.text =
                if (isSend) ctx.getString(R.string.dir_send) else ctx.getString(R.string.dir_receive)
            val chipColor = if (isSend) Color.parseColor("#C97B94") else Color.parseColor("#7BA6C9")
            holder.tvDir.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 24f
                setColor(chipColor)
            }

            if (it.title.isBlank()) {
                holder.tvTitle.visibility = View.GONE
            } else {
                holder.tvTitle.visibility = View.VISIBLE
                holder.tvTitle.text = it.title
            }
            if (it.text.isBlank()) {
                holder.tvText.visibility = View.GONE
            } else {
                holder.tvText.visibility = View.VISIBLE
                holder.tvText.text = it.text
            }

            val time = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(it.time))
            val meta = if (it.device.isNotBlank())
                "$time · ${ctx.getString(R.string.history_from, it.device)}" else time
            holder.tvMeta.text = meta

            // v1.1：图标从本地文件解码（此前存 base64 在 SharedPreferences）
            val bmp = IconUtil.decodeIconFile(it.iconPath)
            if (bmp != null) {
                holder.ivIcon.setImageBitmap(bmp)
            } else {
                holder.ivIcon.setImageResource(android.R.drawable.sym_def_app_icon)
            }

            holder.itemView.setOnClickListener { onClick(it) }
            holder.itemView.setOnLongClickListener { onLongClick(it); true }
        }

        class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvApp: TextView = itemView.findViewById(R.id.tvApp)
            val tvDir: TextView = itemView.findViewById(R.id.tvDir)
            val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
            val tvText: TextView = itemView.findViewById(R.id.tvText)
            val tvMeta: TextView = itemView.findViewById(R.id.tvMeta)
            val ivIcon: ImageView = itemView.findViewById(R.id.ivIcon)
        }
    }
}
