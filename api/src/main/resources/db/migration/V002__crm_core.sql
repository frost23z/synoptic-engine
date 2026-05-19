-- V002__crm_core.sql
-- Baseline migration: crm core.

-- Tables
CREATE TABLE public.activities (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    lead_id uuid,
    user_id uuid,
    person_id uuid,
    organization_id uuid,
    title character varying(255) NOT NULL,
    type character varying(50) NOT NULL,
    comment text,
    is_done boolean DEFAULT false NOT NULL,
    schedule_from timestamp with time zone,
    schedule_to timestamp with time zone,
    created_by uuid,
    updated_by uuid,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    product_id uuid,
    warehouse_id uuid,
    tenant_id uuid DEFAULT '00000000-0000-0000-0000-000000000001'::uuid NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    location character varying(255),
    additional jsonb,
    CONSTRAINT activities_type_check CHECK ((type IN ('CALL', 'MEETING', 'LUNCH', 'NOTE', 'FILE', 'TASK', 'EMAIL', 'MESSAGE'))),
    CONSTRAINT chk_activities_type CHECK ((type IN ('CALL', 'EMAIL', 'MEETING', 'TASK', 'NOTE', 'MESSAGE')))
);

CREATE TABLE public.activity_files (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    activity_id uuid NOT NULL,
    name character varying(500) NOT NULL,
    path character varying(1000) NOT NULL,
    size bigint,
    content_type character varying(255),
    tenant_id uuid DEFAULT '00000000-0000-0000-0000-000000000001'::uuid NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE public.activity_participants (
    activity_id uuid NOT NULL,
    participant_user_id uuid,
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    person_id uuid,
    tenant_id uuid DEFAULT '00000000-0000-0000-0000-000000000001'::uuid NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_activity_participants_one_target CHECK ((((participant_user_id IS NOT NULL) AND (person_id IS NULL)) OR ((participant_user_id IS NULL) AND (person_id IS NOT NULL))))
);

CREATE TABLE public.lead_products (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    lead_id uuid NOT NULL,
    product_id uuid NOT NULL,
    quantity integer DEFAULT 1 NOT NULL,
    unit_price numeric(15,2),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    tenant_id uuid DEFAULT '00000000-0000-0000-0000-000000000001'::uuid NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);

CREATE TABLE public.lead_sources (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    name character varying(100) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    tenant_id uuid DEFAULT '00000000-0000-0000-0000-000000000001'::uuid NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);

CREATE TABLE public.lead_tags (
    lead_id uuid NOT NULL,
    tag_id uuid NOT NULL
);

CREATE TABLE public.lead_types (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    name character varying(100) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    tenant_id uuid DEFAULT '00000000-0000-0000-0000-000000000001'::uuid NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);

CREATE TABLE public.leads (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    title character varying(255) NOT NULL,
    description text,
    amount numeric(15,2),
    expected_close_date date,
    status character varying(50) DEFAULT 'open'::character varying NOT NULL,
    lost_reason text,
    closed_at timestamp with time zone,
    pipeline_id uuid NOT NULL,
    stage_id uuid NOT NULL,
    person_id uuid,
    organization_id uuid,
    lead_source_id uuid,
    lead_type_id uuid,
    user_id uuid,
    created_by uuid,
    updated_by uuid,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    tenant_id uuid DEFAULT '00000000-0000-0000-0000-000000000001'::uuid NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT chk_leads_status CHECK ((status IN ('open', 'won', 'lost', 'abandoned')))
);

CREATE TABLE public.organizations (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    name character varying(255) NOT NULL,
    email character varying(255),
    phone character varying(50),
    website character varying(255),
    address text,
    created_by uuid,
    updated_by uuid,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    tenant_id uuid DEFAULT '00000000-0000-0000-0000-000000000001'::uuid NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);

CREATE TABLE public.person_tags (
    person_id uuid NOT NULL,
    tag_id uuid NOT NULL
);

CREATE TABLE public.persons (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    organization_id uuid,
    first_name character varying(100) NOT NULL,
    last_name character varying(100) NOT NULL,
    email character varying(255),
    phone character varying(50),
    job_title character varying(100),
    created_by uuid,
    updated_by uuid,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    tenant_id uuid DEFAULT '00000000-0000-0000-0000-000000000001'::uuid NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    emails jsonb DEFAULT '[]'::jsonb NOT NULL,
    contact_numbers jsonb DEFAULT '[]'::jsonb NOT NULL
);

CREATE TABLE public.pipelines (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    name character varying(100) NOT NULL,
    description character varying(500),
    is_active boolean DEFAULT true NOT NULL,
    is_default boolean DEFAULT false NOT NULL,
    rotten_days integer DEFAULT 30 NOT NULL,
    created_by uuid,
    updated_by uuid,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    tenant_id uuid DEFAULT '00000000-0000-0000-0000-000000000001'::uuid NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);

CREATE TABLE public.quote_items (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    quote_id uuid NOT NULL,
    product_id uuid,
    quantity integer DEFAULT 1 NOT NULL,
    unit_price numeric(15,2) NOT NULL,
    discount numeric(5,2) DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    tenant_id uuid DEFAULT '00000000-0000-0000-0000-000000000001'::uuid NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT chk_quote_items_price CHECK ((unit_price >= (0)::numeric)),
    CONSTRAINT chk_quote_items_qty CHECK ((quantity > 0))
);

CREATE TABLE public.quotes (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    lead_id uuid NOT NULL,
    user_id uuid,
    title character varying(255) NOT NULL,
    status character varying(50) DEFAULT 'draft'::character varying NOT NULL,
    discount numeric(5,2) DEFAULT 0 NOT NULL,
    tax numeric(5,2) DEFAULT 0 NOT NULL,
    terms text,
    expired_at date,
    created_by uuid,
    updated_by uuid,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    tenant_id uuid DEFAULT '00000000-0000-0000-0000-000000000001'::uuid NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    person_id uuid,
    billing_address jsonb,
    shipping_address jsonb,
    CONSTRAINT chk_quotes_status CHECK ((status IN ('draft', 'sent', 'accepted', 'declined')))
);

CREATE TABLE public.stages (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    pipeline_id uuid NOT NULL,
    name character varying(100) NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    color character varying(20),
    probability integer DEFAULT 0 NOT NULL,
    code character varying(50),
    created_by uuid,
    updated_by uuid,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    tenant_id uuid DEFAULT '00000000-0000-0000-0000-000000000001'::uuid NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT chk_stages_code CHECK (((code IN ('won', 'lost')) OR (code IS NULL))),
    CONSTRAINT chk_stages_prob CHECK (((probability >= 0) AND (probability <= 100)))
);

CREATE TABLE public.tags (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    name character varying(100) NOT NULL,
    color character varying(20),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    tenant_id uuid DEFAULT '00000000-0000-0000-0000-000000000001'::uuid NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);

-- Primary keys
ALTER TABLE ONLY public.activities
    ADD CONSTRAINT pk_activities PRIMARY KEY (id);

ALTER TABLE ONLY public.activity_files
    ADD CONSTRAINT pk_activity_files PRIMARY KEY (id);

ALTER TABLE ONLY public.activity_participants
    ADD CONSTRAINT pk_activity_participants PRIMARY KEY (id);

ALTER TABLE ONLY public.lead_products
    ADD CONSTRAINT pk_lead_products PRIMARY KEY (id);

ALTER TABLE ONLY public.lead_sources
    ADD CONSTRAINT pk_lead_sources PRIMARY KEY (id);

ALTER TABLE ONLY public.lead_tags
    ADD CONSTRAINT pk_lead_tags PRIMARY KEY (lead_id, tag_id);

ALTER TABLE ONLY public.lead_types
    ADD CONSTRAINT pk_lead_types PRIMARY KEY (id);

ALTER TABLE ONLY public.leads
    ADD CONSTRAINT pk_leads PRIMARY KEY (id);

ALTER TABLE ONLY public.organizations
    ADD CONSTRAINT pk_organizations PRIMARY KEY (id);

ALTER TABLE ONLY public.person_tags
    ADD CONSTRAINT pk_person_tags PRIMARY KEY (person_id, tag_id);

ALTER TABLE ONLY public.persons
    ADD CONSTRAINT pk_persons PRIMARY KEY (id);

ALTER TABLE ONLY public.pipelines
    ADD CONSTRAINT pk_pipelines PRIMARY KEY (id);

ALTER TABLE ONLY public.quote_items
    ADD CONSTRAINT pk_quote_items PRIMARY KEY (id);

ALTER TABLE ONLY public.quotes
    ADD CONSTRAINT pk_quotes PRIMARY KEY (id);

ALTER TABLE ONLY public.stages
    ADD CONSTRAINT pk_stages PRIMARY KEY (id);

ALTER TABLE ONLY public.tags
    ADD CONSTRAINT pk_tags PRIMARY KEY (id);

-- Unique constraints
ALTER TABLE ONLY public.lead_products
    ADD CONSTRAINT uq_lead_products UNIQUE (lead_id, product_id);

ALTER TABLE ONLY public.lead_sources
    ADD CONSTRAINT uq_lead_sources_tenant_name UNIQUE (tenant_id, name);

ALTER TABLE ONLY public.lead_types
    ADD CONSTRAINT uq_lead_types_tenant_name UNIQUE (tenant_id, name);

ALTER TABLE ONLY public.tags
    ADD CONSTRAINT uq_tags_tenant_name UNIQUE (tenant_id, name);

-- Foreign keys
ALTER TABLE ONLY public.activities
    ADD CONSTRAINT activities_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);

ALTER TABLE ONLY public.activity_files
    ADD CONSTRAINT activity_files_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);

ALTER TABLE ONLY public.activity_participants
    ADD CONSTRAINT activity_participants_person_id_fkey FOREIGN KEY (person_id) REFERENCES public.persons(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.activity_participants
    ADD CONSTRAINT activity_participants_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);

ALTER TABLE ONLY public.activities
    ADD CONSTRAINT fk_activities_lead FOREIGN KEY (lead_id) REFERENCES public.leads(id) ON DELETE SET NULL;

ALTER TABLE ONLY public.activities
    ADD CONSTRAINT fk_activities_org FOREIGN KEY (organization_id) REFERENCES public.organizations(id) ON DELETE SET NULL;

ALTER TABLE ONLY public.activities
    ADD CONSTRAINT fk_activities_person FOREIGN KEY (person_id) REFERENCES public.persons(id) ON DELETE SET NULL;

ALTER TABLE ONLY public.activities
    ADD CONSTRAINT fk_activities_user FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE SET NULL;

ALTER TABLE ONLY public.activity_files
    ADD CONSTRAINT fk_activity_files_activity FOREIGN KEY (activity_id) REFERENCES public.activities(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.activity_participants
    ADD CONSTRAINT fk_activity_participants_activity FOREIGN KEY (activity_id) REFERENCES public.activities(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.activity_participants
    ADD CONSTRAINT fk_activity_participants_user FOREIGN KEY (participant_user_id) REFERENCES public.users(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.lead_products
    ADD CONSTRAINT fk_lead_products_lead FOREIGN KEY (lead_id) REFERENCES public.leads(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.lead_tags
    ADD CONSTRAINT fk_lead_tags_lead FOREIGN KEY (lead_id) REFERENCES public.leads(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.lead_tags
    ADD CONSTRAINT fk_lead_tags_tag FOREIGN KEY (tag_id) REFERENCES public.tags(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.leads
    ADD CONSTRAINT fk_leads_org FOREIGN KEY (organization_id) REFERENCES public.organizations(id) ON DELETE SET NULL;

ALTER TABLE ONLY public.leads
    ADD CONSTRAINT fk_leads_person FOREIGN KEY (person_id) REFERENCES public.persons(id) ON DELETE SET NULL;

ALTER TABLE ONLY public.leads
    ADD CONSTRAINT fk_leads_pipeline FOREIGN KEY (pipeline_id) REFERENCES public.pipelines(id) ON DELETE RESTRICT;

ALTER TABLE ONLY public.leads
    ADD CONSTRAINT fk_leads_source FOREIGN KEY (lead_source_id) REFERENCES public.lead_sources(id) ON DELETE SET NULL;

ALTER TABLE ONLY public.leads
    ADD CONSTRAINT fk_leads_stage FOREIGN KEY (stage_id) REFERENCES public.stages(id) ON DELETE RESTRICT;

ALTER TABLE ONLY public.leads
    ADD CONSTRAINT fk_leads_type FOREIGN KEY (lead_type_id) REFERENCES public.lead_types(id) ON DELETE SET NULL;

ALTER TABLE ONLY public.leads
    ADD CONSTRAINT fk_leads_user FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE SET NULL;

ALTER TABLE ONLY public.person_tags
    ADD CONSTRAINT fk_person_tags_person FOREIGN KEY (person_id) REFERENCES public.persons(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.person_tags
    ADD CONSTRAINT fk_person_tags_tag FOREIGN KEY (tag_id) REFERENCES public.tags(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.persons
    ADD CONSTRAINT fk_persons_organization FOREIGN KEY (organization_id) REFERENCES public.organizations(id) ON DELETE SET NULL;

ALTER TABLE ONLY public.quote_items
    ADD CONSTRAINT fk_quote_items_quote FOREIGN KEY (quote_id) REFERENCES public.quotes(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.quotes
    ADD CONSTRAINT fk_quotes_lead FOREIGN KEY (lead_id) REFERENCES public.leads(id) ON DELETE RESTRICT;

ALTER TABLE ONLY public.quotes
    ADD CONSTRAINT fk_quotes_user FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE SET NULL;

ALTER TABLE ONLY public.stages
    ADD CONSTRAINT fk_stages_pipeline FOREIGN KEY (pipeline_id) REFERENCES public.pipelines(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.lead_products
    ADD CONSTRAINT lead_products_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);

ALTER TABLE ONLY public.lead_sources
    ADD CONSTRAINT lead_sources_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);

ALTER TABLE ONLY public.lead_types
    ADD CONSTRAINT lead_types_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);

ALTER TABLE ONLY public.leads
    ADD CONSTRAINT leads_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);

ALTER TABLE ONLY public.organizations
    ADD CONSTRAINT organizations_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);

ALTER TABLE ONLY public.persons
    ADD CONSTRAINT persons_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);

ALTER TABLE ONLY public.pipelines
    ADD CONSTRAINT pipelines_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);

ALTER TABLE ONLY public.quote_items
    ADD CONSTRAINT quote_items_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);

ALTER TABLE ONLY public.quotes
    ADD CONSTRAINT quotes_person_id_fkey FOREIGN KEY (person_id) REFERENCES public.persons(id) ON DELETE SET NULL;

ALTER TABLE ONLY public.quotes
    ADD CONSTRAINT quotes_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);

ALTER TABLE ONLY public.stages
    ADD CONSTRAINT stages_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);

ALTER TABLE ONLY public.tags
    ADD CONSTRAINT tags_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);

-- Indexes
CREATE INDEX idx_activities_deleted ON public.activities USING btree (deleted_at) WHERE (deleted_at IS NULL);

CREATE INDEX idx_activities_lead ON public.activities USING btree (lead_id);

CREATE INDEX idx_activities_tenant ON public.activities USING btree (tenant_id);

CREATE INDEX idx_activities_user ON public.activities USING btree (user_id);

CREATE INDEX idx_activity_files_activity ON public.activity_files USING btree (activity_id);

CREATE INDEX idx_activity_files_tenant ON public.activity_files USING btree (tenant_id);

CREATE INDEX idx_activity_participants_activity ON public.activity_participants USING btree (activity_id);

CREATE INDEX idx_activity_participants_person ON public.activity_participants USING btree (person_id);

CREATE INDEX idx_activity_participants_tenant ON public.activity_participants USING btree (tenant_id);

CREATE INDEX idx_lead_products_product ON public.lead_products USING btree (product_id, lead_id);

CREATE INDEX idx_leads_deleted ON public.leads USING btree (deleted_at) WHERE (deleted_at IS NULL);

CREATE INDEX idx_leads_pipeline ON public.leads USING btree (pipeline_id);

CREATE INDEX idx_leads_stage ON public.leads USING btree (stage_id);

CREATE INDEX idx_leads_status ON public.leads USING btree (status);

CREATE INDEX idx_leads_tenant ON public.leads USING btree (tenant_id);

CREATE INDEX idx_leads_tenant_person_status ON public.leads USING btree (tenant_id, person_id, status) WHERE ((deleted_at IS NULL) AND (person_id IS NOT NULL));

CREATE INDEX idx_leads_tenant_source_status ON public.leads USING btree (tenant_id, lead_source_id, status) WHERE ((deleted_at IS NULL) AND (lead_source_id IS NOT NULL));

CREATE INDEX idx_leads_tenant_stage_status ON public.leads USING btree (tenant_id, stage_id, status) WHERE (deleted_at IS NULL);

CREATE INDEX idx_leads_tenant_status_created ON public.leads USING btree (tenant_id, status, created_at DESC) WHERE (deleted_at IS NULL);

CREATE INDEX idx_leads_tenant_status_user_created ON public.leads USING btree (tenant_id, status, user_id, created_at DESC) WHERE (deleted_at IS NULL);

CREATE INDEX idx_leads_tenant_type_status ON public.leads USING btree (tenant_id, lead_type_id, status) WHERE ((deleted_at IS NULL) AND (lead_type_id IS NOT NULL));

CREATE INDEX idx_leads_user ON public.leads USING btree (user_id);

CREATE INDEX idx_organizations_deleted ON public.organizations USING btree (deleted_at) WHERE (deleted_at IS NULL);

CREATE INDEX idx_organizations_tenant ON public.organizations USING btree (tenant_id);

CREATE INDEX idx_persons_deleted ON public.persons USING btree (deleted_at) WHERE (deleted_at IS NULL);

CREATE INDEX idx_persons_organization ON public.persons USING btree (organization_id);

CREATE INDEX idx_persons_tenant ON public.persons USING btree (tenant_id);

CREATE INDEX idx_pipelines_default ON public.pipelines USING btree (is_default) WHERE (is_default = true);

CREATE INDEX idx_quotes_deleted ON public.quotes USING btree (deleted_at) WHERE (deleted_at IS NULL);

CREATE INDEX idx_quotes_lead ON public.quotes USING btree (lead_id);

CREATE INDEX idx_quotes_tenant ON public.quotes USING btree (tenant_id);

CREATE INDEX idx_stages_pipeline ON public.stages USING btree (pipeline_id);
