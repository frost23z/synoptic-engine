package com.synopticengine.api.shared.storage

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path

@Service
class LocalStorageService(
    @Value("\${synoptic.storage.root:./uploads}")
    private val storageRoot: String,
) : StorageService {
    override fun store(
        directory: String,
        filename: String,
        bytes: ByteArray,
        contentType: String,
    ): String {
        val dir = Path.of(storageRoot, directory)
        Files.createDirectories(dir)
        val file = dir.resolve(filename)
        Files.write(file, bytes)
        return file.toString()
    }

    override fun load(path: String): ByteArray {
        val file = Path.of(path)
        if (!Files.exists(file)) throw NoSuchElementException("File not found at path: $path")
        return Files.readAllBytes(file)
    }

    override fun delete(path: String) {
        Files.deleteIfExists(Path.of(path))
    }
}
