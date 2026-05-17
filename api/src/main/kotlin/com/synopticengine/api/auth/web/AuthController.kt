package com.synopticengine.api.auth.web

import com.synopticengine.api.auth.config.UserPrincipal
import com.synopticengine.api.auth.service.AuthService
import com.synopticengine.api.identity.IdentityApi
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/auth")
class AuthController(
    private val authService: AuthService,
    private val identityApi: IdentityApi,
) {
    @PostMapping("/login")
    fun login(
        @Valid @RequestBody request: LoginRequest,
    ): ResponseEntity<TokenResponse> = ResponseEntity.ok(authService.login(request.email, request.password))

    @PostMapping("/refresh")
    fun refresh(
        @Valid @RequestBody request: RefreshRequest,
    ): ResponseEntity<TokenResponse> = ResponseEntity.ok(authService.refresh(request.refreshToken))

    @GetMapping("/me")
    fun me(
        @AuthenticationPrincipal principal: UserPrincipal,
    ): ResponseEntity<MeResponse> {
        val user =
            identityApi.findById(principal.id)
                ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(
            MeResponse(
                id = user.id,
                email = user.email,
                fullName = user.fullName,
                isActive = user.isActive,
                authorities = principal.authorities.mapNotNull { it.authority },
            ),
        )
    }

    @PostMapping("/forgot-password")
    fun forgotPassword(
        @Valid @RequestBody request: ForgotPasswordRequest,
    ): ResponseEntity<Void> {
        authService.forgotPassword(request.email)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/reset-password")
    fun resetPassword(
        @Valid @RequestBody request: ResetPasswordRequest,
    ): ResponseEntity<Void> {
        authService.resetPassword(request.token, request.email, request.newPassword)
        return ResponseEntity.noContent().build()
    }
}
