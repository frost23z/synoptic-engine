package com.synopticengine.api.identity.web

import com.synopticengine.api.identity.service.GroupService
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
@RequestMapping("/groups")
class GroupController(
    private val groupService: GroupService,
) {
    @GetMapping
    @PreAuthorize("hasAuthority('groups.view')")
    fun listAll(): ResponseEntity<List<GroupResponse>> = ResponseEntity.ok(groupService.findAll())

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('groups.view')")
    fun getById(
        @PathVariable id: UUID,
    ): ResponseEntity<GroupResponse> = ResponseEntity.ok(groupService.findById(id))

    @PostMapping
    @PreAuthorize("hasAuthority('groups.create')")
    fun create(
        @Valid @RequestBody request: CreateGroupRequest,
    ): ResponseEntity<GroupResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(groupService.create(request.name, request.description))

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('groups.edit')")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateGroupRequest,
    ): ResponseEntity<GroupResponse> = ResponseEntity.ok(groupService.update(id, request.name, request.description))

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('groups.delete')")
    fun delete(
        @PathVariable id: UUID,
    ): ResponseEntity<Void> {
        groupService.delete(id)
        return ResponseEntity.noContent().build()
    }
}
