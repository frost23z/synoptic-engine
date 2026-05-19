-- V007__sharing_and_rls.sql
-- Baseline migration: sharing and rls.

-- Tables
CREATE TABLE public.cross_tenant_audit (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    owner_tenant_id uuid NOT NULL,
    actor_tenant_id uuid NOT NULL,
    actor_user_id uuid NOT NULL,
    resource_type character varying(50) NOT NULL,
    resource_id uuid NOT NULL,
    action character varying(40) NOT NULL,
    payload_jsonb jsonb,
    at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE public.record_shares (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    owner_tenant_id uuid NOT NULL,
    consumer_tenant_id uuid NOT NULL,
    resource_type character varying(50) NOT NULL,
    resource_id uuid NOT NULL,
    access_level character varying(20) NOT NULL,
    shared_by uuid NOT NULL,
    expires_at timestamp with time zone,
    revoked_at timestamp with time zone,
    note text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT chk_distinct_record_share_tenants CHECK ((owner_tenant_id <> consumer_tenant_id)),
    CONSTRAINT record_shares_access_level_check CHECK ((access_level IN ('NONE', 'READ', 'COMMENT', 'WRITE', 'MANAGE')))
);

CREATE TABLE public.resource_visibility (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    owner_tenant_id uuid NOT NULL,
    consumer_tenant_id uuid NOT NULL,
    resource_type character varying(50) NOT NULL,
    resource_id uuid NOT NULL,
    access_level character varying(20) NOT NULL,
    source character varying(20) NOT NULL,
    source_id uuid NOT NULL,
    expires_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT resource_visibility_access_level_check CHECK ((access_level IN ('NONE', 'READ', 'COMMENT', 'WRITE', 'MANAGE'))),
    CONSTRAINT resource_visibility_source_check CHECK ((source IN ('policy', 'record', 'cascade')))
);

CREATE TABLE public.share_materialization_queue (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    policy_id uuid NOT NULL,
    op character varying(20) NOT NULL,
    enqueued_at timestamp with time zone DEFAULT now() NOT NULL,
    started_at timestamp with time zone,
    finished_at timestamp with time zone,
    error text,
    CONSTRAINT share_materialization_queue_op_check CHECK ((op IN ('INSERT', 'UPDATE', 'DELETE', 'REVOKE')))
);

CREATE TABLE public.tenant_relationships (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    source_tenant_id uuid NOT NULL,
    target_tenant_id uuid NOT NULL,
    relationship_type character varying(30) NOT NULL,
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    initiated_by uuid NOT NULL,
    accepted_by uuid,
    note text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    accepted_at timestamp with time zone,
    revoked_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT chk_distinct_tenants CHECK ((source_tenant_id <> target_tenant_id)),
    CONSTRAINT tenant_relationships_relationship_type_check CHECK ((relationship_type IN ('PARENT_CHILD', 'PARTNER', 'SUPPLIER_CLIENT'))),
    CONSTRAINT tenant_relationships_status_check CHECK ((status IN ('PENDING', 'ACTIVE', 'SUSPENDED', 'REVOKED')))
);

CREATE TABLE public.tenant_share_policies (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    relationship_id uuid NOT NULL,
    resource_type character varying(50) NOT NULL,
    access_level character varying(20) NOT NULL,
    filter_jsonb jsonb,
    cascade_jsonb jsonb,
    materialize boolean DEFAULT true NOT NULL,
    created_by uuid NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    revoked_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT tenant_share_policies_access_level_check CHECK ((access_level IN ('NONE', 'READ', 'COMMENT', 'WRITE', 'MANAGE')))
);

-- Functions
CREATE FUNCTION public.app_current_tenant() RETURNS uuid
    LANGUAGE sql STABLE
    AS $$
    SELECT NULLIF(current_setting('app.current_tenant', true), '')::uuid
$$;

CREATE FUNCTION public.app_has_visibility(p_resource_type character varying, p_resource_id uuid, p_min_level character varying DEFAULT 'READ'::character varying) RETURNS boolean
    LANGUAGE sql STABLE
    AS $$
    SELECT EXISTS (
        SELECT 1
        FROM resource_visibility v
        WHERE v.consumer_tenant_id = app_current_tenant()
          AND v.resource_type = p_resource_type
          AND v.resource_id = p_resource_id
          AND (v.expires_at IS NULL OR v.expires_at > NOW())
          AND (
              CASE p_min_level
                  WHEN 'READ'    THEN v.access_level IN ('READ','COMMENT','WRITE','MANAGE')
                  WHEN 'COMMENT' THEN v.access_level IN ('COMMENT','WRITE','MANAGE')
                  WHEN 'WRITE'   THEN v.access_level IN ('WRITE','MANAGE')
                  WHEN 'MANAGE'  THEN v.access_level IN ('MANAGE')
              END
          )
    )
$$;

-- Primary keys
ALTER TABLE ONLY public.cross_tenant_audit
    ADD CONSTRAINT cross_tenant_audit_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.record_shares
    ADD CONSTRAINT record_shares_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.resource_visibility
    ADD CONSTRAINT resource_visibility_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.share_materialization_queue
    ADD CONSTRAINT share_materialization_queue_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.tenant_relationships
    ADD CONSTRAINT tenant_relationships_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.tenant_share_policies
    ADD CONSTRAINT tenant_share_policies_pkey PRIMARY KEY (id);

-- Unique constraints
ALTER TABLE ONLY public.record_shares
    ADD CONSTRAINT uq_record_share UNIQUE (owner_tenant_id, consumer_tenant_id, resource_type, resource_id);

ALTER TABLE ONLY public.tenant_share_policies
    ADD CONSTRAINT uq_share_policy UNIQUE (relationship_id, resource_type);

ALTER TABLE ONLY public.tenant_relationships
    ADD CONSTRAINT uq_tenant_relationship UNIQUE (source_tenant_id, target_tenant_id, relationship_type);

ALTER TABLE ONLY public.resource_visibility
    ADD CONSTRAINT uq_visibility UNIQUE (consumer_tenant_id, resource_type, resource_id, source, source_id);

-- Foreign keys
ALTER TABLE ONLY public.record_shares
    ADD CONSTRAINT record_shares_consumer_tenant_id_fkey FOREIGN KEY (consumer_tenant_id) REFERENCES public.tenants(id);

ALTER TABLE ONLY public.record_shares
    ADD CONSTRAINT record_shares_owner_tenant_id_fkey FOREIGN KEY (owner_tenant_id) REFERENCES public.tenants(id);

ALTER TABLE ONLY public.record_shares
    ADD CONSTRAINT record_shares_shared_by_fkey FOREIGN KEY (shared_by) REFERENCES public.users(id);

ALTER TABLE ONLY public.tenant_relationships
    ADD CONSTRAINT tenant_relationships_accepted_by_fkey FOREIGN KEY (accepted_by) REFERENCES public.users(id);

ALTER TABLE ONLY public.tenant_relationships
    ADD CONSTRAINT tenant_relationships_initiated_by_fkey FOREIGN KEY (initiated_by) REFERENCES public.users(id);

ALTER TABLE ONLY public.tenant_relationships
    ADD CONSTRAINT tenant_relationships_source_tenant_id_fkey FOREIGN KEY (source_tenant_id) REFERENCES public.tenants(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.tenant_relationships
    ADD CONSTRAINT tenant_relationships_target_tenant_id_fkey FOREIGN KEY (target_tenant_id) REFERENCES public.tenants(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.tenant_share_policies
    ADD CONSTRAINT tenant_share_policies_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.users(id);

ALTER TABLE ONLY public.tenant_share_policies
    ADD CONSTRAINT tenant_share_policies_relationship_id_fkey FOREIGN KEY (relationship_id) REFERENCES public.tenant_relationships(id) ON DELETE CASCADE;

-- Indexes
CREATE INDEX idx_cta_actor ON public.cross_tenant_audit USING btree (actor_tenant_id, at DESC);

CREATE INDEX idx_cta_owner_resource ON public.cross_tenant_audit USING btree (owner_tenant_id, resource_type, resource_id, at DESC);

CREATE INDEX idx_record_shares_owner_active ON public.record_shares USING btree (owner_tenant_id, created_at DESC) WHERE (revoked_at IS NULL);

CREATE INDEX idx_rs_lookup ON public.record_shares USING btree (consumer_tenant_id, resource_type, resource_id) WHERE (revoked_at IS NULL);

CREATE INDEX idx_rs_owner ON public.record_shares USING btree (owner_tenant_id, resource_type, resource_id) WHERE (revoked_at IS NULL);

CREATE INDEX idx_rv_expiry ON public.resource_visibility USING btree (expires_at) WHERE (expires_at IS NOT NULL);

CREATE INDEX idx_rv_lookup ON public.resource_visibility USING btree (consumer_tenant_id, resource_type, resource_id);

CREATE INDEX idx_rv_owner ON public.resource_visibility USING btree (owner_tenant_id, resource_type, resource_id);

CREATE INDEX idx_smq_pending ON public.share_materialization_queue USING btree (enqueued_at) WHERE (finished_at IS NULL);

CREATE INDEX idx_tr_source ON public.tenant_relationships USING btree (source_tenant_id, status);

CREATE INDEX idx_tr_target ON public.tenant_relationships USING btree (target_tenant_id, status);

CREATE INDEX idx_tsp_relationship ON public.tenant_share_policies USING btree (relationship_id) WHERE (revoked_at IS NULL);

CREATE INDEX idx_tsp_resource_type ON public.tenant_share_policies USING btree (resource_type) WHERE (revoked_at IS NULL);

-- Row level security
ALTER TABLE public.leads ENABLE ROW LEVEL SECURITY;

ALTER TABLE public.organizations ENABLE ROW LEVEL SECURITY;

ALTER TABLE public.persons ENABLE ROW LEVEL SECURITY;

ALTER TABLE public.products ENABLE ROW LEVEL SECURITY;

-- RLS policies
CREATE POLICY rls_leads_read ON public.leads FOR SELECT USING (((public.app_current_tenant() IS NULL) OR (tenant_id = public.app_current_tenant()) OR public.app_has_visibility('leads'::character varying, id, 'READ'::character varying)));

CREATE POLICY rls_leads_write ON public.leads USING (((public.app_current_tenant() IS NULL) OR (tenant_id = public.app_current_tenant()) OR public.app_has_visibility('leads'::character varying, id, 'WRITE'::character varying))) WITH CHECK (((public.app_current_tenant() IS NULL) OR (tenant_id = public.app_current_tenant()) OR public.app_has_visibility('leads'::character varying, id, 'WRITE'::character varying)));

CREATE POLICY rls_organizations_read ON public.organizations FOR SELECT USING (((public.app_current_tenant() IS NULL) OR (tenant_id = public.app_current_tenant()) OR public.app_has_visibility('contacts.organizations'::character varying, id, 'READ'::character varying)));

CREATE POLICY rls_organizations_write ON public.organizations USING (((public.app_current_tenant() IS NULL) OR (tenant_id = public.app_current_tenant()) OR public.app_has_visibility('contacts.organizations'::character varying, id, 'WRITE'::character varying))) WITH CHECK (((public.app_current_tenant() IS NULL) OR (tenant_id = public.app_current_tenant()) OR public.app_has_visibility('contacts.organizations'::character varying, id, 'WRITE'::character varying)));

CREATE POLICY rls_persons_read ON public.persons FOR SELECT USING (((public.app_current_tenant() IS NULL) OR (tenant_id = public.app_current_tenant()) OR public.app_has_visibility('contacts.persons'::character varying, id, 'READ'::character varying)));

CREATE POLICY rls_persons_write ON public.persons USING (((public.app_current_tenant() IS NULL) OR (tenant_id = public.app_current_tenant()) OR public.app_has_visibility('contacts.persons'::character varying, id, 'WRITE'::character varying))) WITH CHECK (((public.app_current_tenant() IS NULL) OR (tenant_id = public.app_current_tenant()) OR public.app_has_visibility('contacts.persons'::character varying, id, 'WRITE'::character varying)));

CREATE POLICY rls_products_read ON public.products FOR SELECT USING (((public.app_current_tenant() IS NULL) OR (tenant_id = public.app_current_tenant()) OR public.app_has_visibility('products'::character varying, id, 'READ'::character varying)));

CREATE POLICY rls_products_write ON public.products USING (((public.app_current_tenant() IS NULL) OR (tenant_id = public.app_current_tenant()) OR public.app_has_visibility('products'::character varying, id, 'WRITE'::character varying))) WITH CHECK (((public.app_current_tenant() IS NULL) OR (tenant_id = public.app_current_tenant()) OR public.app_has_visibility('products'::character varying, id, 'WRITE'::character varying)));
