package com.muhan.messagesync

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 首页：以历史列表形式展示服务端转发与接收端收到的通知（带应用图标）。
 */
class MainActivity : BaseActivity() {

    private lateinit var rvHistory: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var adapter: HistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        rvHistory = findViewById(R.id.rvHistory)
        tvEmpty = findViewById(R.id.tvEmpty)
        rvHistory.layoutManager = LinearLayoutManager(this)
        adapter = HistoryAdapter(emptyList())
        rvHistory.adapter = adapter

        findViewById<View>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<View>(R.id.btnClear).setOnClickListener {
            HistoryStore.clear(this)
            loadHistory()
            Toast.makeText(this, getString(R.string.history_cleared), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        loadHistory()
    }

    private fun loadHistory() {
        val items = HistoryStore.getAll(this)
        adapter.update(items)
        tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    class HistoryAdapter(private var items: List<HistoryStore.Item>) :
        RecyclerView.Adapter<HistoryAdapter.VH>() {

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

            // 方向徽章
            val isSend = it.direction == "send"
            holder.tvDir.text = if (isSend) ctx.getString(R.string.dir_send) else ctx.getString(R.string.dir_receive)
            val chipColor = if (isSend) Color.parseColor("#C97B94") else Color.parseColor("#7BA6C9")
            (holder.tvDir.background as? GradientDrawable)?.setColor(chipColor)
                ?: holder.tvDir.setBackgroundResource(0)
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

            // 应用图标（base64 解码；为空时回退默认图标）
            val bmp = IconUtil.base64ToBitmap(it.icon)
            if (bmp != null) {
                holder.ivIcon.setImageBitmap(bmp)
            } else {
                holder.ivIcon.setImageResource(android.R.drawable.sym_def_app_icon)
            }
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
