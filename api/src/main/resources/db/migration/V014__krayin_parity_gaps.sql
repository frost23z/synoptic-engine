-- V014__krayin_parity_gaps.sql
-- Fill remaining backend parity gaps needed by frontend screens.

-- ── Leads ────────────────────────────────────────────────────────────────────
ALTER TABLE public.leads
    ADD COLUMN IF NOT EXISTS stage_updated_at timestamp with time zone DEFAULT now() NOT NULL;

UPDATE public.leads
SET stage_updated_at = COALESCE(stage_updated_at, updated_at, created_at, now())
WHERE stage_updated_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_leads_pipeline_stage_updated
    ON public.leads (tenant_id, pipeline_id, stage_updated_at)
    WHERE deleted_at IS NULL AND status = 'open';

-- ── Quotes ───────────────────────────────────────────────────────────────────
ALTER TABLE public.quotes DROP CONSTRAINT IF EXISTS chk_quotes_status;
UPDATE public.quotes SET status = 'rejected' WHERE status = 'declined';
ALTER TABLE public.quotes
    ADD CONSTRAINT chk_quotes_status CHECK ((status IN ('draft', 'sent', 'accepted', 'rejected', 'expired')));

-- ── Emails ───────────────────────────────────────────────────────────────────
ALTER TABLE public.emails DROP CONSTRAINT IF EXISTS chk_emails_status;
ALTER TABLE public.emails
    ADD CONSTRAINT chk_emails_status CHECK ((status IN ('DRAFT', 'OUTBOX', 'SENT', 'FAILED')));

-- ── Attributes ───────────────────────────────────────────────────────────────
ALTER TABLE public.attributes
    ADD COLUMN IF NOT EXISTS is_required boolean DEFAULT false NOT NULL,
    ADD COLUMN IF NOT EXISTS is_unique boolean DEFAULT false NOT NULL,
    ADD COLUMN IF NOT EXISTS quick_add boolean DEFAULT false NOT NULL,
    ADD COLUMN IF NOT EXISTS lookup_type character varying(100),
    ADD COLUMN IF NOT EXISTS validation_rules jsonb DEFAULT '{}'::jsonb NOT NULL;

-- ── Web forms ────────────────────────────────────────────────────────────────
ALTER TABLE public.web_forms
    ADD COLUMN IF NOT EXISTS background_color character varying(20),
    ADD COLUMN IF NOT EXISTS submit_success_action character varying(50) DEFAULT 'message' NOT NULL,
    ADD COLUMN IF NOT EXISTS submit_success_message text,
    ADD COLUMN IF NOT EXISTS submit_success_url character varying(2048),
    ADD COLUMN IF NOT EXISTS captcha_enabled boolean DEFAULT false NOT NULL;
