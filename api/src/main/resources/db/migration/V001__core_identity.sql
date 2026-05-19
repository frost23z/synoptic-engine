-- V001__core_identity.sql
-- Baseline migration: core identity.

-- Tables
CREATE TABLE public.event_publication (
    id uuid NOT NULL,
    listener_id text NOT NULL,
    event_type text NOT NULL,
    serialized_event text NOT NULL,
    publication_date timestamp with time zone NOT NULL,
    completion_date timestamp with time zone,
    status text,
    completion_attempts integer DEFAULT 0 NOT NULL,
    last_resubmission_date timestamp with time zone
);

CREATE TABLE public.event_publication_archive (
    id uuid NOT NULL,
    listener_id text NOT NULL,
    event_type text NOT NULL,
    serialized_event text NOT NULL,
    publication_date timestamp with time zone NOT NULL,
    completion_date timestamp with time zone,
    status text,
    completion_attempts integer DEFAULT 0 NOT NULL,
    last_resubmission_date timestamp with time zone
);

CREATE TABLE public.groups (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    name character varying(100) NOT NULL,
    description character varying(500),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    tenant_id uuid DEFAULT '00000000-0000-0000-0000-000000000001'::uuid NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);

CREATE TABLE public.permissions (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    key character varying(100) NOT NULL,
    description character varying(500),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);

CREATE TABLE public.role_permissions (
    role_id uuid NOT NULL,
    permission_id uuid NOT NULL
);

CREATE TABLE public.roles (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    name character varying(100) NOT NULL,
    description character varying(500),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    tenant_id uuid DEFAULT '00000000-0000-0000-0000-000000000001'::uuid NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    permission_type character varying(10) DEFAULT 'CUSTOM'::character varying NOT NULL,
    CONSTRAINT roles_permission_type_check CHECK ((permission_type IN ('ALL', 'CUSTOM')))
);

CREATE TABLE public.tenants (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    name character varying(255) NOT NULL,
    slug character varying(100) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    legal_name character varying(255),
    timezone character varying(64),
    locale character varying(16),
    deleted_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT tenants_status_check CHECK ((status IN ('ACTIVE', 'SUSPENDED', 'REVOKED')))
);

CREATE TABLE public.user_groups (
    user_id uuid NOT NULL,
    group_id uuid NOT NULL
);

CREATE TABLE public.user_password_resets (
    email character varying(255) NOT NULL,
    token character varying(255) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE public.user_roles (
    user_id uuid NOT NULL,
    role_id uuid NOT NULL
);

CREATE TABLE public.users (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    email character varying(255) NOT NULL,
    password_hash character varying(255) NOT NULL,
    first_name character varying(100) NOT NULL,
    last_name character varying(100) NOT NULL,
    phone character varying(50),
    is_active boolean DEFAULT true NOT NULL,
    created_by uuid,
    updated_by uuid,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    view_permission character varying(20) DEFAULT 'GLOBAL'::character varying NOT NULL,
    tenant_id uuid DEFAULT '00000000-0000-0000-0000-000000000001'::uuid NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);

-- Primary keys
ALTER TABLE ONLY public.event_publication_archive
    ADD CONSTRAINT event_publication_archive_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.event_publication
    ADD CONSTRAINT event_publication_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.groups
    ADD CONSTRAINT pk_groups PRIMARY KEY (id);

ALTER TABLE ONLY public.permissions
    ADD CONSTRAINT pk_permissions PRIMARY KEY (id);

ALTER TABLE ONLY public.role_permissions
    ADD CONSTRAINT pk_role_permissions PRIMARY KEY (role_id, permission_id);

ALTER TABLE ONLY public.roles
    ADD CONSTRAINT pk_roles PRIMARY KEY (id);

ALTER TABLE ONLY public.tenants
    ADD CONSTRAINT pk_tenants PRIMARY KEY (id);

ALTER TABLE ONLY public.user_groups
    ADD CONSTRAINT pk_user_groups PRIMARY KEY (user_id, group_id);

ALTER TABLE ONLY public.user_password_resets
    ADD CONSTRAINT pk_user_password_resets PRIMARY KEY (email);

ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role_id);

ALTER TABLE ONLY public.users
    ADD CONSTRAINT pk_users PRIMARY KEY (id);

-- Unique constraints
ALTER TABLE ONLY public.groups
    ADD CONSTRAINT uq_groups_tenant_name UNIQUE (tenant_id, name);

ALTER TABLE ONLY public.permissions
    ADD CONSTRAINT uq_permissions_key UNIQUE (key);

ALTER TABLE ONLY public.roles
    ADD CONSTRAINT uq_roles_tenant_name UNIQUE (tenant_id, name);

ALTER TABLE ONLY public.tenants
    ADD CONSTRAINT uq_tenants_slug UNIQUE (slug);

ALTER TABLE ONLY public.users
    ADD CONSTRAINT uq_users_email UNIQUE (email);

-- Foreign keys
ALTER TABLE ONLY public.role_permissions
    ADD CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_id) REFERENCES public.permissions(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.role_permissions
    ADD CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES public.roles(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.user_groups
    ADD CONSTRAINT fk_user_groups_group FOREIGN KEY (group_id) REFERENCES public.groups(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.user_groups
    ADD CONSTRAINT fk_user_groups_user FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES public.roles(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.groups
    ADD CONSTRAINT groups_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);

ALTER TABLE ONLY public.roles
    ADD CONSTRAINT roles_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);

-- Indexes
CREATE INDEX event_publication_archive_by_completion_date_idx ON public.event_publication_archive USING btree (completion_date);

CREATE INDEX event_publication_archive_serialized_event_hash_idx ON public.event_publication_archive USING hash (serialized_event);

CREATE INDEX event_publication_by_completion_date_idx ON public.event_publication USING btree (completion_date);

CREATE INDEX event_publication_serialized_event_hash_idx ON public.event_publication USING hash (serialized_event);

CREATE INDEX idx_password_resets_token ON public.user_password_resets USING btree (token);

CREATE INDEX idx_roles_tenant ON public.roles USING btree (tenant_id);

CREATE INDEX idx_tenants_status_active ON public.tenants USING btree (status) WHERE (deleted_at IS NULL);

CREATE INDEX idx_user_groups_group ON public.user_groups USING btree (group_id);

CREATE INDEX idx_user_groups_user ON public.user_groups USING btree (user_id);

CREATE INDEX idx_user_roles_role ON public.user_roles USING btree (role_id);

CREATE INDEX idx_user_roles_user ON public.user_roles USING btree (user_id);

CREATE INDEX idx_users_deleted ON public.users USING btree (deleted_at) WHERE (deleted_at IS NULL);

CREATE INDEX idx_users_email ON public.users USING btree (email);

CREATE INDEX idx_users_tenant ON public.users USING btree (tenant_id);
