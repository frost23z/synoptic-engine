package com.synopticengine.api.settings.webform.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class WebFormCaptchaVerifier(
    @Value("\${webform.captcha.secret:}") private val secret: String,
    @Value("\${webform.captcha.verify-url:https://www.google.com/recaptcha/api/siteverify}")
    private val verifyUrl: String,
) {
    private val restClient = RestClient.create()

    fun verify(
        token: String?,
        remoteIp: String?,
    ): Boolean {
        if (secret.isBlank()) return false
        if (token.isNullOrBlank()) return false
        return runCatching {
            val response =
                restClient
                    .post()
                    .uri(verifyUrl)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body("secret=$secret&response=$token&remoteip=${remoteIp.orEmpty()}")
                    .retrieve()
                    .body(CaptchaVerifyResponse::class.java)
            response?.success == true
        }.getOrDefault(false)
    }
}

data class CaptchaVerifyResponse(
    val success: Boolean = false,
)
