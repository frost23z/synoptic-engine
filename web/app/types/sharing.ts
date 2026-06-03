import type { PageResponse } from './api'

// ── Enums (mirror the backend sharing domain) ──────────────────────────────
export type RelationshipType = 'PARENT_CHILD' | 'PARTNER' | 'SUPPLIER_CLIENT'
export type RelationshipStatus = 'PENDING' | 'ACTIVE' | 'SUSPENDED' | 'REVOKED'
export type AccessLevel = 'NONE' | 'READ' | 'COMMENT' | 'WRITE' | 'MANAGE'
export type CrossTenantAction =
    | 'VIEW'
    | 'EDIT'
    | 'COMMENT'
    | 'DELETE'
    | 'SHARE'
    | 'RESHARE'
    | 'REVOKE'

type BadgeColor = 'primary' | 'secondary' | 'success' | 'info' | 'warning' | 'error' | 'neutral'

export const RELATIONSHIP_TYPE_LABEL: Record<RelationshipType, string> = {
    PARENT_CHILD: 'Parent / Child',
    PARTNER: 'Partner',
    SUPPLIER_CLIENT: 'Supplier / Client',
}

export const RELATIONSHIP_STATUS_LABEL: Record<RelationshipStatus, string> = {
    PENDING: 'Pending',
    ACTIVE: 'Active',
    SUSPENDED: 'Suspended',
    REVOKED: 'Revoked',
}

export const RELATIONSHIP_STATUS_COLOR: Record<RelationshipStatus, BadgeColor> = {
    PENDING: 'warning',
    ACTIVE: 'success',
    SUSPENDED: 'neutral',
    REVOKED: 'error',
}

export const ACCESS_LEVEL_LABEL: Record<AccessLevel, string> = {
    NONE: 'None',
    READ: 'Read',
    COMMENT: 'Comment',
    WRITE: 'Write',
    MANAGE: 'Manage',
}

export const ACCESS_LEVEL_COLOR: Record<AccessLevel, BadgeColor> = {
    NONE: 'neutral',
    READ: 'info',
    COMMENT: 'secondary',
    WRITE: 'warning',
    MANAGE: 'success',
}

/** Access levels offered in pickers (NONE is not a grantable share level). */
export const GRANTABLE_ACCESS_LEVELS: AccessLevel[] = ['READ', 'COMMENT', 'WRITE', 'MANAGE']

export const CROSS_TENANT_ACTION_COLOR: Record<CrossTenantAction, BadgeColor> = {
    VIEW: 'neutral',
    EDIT: 'warning',
    COMMENT: 'secondary',
    DELETE: 'error',
    SHARE: 'info',
    RESHARE: 'info',
    REVOKE: 'error',
}

/** Shareable resource types — the plural path names the backend recognises. */
export const SHARE_RESOURCE_TYPES = [
    'leads',
    'persons',
    'organizations',
    'quotes',
    'products',
] as const
export type ShareResourceType = (typeof SHARE_RESOURCE_TYPES)[number]

export const RESOURCE_TYPE_LABEL: Record<string, string> = {
    leads: 'Leads',
    persons: 'Persons',
    organizations: 'Organizations',
    quotes: 'Quotes',
    products: 'Products',
}

// ── DTOs ───────────────────────────────────────────────────────────────────
export interface RelationshipResponse {
    id: string
    sourceTenantId: string
    targetTenantId: string
    type: RelationshipType
    status: RelationshipStatus
    initiatedBy: string
    acceptedBy?: string | null
    note?: string | null
    createdAt?: string | null
    acceptedAt?: string | null
    revokedAt?: string | null
}

export interface SharePolicyResponse {
    id: string
    relationshipId: string
    resourceType: string
    accessLevel: AccessLevel
    filterJson?: string | null
    cascadeJson?: string | null
    materialize: boolean
    createdBy: string
    createdAt?: string | null
    updatedAt?: string | null
    revokedAt?: string | null
}

export interface RecordShareResponse {
    id: string
    ownerTenantId: string
    consumerTenantId: string
    resourceType: string
    resourceId: string
    accessLevel: AccessLevel
    sharedBy: string
    expiresAt?: string | null
    revokedAt?: string | null
    note?: string | null
    createdAt?: string | null
}

export interface CrossTenantAuditDto {
    id: string
    ownerTenantId: string
    actorTenantId: string
    actorUserId: string
    resourceType: string
    resourceId: string
    action: CrossTenantAction
    payloadJson?: string | null
    at?: string | null
}

export interface TenantResponse {
    id: string
    name: string
    slug: string
    status: string
    legalName?: string | null
    timezone?: string | null
    locale?: string | null
    createdAt?: string | null
    updatedAt?: string | null
}

export type CrossTenantAuditPage = PageResponse<CrossTenantAuditDto>
