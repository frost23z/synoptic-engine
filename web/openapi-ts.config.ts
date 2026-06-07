import { defineConfig } from '@hey-api/openapi-ts'

/**
 * Generates type-safe artifacts from the backend OpenAPI spec
 * (`../api-docs.json`, dumped from springdoc `/v3/api-docs`):
 *   - `app/api/types.gen.ts` — request/response/enum types (DTO source of truth)
 *   - `app/api/zod.gen.ts`   — Zod schemas mirroring backend bean-validation
 *
 * We intentionally do NOT generate an HTTP client — calls keep going through
 * `useApi()` (auth header + 401 single-flight refresh). Run `pnpm openapi:gen`.
 */
export default defineConfig({
    input: '../api-docs.json',
    output: {
        path: 'app/api',
    },
    plugins: ['@hey-api/typescript', 'zod'],
    postProcess: ['prettier'],
})
