package com.synopticengine.api.identity.repo

import com.synopticengine.api.identity.domain.Role
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface RoleRepository : JpaRepository<Role, UUID> {
    fun findByName(name: String): Role?

    fun findAllByNameIn(names: Collection<String>): List<Role>
}
