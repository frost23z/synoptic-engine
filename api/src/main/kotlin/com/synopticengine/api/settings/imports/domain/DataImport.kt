package com.synopticengine.api.settings.imports.domain

import com.synopticengine.api.shared.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import org.hibernate.annotations.Filter
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

enum class ImportStatus { PENDING, PROCESSING, COMPLETED, FAILED }

@Entity
@Table(name = "data_imports")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
class DataImport : BaseEntity() {
    @Column(nullable = false)
    var name: String = ""

    @Column(name = "file_path", nullable = false, length = 1000)
    var filePath: String = ""

    @Column(name = "entity_type", nullable = false)
    var entityType: String = ""

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: ImportStatus = ImportStatus.PENDING

    @Column(name = "error_count", nullable = false)
    var errorCount: Int = 0

    @Column(name = "success_count", nullable = false)
    var successCount: Int = 0

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    var errors: List<Map<String, String>>? = null
}
