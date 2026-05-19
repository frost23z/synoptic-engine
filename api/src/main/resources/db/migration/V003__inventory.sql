-- V003__inventory.sql
-- Baseline migration: inventory.

-- Tables
CREATE TABLE public.product_inventories (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    product_id uuid NOT NULL,
    warehouse_id uuid NOT NULL,
    warehouse_location_id uuid,
    quantity integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    tenant_id uuid DEFAULT '00000000-0000-0000-0000-000000000001'::uuid NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT chk_product_inventories_qty CHECK ((quantity >= 0))
);

CREATE TABLE public.product_tags (
    product_id uuid NOT NULL,
    tag_id uuid NOT NULL
);

CREATE TABLE public.products (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    price numeric(15,2) DEFAULT 0 NOT NULL,
    sku character varying(100),
    is_active boolean DEFAULT true NOT NULL,
    created_by uuid,
    updated_by uuid,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    tenant_id uuid DEFAULT '00000000-0000-0000-0000-000000000001'::uuid NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);

CREATE TABLE public.warehouse_locations (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    warehouse_id uuid NOT NULL,
    name character varying(255) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    tenant_id uuid DEFAULT '00000000-0000-0000-0000-000000000001'::uuid NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);

CREATE TABLE public.warehouse_tags (
    warehouse_id uuid NOT NULL,
    tag_id uuid NOT NULL
);

CREATE TABLE public.warehouses (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    contact_name character varying(255),
    contact_email character varying(255),
    contact_phone character varying(50),
    contact_address text,
    created_by uuid,
    updated_by uuid,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    tenant_id uuid DEFAULT '00000000-0000-0000-0000-000000000001'::uuid NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);

-- Primary keys
ALTER TABLE ONLY public.product_inventories
    ADD CONSTRAINT pk_product_inventories PRIMARY KEY (id);

ALTER TABLE ONLY public.product_tags
    ADD CONSTRAINT pk_product_tags PRIMARY KEY (product_id, tag_id);

ALTER TABLE ONLY public.products
    ADD CONSTRAINT pk_products PRIMARY KEY (id);

ALTER TABLE ONLY public.warehouse_locations
    ADD CONSTRAINT pk_warehouse_locations PRIMARY KEY (id);

ALTER TABLE ONLY public.warehouse_tags
    ADD CONSTRAINT pk_warehouse_tags PRIMARY KEY (warehouse_id, tag_id);

ALTER TABLE ONLY public.warehouses
    ADD CONSTRAINT pk_warehouses PRIMARY KEY (id);

-- Unique constraints
ALTER TABLE ONLY public.product_inventories
    ADD CONSTRAINT uq_product_inventories UNIQUE (product_id, warehouse_id, warehouse_location_id);

ALTER TABLE ONLY public.products
    ADD CONSTRAINT uq_products_tenant_sku UNIQUE (tenant_id, sku);

-- Foreign keys
ALTER TABLE ONLY public.activities
    ADD CONSTRAINT fk_activities_product FOREIGN KEY (product_id) REFERENCES public.products(id) ON DELETE SET NULL;

ALTER TABLE ONLY public.activities
    ADD CONSTRAINT fk_activities_warehouse FOREIGN KEY (warehouse_id) REFERENCES public.warehouses(id) ON DELETE SET NULL;

ALTER TABLE ONLY public.lead_products
    ADD CONSTRAINT fk_lead_products_product FOREIGN KEY (product_id) REFERENCES public.products(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.product_inventories
    ADD CONSTRAINT fk_product_inventories_location FOREIGN KEY (warehouse_location_id) REFERENCES public.warehouse_locations(id) ON DELETE SET NULL;

ALTER TABLE ONLY public.product_inventories
    ADD CONSTRAINT fk_product_inventories_product FOREIGN KEY (product_id) REFERENCES public.products(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.product_inventories
    ADD CONSTRAINT fk_product_inventories_warehouse FOREIGN KEY (warehouse_id) REFERENCES public.warehouses(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.product_tags
    ADD CONSTRAINT fk_product_tags_product FOREIGN KEY (product_id) REFERENCES public.products(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.product_tags
    ADD CONSTRAINT fk_product_tags_tag FOREIGN KEY (tag_id) REFERENCES public.tags(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.quote_items
    ADD CONSTRAINT fk_quote_items_product FOREIGN KEY (product_id) REFERENCES public.products(id) ON DELETE SET NULL;

ALTER TABLE ONLY public.warehouse_locations
    ADD CONSTRAINT fk_warehouse_locations_warehouse FOREIGN KEY (warehouse_id) REFERENCES public.warehouses(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.warehouse_tags
    ADD CONSTRAINT fk_warehouse_tags_tag FOREIGN KEY (tag_id) REFERENCES public.tags(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.warehouse_tags
    ADD CONSTRAINT fk_warehouse_tags_warehouse FOREIGN KEY (warehouse_id) REFERENCES public.warehouses(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.product_inventories
    ADD CONSTRAINT product_inventories_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);

ALTER TABLE ONLY public.products
    ADD CONSTRAINT products_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);

ALTER TABLE ONLY public.warehouse_locations
    ADD CONSTRAINT warehouse_locations_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);

ALTER TABLE ONLY public.warehouses
    ADD CONSTRAINT warehouses_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);

-- Indexes
CREATE INDEX idx_product_inventories_product ON public.product_inventories USING btree (product_id);

CREATE INDEX idx_product_inventories_warehouse ON public.product_inventories USING btree (warehouse_id);

CREATE INDEX idx_products_deleted ON public.products USING btree (deleted_at) WHERE (deleted_at IS NULL);

CREATE INDEX idx_products_tenant ON public.products USING btree (tenant_id);

CREATE INDEX idx_warehouse_locations_warehouse ON public.warehouse_locations USING btree (warehouse_id);

CREATE INDEX idx_warehouses_deleted ON public.warehouses USING btree (deleted_at) WHERE (deleted_at IS NULL);

CREATE INDEX idx_warehouses_tenant ON public.warehouses USING btree (tenant_id);
