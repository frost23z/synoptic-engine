import type { AuthUser, TokenResponse } from '~/types/auth'

export const useAuthStore = defineStore('auth', () => {
    const config = useRuntimeConfig()

    const accessToken = useCookie<string | null>('syn_access', {
        default: () => null,
        maxAge: 60 * 15, // 15 min — matches backend
        sameSite: 'lax',
    })

    const refreshToken = useCookie<string | null>('syn_refresh', {
        default: () => null,
        maxAge: 60 * 60 * 24 * 7, // 7 days — matches backend
        sameSite: 'lax',
    })

    const user = ref<AuthUser | null>(null)

    const isAuthenticated = computed(() => !!accessToken.value)

    function setSession(data: TokenResponse) {
        accessToken.value = data.accessToken
        refreshToken.value = data.refreshToken
        user.value = {
            id: data.userId,
            email: data.email,
            fullName: data.fullName,
            authorities: data.authorities,
        }
    }

    async function login(email: string, password: string) {
        const data = await $fetch<TokenResponse>('/auth/login', {
            baseURL: config.public.apiBase,
            method: 'POST',
            body: { email, password },
        })
        setSession(data)
    }

    async function refresh() {
        if (!refreshToken.value) throw new Error('No refresh token')
        const data = await $fetch<TokenResponse>('/auth/refresh', {
            baseURL: config.public.apiBase,
            method: 'POST',
            body: { refreshToken: refreshToken.value },
        })
        setSession(data)
    }

    async function fetchMe() {
        const data = await $fetch<{
            id: string
            email: string
            fullName: string
            authorities: string[]
        }>('/auth/me', {
            baseURL: config.public.apiBase,
            headers: { Authorization: `Bearer ${accessToken.value}` },
        })
        user.value = {
            id: data.id,
            email: data.email,
            fullName: data.fullName,
            authorities: data.authorities,
        }
    }

    function logout() {
        accessToken.value = null
        refreshToken.value = null
        user.value = null
        return navigateTo('/login')
    }

    return {
        accessToken,
        refreshToken,
        user,
        isAuthenticated,
        login,
        refresh,
        fetchMe,
        logout,
    }
})
