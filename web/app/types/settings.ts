import type { PageResponse } from './api'

// ── Users ────────────────────────────────────────────────────────────────
export interface UserResponse {
    id: string
    email: string
    firstName: string
    lastName: string
    fullName: string
    active: boolean
    createdAt: string
    updatedAt: string
}

export type UsersPage = PageResponse<UserResponse>

// ── Roles ────────────────────────────────────────────────────────────────
export interface RoleResponse {
    id: string
    name: string
    description?: string
    permissions: string[]
    createdAt: string
    updatedAt: string
}

export interface PermissionResponse {
    id: string
    name: string
    description?: string
}

// ── Groups ───────────────────────────────────────────────────────────────
export interface GroupResponse {
    id: string
    name: string
    description?: string
    createdAt: string
    updatedAt: string
}

// ── Pipelines ────────────────────────────────────────────────────────────
export interface PipelineResponse {
    id: string
    name: string
    description?: string
    rottenDays?: number
    default: boolean
    active: boolean
    createdAt: string
    updatedAt: string
}

export interface StageResponse {
    id: string
    pipelineId: string
    name: string
    sortOrder: number
    color?: string
    probability?: number
    code: string
    createdAt: string
    updatedAt: string
}

// ── Automation ───────────────────────────────────────────────────────────
export interface WorkflowResponse {
    id: string
    name: string
    description?: string
    eventName: string
    conditions: Record<string, string>[]
    actions: Record<string, string>[]
    active: boolean
    createdAt: string
    updatedAt: string
}

export interface WebhookResponse {
    id: string
    name: string
    payloadUrl: string
    events: string[]
    active: boolean
    createdAt: string
    updatedAt: string
}

// ── Marketing ────────────────────────────────────────────────────────────
export interface MarketingEventResponse {
    id: string
    name: string
    description?: string
    createdAt: string
    updatedAt: string
}

export interface MarketingCampaignResponse {
    id: string
    name: string
    subject: string
    description?: string
    eventId?: string
    emailTemplateId?: string
    createdAt: string
    updatedAt: string
}
