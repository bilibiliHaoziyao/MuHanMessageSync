package com.muhan.messagesync

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText

/**
 * 同步选项：转发过滤、轮询间隔、通知优先级、历史保留、通知行为、深色模式等。
 */
class OptionsActivity : BaseActivity() {

    private lateinit var modeToggle: MaterialButtonToggleGroup
    private lateinit var prioToggle: MaterialButtonToggleGroup
    private lateinit var darkToggle: MaterialButtonToggleGroup
    private lateinit var etAppFilter: TextInputEditText
    private lateinit var etPoll: TextInputEditText
    private lateinit var etHistoryLimit: TextInputEditText
    private lateinit var switchSkipEmpty: MaterialSwitch
    private lateinit var switchSkipSystem: MaterialSwitch
    private lateinit var switchVibrate: MaterialSwitch
    private lateinit var switchSound: MaterialSwitch
    private lateinit var switchShowIcon: MaterialSwitch
    private lateinit var switchAutoStart: MaterialSwitch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_options)
        bindViews()
        load()
    }

    private fun bindViews() {
        modeToggle = findViewById(R.id.modeToggle)
        prioToggle = findViewById(R.id.prioToggle)
        darkToggle = findViewById(R.id.darkToggle)
        etAppFilter = findViewById(R.id.etAppFilter)
        etPoll = findViewById(R.id.etPoll)
        etHistoryLimit = findViewById(R.id.etHistoryLimit)
        switchSkipEmpty = findViewById(R.id.switchSkipEmpty)
        switchSkipSystem = findViewById(R.id.switchSkipSystem)
        switchVibrate = findViewById(R.id.switchVibrate)
        switchSound = findViewById(R.id.switchSound)
        switchShowIcon = findViewById(R.id.switchShowIcon)
        switchAutoStart = findViewById(R.id.switchAutoStart)

        findViewById<MaterialButton>(R.id.btnSaveOptions).setOnClickListener { save() }
    }

    private fun load() {
        val s = SettingsStore.load(this)
        when (s.forwardMode) {
            "whitelist" -> modeToggle.check(R.id.btnModeWhite)
            "blacklist" -> modeToggle.check(R.id.btnModeBlack)
            else -> modeToggle.check(R.id.btnModeAll)
        }
        etAppFilter.setText(s.appFilter.joinToString(","))
        switchSkipEmpty.isChecked = s.skipEmptyText
        switchSkipSystem.isChecked = s.skipSystemApps
        etPoll.setText(s.pollIntervalSec.toString())
        if (s.notifyPriority == "high") prioToggle.check(R.id.btnPrioHigh) else prioToggle.check(R.id.btnPrioNormal)
        etHistoryLimit.setText(s.historyLimit.toString())
        switchVibrate.isChecked = s.receiveVibrate
        switchSound.isChecked = s.receiveSound
        switchShowIcon.isChecked = s.showAppIcon
        switchAutoStart.isChecked = s.autoStart
        when (s.darkMode) {
            "light" -> darkToggle.check(R.id.btnDarkLight)
            "dark" -> darkToggle.check(R.id.btnDarkDark)
            else -> darkToggle.check(R.id.btnDarkSystem)
        }
    }

    private fun save() {
        val s = SettingsStore.load(this)
        val forwardMode = when (modeToggle.checkedButtonId) {
            R.id.btnModeWhite -> "whitelist"
            R.id.btnModeBlack -> "blacklist"
            else -> "all"
        }
        val notifyPriority = if (prioToggle.checkedButtonId == R.id.btnPrioHigh) "high" else "default"
        val darkMode = when (darkToggle.checkedButtonId) {
            R.id.btnDarkLight -> "light"
            R.id.btnDarkDark -> "dark"
            else -> "system"
        }
        val appFilter = (etAppFilter.text?.toString() ?: "")
            .split(',', '，', ';', '；', '\n')
            .map { it.trim() }
            .filter { it.isNotBlank() }
        val poll = etPoll.text?.toString()?.toIntOrNull()?.coerceIn(5, 300) ?: 20
        val limit = etHistoryLimit.text?.toString()?.toIntOrNull()?.coerceIn(20, 1000) ?: 200

        SettingsStore.save(
            this,
            s.copy(
                forwardMode = forwardMode,
                appFilter = appFilter,
                skipEmptyText = switchSkipEmpty.isChecked,
                skipSystemApps = switchSkipSystem.isChecked,
                pollIntervalSec = poll,
                notifyPriority = notifyPriority,
                historyLimit = limit,
                receiveVibrate = switchVibrate.isChecked,
                receiveSound = switchSound.isChecked,
                showAppIcon = switchShowIcon.isChecked,
                autoStart = switchAutoStart.isChecked,
                darkMode = darkMode
            )
        )
        Toast.makeText(this, getString(R.string.opt_saved), Toast.LENGTH_SHORT).show()
        // 深色模式变更需重建以使主题立即生效
        recreate()
    }
}
