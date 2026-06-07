package com.synopticengine.api

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

/**
 * Drift gate for the backend↔frontend type-safety pipeline.
 *
 * The committed `api-docs.json` snapshot at the repo root is the input to the
 * frontend's Hey API generator (`web/openapi-ts.config.ts`). If the backend API
 * changes but the snapshot isn't regenerated, the generated TypeScript types/Zod
 * schemas silently go stale — exactly the DTO drift the pipeline exists to kill.
 *
 * This test boots the real context (reusing the Testcontainers setup) and GETs
 * `/v3/api-docs`, writing springdoc's **raw** response verbatim — so the output is
 * byte-identical to what the snapshot was dumped from. CI then runs
 * `git diff --exit-code api-docs.json`; a non-empty diff fails the build, forcing
 * the snapshot (and therefore the generated frontend artifacts) back in sync.
 *
 * The file is only written when `-Dopenapi.dump=true` is passed (CI does this), so
 * a normal local `./gradlew test` never dirties the working tree.
 */
class OpenApiSpecDriftTest : AbstractIntegrationTest() {
    @Test
    fun `OpenAPI spec is reachable and dumped for the drift gate when requested`() {
        val body = get("/v3/api-docs", null).response.contentAsString

        assertTrue(body.contains("\"openapi\""), "response should be a valid OpenAPI document")
        assertTrue(body.contains("\"operationId\""), "spec should expose stable operationIds")

        if (System.getProperty("openapi.dump") != null) {
            // Path is relative to the `api/` module dir (Gradle's working dir); the
            // snapshot lives at the repo root, one level up. Overridable for CI.
            val target = File(System.getProperty("openapi.dump.path") ?: "../api-docs.json")
            target.writeText(body)
        }
    }
}
