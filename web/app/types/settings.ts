import type { PageResponse } from './api'

// ── Users ────────────────────────────────────────────────────────────────
export interface UserResponse {
    id: string
    email: string
    firstName: string
    lastName: string
    fullName: string
    isActive: boolean
    createdAt: string
    updatedAt: string
}

export type ViewPermission = 'ALL' | 'GLOBAL' | 'GROUP' | 'INDIVIDUAL'

export interface UserDetailResponse {
    id: string
    email: string
    firstName: string
    lastName: string
    fullName: string
    phone?: string
    isActive: boolean
    viewPermission: ViewPermission
    roles: string[]
    /** Each entry is `{ id, name }`. */
    groups: { id: string; name: string }[]
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
export interface StageResponse {
    id: string
    pipelineId: string
    name: string
    sortOrder: number
    color?: string
    probability: number
    code?: string
    createdAt?: string
    updatedAt?: string
}

export interface PipelineResponse {
    id: string
    name: string
    description?: string
    isActive: boolean
    isDefault: boolean
    rottenDays: number
    stages: StageResponse[]
    createdAt?: string
    updatedAt?: string
}

/** `{ id, sortOrder }` entries for `PUT /pipelines/{id}/stages/reorder`. */
export interface StageOrderEntry {
    id: string
    sortOrder: number
}

// ── Saved datagrid filters ───────────────────────────────────────────────
export interface DataGridFilterResponse {
    id: string
    userId: string
    name: string
    /** The list/datagrid this filter belongs to, e.g. `leads`. */
    src: string
    applied: Record<string, unknown>
    createdAt?: string
    updatedAt?: string
}

// ── Tenants ──────────────────────────────────────────────────────────────
export interface TenantResponse {
    id: string
    name: string
    slug: string
    status: string
    legalName?: string
    timezone?: string
    locale?: string
    createdAt?: string
    updatedAt?: string
}

// ── System config ──────────────────────────────────────────────────────────
export interface SystemConfigResponse {
    code: string
    /** Masked as `***` for secret items that have a value set. */
    value?: string
    groupName: string
    label: string
    /** `text` | `email` | `textarea` | `password` | `boolean` | `number` */
    type: string
    isSecret: boolean
    sortOrder: number
}

export interface SystemConfigGroupResponse {
    group: string
    items: SystemConfigResponse[]
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
