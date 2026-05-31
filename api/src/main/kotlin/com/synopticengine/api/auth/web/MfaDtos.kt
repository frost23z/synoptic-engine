package com.synopticengine.api.auth.web

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class MfaSetupResponse(
    val secret: String,
    val qrUri: String,
)

data class MfaConfirmRequest(
    @field:NotBlank(message = "TOTP code is required")
    @field:Size(min = 6, max = 8, message = "Code must be 6–8 characters")
    val code: String,
)

data class MfaConfirmResponse(
    val backupCodes: List<String>,
)

data class MfaVerifyRequest(
    @field:NotBlank(message = "MFA token is required")
    val mfaToken: String,
    @field:NotBlank(message = "TOTP code is required")
    @field:Size(min = 6, max = 8, message = "Code must be 6–8 characters")
    val code: String,
)

data class MfaDisableRequest(
    @field:NotBlank(message = "TOTP or backup code is required")
    val code: String,
)

data class MfaRegenerateBackupCodesResponse(
    val backupCodes: List<String>,
)
