package com.synopticengine.api.identity.repo

import com.synopticengine.api.identity.domain.Tenant
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface TenantRepository : JpaRepository<Tenant, UUID> {
    fun findBySlug(slug: String): Tenant?

    fun existsBySlug(slug: String): Boolean
}
