export interface AuthUser {
    id: string
    email: string
    fullName: string
    authorities: string[]
    avatar?: string
}

export interface TokenResponse {
    accessToken: string
    refreshToken: string
    tokenType: string
    userId: string
    email: string
    fullName: string
    authorities: string[]
}
