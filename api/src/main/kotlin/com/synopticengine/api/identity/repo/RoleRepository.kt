package com.synopticengine.api.identity.repo

import com.synopticengine.api.identity.domain.Role
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface RoleRepository : JpaRepository<Role, UUID> {
    fun findByName(name: String): Role?

    fun findAllByNameIn(names: Collection<String>): List<Role>

    // Tenant-aware load. JpaRepository.findById hits `EntityManager.find()` and
    // bypasses Hibernate's `@Filter("tenantFilter")`; JPQL goes through query
    // rewriting so the filter applies. Callers in RoleService must use this.
    @Query("SELECT r FROM Role r WHERE r.id = :id")
    fun findActiveById(
        @Param("id") id: UUID,
    ): Role?
}
