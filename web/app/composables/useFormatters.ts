export function useFormatters() {
    function formatCurrency(amount?: number | null): string {
        if (amount == null) return '—'
        return new Intl.NumberFormat('en-US', {
            style: 'currency',
            currency: 'USD',
            maximumFractionDigits: 0,
        }).format(amount)
    }

    function formatDate(dateStr?: string | null): string {
        if (!dateStr) return '—'
        return new Date(dateStr).toLocaleDateString('en-US', {
            month: 'short',
            day: 'numeric',
            year: 'numeric',
        })
    }

    function formatRelativeDate(dateStr?: string | null): string {
        if (!dateStr) return '—'
        const date = new Date(dateStr)
        const now = new Date()
        const diffMs = now.getTime() - date.getTime()
        const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24))
        if (diffDays === 0) return 'Today'
        if (diffDays === 1) return 'Yesterday'
        if (diffDays < 30) return `${diffDays}d ago`
        if (diffDays < 365) return `${Math.floor(diffDays / 30)}mo ago`
        return `${Math.floor(diffDays / 365)}y ago`
    }

    return { formatCurrency, formatDate, formatRelativeDate }
}
