-- V016__web_form_create_lead.sql
-- Add Krayin-parity create_lead switch for web-form submissions.

ALTER TABLE public.web_forms
    ADD COLUMN IF NOT EXISTS create_lead boolean DEFAULT false NOT NULL;
