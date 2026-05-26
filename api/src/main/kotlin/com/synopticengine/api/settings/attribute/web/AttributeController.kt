package com.synopticengine.api.settings.attribute.web

import com.synopticengine.api.settings.attribute.service.AttributeService
import jakarta.validation.Valid
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
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
@RequestMapping($$"${api.base-path}/settings/attributes")
class AttributeController(
    private val attributeService: AttributeService,
) {
    @GetMapping
    @PreAuthorize("hasAuthority('attributes.view')")
    fun listAll(
        @RequestParam entityType: String?,
    ): ResponseEntity<List<AttributeResponse>> = ResponseEntity.ok(attributeService.findAll(entityType))

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('attributes.view')")
    fun getById(
        @PathVariable id: UUID,
    ): ResponseEntity<AttributeResponse> = ResponseEntity.ok(attributeService.findById(id))

    @GetMapping("/values")
    @PreAuthorize("hasAuthority('attributes.view')")
    fun getValues(
        @RequestParam entityId: UUID,
        @RequestParam entityType: String,
    ): ResponseEntity<List<AttributeValueResponse>> =
        ResponseEntity.ok(attributeService.getEntityValues(entityId, entityType))

    @PostMapping
    @PreAuthorize("hasAuthority('attributes.create')")
    fun create(
        @Valid @RequestBody request: CreateAttributeRequest,
    ): ResponseEntity<AttributeResponse> =
        ResponseEntity
            .status(HttpStatus.CREATED)
            .body(
                attributeService.create(
                    request.code,
                    request.adminName,
                    request.type,
                    request.entityType,
                    request.isUserDefined,
                    request.isRequired,
                    request.isUnique,
                    request.quickAdd,
                    request.lookup,
                    request.lookupType,
                    request.validationRules,
                    request.sortOrder,
                ),
            )

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('attributes.edit')")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateAttributeRequest,
    ): ResponseEntity<AttributeResponse> =
        ResponseEntity.ok(
            attributeService.update(
                id,
                request.adminName,
                request.type,
                request.isRequired,
                request.isUnique,
                request.quickAdd,
                request.lookup,
                request.lookupType,
                request.validationRules,
                request.sortOrder,
            ),
        )

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('attributes.delete')")
    fun delete(
        @PathVariable id: UUID,
    ): ResponseEntity<Void> {
        attributeService.delete(id)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{id}/options")
    @PreAuthorize("hasAuthority('attributes.edit')")
    fun addOption(
        @PathVariable id: UUID,
        @Valid @RequestBody request: AttributeOptionRequest,
    ): ResponseEntity<AttributeOptionResponse> =
        ResponseEntity
            .status(
                HttpStatus.CREATED,
            ).body(attributeService.addOption(id, request.adminName, request.sortOrder))

    @PutMapping("/{id}/options/{optionId}")
    @PreAuthorize("hasAuthority('attributes.edit')")
    fun updateOption(
        @PathVariable id: UUID,
        @PathVariable optionId: UUID,
        @Valid @RequestBody request: AttributeOptionRequest,
    ): ResponseEntity<AttributeOptionResponse> =
        ResponseEntity.ok(attributeService.updateOption(id, optionId, request.adminName, request.sortOrder))

    @DeleteMapping("/{id}/options/{optionId}")
    @PreAuthorize("hasAuthority('attributes.edit')")
    fun deleteOption(
        @PathVariable id: UUID,
        @PathVariable optionId: UUID,
    ): ResponseEntity<Void> {
        attributeService.deleteOption(id, optionId)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/values")
    @PreAuthorize("hasAuthority('attributes.edit')")
    fun setValue(
        @RequestBody request: SetAttributeValueRequest,
    ): ResponseEntity<AttributeValueResponse> =
        ResponseEntity.ok(
            attributeService.setValue(request.attributeId, request.entityId, request.entityType, request.value),
        )

    @PostMapping("/mass-update")
    @PreAuthorize("hasAuthority('attributes.edit')")
    fun massUpdate(
        @RequestBody request: MassUpdateAttributeRequest,
    ): ResponseEntity<Void> {
        attributeService.massUpdate(request.ids, request.adminName, request.sortOrder)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/mass-destroy")
    @PreAuthorize("hasAuthority('attributes.delete')")
    fun massDestroy(
        @RequestBody request: MassDestroyAttributeRequest,
    ): ResponseEntity<Void> {
        attributeService.massDestroy(request.ids)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/lookup/{lookup}")
    @PreAuthorize("hasAuthority('attributes.view')")
    fun lookup(
        @PathVariable lookup: String,
    ): ResponseEntity<List<AttributeLookupItem>> = ResponseEntity.ok(attributeService.lookup(lookup))

    @GetMapping("/lookup-entity/{lookup}")
    @PreAuthorize("hasAuthority('attributes.view')")
    fun lookupEntity(
        @PathVariable lookup: String,
    ): ResponseEntity<List<AttributeLookupItem>> = ResponseEntity.ok(attributeService.lookup(lookup))

    @GetMapping("/check-unique-validation")
    @PreAuthorize("hasAuthority('attributes.view')")
    fun checkUniqueValidation(
        @RequestParam code: String,
        @RequestParam entityType: String,
        @RequestParam value: String,
        @RequestParam(required = false) excludeId: UUID?,
    ): ResponseEntity<Map<String, Boolean>> =
        ResponseEntity.ok(
            mapOf("isUnique" to attributeService.checkUniqueValidation(code, entityType, value, excludeId)),
        )

    @GetMapping("/download")
    @PreAuthorize("hasAuthority('attributes.view')")
    fun download(
        @RequestParam(required = false, defaultValue = "false") export: Boolean,
    ): ResponseEntity<ByteArray> {
        if (!export) return ResponseEntity.noContent().build()
        val csv = attributeService.downloadCsv()
        val headers = HttpHeaders()
        headers.contentType = MediaType("text", "csv")
        headers.contentDisposition =
            ContentDisposition.attachment().filename("attributes.csv").build()
        return ResponseEntity.ok().headers(headers).body(csv.toByteArray(Charsets.UTF_8))
    }
}
