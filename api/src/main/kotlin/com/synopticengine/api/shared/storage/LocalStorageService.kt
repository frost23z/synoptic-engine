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
    private val rootPath: Path = Path.of(storageRoot).toAbsolutePath().normalize()

    override fun store(
        directory: String,
        filename: String,
        bytes: ByteArray,
        contentType: String,
    ): String {
        val relativeDirectory = Path.of(directory).normalize()
        require(!relativeDirectory.isAbsolute && !relativeDirectory.startsWith("..")) {
            "Invalid storage directory: $directory"
        }
        val sanitizedFilename = Path.of(filename).fileName?.toString() ?: throw IllegalArgumentException("Invalid filename")
        val dir = rootPath.resolve(relativeDirectory).normalize()
        require(dir.startsWith(rootPath)) { "Storage directory escapes root: $directory" }
        Files.createDirectories(dir)
        val file = dir.resolve(sanitizedFilename).normalize()
        require(file.startsWith(rootPath)) { "Storage file path escapes root" }
        Files.write(file, bytes)
        return rootPath.relativize(file).toString().replace('\\', '/')
    }

    override fun load(path: String): ByteArray {
        val file = resolveStoredPath(path)
        if (!Files.exists(file)) throw NoSuchElementException("File not found at path: $path")
        return Files.readAllBytes(file)
    }

    override fun delete(path: String) {
        Files.deleteIfExists(resolveStoredPath(path))
    }

    private fun resolveStoredPath(path: String): Path {
        val raw = Path.of(path)
        val resolved =
            if (raw.isAbsolute) {
                raw.normalize()
            } else {
                rootPath.resolve(raw).normalize()
            }
        require(resolved.startsWith(rootPath)) { "Storage path escapes root: $path" }
        return resolved
    }
}
