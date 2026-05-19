-- V004__email.sql
-- Baseline migration: email.

-- Tables
CREATE TABLE public.email_attachments (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    email_id uuid NOT NULL,
    attachment_path character varying(1000) NOT NULL,
    attachment_filename character varying(500) NOT NULL,
    content_type character varying(255),
    size bigint,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    tenant_id uuid DEFAULT '00000000-0000-0000-0000-000000000001'::uuid NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    deleted_at timestamp with time zone
);

CREATE TABLE public.email_tags (
    email_id uuid NOT NULL,
    tag_id uuid NOT NULL
);

CREATE TABLE public.emails (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    subject character varying(500),
    source character varying(100),
    name character varying(255),
    user_type character varying(100),
    is_read boolean DEFAULT false NOT NULL,
    folders jsonb DEFAULT '["inbox"]'::jsonb NOT NULL,
    "from" jsonb,
    sender jsonb,
    reply_to jsonb,
    cc jsonb,
    bcc jsonb,
    body text,
    reply text,
    unique_id character varying(500),
    message_id character varying(500),
    reference_ids jsonb,
    person_id uuid,
    parent_id uuid,
    lead_id uuid,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    tenant_id uuid DEFAULT '00000000-0000-0000-0000-000000000001'::uuid NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    deleted_at timestamp with time zone,
    status character varying(20) DEFAULT 'SENT'::character varying NOT NULL,
    CONSTRAINT chk_emails_status CHECK ((status IN ('DRAFT', 'SENT')))
);

CREATE TABLE public.lead_emails (
    lead_id uuid NOT NULL,
    email_id uuid NOT NULL
);

-- Primary keys
ALTER TABLE ONLY public.email_attachments
    ADD CONSTRAINT pk_email_attachments PRIMARY KEY (id);

ALTER TABLE ONLY public.email_tags
    ADD CONSTRAINT pk_email_tags PRIMARY KEY (email_id, tag_id);

ALTER TABLE ONLY public.emails
    ADD CONSTRAINT pk_emails PRIMARY KEY (id);

ALTER TABLE ONLY public.lead_emails
    ADD CONSTRAINT pk_lead_emails PRIMARY KEY (lead_id, email_id);

-- Foreign keys
ALTER TABLE ONLY public.email_attachments
    ADD CONSTRAINT email_attachments_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);

ALTER TABLE ONLY public.emails
    ADD CONSTRAINT emails_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);

ALTER TABLE ONLY public.email_attachments
    ADD CONSTRAINT fk_email_attachments_email FOREIGN KEY (email_id) REFERENCES public.emails(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.email_tags
    ADD CONSTRAINT fk_email_tags_email FOREIGN KEY (email_id) REFERENCES public.emails(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.email_tags
    ADD CONSTRAINT fk_email_tags_tag FOREIGN KEY (tag_id) REFERENCES public.tags(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.emails
    ADD CONSTRAINT fk_emails_lead FOREIGN KEY (lead_id) REFERENCES public.leads(id) ON DELETE SET NULL;

ALTER TABLE ONLY public.emails
    ADD CONSTRAINT fk_emails_parent FOREIGN KEY (parent_id) REFERENCES public.emails(id) ON DELETE SET NULL;

ALTER TABLE ONLY public.emails
    ADD CONSTRAINT fk_emails_person FOREIGN KEY (person_id) REFERENCES public.persons(id) ON DELETE SET NULL;

ALTER TABLE ONLY public.lead_emails
    ADD CONSTRAINT fk_lead_emails_email FOREIGN KEY (email_id) REFERENCES public.emails(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.lead_emails
    ADD CONSTRAINT fk_lead_emails_lead FOREIGN KEY (lead_id) REFERENCES public.leads(id) ON DELETE CASCADE;

-- Indexes
CREATE INDEX idx_email_attachments_email ON public.email_attachments USING btree (email_id);

CREATE INDEX idx_email_attachments_not_deleted ON public.email_attachments USING btree (id) WHERE (deleted_at IS NULL);

CREATE INDEX idx_emails_lead_id ON public.emails USING btree (lead_id);

CREATE INDEX idx_emails_not_deleted ON public.emails USING btree (id) WHERE (deleted_at IS NULL);

CREATE INDEX idx_emails_person_id ON public.emails USING btree (person_id);

CREATE INDEX idx_emails_status ON public.emails USING btree (tenant_id, status, created_at DESC) WHERE (deleted_at IS NULL);

CREATE INDEX idx_lead_emails_lead ON public.lead_emails USING btree (lead_id);
