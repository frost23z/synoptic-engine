import type { PageResponse } from './api'
import type { TagResponse } from './leads'

export interface PersonResponse {
    id: string
    firstName: string
    lastName: string
    fullName: string
    organizationId?: string
    email?: string
    phone?: string
    jobTitle?: string
    tags: TagResponse[]
    createdAt: string
    updatedAt: string
}

export interface OrganizationResponse {
    id: string
    name: string
    email?: string
    phone?: string
    website?: string
    address?: string
    createdAt: string
    updatedAt: string
}

export type PersonsPage = PageResponse<PersonResponse>
export type OrgsPage = PageResponse<OrganizationResponse>
