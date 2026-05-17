CREATE TABLE warehouses
(
    id              UUID                     NOT NULL DEFAULT gen_random_uuid(),
    name            VARCHAR(255)             NOT NULL,
    description     TEXT,
    contact_name    VARCHAR(255),
    contact_email   VARCHAR(255),
    contact_phone   VARCHAR(50),
    contact_address TEXT,
    created_by      UUID,
    updated_by      UUID,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMP WITH TIME ZONE,

    CONSTRAINT pk_warehouses PRIMARY KEY (id)
);

CREATE TABLE warehouse_locations
(
    id           UUID                     NOT NULL DEFAULT gen_random_uuid(),
    warehouse_id UUID                     NOT NULL,
    name         VARCHAR(255)             NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_warehouse_locations PRIMARY KEY (id),
    CONSTRAINT fk_warehouse_locations_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses (id) ON DELETE CASCADE
);

CREATE TABLE product_inventories
(
    id                   UUID                     NOT NULL DEFAULT gen_random_uuid(),
    product_id           UUID                     NOT NULL,
    warehouse_id         UUID                     NOT NULL,
    warehouse_location_id UUID,
    quantity             INT                      NOT NULL DEFAULT 0,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_product_inventories PRIMARY KEY (id),
    CONSTRAINT fk_product_inventories_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
    CONSTRAINT fk_product_inventories_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses (id) ON DELETE CASCADE,
    CONSTRAINT fk_product_inventories_location FOREIGN KEY (warehouse_location_id) REFERENCES warehouse_locations (id) ON DELETE SET NULL,
    CONSTRAINT uq_product_inventories UNIQUE (product_id, warehouse_id, warehouse_location_id),
    CONSTRAINT chk_product_inventories_qty CHECK (quantity >= 0)
);

CREATE INDEX idx_warehouses_deleted ON warehouses (deleted_at) WHERE deleted_at IS NULL;
CREATE INDEX idx_warehouse_locations_warehouse ON warehouse_locations (warehouse_id);
CREATE INDEX idx_product_inventories_product ON product_inventories (product_id);
CREATE INDEX idx_product_inventories_warehouse ON product_inventories (warehouse_id);

-- Permissions and role assignments are handled by BootstrapService on startup.
