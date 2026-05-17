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
                options.headers = {
                    ...(options.headers as Record<string, string>),
                    Authorization: `Bearer ${token}`,
                }
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
