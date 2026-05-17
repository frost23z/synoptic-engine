import type { PageResponse } from './api'

export type MailFolder = 'inbox' | 'sent' | 'draft' | 'trash'

export interface EmailAddress {
    name?: string
    email?: string
    [key: string]: string | undefined
}

export interface EmailAttachment {
    id: string
    name: string
    size?: number
}

export interface EmailResponse {
    id: string
    subject: string
    body?: string
    name?: string
    source?: string
    userType?: string
    folders: string[]
    from?: EmailAddress
    sender?: EmailAddress
    replyTo?: EmailAddress[]
    cc?: EmailAddress[]
    bcc?: EmailAddress[]
    reply?: string
    uniqueId?: string
    messageId?: string
    referenceIds?: string[]
    personId?: string
    parentId?: string
    leadId?: string
    attachments?: EmailAttachment[]
    read: boolean
    createdAt: string
    updatedAt: string
}

export type EmailsPage = PageResponse<EmailResponse>
