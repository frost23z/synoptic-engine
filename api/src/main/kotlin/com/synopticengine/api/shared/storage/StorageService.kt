package com.synopticengine.api.shared.storage

interface StorageService {
    fun store(
        directory: String,
        filename: String,
        bytes: ByteArray,
        contentType: String,
    ): String

    fun load(path: String): ByteArray

    fun delete(path: String)
}
