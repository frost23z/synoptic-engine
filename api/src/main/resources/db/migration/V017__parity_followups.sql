-- V017__parity_followups.sql
-- Backend parity follow-ups: quotes, email, marketing, webhooks.

ALTER TABLE public.quotes
    ADD COLUMN IF NOT EXISTS adjustment numeric(12,2) DEFAULT 0 NOT NULL;

ALTER TABLE public.emails
    ADD COLUMN IF NOT EXISTS "to" jsonb;

ALTER TABLE public.marketing_events
    ADD COLUMN IF NOT EXISTS event_date date;

ALTER TABLE public.webhooks
    ADD COLUMN IF NOT EXISTS secret character varying(255);
