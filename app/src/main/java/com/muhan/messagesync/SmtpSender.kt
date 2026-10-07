package com.muhan.messagesync

import android.util.Log
import java.util.Date
import java.util.Properties
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

/**
 * SMTP 发信器（服务端模式）。
 */
object SmtpSender {

    private const val TAG = "SmtpSender"

    /**
     * 发送一封同步邮件到所有收件人。
     */
    fun send(s: SettingsStore.Settings, subject: String, body: String) {
        val props = Properties().apply {
            put("mail.smtp.auth", "true")
            put("mail.smtp.host", s.smtpHost)
            put("mail.smtp.port", s.smtpPort.toString())
            if (s.smtpSsl) {
                put("mail.smtp.ssl.enable", "true")
            } else {
                put("mail.smtp.starttls.enable", "true")
            }
            put("mail.smtp.connectiontimeout", "15000")
            put("mail.smtp.timeout", "30000")
            put("mail.smtp.writetimeout", "30000")
            put("mail.smtp.ssl.checkserveridentity", "true")
        }

        val session = Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication =
                PasswordAuthentication(s.smtpUser, s.smtpPassword)
        })

        val msg = MimeMessage(session).apply {
            setFrom(InternetAddress(s.smtpFrom.ifBlank { s.smtpUser }, "慕寒消息云同步", "UTF-8"))
            s.recipients.forEach {
                addRecipient(Message.RecipientType.TO, InternetAddress(it))
            }
            setHeader(MailPayload.HEADER_MARK, "1")
            setHeader(MailPayload.HEADER_DEVICE, s.deviceName)
            setSubject(subject, "UTF-8")
            setSentDate(Date())
            setText(body, "UTF-8", "plain")
        }

        Transport.send(msg)
        Log.i(TAG, "邮件已发送: $subject -> ${s.recipients}")
    }
}
