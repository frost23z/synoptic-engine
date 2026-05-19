package com.synopticengine.api.support

import com.synopticengine.api.identity.service.UserService
import com.synopticengine.api.support.factories.ActivityFactory
import com.synopticengine.api.support.factories.LeadFactory
import com.synopticengine.api.support.factories.OrganizationFactory
import com.synopticengine.api.support.factories.PersonFactory
import com.synopticengine.api.support.factories.TagFactory
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.test.web.servlet.MockMvc
import tools.jackson.databind.ObjectMapper

/**
 * Wires the [TestHttp], [TestAuth] and entity factories as Spring beans so
 * integration tests can pull just the helpers they need (the base class autowires
 * a few common ones; individual tests can autowire the others).
 */
@TestConfiguration
class TestSupportConfig {
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
}
