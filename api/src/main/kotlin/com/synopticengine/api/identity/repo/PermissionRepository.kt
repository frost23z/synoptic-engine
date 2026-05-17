package com.synopticengine.api.identity.repo

import com.synopticengine.api.identity.domain.Permission
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface PermissionRepository : JpaRepository<Permission, UUID> {
    fun findAllByKeyIn(keys: Collection<String>): List<Permission>

    @Query("SELECT p.key FROM Permission p")
    fun findAllKeys(): Set<String>
}
