import type { PageResponse } from './api'

export type QuoteStatus = 'draft' | 'sent' | 'accepted' | 'declined'

export const QUOTE_STATUS_COLOR: Record<QuoteStatus, 'neutral' | 'info' | 'success' | 'error'> = {
    draft: 'neutral',
    sent: 'info',
    accepted: 'success',
    declined: 'error',
}

export const QUOTE_STATUS_LABEL: Record<QuoteStatus, string> = {
    draft: 'Draft',
    sent: 'Sent',
    accepted: 'Accepted',
    declined: 'Declined',
}

export interface QuoteItemResponse {
    id: string
    productId: string
    quantity: number
    unitPrice: number
    discount: number
    lineTotal: number
}

export interface QuoteResponse {
    id: string
    leadId: string
    userId?: string
    title: string
    status: QuoteStatus
    discount: number
    tax: number
    terms?: string
    expiredAt?: string
    items: QuoteItemResponse[]
    subTotal: number
    grandTotal: number
    createdAt: string
    updatedAt: string
}

export type QuotesPage = PageResponse<QuoteResponse>

export interface LeadProductResponse {
    productId: string
    productName: string
    quantity: number
    unitPrice: number
}
