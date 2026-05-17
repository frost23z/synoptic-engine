package com.synopticengine.api.shared.domain

import java.time.Instant

interface SoftDeletable {
    var deletedAt: Instant?
    val isDeleted: Boolean get() = deletedAt != null
}
