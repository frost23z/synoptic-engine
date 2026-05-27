package com.synopticengine.api.crm

import com.synopticengine.api.crm.contact.repo.OrganizationRepository
import com.synopticengine.api.crm.contact.repo.PersonRepository
import com.synopticengine.api.crm.lead.repo.LeadRepository
import com.synopticengine.api.crm.quote.repo.QuoteRepository
import com.synopticengine.api.identity.repo.UserRepository
import com.synopticengine.api.inventory.product.repo.ProductRepository
import com.synopticengine.api.inventory.warehouse.repo.WarehouseRepository
import org.junit.jupiter.api.Test
import org.springframework.data.jpa.repository.Query
import kotlin.reflect.KClass
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.jvm.javaMethod
import kotlin.test.assertTrue

/**
 * T4.2 — Repository query-safety audit.
 *
 * Asserts that no `@Query` annotation value contains raw string concatenation
 * (`+`, `${`, or `||`) used to splice user-controlled input directly into SQL/JPQL.
 *
 * The only permitted LIKE pattern is the parameterized form:
 *   `LIKE LOWER(CONCAT('%', :param, '%'))`
 *
 * This is safe because the `%` wildcards are hardcoded literals in the SQL text;
 * the user-supplied value always flows through a bind parameter (`:param`), which
 * the JDBC driver treats as data, never as query structure.
 *
 * Pure unit test — no Spring context, no database.
 */
class RepositoryQuerySafetyTest {
    /**
     * Repository interfaces that carry user-facing search / filter queries.
     * Add new repositories here whenever a LIKE-style search is introduced.
     */
    private val repositoriesToAudit: List<KClass<*>> =
        listOf(
            LeadRepository::class,
            PersonRepository::class,
            OrganizationRepository::class,
            QuoteRepository::class,
            UserRepository::class,
            ProductRepository::class,
            WarehouseRepository::class,
        )

    // ── Dangerous patterns that must NOT appear in query strings ──────────────

    /**
     * Patterns that would indicate unsafe string concatenation in a query.
     * Note: Kotlin `${}` / `$param` cannot appear in annotation string values
     * (annotations require compile-time constants), so these patterns detect
     * JPQL string-concatenation via `+` or SQL `||` operators.
     */
    private val unsafePatterns =
        listOf(
            // SQL concatenation operator `||` spliced around a wildcard literal —
            // e.g. '%' || userInput || '%'  or  '%' || :bad (where the bind is on
            // the wrong side of ||, building the pattern via concatenation).
            Regex("""'%'\s*\|\|"""),
            Regex("""\|\|\s*'%'"""),
            // Java/Kotlin string `+` operator used to build LIKE patterns.
            // Annotation values are compile-time constants so Kotlin interpolation
            // (`$var`) is not possible; `+` is the only remaining risk.
            Regex("""'%'\s*\+"""),
            Regex("""\+\s*'%'"""),
        )

    @Test
    fun `all @Query annotations use bind parameters, not string concatenation`() {
        val violations = mutableListOf<String>()

        for (repoClass in repositoriesToAudit) {
            val javaInterface = repoClass.java
            // Walk both declared and inherited methods to catch default-method overrides.
            val allMethods =
                (javaInterface.declaredMethods + javaInterface.methods)
                    .distinctBy { it.name + it.parameterTypes.map { p -> p.name } }

            for (method in allMethods) {
                val query = method.getAnnotation(Query::class.java) ?: continue
                val value = query.value

                for (pattern in unsafePatterns) {
                    if (pattern.containsMatchIn(value)) {
                        violations +=
                            "${repoClass.simpleName}.${method.name}: " +
                            "unsafe pattern '${pattern.pattern}' found in @Query value"
                    }
                }
            }
        }

        assertTrue(
            violations.isEmpty(),
            "Found ${violations.size} unsafe query pattern(s):\n" + violations.joinToString("\n"),
        )
    }

    @Test
    fun `LIKE queries use CONCAT bind-parameter form`() {
        // Positive assertion: every LIKE expression that uses CONCAT must pair it with
        // a named bind parameter (:param), not a hard-coded literal or concatenated string.
        // The lookahead `(?!\s*:)` is applied BEFORE consuming any leading whitespace,
        // so it correctly catches spaces between the comma and a colon
        // (e.g. `CONCAT('%', :q, '%')` → comma immediately followed by ` :` → safe).
        // A naive `\s*(?!:)` would consume zero spaces and then test the space char,
        // not the colon, giving a false positive.
        val concatWithoutParam = Regex("""CONCAT\s*\(\s*'%'\s*,(?!\s*:)""")
        val violations = mutableListOf<String>()

        for (repoClass in repositoriesToAudit) {
            for (method in repoClass.java.methods) {
                val query = method.getAnnotation(Query::class.java) ?: continue
                val upperValue = query.value.uppercase()

                if (upperValue.contains("LIKE") && upperValue.contains("CONCAT")) {
                    if (concatWithoutParam.containsMatchIn(query.value)) {
                        violations +=
                            "${repoClass.simpleName}.${method.name}: " +
                            "CONCAT LIKE expression does not use a bind parameter"
                    }
                }
            }
        }

        assertTrue(
            violations.isEmpty(),
            "Found CONCAT LIKE expressions missing bind parameters:\n" + violations.joinToString("\n"),
        )
    }

    @Test
    fun `LeadRepository search uses named bind params`() {
        val methods = LeadRepository::class.memberFunctions
        val searchMethod = methods.firstOrNull { it.name == "search" }
        requireNotNull(searchMethod) { "LeadRepository.search not found" }

        val query =
            searchMethod.javaMethod
                ?.getAnnotation(Query::class.java)
                ?: error("LeadRepository.search has no @Query annotation")

        // The LIKE pattern must reference the named param :q, not a raw literal.
        assertTrue(query.value.contains(":q"), "search() must use :q bind parameter")
        assertTrue(
            query.value.uppercase().contains("CONCAT('%', :q, '%')".uppercase()),
            "search() LIKE pattern must be CONCAT('%', :q, '%') — found: ${query.value}",
        )
    }

    @Test
    fun `PersonRepository search uses named bind params`() {
        val methods = PersonRepository::class.java.methods
        val searchMethod = methods.firstOrNull { it.name == "search" }
        requireNotNull(searchMethod) { "PersonRepository.search not found" }

        val query = searchMethod.getAnnotation(Query::class.java) ?: error("PersonRepository.search has no @Query")
        assertTrue(query.value.contains(":q"), "search() must use :q bind parameter")
        assertTrue(
            query.value.uppercase().contains("CONCAT('%', :q, '%')".uppercase()),
            "search() LIKE pattern must be CONCAT('%', :q, '%')",
        )
    }

    @Test
    fun `OrganizationRepository search uses named bind params`() {
        val methods = OrganizationRepository::class.java.methods
        val searchMethod = methods.firstOrNull { it.name == "search" }
        requireNotNull(searchMethod) { "OrganizationRepository.search not found" }

        val query =
            searchMethod.getAnnotation(Query::class.java) ?: error("OrganizationRepository.search has no @Query")
        assertTrue(query.value.contains(":q"), "search() must use :q bind parameter")
    }

    @Test
    fun `UserRepository searchActive uses named bind params`() {
        val methods = UserRepository::class.java.methods
        val searchMethod = methods.firstOrNull { it.name == "searchActive" }
        requireNotNull(searchMethod) { "UserRepository.searchActive not found" }

        val query = searchMethod.getAnnotation(Query::class.java) ?: error("UserRepository.searchActive has no @Query")
        assertTrue(
            query.value.contains(":query") || query.value.contains(":q"),
            "UserRepository.searchActive must use a named bind parameter, found: ${query.value}",
        )
    }
}
