package com.muhan.messagesync

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var modeToggle: MaterialButtonToggleGroup
    private lateinit var btnModeSender: MaterialButton
    private lateinit var btnModeReceiver: MaterialButton
    private lateinit var cardSender: MaterialCardView
    private lateinit var cardReceiver: MaterialCardView
    private lateinit var etSmtpHost: TextInputEditText
    private lateinit var etSmtpPort: TextInputEditText
    private lateinit var switchSmtpSsl: MaterialSwitch
    private lateinit var etSmtpUser: TextInputEditText
    private lateinit var etSmtpPassword: TextInputEditText
    private lateinit var etSmtpFrom: TextInputEditText
    private lateinit var etRecipients: TextInputEditText
    private lateinit var etImapHost: TextInputEditText
    private lateinit var etImapPort: TextInputEditText
    private lateinit var switchImapSsl: MaterialSwitch
    private lateinit var etImapUser: TextInputEditText
    private lateinit var etImapPassword: TextInputEditText
    private lateinit var etImapFolder: TextInputEditText
    private lateinit var etDeviceName: TextInputEditText
    private lateinit var btnPresetQQ: MaterialButton
    private lateinit var btnPreset163: MaterialButton
    private lateinit var btnPresetGmail: MaterialButton
    private lateinit var btnPresetOutlook: MaterialButton
    private lateinit var btnSave: MaterialButton
    private lateinit var btnToggleService: MaterialButton
    private lateinit var btnTest: MaterialButton
    private lateinit var tvStatus: TextView
    private lateinit var btnAbout: TextView

    private var serviceRunning = false

    private val notifPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) Toast.makeText(this, "通知权限已授予", Toast.LENGTH_SHORT).show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bindViews()
        loadSettings()

        btnModeSender.setOnClickListener { updateModeVisibility("sender") }
        btnModeReceiver.setOnClickListener { updateModeVisibility("receiver") }

        // 邮箱服务商快速预设：一键填入 SMTP / IMAP 服务器与端口
        btnPresetQQ.setOnClickListener {
            applyPreset("QQ邮箱", "smtp.qq.com", 465, true, "imap.qq.com", 993, true)
        }
        btnPreset163.setOnClickListener {
            applyPreset("163邮箱", "smtp.163.com", 465, true, "imap.163.com", 993, true)
        }
        btnPresetGmail.setOnClickListener {
            applyPreset("Gmail", "smtp.gmail.com", 465, true, "imap.gmail.com", 993, true)
        }
        btnPresetOutlook.setOnClickListener {
            applyPreset("Outlook", "smtp.office365.com", 587, false, "imap-mail.outlook.com", 993, true)
        }

        btnSave.setOnClickListener {
            saveSettings()
            Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show()
        }

        btnToggleService.setOnClickListener { toggleService() }
        btnTest.setOnClickListener { runTest() }
        btnAbout.setOnClickListener { startActivity(Intent(this, AboutActivity::class.java)) }

        requestNotificationPermissionIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun bindViews() {
        modeToggle = findViewById(R.id.modeToggle)
        btnModeSender = findViewById(R.id.btnModeSender)
        btnModeReceiver = findViewById(R.id.btnModeReceiver)
        cardSender = findViewById(R.id.cardSender)
        cardReceiver = findViewById(R.id.cardReceiver)
        etSmtpHost = findViewById(R.id.etSmtpHost)
        etSmtpPort = findViewById(R.id.etSmtpPort)
        switchSmtpSsl = findViewById(R.id.switchSmtpSsl)
        etSmtpUser = findViewById(R.id.etSmtpUser)
        etSmtpPassword = findViewById(R.id.etSmtpPassword)
        etSmtpFrom = findViewById(R.id.etSmtpFrom)
        etRecipients = findViewById(R.id.etRecipients)
        etImapHost = findViewById(R.id.etImapHost)
        etImapPort = findViewById(R.id.etImapPort)
        switchImapSsl = findViewById(R.id.switchImapSsl)
        etImapUser = findViewById(R.id.etImapUser)
        etImapPassword = findViewById(R.id.etImapPassword)
        etImapFolder = findViewById(R.id.etImapFolder)
        etDeviceName = findViewById(R.id.etDeviceName)
        btnPresetQQ = findViewById(R.id.btnPresetQQ)
        btnPreset163 = findViewById(R.id.btnPreset163)
        btnPresetGmail = findViewById(R.id.btnPresetGmail)
        btnPresetOutlook = findViewById(R.id.btnPresetOutlook)
        btnSave = findViewById(R.id.btnSave)
        btnToggleService = findViewById(R.id.btnToggleService)
        btnTest = findViewById(R.id.btnTest)
        tvStatus = findViewById(R.id.tvStatus)
        btnAbout = findViewById(R.id.btnAbout)
    }

    private fun loadSettings() {
        val s = SettingsStore.load(this)
        etSmtpHost.setText(s.smtpHost)
        etSmtpPort.setText(s.smtpPort.toString())
        switchSmtpSsl.isChecked = s.smtpSsl
        etSmtpUser.setText(s.smtpUser)
        etSmtpPassword.setText(s.smtpPassword)
        etSmtpFrom.setText(s.smtpFrom)
        etRecipients.setText(s.recipients.joinToString(","))
        etImapHost.setText(s.imapHost)
        etImapPort.setText(s.imapPort.toString())
        switchImapSsl.isChecked = s.imapSsl
        etImapUser.setText(s.imapUser)
        etImapPassword.setText(s.imapPassword)
        etImapFolder.setText(s.imapFolder)
        etDeviceName.setText(s.deviceName)

        if (s.mode == "receiver") {
            modeToggle.check(R.id.btnModeReceiver)
        } else {
            modeToggle.check(R.id.btnModeSender)
        }
        updateModeVisibility(s.mode)
    }

    private fun collectSettings(): SettingsStore.Settings {
        val s = SettingsStore.load(this)
        return s.copy(
            mode = if (modeToggle.checkedButtonId == R.id.btnModeReceiver) "receiver" else "sender",
            deviceName = etDeviceName.text?.toString()?.trim().takeUnless { it.isNullOrBlank() }
                ?: SettingsStore.defaultDeviceName(),
            smtpHost = etSmtpHost.text?.toString()?.trim() ?: "",
            smtpPort = etSmtpPort.text?.toString()?.toIntOrNull() ?: 465,
            smtpSsl = switchSmtpSsl.isChecked,
            smtpUser = etSmtpUser.text?.toString()?.trim() ?: "",
            smtpPassword = etSmtpPassword.text?.toString() ?: "",
            smtpFrom = etSmtpFrom.text?.toString()?.trim() ?: "",
            recipients = (etRecipients.text?.toString() ?: "")
                .split(',', '，', ';', '；', '\n')
                .map { it.trim() }
                .filter { it.contains('@') },
            imapHost = etImapHost.text?.toString()?.trim() ?: "",
            imapPort = etImapPort.text?.toString()?.toIntOrNull() ?: 993,
            imapSsl = switchImapSsl.isChecked,
            imapUser = etImapUser.text?.toString()?.trim() ?: "",
            imapPassword = etImapPassword.text?.toString() ?: "",
            imapFolder = etImapFolder.text?.toString()?.trim()?.ifBlank { "INBOX" } ?: "INBOX"
        )
    }

    private fun saveSettings() {
        SettingsStore.save(this, collectSettings())
    }

    private fun updateModeVisibility(mode: String) {
        cardSender.visibility = if (mode == "sender") MaterialCardView.VISIBLE else MaterialCardView.GONE
        cardReceiver.visibility = if (mode == "receiver") MaterialCardView.VISIBLE else MaterialCardView.GONE
    }

    /**
     * 应用邮箱服务商预设：填入 SMTP / IMAP 服务器地址、端口与加密方式，并自动保存。
     * 账号、授权码、收件人仍需用户自行填写。
     */
    private fun applyPreset(
        label: String,
        smtpHost: String, smtpPort: Int, smtpSslOn: Boolean,
        imapHost: String, imapPort: Int, imapSslOn: Boolean
    ) {
        etSmtpHost.setText(smtpHost)
        etSmtpPort.setText(smtpPort.toString())
        switchSmtpSsl.isChecked = smtpSslOn
        etImapHost.setText(imapHost)
        etImapPort.setText(imapPort.toString())
        switchImapSsl.isChecked = imapSslOn
        if (etSmtpUser.text.isNullOrBlank()) etSmtpUser.setText(etImapUser.text?.toString() ?: "")
        if (etImapUser.text.isNullOrBlank()) etImapUser.setText(etSmtpUser.text?.toString() ?: "")
        saveSettings()
        Toast.makeText(this, "已应用${label}预设，请补全账号、授权码与收件人", Toast.LENGTH_LONG).show()
    }

    private fun toggleService() {
        if (serviceRunning) {
            stopServices()
            SettingsStore.setServiceEnabled(this, false)
            serviceRunning = false
            refreshStatus()
            return
        }

        val s = collectSettings()
        saveSettings()

        if (s.mode == "sender") {
            if (!s.smtpValid) {
                Toast.makeText(this, "请先完整填写 SMTP 服务器、账号、密码和收件人", Toast.LENGTH_LONG).show()
                return
            }
            if (!isNotificationListenerEnabled()) {
                AlertDialog.Builder(this)
                    .setTitle("需要通知读取权限")
                    .setMessage("服务端模式需要授予\"通知使用权\"才能读取系统通知。\n请在接下来的页面中找到「慕寒消息云同步」并开启权限。")
                    .setPositiveButton("去开启") { _, _ ->
                        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }
                    .setNegativeButton("取消", null)
                    .show()
                return
            }
            startForegroundService(Intent(this, SenderService::class.java))
        } else {
            if (!s.imapValid) {
                Toast.makeText(this, "请先完整填写 IMAP 服务器、账号和密码", Toast.LENGTH_LONG).show()
                return
            }
            startForegroundService(Intent(this, ReceiverService::class.java))
        }
        SettingsStore.setServiceEnabled(this, true)
        serviceRunning = true
        refreshStatus()
    }

    private fun stopServices() {
        stopService(Intent(this, SenderService::class.java))
        stopService(Intent(this, ReceiverService::class.java))
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val enabled = NotificationManagerCompat.getEnabledListenerPackages(this)
        return enabled.contains(packageName)
    }

    private fun runTest() {
        val s = collectSettings()
        saveSettings()
        thread {
            try {
                if (s.mode == "sender") {
                    if (!s.smtpValid) {
                        runOnUiThread { Toast.makeText(this, "SMTP 设置不完整", Toast.LENGTH_SHORT).show() }
                        return@thread
                    }
                    SmtpSender.send(
                        s,
                        "${MailPayload.SUBJECT_PREFIX}测试邮件",
                        MailPayload(s.deviceName, "慕寒消息云同步", packageName, "测试通知", "这是一条测试消息，收到即表示链路正常 ✓", System.currentTimeMillis())
                            .toJson().toString(2)
                    )
                    runOnUiThread { Toast.makeText(this, "测试邮件已发送 ✓", Toast.LENGTH_SHORT).show() }
                } else {
                    NotifyHelper.postMessage(
                        this, s.deviceName, "慕寒消息云同步", "测试通知",
                        "这是一条本地测试通知 ✓"
                    )
                    runOnUiThread { Toast.makeText(this, "已发送本地测试通知 ✓", Toast.LENGTH_SHORT).show() }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "失败：${e.message ?: e.javaClass.simpleName}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun refreshStatus() {
        val s = SettingsStore.load(this)
        val listenerEnabled = isNotificationListenerEnabled()
        val status = when {
            SettingsStore.isServiceEnabled(this) && listenerEnabled && s.mode == "sender" ->
                "● 服务端运行中 — 正在监听通知并转发到：${s.recipients.joinToString("、")}"
            SettingsStore.isServiceEnabled(this) && s.mode == "receiver" ->
                "● 接收端运行中 — 实时监听 ${s.imapHost} 的新邮件"
            s.mode == "sender" && !listenerEnabled ->
                "○ 服务端就绪，但尚未授予\"通知使用权\"，点击「启动服务」按提示开启"
            else ->
                "○ 服务未运行，点击「启动服务」开始同步"
        }
        tvStatus.text = status
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
