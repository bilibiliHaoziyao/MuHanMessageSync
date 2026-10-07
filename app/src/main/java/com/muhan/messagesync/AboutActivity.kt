package com.muhan.messagesync

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity

class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        // 慕寒_Official → GitHub 主页
        findViewById<View>(R.id.devMuhan).setOnClickListener {
            open("https://github.com/bilibiliHaoziyao")
        }
        // 仓库地址
        findViewById<TextView>(R.id.tvRepo).setOnClickListener {
            open(getString(R.string.repo_url))
        }
    }

    private fun open(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            // 无浏览器
        }
    }
}
