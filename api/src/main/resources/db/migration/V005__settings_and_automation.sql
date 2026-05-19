-- V005__settings_and_automation.sql
-- Baseline migration: settings and automation.

-- Tables
CREATE TABLE public.attribute_options (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    attribute_id uuid NOT NULL,
    admin_name character varying(255) NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    tenant_id uuid DEFAULT '00000000-0000-0000-0000-000000000001'::uuid NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);

CREATE TABLE public.attribute_values (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    attribute_id uuid NOT NULL,
    entity_id uuid NOT NULL,
    entity_type character varying(100) NOT NULL,
    value text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    tenant_id uuid DEFAULT '00000000-0000-0000-0000-000000000001'::uuid NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);

CREATE TABLE public.attributes (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    code character varying(100) NOT NULL,
    admin_name character varying(255) NOT NULL,
    type character varying(50) NOT NULL,
    is_user_defined boolean DEFAULT true NOT NULL,
    lookup character varying(255),
    entity_type character varying(100) NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    tenant_id uuid DEFAULT '00000000-0000-0000-0000-000000000001'::uuid NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    deleted_at timestamp with time zone
);

CREATE TABLE public.data_imports (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    name character varying(255) NOT NULL,
    file_path character varying(1000) NOT NULL,
    entity_type character varying(100) NOT NULL,
    status character varying(50) DEFAULT 'PENDING'::character varying NOT NULL,
    error_count integer DEFAULT 0 NOT NULL,
    success_count integer DEFAULT 0 NOT NULL,
    errors jsonb,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    tenant_id uuid DEFAULT '00000000-0000-0000-0000-000000000001'::uuid NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);

CREATE TABLE public.email_templates (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    name character varying(255) NOT NULL,
    subject character varying(500) NOT NULL,
    content text NOT NULL,
    is_predefined boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    tenant_id uuid DEFAULT '00000000-0000-0000-0000-000000000001'::uuid NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    deleted_at timestamp with time zone
);

CREATE TABLE public.marketing_campaigns (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    name character varying(255) NOT NULL,
    subject character varying(500) NOT NULL,
    description text,
    event_id uuid,
    email_template_id uuid,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    tenant_id uuid DEFAULT '00000000-0000-0000-0000-000000000001'::uuid NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    deleted_at timestamp with time zone
);

CREATE TABLE public.marketing_events (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    tenant_id uuid DEFAULT '00000000-0000-0000-0000-000000000001'::uuid NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    deleted_at timestamp with time zone
);

CREATE TABLE public.system_configs (
    code character varying(255) NOT NULL,
    value text,
    group_name character varying(100) DEFAULT 'general'::character varying NOT NULL,
    label character varying(255) NOT NULL,
    type character varying(50) DEFAULT 'text'::character varying NOT NULL,
    is_secret boolean DEFAULT false NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    tenant_id uuid DEFAULT '00000000-0000-0000-0000-000000000001'::uuid NOT NULL
);

CREATE TABLE public.web_form_attributes (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    web_form_id uuid NOT NULL,
    attribute_id uuid NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    is_required boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    tenant_id uuid DEFAULT '00000000-0000-0000-0000-000000000001'::uuid NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);

CREATE TABLE public.web_forms (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    title character varying(255) NOT NULL,
    description text,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    tenant_id uuid DEFAULT '00000000-0000-0000-0000-000000000001'::uuid NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    deleted_at timestamp with time zone
);

CREATE TABLE public.webhooks (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    name character varying(255) NOT NULL,
    payload_url character varying(2048) NOT NULL,
    events jsonb DEFAULT '[]'::jsonb NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    tenant_id uuid DEFAULT '00000000-0000-0000-0000-000000000001'::uuid NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    deleted_at timestamp with time zone
);

CREATE TABLE public.workflow_action_runs (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    workflow_id uuid NOT NULL,
    event_name character varying(100) NOT NULL,
    entity_type character varying(100) NOT NULL,
    entity_id uuid NOT NULL,
    action_type character varying(100) NOT NULL,
    status character varying(20) NOT NULL,
    error_message text,
    payload jsonb,
    tenant_id uuid NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_workflow_action_runs_status CHECK ((status IN ('SUCCESS', 'FAILED', 'SKIPPED')))
);

CREATE TABLE public.workflows (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    event_name character varying(255) NOT NULL,
    conditions jsonb DEFAULT '[]'::jsonb NOT NULL,
    actions jsonb DEFAULT '[]'::jsonb NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    tenant_id uuid DEFAULT '00000000-0000-0000-0000-000000000001'::uuid NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    deleted_at timestamp with time zone,
    condition_type character varying(10) DEFAULT 'and'::character varying NOT NULL
);

-- Primary keys
ALTER TABLE ONLY public.attribute_options
    ADD CONSTRAINT pk_attribute_options PRIMARY KEY (id);

ALTER TABLE ONLY public.attribute_values
    ADD CONSTRAINT pk_attribute_values PRIMARY KEY (id);

ALTER TABLE ONLY public.attributes
    ADD CONSTRAINT pk_attributes PRIMARY KEY (id);

ALTER TABLE ONLY public.data_imports
    ADD CONSTRAINT pk_data_imports PRIMARY KEY (id);

ALTER TABLE ONLY public.email_templates
    ADD CONSTRAINT pk_email_templates PRIMARY KEY (id);

ALTER TABLE ONLY public.marketing_campaigns
    ADD CONSTRAINT pk_marketing_campaigns PRIMARY KEY (id);

ALTER TABLE ONLY public.marketing_events
    ADD CONSTRAINT pk_marketing_events PRIMARY KEY (id);

ALTER TABLE ONLY public.system_configs
    ADD CONSTRAINT pk_system_configs PRIMARY KEY (code);

ALTER TABLE ONLY public.web_form_attributes
    ADD CONSTRAINT pk_web_form_attributes PRIMARY KEY (id);

ALTER TABLE ONLY public.web_forms
    ADD CONSTRAINT pk_web_forms PRIMARY KEY (id);

ALTER TABLE ONLY public.webhooks
    ADD CONSTRAINT pk_webhooks PRIMARY KEY (id);

ALTER TABLE ONLY public.workflow_action_runs
    ADD CONSTRAINT pk_workflow_action_runs PRIMARY KEY (id);

ALTER TABLE ONLY public.workflows
    ADD CONSTRAINT pk_workflows PRIMARY KEY (id);

-- Unique constraints
ALTER TABLE ONLY public.attribute_values
    ADD CONSTRAINT uq_attribute_values UNIQUE (attribute_id, entity_id);

ALTER TABLE ONLY public.attributes
    ADD CONSTRAINT uq_attributes_code_entity UNIQUE (code, entity_type);

ALTER TABLE ONLY public.email_templates
    ADD CONSTRAINT uq_email_templates_tenant_name UNIQUE (tenant_id, name);

ALTER TABLE ONLY public.marketing_campaigns
    ADD CONSTRAINT uq_marketing_campaigns_tenant_name UNIQUE (tenant_id, name);

ALTER TABLE ONLY public.marketing_events
    ADD CONSTRAINT uq_marketing_events_tenant_name UNIQUE (tenant_id, name);

-- Foreign keys
ALTER TABLE ONLY public.attribute_options
    ADD CONSTRAINT attribute_options_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);

ALTER TABLE ONLY public.attribute_values
    ADD CONSTRAINT attribute_values_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);

ALTER TABLE ONLY public.attributes
    ADD CONSTRAINT attributes_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);

ALTER TABLE ONLY public.data_imports
    ADD CONSTRAINT data_imports_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);

ALTER TABLE ONLY public.email_templates
    ADD CONSTRAINT email_templates_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);

ALTER TABLE ONLY public.attribute_options
    ADD CONSTRAINT fk_attribute_options_attr FOREIGN KEY (attribute_id) REFERENCES public.attributes(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.attribute_values
    ADD CONSTRAINT fk_attribute_values_attr FOREIGN KEY (attribute_id) REFERENCES public.attributes(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.marketing_campaigns
    ADD CONSTRAINT fk_marketing_campaigns_event FOREIGN KEY (event_id) REFERENCES public.marketing_events(id) ON DELETE SET NULL;

ALTER TABLE ONLY public.marketing_campaigns
    ADD CONSTRAINT fk_marketing_campaigns_template FOREIGN KEY (email_template_id) REFERENCES public.email_templates(id) ON DELETE SET NULL;

ALTER TABLE ONLY public.web_form_attributes
    ADD CONSTRAINT fk_web_form_attributes_attr FOREIGN KEY (attribute_id) REFERENCES public.attributes(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.web_form_attributes
    ADD CONSTRAINT fk_web_form_attributes_form FOREIGN KEY (web_form_id) REFERENCES public.web_forms(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.workflow_action_runs
    ADD CONSTRAINT fk_workflow_action_runs_tenant FOREIGN KEY (tenant_id) REFERENCES public.tenants(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.workflow_action_runs
    ADD CONSTRAINT fk_workflow_action_runs_workflow FOREIGN KEY (workflow_id) REFERENCES public.workflows(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.marketing_campaigns
    ADD CONSTRAINT marketing_campaigns_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);

ALTER TABLE ONLY public.marketing_events
    ADD CONSTRAINT marketing_events_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);

ALTER TABLE ONLY public.system_configs
    ADD CONSTRAINT system_configs_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);

ALTER TABLE ONLY public.web_form_attributes
    ADD CONSTRAINT web_form_attributes_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);

ALTER TABLE ONLY public.web_forms
    ADD CONSTRAINT web_forms_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);

ALTER TABLE ONLY public.webhooks
    ADD CONSTRAINT webhooks_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);

ALTER TABLE ONLY public.workflows
    ADD CONSTRAINT workflows_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);

-- Indexes
CREATE INDEX idx_attribute_options_attr ON public.attribute_options USING btree (attribute_id);

CREATE INDEX idx_attribute_values_entity ON public.attribute_values USING btree (entity_id, entity_type);

CREATE INDEX idx_attributes_entity_type ON public.attributes USING btree (entity_type);

CREATE INDEX idx_attributes_not_deleted ON public.attributes USING btree (id) WHERE (deleted_at IS NULL);

CREATE INDEX idx_data_imports_entity_type ON public.data_imports USING btree (entity_type);

CREATE INDEX idx_data_imports_status ON public.data_imports USING btree (status);

CREATE INDEX idx_email_templates_not_deleted ON public.email_templates USING btree (id) WHERE (deleted_at IS NULL);

CREATE INDEX idx_marketing_campaigns_not_deleted ON public.marketing_campaigns USING btree (id) WHERE (deleted_at IS NULL);

CREATE INDEX idx_marketing_events_not_deleted ON public.marketing_events USING btree (id) WHERE (deleted_at IS NULL);

CREATE INDEX idx_web_form_attributes_form ON public.web_form_attributes USING btree (web_form_id);

CREATE INDEX idx_web_forms_not_deleted ON public.web_forms USING btree (id) WHERE (deleted_at IS NULL);

CREATE INDEX idx_webhooks_not_deleted ON public.webhooks USING btree (id) WHERE (deleted_at IS NULL);

CREATE INDEX idx_workflow_action_runs_entity ON public.workflow_action_runs USING btree (entity_type, entity_id, created_at DESC);

CREATE INDEX idx_workflow_action_runs_tenant ON public.workflow_action_runs USING btree (tenant_id, created_at DESC);

CREATE INDEX idx_workflow_action_runs_workflow ON public.workflow_action_runs USING btree (workflow_id, created_at DESC);

CREATE INDEX idx_workflows_event_name ON public.workflows USING btree (event_name);

CREATE INDEX idx_workflows_not_deleted ON public.workflows USING btree (id) WHERE (deleted_at IS NULL);
