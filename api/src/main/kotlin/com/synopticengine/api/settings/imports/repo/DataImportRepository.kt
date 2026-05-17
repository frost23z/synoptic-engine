package com.synopticengine.api.settings.imports.repo

import com.synopticengine.api.settings.imports.domain.DataImport
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface DataImportRepository : JpaRepository<DataImport, UUID>
