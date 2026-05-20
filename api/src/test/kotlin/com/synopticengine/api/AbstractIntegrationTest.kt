package com.synopticengine.api

import com.synopticengine.api.identity.service.UserService
import com.synopticengine.api.support.TestAuth
import com.synopticengine.api.support.TestHttp
import com.synopticengine.api.support.TestSupportConfig
import org.junit.jupiter.api.Tag
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import tools.jackson.databind.ObjectMapper
import java.util.UUID

/**
 * Thin base for integration tests that need a booted Spring context. The HTTP and
 * auth machinery lives in [TestHttp] and [TestAuth] (wired by [TestSupportConfig]);
 * the methods below are backwards-compatible thin delegates so the 50+ existing
 * tests keep working unchanged. New tests should prefer `http.`/`auth.` directly.
 *
 * Carries the `integration` JUnit tag so the `unitTests` Gradle task can exclude
 * the slow, context-booting tests for sub-second local feedback.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class, TestSupportConfig::class)
@Tag("integration")
abstract class AbstractIntegrationTest {
    @Autowired protected lateinit var mockMvc: MockMvc

    @Autowired protected lateinit var objectMapper: ObjectMapper

    @Autowired protected lateinit var userService: UserService

    @Autowired protected lateinit var http: TestHttp

    @Autowired protected lateinit var auth: TestAuth

    protected fun adminToken(): String = auth.adminToken()

    protected fun salespersonToken(): String = auth.salespersonToken()

    protected fun tokenFor(
        roleNames: Set<String>,
        tenantId: UUID = com.synopticengine.api.shared.TenantContext.SEED_TENANT_ID,
    ): String = auth.tokenFor(roleNames, tenantId)

    protected fun login(
        email: String,
        password: String,
    ): String = auth.login(email, password)

    protected fun get(
        path: String,
        token: String?,
    ): MvcResult = http.get(path, token)

    protected fun post(
        path: String,
        token: String?,
        body: Any?,
    ): MvcResult = http.post(path, token, body)

    protected fun put(
        path: String,
        token: String?,
        body: Any?,
    ): MvcResult = http.put(path, token, body)

    protected fun delete(
        path: String,
        token: String?,
    ): MvcResult = http.delete(path, token)

    protected fun patch(
        path: String,
        token: String?,
        body: Any? = null,
    ): MvcResult = http.patch(path, token, body)

    protected fun multipart(
        path: String,
        token: String?,
        fileBytes: ByteArray,
        filename: String,
        params: Map<String, String> = emptyMap(),
    ): MvcResult = http.multipart(path, token, fileBytes, filename, params)

    protected fun MvcResult.status(): Int = response.status

    protected fun MvcResult.bodyAsMap(): Map<String, Any>? = http.bodyAsMap(this)

    protected fun MvcResult.bodyAsList(): List<Map<String, Any>>? = http.bodyAsList(this)
}
