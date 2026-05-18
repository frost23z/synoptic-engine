package com.synopticengine.api.identity.domain

import com.synopticengine.api.shared.domain.AuditableEntity
import com.synopticengine.api.shared.domain.SoftDeletable
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.JoinTable
import jakarta.persistence.ManyToMany
import jakarta.persistence.Table
import org.hibernate.annotations.Filter
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction

@Entity
@Table(name = "users")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@SQLDelete(sql = "UPDATE users SET deleted_at = NOW() WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at IS NULL")
class User :
    AuditableEntity(),
    SoftDeletable {
    @Column(nullable = false, unique = true)
    var email: String = ""

    @Column(nullable = false)
    var passwordHash: String = ""

    @Column(nullable = false)
    var firstName: String = ""

    @Column(nullable = false)
    var lastName: String = ""

    @Column
    var phone: String? = null

    @Column(nullable = false)
    var isActive: Boolean = true

    @Column
    override var deletedAt: java.time.Instant? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var viewPermission: ViewPermission = ViewPermission.GLOBAL

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "user_roles",
        joinColumns = [JoinColumn(name = "user_id")],
        inverseJoinColumns = [JoinColumn(name = "role_id")],
    )
    val roles: MutableSet<Role> = mutableSetOf()

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "user_groups",
        joinColumns = [JoinColumn(name = "user_id")],
        inverseJoinColumns = [JoinColumn(name = "group_id")],
    )
    val groups: MutableSet<Group> = mutableSetOf()

    val fullName: String
        get() = "$firstName $lastName".trim()
}
