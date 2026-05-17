import type { PageResponse } from './api'

export type LeadStatus = 'open' | 'won' | 'lost' | 'abandoned'

export const LEAD_STATUS_COLOR: Record<LeadStatus, 'info' | 'success' | 'error' | 'neutral'> = {
    open: 'info',
    won: 'success',
    lost: 'error',
    abandoned: 'neutral',
}

export const LEAD_STATUS_LABEL: Record<LeadStatus, string> = {
    open: 'Open',
    won: 'Won',
    lost: 'Lost',
    abandoned: 'Abandoned',
}

export interface TagResponse {
    id: string
    name: string
    color: string
    createdAt: string
}

export interface LeadResponse {
    id: string
    title: string
    description?: string
    amount?: number
    expectedCloseDate?: string
    status: LeadStatus
    lostReason?: string
    closedAt?: string
    pipelineId: string
    stageId: string
    personId?: string
    organizationId?: string
    leadSourceId?: string
    leadTypeId?: string
    userId?: string
    tags: TagResponse[]
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

export interface KanbanStageGroup {
    stage: StageResponse
    leads: LeadResponse[]
    totalAmount: number
}

export type LeadsPage = PageResponse<LeadResponse>
