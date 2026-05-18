package com.synopticengine.api.identity.web

import com.synopticengine.api.identity.service.RoleService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping($$"${api.base-path}/roles")
class RoleController(
    private val roleService: RoleService,
) {
    @GetMapping
    @PreAuthorize("hasAuthority('roles.view')")
    fun listAll(): ResponseEntity<List<RoleResponse>> = ResponseEntity.ok(roleService.findAll())

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('roles.view')")
    fun listAllPermissions(): ResponseEntity<List<PermissionResponse>> =
        ResponseEntity.ok(roleService.findAllPermissions())

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('roles.view')")
    fun getById(
        @PathVariable id: UUID,
    ): ResponseEntity<RoleResponse> = ResponseEntity.ok(roleService.findById(id))

    @PostMapping
    @PreAuthorize("hasAuthority('roles.create')")
    fun create(
        @Valid @RequestBody request: CreateRoleRequest,
    ): ResponseEntity<RoleResponse> =
        ResponseEntity
            .status(HttpStatus.CREATED)
            .body(roleService.create(request.name, request.description, request.permissions))

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('roles.edit')")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateRoleRequest,
    ): ResponseEntity<RoleResponse> =
        ResponseEntity.ok(roleService.update(id, request.name, request.description, request.permissions))

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('roles.edit')")
    fun delete(
        @PathVariable id: UUID,
    ): ResponseEntity<Void> {
        roleService.delete(id)
        return ResponseEntity.noContent().build()
    }
}
