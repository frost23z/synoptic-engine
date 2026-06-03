/**
 * Returns a $fetch instance pre-configured with:
 *  - base URL from runtimeConfig
 *  - Authorization header from auth store
 *  - Auto-logout on 401 (after attempted refresh)
 */
export const useApi = () => {
    const config = useRuntimeConfig()
    const authStore = useAuthStore()

    return $fetch.create({
        baseURL: config.public.apiBase as string,
        onRequest({ options }) {
            const token = authStore.accessToken
            if (token) {
                const headers = new Headers(options.headers as HeadersInit)
                headers.set('Authorization', `Bearer ${token}`)
                options.headers = headers
            }
        },
        async onResponseError({ response }) {
            if (response.status === 401) {
                try {
                    await authStore.refresh()
                } catch {
                    await authStore.logout()
                }
            }
        },
    })
}
