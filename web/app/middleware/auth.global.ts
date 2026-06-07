export default defineNuxtRouteMiddleware((to) => {
    const authStore = useAuthStore()

    const isPublic =
        to.path === '/login' || to.path === '/register' || to.path === '/reset-password'

    if (!authStore.isAuthenticated && !isPublic) {
        return navigateTo('/login')
    }

    if (authStore.isAuthenticated && isPublic) {
        return navigateTo('/')
    }
})
