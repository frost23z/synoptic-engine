package com.synopticengine.api.settings.config.web

import com.synopticengine.api.settings.config.service.SystemConfigService
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping($$"${api.base-path}/settings/config")
class SystemConfigController(
    private val systemConfigService: SystemConfigService,
) {
    @GetMapping
    @PreAuthorize("hasAuthority('settings.view')")
    fun listAll(): ResponseEntity<List<SystemConfigGroupResponse>> = ResponseEntity.ok(systemConfigService.findAll())

    @GetMapping("/{code}")
    @PreAuthorize("hasAuthority('settings.view')")
    fun getByCode(
        @PathVariable code: String,
    ): ResponseEntity<SystemConfigResponse> = ResponseEntity.ok(systemConfigService.findByCode(code))

    @PutMapping("/{code}")
    @PreAuthorize("hasAuthority('settings.edit')")
    fun update(
        @PathVariable code: String,
        @RequestBody request: UpdateSystemConfigRequest,
    ): ResponseEntity<SystemConfigResponse> = ResponseEntity.ok(systemConfigService.update(code, request.value))
}
