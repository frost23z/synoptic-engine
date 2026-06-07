// Pipeline DTO bridged to the generated source of truth (OpenAPI). The previous
// hand-written shape had drifted (`default`/`active` instead of the backend's
// `isDefault`/`isActive`); the generated `PipelineResponse` is authoritative.
export type { PipelineResponse } from '~/api/types.gen'
