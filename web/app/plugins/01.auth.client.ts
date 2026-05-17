/**
 * On app init: if an access token cookie exists, fetch the current user
 * to validate the token and populate the user store.
 * Falls back to token refresh if the access token has expired.
 */
export default defineNuxtPlugin(async () => {
    const authStore = useAuthStore()

    if (!authStore.accessToken) return

    try {
        await authStore.fetchMe()
    } catch {
        // Access token likely expired — try refreshing
        try {
            await authStore.refresh()
            await authStore.fetchMe()
        } catch {
            // Refresh also failed — clear session silently
            authStore.accessToken = null
            authStore.refreshToken = null
            authStore.user = null
        }
    }
})
