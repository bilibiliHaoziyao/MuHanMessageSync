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

        // 慕寒_Official 头像圆角矩形裁剪（XML 属性在低版本不生效，代码兜底）
        findViewById<View>(R.id.imgMuhan).clipToOutline = true

        // 慕寒_Official → GitHub 主页
        findViewById<View>(R.id.devMuhan).setOnClickListener {
            open("https://github.com/bilibiliHaoziyao")
        }
        // 仓库地址
        findViewById<View>(R.id.tvRepo).setOnClickListener {
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
