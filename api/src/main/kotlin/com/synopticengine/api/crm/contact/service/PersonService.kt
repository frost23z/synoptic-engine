package com.synopticengine.api.crm.contact.service

import com.synopticengine.api.crm.contact.domain.Person
import com.synopticengine.api.crm.contact.repo.PersonRepository
import com.synopticengine.api.crm.contact.web.PersonResponse
import com.synopticengine.api.crm.lead.repo.LeadRepository
import com.synopticengine.api.crm.scoping.ScopeResolver
import com.synopticengine.api.crm.tag.repo.TagRepository
import com.synopticengine.api.crm.tag.service.toResponse
import com.synopticengine.api.shared.DomainEvent
import com.synopticengine.api.shared.web.PageResponse
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
@Transactional(readOnly = true)
class PersonService(
    private val personRepository: PersonRepository,
    private val tagRepository: TagRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val scopeResolver: ScopeResolver,
    private val leadRepository: LeadRepository,
) {
    fun findAll(pageable: Pageable): PageResponse<PersonResponse> {
        val scopeIds = scopeResolver.userIdsForCurrentUser()
        return if (scopeIds == null) {
            PageResponse.of(personRepository.findAllByDeletedAtIsNull(pageable)) { it.toResponse() }
        } else {
            PageResponse.of(personRepository.findAllScopedByCreatedBy(scopeIds, pageable)) { it.toResponse() }
        }
    }

    fun findById(id: UUID): PersonResponse =
        (personRepository.findActiveById(id) ?: throw NoSuchElementException("Person not found: $id")).toResponse()

    fun search(
        q: String,
        pageable: Pageable,
    ): PageResponse<PersonResponse> = PageResponse.of(personRepository.search(q, pageable)) { it.toResponse() }

    fun findByOrganization(
        organizationId: UUID,
        pageable: Pageable,
    ): PageResponse<PersonResponse> =
        PageResponse.of(
            personRepository.findAllByOrganizationIdAndDeletedAtIsNull(organizationId, pageable),
        ) { it.toResponse() }

    @Transactional
    fun create(
        firstName: String,
        lastName: String,
        organizationId: UUID?,
        email: String?,
        phone: String?,
        jobTitle: String?,
    ): PersonResponse {
        val person =
            personRepository.save(
                Person().apply {
                    this.firstName = firstName
                    this.lastName = lastName
                    this.organizationId = organizationId
                    this.email = email
                    this.phone = phone
                    this.jobTitle = jobTitle
                },
            )
        eventPublisher.publishEvent(DomainEvent("person.created", "Person", person.id!!))
        return person.toResponse()
    }

    @Transactional
    fun update(
        id: UUID,
        firstName: String,
        lastName: String,
        organizationId: UUID?,
        email: String?,
        phone: String?,
        jobTitle: String?,
    ): PersonResponse {
        val person = requirePerson(id)
        person.firstName = firstName
        person.lastName = lastName
        person.organizationId = organizationId
        person.email = email
        person.phone = phone
        person.jobTitle = jobTitle
        return personRepository.save(person).toResponse()
    }

    @Transactional
    fun delete(id: UUID) {
        val person = requirePerson(id)
        if (leadRepository.existsByPersonIdAndDeletedAtIsNull(id)) {
            throw IllegalStateException(
                "Cannot delete a person who has open leads. Detach the leads first.",
            )
        }
        person.deletedAt = Instant.now()
        personRepository.save(person)
    }

    /**
     * Soft-deletes each person that has no open leads; persons with leads are skipped.
     * Returns the ids that were actually deleted so the API can tell the caller which
     * mass-delete entries succeeded.
     */
    @Transactional
    fun massDestroy(ids: List<UUID>): List<UUID> {
        val deleted = mutableListOf<UUID>()
        ids.forEach { id ->
            personRepository.findActiveById(id)?.let { person ->
                if (!leadRepository.existsByPersonIdAndDeletedAtIsNull(id)) {
                    person.deletedAt = Instant.now()
                    personRepository.save(person)
                    deleted.add(id)
                }
            }
        }
        return deleted
    }

    @Transactional
    fun attachTag(
        personId: UUID,
        tagId: UUID,
    ): PersonResponse {
        val person = requirePerson(personId)
        val tag = tagRepository.findById(tagId).orElseThrow { NoSuchElementException("Tag not found: $tagId") }
        person.tags.add(tag)
        return personRepository.save(person).toResponse()
    }

    @Transactional
    fun detachTag(
        personId: UUID,
        tagId: UUID,
    ): PersonResponse {
        val person = requirePerson(personId)
        person.tags.removeIf { it.id == tagId }
        return personRepository.save(person).toResponse()
    }

    private fun requirePerson(id: UUID): Person =
        personRepository
            .findById(id)
            .orElseThrow { NoSuchElementException("Person not found: $id") }
            .also { if (it.deletedAt != null) throw NoSuchElementException("Person not found: $id") }
}

fun Person.toResponse() =
    PersonResponse(
        id = id!!,
        firstName = firstName,
        lastName = lastName,
        fullName = fullName,
        organizationId = organizationId,
        email = email,
        phone = phone,
        jobTitle = jobTitle,
        tags = tags.map { it.toResponse() },
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
