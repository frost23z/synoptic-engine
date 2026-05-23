package com.synopticengine.api.crm.email.service

import com.synopticengine.api.crm.email.domain.EmailStatus
import com.synopticengine.api.crm.email.repo.EmailRepository
import com.synopticengine.api.shared.email.MailSenderService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class EmailDeliveryService(
    private val emailRepository: EmailRepository,
    private val mailSenderService: MailSenderService,
) {
    @Transactional
    fun deliver(
        emailId: UUID,
        to: String,
        subject: String,
        body: String,
        cc: List<String>?,
        bcc: List<String>?,
    ) {
        val email = emailRepository.findActiveById(emailId) ?: return
        try {
            mailSenderService.sendEmail(to, subject, body, cc, bcc)
            email.status = EmailStatus.SENT
            email.folders = listOf("sent")
            emailRepository.save(email)
        } catch (ex: Exception) {
            email.status = EmailStatus.FAILED
            emailRepository.save(email)
            throw ex
        }
    }
}
