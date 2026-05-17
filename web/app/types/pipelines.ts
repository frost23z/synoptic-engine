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
