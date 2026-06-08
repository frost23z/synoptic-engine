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
    /**
     * The app serves every endpoint under `server.servlet.context-path=/api`. MockMvc does
     * **not** apply the servlet context path automatically, so we set it explicitly here and
     * normalize each test's path to live under `/api`. This keeps the existing tests' literal
     * paths working unchanged — both the historical `/api/...` business paths and the bare
     * `/auth/...`, `/web-forms/...`, `/v3/api-docs` paths — while the handler/security layers
     * see the context-relative path (`/leads`, `/auth/login`, …) as they do in production.
     */
    private fun apiPath(path: String): String = if (path.startsWith("$CONTEXT_PATH/") || path == CONTEXT_PATH) path else "$CONTEXT_PATH$path"

    fun get(
        path: String,
        token: String?,
    ): MvcResult {
        val req = MockMvcRequestBuilders.get(apiPath(path)).contextPath(CONTEXT_PATH)
        if (token != null) req.header("Authorization", "Bearer $token")
        return mockMvc.perform(req).andReturn()
    }

    fun post(
        path: String,
        token: String?,
        body: Any?,
    ): MvcResult {
        val req = MockMvcRequestBuilders.post(apiPath(path)).contextPath(CONTEXT_PATH).contentType(MediaType.APPLICATION_JSON)
        if (token != null) req.header("Authorization", "Bearer $token")
        if (body != null) req.content(objectMapper.writeValueAsString(body))
        return mockMvc.perform(req).andReturn()
    }

    fun put(
        path: String,
        token: String?,
        body: Any?,
    ): MvcResult {
        val req = MockMvcRequestBuilders.put(apiPath(path)).contextPath(CONTEXT_PATH).contentType(MediaType.APPLICATION_JSON)
        if (token != null) req.header("Authorization", "Bearer $token")
        if (body != null) req.content(objectMapper.writeValueAsString(body))
        return mockMvc.perform(req).andReturn()
    }

    fun delete(
        path: String,
        token: String?,
    ): MvcResult {
        val req = MockMvcRequestBuilders.delete(apiPath(path)).contextPath(CONTEXT_PATH)
        if (token != null) req.header("Authorization", "Bearer $token")
        return mockMvc.perform(req).andReturn()
    }

    fun patch(
        path: String,
        token: String?,
        body: Any? = null,
    ): MvcResult {
        val req = MockMvcRequestBuilders.patch(apiPath(path)).contextPath(CONTEXT_PATH).contentType(MediaType.APPLICATION_JSON)
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
                .multipart(apiPath(path))
                .file(MockMultipartFile("file", filename, "text/csv", fileBytes))
        req.contextPath(CONTEXT_PATH)
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

    private companion object {
        /** Mirrors `server.servlet.context-path` in application.yaml. */
        const val CONTEXT_PATH = "/api"
    }
}
