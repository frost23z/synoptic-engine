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
            tenantId: data.tenantId,
            email: data.email,
            fullName: data.fullName,
            authorities: data.authorities,
        }
    }

    async function login(email: string, password: string) {
        const data = await $fetch<TokenResponse>('/api/auth/login', {
            baseURL: config.public.apiBase,
            method: 'POST',
            body: { email, password },
        })
        setSession(data)
    }

    // Self-serve signup: creates a new company (tenant) + first admin, then logs in (the
    // backend returns tokens directly, so we set the session straight away).
    async function register(companyName: string, email: string, password: string) {
        const data = await $fetch<TokenResponse>('/api/auth/register', {
            baseURL: config.public.apiBase,
            method: 'POST',
            body: { companyName, email, password },
        })
        setSession(data)
    }

    // Single-flight refresh: concurrent 401s share one in-flight call instead
    // of each firing its own /api/auth/refresh (which races and storms the server).
    // Kept inside the store setup so it stays per-instance (SSR-safe).
    let refreshInFlight: Promise<void> | null = null

    function refresh() {
        if (!refreshInFlight) {
            refreshInFlight = (async () => {
                if (!refreshToken.value) throw new Error('No refresh token')
                const data = await $fetch<TokenResponse>('/api/auth/refresh', {
                    baseURL: config.public.apiBase,
                    method: 'POST',
                    body: { refreshToken: refreshToken.value },
                })
                setSession(data)
            })().finally(() => {
                refreshInFlight = null
            })
        }
        return refreshInFlight
    }

    async function fetchMe() {
        const data = await $fetch<{
            id: string
            tenantId: string
            email: string
            fullName: string
            authorities: string[]
        }>('/api/auth/me', {
            baseURL: config.public.apiBase,
            headers: { Authorization: `Bearer ${accessToken.value}` },
        })
        user.value = {
            id: data.id,
            tenantId: data.tenantId,
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
        register,
        refresh,
        fetchMe,
        logout,
    }
})
