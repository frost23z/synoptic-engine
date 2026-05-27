package com.synopticengine.api.inventory.transfer.repo

import com.synopticengine.api.inventory.transfer.domain.TransferOrder
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface TransferOrderRepository : JpaRepository<TransferOrder, UUID> {
    fun findAllByDeletedAtIsNull(): List<TransferOrder>

    fun findByIdAndDeletedAtIsNull(id: UUID): TransferOrder?
}
