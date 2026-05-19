package com.synopticengine.api.support

import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper

/**
 * Thin wrapper around [MockMvc] that exposes the verb-and-body methods integration tests
 * actually use. Lives in its own class so the base test type doesn't need to depend on
 * Spring MVC types — and so tests that don't need auth/identity machinery can autowire
 * just this.
 */
class TestHttp(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
) {
    fun get(
        path: String,
        token: String?,
    ): MvcResult {
        val req = MockMvcRequestBuilders.get(path)
        if (token != null) req.header("Authorization", "Bearer $token")
        return mockMvc.perform(req).andReturn()
    }

    fun post(
        path: String,
        token: String?,
        body: Any?,
    ): MvcResult {
        val req = MockMvcRequestBuilders.post(path).contentType(MediaType.APPLICATION_JSON)
        if (token != null) req.header("Authorization", "Bearer $token")
        if (body != null) req.content(objectMapper.writeValueAsString(body))
        return mockMvc.perform(req).andReturn()
    }

    fun put(
        path: String,
        token: String?,
        body: Any?,
    ): MvcResult {
        val req = MockMvcRequestBuilders.put(path).contentType(MediaType.APPLICATION_JSON)
        if (token != null) req.header("Authorization", "Bearer $token")
        if (body != null) req.content(objectMapper.writeValueAsString(body))
        return mockMvc.perform(req).andReturn()
    }

    fun delete(
        path: String,
        token: String?,
    ): MvcResult {
        val req = MockMvcRequestBuilders.delete(path)
        if (token != null) req.header("Authorization", "Bearer $token")
        return mockMvc.perform(req).andReturn()
    }

    fun patch(
        path: String,
        token: String?,
        body: Any? = null,
    ): MvcResult {
        val req = MockMvcRequestBuilders.patch(path).contentType(MediaType.APPLICATION_JSON)
        if (token != null) req.header("Authorization", "Bearer $token")
        if (body != null) req.content(objectMapper.writeValueAsString(body))
        return mockMvc.perform(req).andReturn()
    }

    fun multipart(
        path: String,
        token: String?,
        fileBytes: ByteArray,
        filename: String,
        params: Map<String, String> = emptyMap(),
    ): MvcResult {
        val req =
            MockMvcRequestBuilders
                .multipart(path)
                .file(MockMultipartFile("file", filename, "text/csv", fileBytes))
        if (token != null) req.header("Authorization", "Bearer $token")
        params.forEach { (k, v) -> req.param(k, v) }
        return mockMvc.perform(req).andReturn()
    }

    fun bodyAsMap(result: MvcResult): Map<String, Any>? {
        val content = result.response.contentAsString
        if (content.isBlank()) return null
        return objectMapper.readValue(content, object : TypeReference<Map<String, Any>>() {})
    }

    fun bodyAsList(result: MvcResult): List<Map<String, Any>>? {
        val content = result.response.contentAsString
        if (content.isBlank()) return null
        return objectMapper.readValue(content, object : TypeReference<List<Map<String, Any>>>() {})
    }
}
