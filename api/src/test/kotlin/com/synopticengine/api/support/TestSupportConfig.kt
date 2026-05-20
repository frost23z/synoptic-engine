package com.synopticengine.api.support

import com.synopticengine.api.identity.TenantApi
import com.synopticengine.api.identity.service.UserService
import com.synopticengine.api.support.factories.ActivityFactory
import com.synopticengine.api.support.factories.AttributeFactory
import com.synopticengine.api.support.factories.LeadFactory
import com.synopticengine.api.support.factories.OrganizationFactory
import com.synopticengine.api.support.factories.PersonFactory
import com.synopticengine.api.support.factories.PipelineResolver
import com.synopticengine.api.support.factories.ProductFactory
import com.synopticengine.api.support.factories.QuoteFactory
import com.synopticengine.api.support.factories.TagFactory
import com.synopticengine.api.support.factories.TenantProvisioner
import com.synopticengine.api.support.factories.WarehouseFactory
import jakarta.mail.internet.MimeMessage
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.JavaMailSenderImpl
import org.springframework.test.web.servlet.MockMvc
import tools.jackson.databind.ObjectMapper

/**
 * Wires the [TestHttp], [TestAuth] and entity factories as Spring beans so
 * integration tests can pull just the helpers they need (the base class autowires
 * a few common ones; individual tests can autowire the others).
 */
@TestConfiguration
class TestSupportConfig {
    /**
     * No-op `JavaMailSender` for integration tests. The 09 P1-3 fix lets SMTP failures
     * propagate; without overriding the bean, every test that triggers a non-draft
     * compose / quote-send would explode trying to connect to localhost:1025.
     */
    @Bean
    @Primary
    fun testMailSender(): JavaMailSender =
        object : JavaMailSenderImpl() {
            override fun send(mimeMessage: MimeMessage) {
                // intentionally a no-op; production wires a real SMTP sender
            }

            override fun send(vararg mimeMessages: MimeMessage) {
                // intentionally a no-op
            }
        }

    @Bean
    fun testHttp(
        mockMvc: MockMvc,
        objectMapper: ObjectMapper,
    ): TestHttp = TestHttp(mockMvc, objectMapper)

    @Bean
    fun testAuth(
        userService: UserService,
        testHttp: TestHttp,
    ): TestAuth = TestAuth(userService, testHttp)

    @Bean
    fun tagFactory(testHttp: TestHttp): TagFactory = TagFactory(testHttp)

    @Bean
    fun personFactory(testHttp: TestHttp): PersonFactory = PersonFactory(testHttp)

    @Bean
    fun organizationFactory(testHttp: TestHttp): OrganizationFactory = OrganizationFactory(testHttp)

    @Bean
    fun activityFactory(testHttp: TestHttp): ActivityFactory = ActivityFactory(testHttp)

    @Bean
    fun leadFactory(
        testHttp: TestHttp,
        personFactory: PersonFactory,
    ): LeadFactory = LeadFactory(testHttp, personFactory)

    @Bean
    fun productFactory(testHttp: TestHttp): ProductFactory = ProductFactory(testHttp)

    @Bean
    fun warehouseFactory(testHttp: TestHttp): WarehouseFactory = WarehouseFactory(testHttp)

    @Bean
    fun quoteFactory(
        testHttp: TestHttp,
        leadFactory: LeadFactory,
    ): QuoteFactory = QuoteFactory(testHttp, leadFactory)

    @Bean
    fun attributeFactory(testHttp: TestHttp): AttributeFactory = AttributeFactory(testHttp)

    @Bean
    fun pipelineResolver(testHttp: TestHttp): PipelineResolver = PipelineResolver(testHttp)

    @Bean
    fun tenantProvisioner(
        tenantApi: TenantApi,
        testAuth: TestAuth,
    ): TenantProvisioner = TenantProvisioner(tenantApi, testAuth)
}
