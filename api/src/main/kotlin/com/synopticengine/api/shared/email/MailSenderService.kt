package com.synopticengine.api.shared.email

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Service

@Service
class MailSenderService(
    private val mailSender: JavaMailSender,
    @Value("\${mail.from:noreply@synopticengine.com}") private val fromAddress: String,
) {
    private val log = LoggerFactory.getLogger(MailSenderService::class.java)

    fun sendEmail(
        to: String,
        subject: String,
        body: String,
        cc: List<String>? = null,
        bcc: List<String>? = null,
    ) {
        try {
            val message = mailSender.createMimeMessage()
            val helper = MimeMessageHelper(message, true, "UTF-8")
            helper.setFrom(fromAddress)
            helper.setTo(to)
            helper.setSubject(subject)
            helper.setText(body, true)
            if (!cc.isNullOrEmpty()) helper.setCc(cc.toTypedArray())
            if (!bcc.isNullOrEmpty()) helper.setBcc(bcc.toTypedArray())
            mailSender.send(message)
            log.info("Email sent to $to with subject: $subject")
        } catch (e: Exception) {
            log.error("Failed to send email to $to: ${e.message}")
        }
    }

    fun sendHtmlEmail(
        to: String,
        subject: String,
        htmlBody: String,
    ) {
        try {
            val message = mailSender.createMimeMessage()
            val helper = MimeMessageHelper(message, true, "UTF-8")
            helper.setFrom(fromAddress)
            helper.setTo(to)
            helper.setSubject(subject)
            helper.setText(htmlBody, true)
            mailSender.send(message)
        } catch (e: Exception) {
            log.error("Failed to send HTML email to $to: ${e.message}")
        }
    }
}
