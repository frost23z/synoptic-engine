package com.synopticengine.api

import com.synopticengine.api.identity.service.UserService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
abstract class AbstractIntegrationTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var userService: UserService

    protected fun adminToken(): String = tokenFor(setOf("ADMIN"))

    protected fun salespersonToken(): String = tokenFor(setOf("SALESPERSON"))

    protected fun tokenFor(roleNames: Set<String>): String {
        val email = "test-${UUID.randomUUID()}@test.com"
        userService.create(
            email = email,
            password = "password123",
            firstName = "Test",
            lastName = "User",
            roleNames = roleNames,
        )
        return login(email, "password123")
    }

    protected fun login(
        email: String,
        password: String,
    ): String {
        val result =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mapOf("email" to email, "password" to password))),
                ).andReturn()
        val body = result.bodyAsMap()!!
        return body["accessToken"] as String
    }

    protected fun get(
        path: String,
        token: String?,
    ): MvcResult {
        val req = MockMvcRequestBuilders.get(path)
        if (token != null) req.header("Authorization", "Bearer $token")
        return mockMvc.perform(req).andReturn()
    }

    protected fun post(
        path: String,
        token: String?,
        body: Any?,
    ): MvcResult {
        val req =
            MockMvcRequestBuilders
                .post(path)
                .contentType(MediaType.APPLICATION_JSON)
        if (token != null) req.header("Authorization", "Bearer $token")
        if (body != null) req.content(objectMapper.writeValueAsString(body))
        return mockMvc.perform(req).andReturn()
    }

    protected fun put(
        path: String,
        token: String?,
        body: Any?,
    ): MvcResult {
        val req =
            MockMvcRequestBuilders
                .put(path)
                .contentType(MediaType.APPLICATION_JSON)
        if (token != null) req.header("Authorization", "Bearer $token")
        if (body != null) req.content(objectMapper.writeValueAsString(body))
        return mockMvc.perform(req).andReturn()
    }

    protected fun delete(
        path: String,
        token: String?,
    ): MvcResult {
        val req = MockMvcRequestBuilders.delete(path)
        if (token != null) req.header("Authorization", "Bearer $token")
        return mockMvc.perform(req).andReturn()
    }

    protected fun patch(
        path: String,
        token: String?,
        body: Any? = null,
    ): MvcResult {
        val req = MockMvcRequestBuilders.patch(path).contentType(MediaType.APPLICATION_JSON)
        if (token != null) req.header("Authorization", "Bearer $token")
        if (body != null) req.content(objectMapper.writeValueAsString(body))
        return mockMvc.perform(req).andReturn()
    }

    protected fun multipart(
        path: String,
        token: String?,
        fileBytes: ByteArray,
        filename: String,
        params: Map<String, String> = emptyMap(),
    ): MvcResult {
        val req =
            MockMvcRequestBuilders
                .multipart(path)
                .file(
                    org.springframework.mock.web.MockMultipartFile(
                        "file",
                        filename,
                        "text/csv",
                        fileBytes,
                    ),
                )
        if (token != null) req.header("Authorization", "Bearer $token")
        params.forEach { (k, v) -> req.param(k, v) }
        return mockMvc.perform(req).andReturn()
    }

    protected fun MvcResult.status(): Int = response.status

    protected fun MvcResult.bodyAsMap(): Map<String, Any>? {
        val content = response.contentAsString
        if (content.isBlank()) return null
        return objectMapper.readValue(content, object : TypeReference<Map<String, Any>>() {})
    }

    protected fun MvcResult.bodyAsList(): List<Map<String, Any>>? {
        val content = response.contentAsString
        if (content.isBlank()) return null
        return objectMapper.readValue(content, object : TypeReference<List<Map<String, Any>>>() {})
    }
}
