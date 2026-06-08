/**
 * Resolves and validates the API base URL at app startup.
 *
 * `runtimeConfig.public.apiBase` has no hardcoded fallback (see nuxt.config.ts) so a production
 * build can never silently default to localhost. This plugin enforces that contract:
 *   - In development, if `NUXT_PUBLIC_API_BASE` is unset, fall back to the local API and warn.
 *   - In production, if it is unset, throw immediately — fail fast rather than ship a broken
 *     build that points nowhere (or at the wrong host).
 *
 * Runs on both server and client so a misconfigured deploy fails on the very first render.
 */
export default defineNuxtPlugin(() => {
    const config = useRuntimeConfig()

    if (!config.public.apiBase) {
        if (import.meta.dev) {
            config.public.apiBase = 'http://localhost:8090'
            console.warn(
                '[api-base] NUXT_PUBLIC_API_BASE is not set — defaulting to http://localhost:8090 (dev only).'
            )
        } else {
            throw new Error(
                '[api-base] NUXT_PUBLIC_API_BASE is not set. Set it to the backend base URL (e.g. https://your-api.fly.dev) before building/serving in production.'
            )
        }
    }
})
