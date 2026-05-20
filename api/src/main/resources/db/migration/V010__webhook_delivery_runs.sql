-- 09 P3-1: durable audit trail for webhook deliveries. The dispatcher previously
-- only wrote success/failure to SLF4J, leaving operators with no way to see why
-- subscribers stopped receiving events. Mirrors workflow_action_runs:
--   - One row per dispatch attempt.
--   - status in (SUCCESS, FAILED) — no SKIPPED equivalent since the dispatcher
--     pre-filters non-matching webhooks before logging.
--   - response_code / response_body captured for SUCCESS; error captured for FAILED.

CREATE TABLE public.webhook_delivery_runs (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    webhook_id uuid NOT NULL,
    event_name character varying(255) NOT NULL,
    entity_type character varying(100) NOT NULL,
    entity_id uuid NOT NULL,
    status character varying(20) NOT NULL,
    response_code integer,
    response_body text,
    error_message text,
    tenant_id uuid NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT pk_webhook_delivery_runs PRIMARY KEY (id),
    CONSTRAINT chk_webhook_delivery_runs_status CHECK (status IN ('SUCCESS', 'FAILED')),
    CONSTRAINT fk_webhook_delivery_runs_tenant FOREIGN KEY (tenant_id) REFERENCES public.tenants(id),
    CONSTRAINT fk_webhook_delivery_runs_webhook FOREIGN KEY (webhook_id) REFERENCES public.webhooks(id) ON DELETE CASCADE
);

CREATE INDEX idx_webhook_delivery_runs_webhook_created
    ON public.webhook_delivery_runs (webhook_id, created_at DESC);

CREATE INDEX idx_webhook_delivery_runs_tenant_created
    ON public.webhook_delivery_runs (tenant_id, created_at DESC);
