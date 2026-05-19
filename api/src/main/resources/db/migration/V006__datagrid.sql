-- V006__datagrid.sql
-- Baseline migration: datagrid.

-- Tables
CREATE TABLE public.datagrid_saved_filters (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    name character varying(255) NOT NULL,
    src character varying(100) NOT NULL,
    applied jsonb DEFAULT '{}'::jsonb NOT NULL,
    tenant_id uuid DEFAULT '00000000-0000-0000-0000-000000000001'::uuid NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

-- Primary keys
ALTER TABLE ONLY public.datagrid_saved_filters
    ADD CONSTRAINT pk_datagrid_saved_filters PRIMARY KEY (id);

-- Foreign keys
ALTER TABLE ONLY public.datagrid_saved_filters
    ADD CONSTRAINT datagrid_saved_filters_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);

ALTER TABLE ONLY public.datagrid_saved_filters
    ADD CONSTRAINT datagrid_saved_filters_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;

-- Indexes
CREATE INDEX idx_datagrid_filters_tenant ON public.datagrid_saved_filters USING btree (tenant_id);

CREATE INDEX idx_datagrid_filters_user_src ON public.datagrid_saved_filters USING btree (user_id, src);
