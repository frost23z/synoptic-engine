import type { PageResponse } from './api'

export type ActivityType = 'CALL' | 'EMAIL' | 'MEETING' | 'TASK' | 'NOTE' | 'MESSAGE'

export const ACTIVITY_TYPE_ICON: Record<ActivityType, string> = {
    CALL: 'i-tabler-phone',
    EMAIL: 'i-tabler-mail',
    MEETING: 'i-tabler-users',
    TASK: 'i-tabler-checkbox',
    NOTE: 'i-tabler-note',
    MESSAGE: 'i-tabler-message',
}

export const ACTIVITY_TYPE_COLOR: Record<ActivityType, string> = {
    CALL: 'info',
    EMAIL: 'primary',
    MEETING: 'warning',
    TASK: 'secondary',
    NOTE: 'neutral',
    MESSAGE: 'success',
}

export interface ActivityResponse {
    id: string
    title: string
    type: ActivityType
    comment?: string
    scheduleFrom: string
    scheduleTo: string
    done: boolean
    leadId?: string
    userId?: string
    personId?: string
    organizationId?: string
    createdAt: string
    updatedAt: string
}

export type ActivitiesPage = PageResponse<ActivityResponse>

export interface ActivityFileResponse {
    id: string
    activityId: string
    name: string
    size?: number
    contentType?: string
    createdAt: string
}

export interface ActivityParticipantResponse {
    id: string
    fullName: string
    email: string
}
