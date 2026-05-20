package com.synopticengine.api

import com.synopticengine.api.shared.bootstrap.PermissionRegistry
import org.junit.jupiter.api.Test
import org.springframework.aop.support.AopUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.RestController
import kotlin.test.assertTrue

/**
 * Phase 4 P1-4: per-module `*PermissionRegistryTest`s used to assert hardcoded key lists
 * that drifted from reality (e.g. `tags.create` and `pipelines.create` are wired in
 * `@PreAuthorize` but were never in `CrmPermissionRegistryTest`). This test inverts
 * the assertion: walk every `@PreAuthorize("hasAuthority('K')")` on a `@RestController`
 * and assert each `K` lives in some [PermissionRegistry].
 *
 * If you add a new `@PreAuthorize` for a permission you haven't registered, this test
 * will catch it — no manual update needed.
 */
class AllPermissionsRegisteredTest : AbstractIntegrationTest() {
    @Autowired private lateinit var applicationContext: ApplicationContext

    @Autowired private lateinit var registries: List<PermissionRegistry>

    @Test
    fun `every @PreAuthorize hasAuthority key is registered in some PermissionRegistry`() {
        val annotated = scanAnnotatedKeys()
        assertTrue(annotated.isNotEmpty(), "Scanner returned no keys — wiring must have changed")

        val registered = registries.flatMap { it.permissions() }.map { it.key }.toSet()
        val missing = annotated - registered

        assertTrue(
            missing.isEmpty(),
            "Permission keys appear in @PreAuthorize annotations but no PermissionRegistry: $missing.\n" +
                "Either register them, or remove the @PreAuthorize annotation. " +
                "Bootstrap won't seed the role <-> permission edges otherwise.",
        )
    }

    @Test
    fun `every PermissionRegistry declares at least one permission tagged with a module`() {
        // Catches an accidentally-empty registry (would mean its module's bootstrap
        // role-edges silently vanish) and any permission missing its module tag.
        for (registry in registries) {
            val perms = registry.permissions()
            assertTrue(perms.isNotEmpty(), "${registry::class.simpleName} returned no permissions")
            perms.forEach { p ->
                assertTrue(
                    p.module.isNotBlank(),
                    "${registry::class.simpleName} permission ${p.key} has a blank module tag",
                )
            }
        }
    }

    private fun scanAnnotatedKeys(): Set<String> {
        // Match `hasAuthority('K')` and `hasAuthority("K")`. Kotlin `const val` templates
        // (e.g. `${SharingPermissions.RECORDS_SHARE}`) are resolved at compile time, so the
        // reflective annotation value already carries the literal string.
        val pattern = Regex("""hasAuthority\(\s*['"]([^'"]+)['"]\s*\)""")
        val controllers =
            applicationContext.getBeansWithAnnotation(RestController::class.java).values +
                applicationContext.getBeansWithAnnotation(Controller::class.java).values
        val keys = mutableSetOf<String>()
        for (bean in controllers.toSet()) {
            val type = AopUtils.getTargetClass(bean)
            // Class-level @PreAuthorize (rare today but supported).
            type.getAnnotation(PreAuthorize::class.java)?.let { addAll(it, pattern, keys) }
            for (method in type.declaredMethods) {
                method.getAnnotation(PreAuthorize::class.java)?.let { addAll(it, pattern, keys) }
            }
        }
        return keys
    }

    private fun addAll(
        annotation: PreAuthorize,
        pattern: Regex,
        keys: MutableSet<String>,
    ) {
        pattern.findAll(annotation.value).forEach { keys.add(it.groupValues[1]) }
    }
}
