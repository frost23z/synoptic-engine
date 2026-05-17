package com.synopticengine.api.crm.datagrid.web

import com.synopticengine.api.auth.config.UserPrincipal
import com.synopticengine.api.crm.datagrid.service.DataGridFilterService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping($$"${api.base-path}/datagrid/saved-filters")
class DataGridFilterController(
    private val service: DataGridFilterService,
) {
    @GetMapping
    fun list(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestParam src: String?,
    ): ResponseEntity<List<DataGridFilterResponse>> = ResponseEntity.ok(service.findByUserAndSrc(principal.id, src))

    @PostMapping
    fun save(
        @AuthenticationPrincipal principal: UserPrincipal,
        @Valid @RequestBody request: SaveFilterRequest,
    ): ResponseEntity<DataGridFilterResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(
            service.save(principal.id, request.name, request.src, request.applied),
        )

    @PutMapping("/{id}")
    fun update(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateFilterRequest,
    ): ResponseEntity<DataGridFilterResponse> =
        ResponseEntity.ok(service.update(id, principal.id, request.name, request.applied))

    @DeleteMapping("/{id}")
    fun delete(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable id: UUID,
    ): ResponseEntity<Void> {
        service.delete(id, principal.id)
        return ResponseEntity.noContent().build()
    }
}
