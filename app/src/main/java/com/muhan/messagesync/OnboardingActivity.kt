package com.muhan.messagesync

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import com.google.android.material.button.MaterialButton

/**
 * 首次进入引导：开启「通知使用权」与「后台保活」。
 * 已完成过引导的用户会直接进入主界面。
 */
class OnboardingActivity : AppCompatActivity() {

    private lateinit var tvStep1Status: TextView
    private lateinit var tvStep2Status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 已引导过，直接进主界面
        if (SettingsStore.isOnboarded(this)) {
            goMain()
            return
        }
        setContentView(R.layout.activity_onboarding)

        tvStep1Status = findViewById(R.id.tvStep1Status)
        tvStep2Status = findViewById(R.id.tvStep2Status)

        // 第一步：通知使用权
        findViewById<MaterialButton>(R.id.btnStep1).setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            } catch (e: Exception) {
                Toast.makeText(this, "无法打开通知使用权设置", Toast.LENGTH_SHORT).show()
            }
        }

        // 第二步：后台保活（忽略电池优化）
        findViewById<MaterialButton>(R.id.btnStep2).setOnClickListener {
            openKeepAliveSettings()
        }

        // 完成
        findViewById<MaterialButton>(R.id.btnDone).setOnClickListener {
            SettingsStore.setOnboarded(this)
            goMain()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        val listenerOk = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        tvStep1Status.text = if (listenerOk) getString(R.string.onboard_status_ok)
        else getString(R.string.onboard_status_todo)

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val batteryOk = pm.isIgnoringBatteryOptimizations(packageName)
        tvStep2Status.text = if (batteryOk) getString(R.string.onboard_status_ok)
        else getString(R.string.onboard_status_todo)
    }

    private fun openKeepAliveSettings() {
        // 优先拉起「忽略电池优化」确认框
        try {
            @Suppress("BatteryLife")
            val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(Uri.parse("package:$packageName"))
            startActivity(i)
            return
        } catch (e: Exception) {
        }
        // 兜底：打开应用详情页（可设置自启动/后台权限）
        try {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:$packageName"))
            )
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开系统设置，请手动设置后台保活", Toast.LENGTH_LONG).show()
        }
    }

    private fun goMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
