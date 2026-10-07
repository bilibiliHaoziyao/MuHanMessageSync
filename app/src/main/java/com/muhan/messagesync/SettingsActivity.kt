package com.muhan.messagesync

import android.content.Intent
import android.os.Bundle
import android.view.View

/**
 * 设置主页：入口列表（邮箱服务器设置 / 同步选项 / 关于）。
 */
class SettingsActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<View>(R.id.cardServer).setOnClickListener {
            startActivity(Intent(this, ServerSettingsActivity::class.java))
        }
        findViewById<View>(R.id.cardOptions).setOnClickListener {
            startActivity(Intent(this, OptionsActivity::class.java))
        }
        findViewById<View>(R.id.cardAbout).setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }
    }
}
