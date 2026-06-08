package com.synopticengine.api.identity.web

import com.synopticengine.api.identity.service.UserService
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
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/users")
class UserController(
    private val userService: UserService,
) {
    @GetMapping
    @PreAuthorize("hasAuthority('users.view')")
    fun listAll(): ResponseEntity<List<UserResponse>> =
        ResponseEntity.ok(
            userService.findAllActive().map {
                UserResponse(
                    id = it.id,
                    email = it.email,
                    firstName = it.firstName,
                    lastName = it.lastName,
                    fullName = it.fullName,
                    isActive = it.isActive,
                    createdAt = it.createdAt,
                    updatedAt = it.updatedAt,
                )
            },
        )

    @GetMapping("/search")
    @PreAuthorize("hasAuthority('users.view')")
    fun search(
        @RequestParam q: String,
    ): ResponseEntity<List<UserResponse>> =
        ResponseEntity.ok(
            userService.search(q).map {
                UserResponse(
                    id = it.id,
                    email = it.email,
                    firstName = it.firstName,
                    lastName = it.lastName,
                    fullName = it.fullName,
                    isActive = it.isActive,
                    createdAt = it.createdAt,
                    updatedAt = it.updatedAt,
                )
            },
        )

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('users.view')")
    fun getById(
        @PathVariable id: UUID,
    ): ResponseEntity<UserDetailResponse> = ResponseEntity.ok(userService.findDetailById(id))

    @PostMapping
    @PreAuthorize("hasAuthority('users.create')")
    fun create(
        @Valid @RequestBody request: CreateUserRequest,
    ): ResponseEntity<UserDetailResponse> {
        val user =
            userService.create(
                email = request.email,
                password = request.password,
                firstName = request.firstName,
                lastName = request.lastName,
                phone = request.phone,
                roleNames = request.roles,
                groupIds = request.groups,
                viewPermission = request.viewPermission,
            )
        return ResponseEntity.status(HttpStatus.CREATED).body(user)
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('users.edit')")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateUserRequest,
    ): ResponseEntity<UserDetailResponse> =
        ResponseEntity.ok(
            userService.update(
                id = id,
                firstName = request.firstName,
                lastName = request.lastName,
                phone = request.phone,
                roleNames = request.roles,
                groupIds = request.groups,
                viewPermission = request.viewPermission,
            ),
        )

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('users.delete')")
    fun deactivate(
        @PathVariable id: UUID,
    ): ResponseEntity<Void> {
        userService.deactivate(id)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/mass-destroy")
    @PreAuthorize("hasAuthority('users.delete')")
    fun massDeactivate(
        @Valid @RequestBody request: MassDeactivateRequest,
    ): ResponseEntity<Void> {
        userService.massDeactivate(request.ids)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/mass-update")
    @PreAuthorize("hasAuthority('users.edit')")
    fun massUpdateStatus(
        @Valid @RequestBody request: UpdateUsersStatusRequest,
    ): ResponseEntity<Void> {
        userService.setActiveStatus(request.ids, request.isActive)
        return ResponseEntity.noContent().build()
    }

    @PutMapping("/{id}/password")
    @PreAuthorize("hasAuthority('users.edit')")
    fun setPassword(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateUserPasswordRequest,
    ): ResponseEntity<Void> {
        userService.setPassword(id, request.password)
        return ResponseEntity.noContent().build()
    }
}
