-- V022__inventory_erp.sql
-- Phase 8: Movements ledger, stock states, reservations, transfers,
--           reorder points, soft-delete for warehouse_locations,
--           weighted-average cost tracking, and marketing send pipeline.

-- ── T8.6 — Add soft-delete to warehouse_locations ────────────────────────────
ALTER TABLE warehouse_locations ADD COLUMN deleted_at TIMESTAMPTZ;

CREATE INDEX idx_warehouse_locations_deleted
    ON warehouse_locations (deleted_at)
    WHERE deleted_at IS NULL;

-- ── T8.1 — Update product_inventories: rename + new balance columns ───────────
ALTER TABLE product_inventories RENAME COLUMN quantity TO on_hand;

ALTER TABLE product_inventories
    ADD COLUMN reserved    INT             NOT NULL DEFAULT 0,
    ADD COLUMN in_transit  INT             NOT NULL DEFAULT 0,
    ADD COLUMN damaged     INT             NOT NULL DEFAULT 0,
    ADD COLUMN unit_cost   NUMERIC(15,4)   DEFAULT 0;

ALTER TABLE product_inventories DROP CONSTRAINT chk_product_inventories_qty;
ALTER TABLE product_inventories
    ADD CONSTRAINT chk_pi_on_hand    CHECK (on_hand    >= 0),
    ADD CONSTRAINT chk_pi_reserved   CHECK (reserved   >= 0),
    ADD CONSTRAINT chk_pi_in_transit CHECK (in_transit >= 0),
    ADD CONSTRAINT chk_pi_damaged    CHECK (damaged    >= 0);

-- ── T8.5 — Add reorder_threshold to products ─────────────────────────────────
ALTER TABLE products ADD COLUMN reorder_threshold INT;

-- ── T8.1 — inventory_movements ledger ────────────────────────────────────────
CREATE TABLE inventory_movements (
    id               UUID             DEFAULT gen_random_uuid() NOT NULL,
    tenant_id        UUID             NOT NULL DEFAULT '00000000-0000-0000-0000-000000000001',
    product_id       UUID             NOT NULL,
    movement_type    VARCHAR(20)      NOT NULL,
    from_location_id UUID,
    to_location_id   UUID,
    quantity         INT              NOT NULL,
    unit_cost        NUMERIC(15,4),
    ref_doc_type     VARCHAR(50),
    ref_doc_id       UUID,
    actor_id         UUID,
    notes            TEXT,
    version          BIGINT           NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_inventory_movements PRIMARY KEY (id),
    CONSTRAINT chk_im_movement_type CHECK (movement_type IN (
        'RECEIPT','ISSUE','ADJUST','TRANSFER_IN','TRANSFER_OUT','RESERVE','RELEASE'
    )),
    CONSTRAINT chk_im_quantity CHECK (quantity > 0),
    CONSTRAINT fk_im_tenant   FOREIGN KEY (tenant_id)        REFERENCES tenants(id),
    CONSTRAINT fk_im_product  FOREIGN KEY (product_id)       REFERENCES products(id) ON DELETE CASCADE,
    CONSTRAINT fk_im_from_loc FOREIGN KEY (from_location_id) REFERENCES warehouse_locations(id) ON DELETE SET NULL,
    CONSTRAINT fk_im_to_loc   FOREIGN KEY (to_location_id)   REFERENCES warehouse_locations(id) ON DELETE SET NULL
);

CREATE INDEX idx_im_tenant   ON inventory_movements (tenant_id);
CREATE INDEX idx_im_product  ON inventory_movements (product_id);
CREATE INDEX idx_im_type     ON inventory_movements (movement_type);

ALTER TABLE inventory_movements ENABLE ROW LEVEL SECURITY;
CREATE POLICY rls_inventory_movements ON inventory_movements
    USING  ((app_current_tenant() IS NULL) OR (tenant_id = app_current_tenant()))
    WITH CHECK ((app_current_tenant() IS NULL) OR (tenant_id = app_current_tenant()));

-- ── T8.4 — transfer_orders ────────────────────────────────────────────────────
CREATE TABLE transfer_orders (
    id               UUID         DEFAULT gen_random_uuid() NOT NULL,
    tenant_id        UUID         NOT NULL DEFAULT '00000000-0000-0000-0000-000000000001',
    from_location_id UUID         NOT NULL,
    to_location_id   UUID         NOT NULL,
    product_id       UUID         NOT NULL,
    quantity         INT          NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    out_movement_id  UUID,
    in_movement_id   UUID,
    notes            TEXT,
    created_by       UUID,
    updated_by       UUID,
    deleted_at       TIMESTAMPTZ,
    version          BIGINT       NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_transfer_orders    PRIMARY KEY (id),
    CONSTRAINT chk_to_status         CHECK (status IN ('PENDING','IN_TRANSIT','COMPLETED','CANCELLED')),
    CONSTRAINT chk_to_qty            CHECK (quantity > 0),
    CONSTRAINT fk_to_tenant          FOREIGN KEY (tenant_id)        REFERENCES tenants(id),
    CONSTRAINT fk_to_from_loc        FOREIGN KEY (from_location_id) REFERENCES warehouse_locations(id),
    CONSTRAINT fk_to_to_loc          FOREIGN KEY (to_location_id)   REFERENCES warehouse_locations(id),
    CONSTRAINT fk_to_product         FOREIGN KEY (product_id)       REFERENCES products(id) ON DELETE CASCADE,
    CONSTRAINT fk_to_out_movement    FOREIGN KEY (out_movement_id)  REFERENCES inventory_movements(id) ON DELETE SET NULL,
    CONSTRAINT fk_to_in_movement     FOREIGN KEY (in_movement_id)   REFERENCES inventory_movements(id) ON DELETE SET NULL
);

CREATE INDEX idx_to_tenant  ON transfer_orders (tenant_id);
CREATE INDEX idx_to_product ON transfer_orders (product_id);
CREATE INDEX idx_to_status  ON transfer_orders (status) WHERE deleted_at IS NULL;
CREATE INDEX idx_to_deleted ON transfer_orders (deleted_at) WHERE deleted_at IS NULL;

ALTER TABLE transfer_orders ENABLE ROW LEVEL SECURITY;
CREATE POLICY rls_transfer_orders ON transfer_orders
    USING  ((app_current_tenant() IS NULL) OR (tenant_id = app_current_tenant()))
    WITH CHECK ((app_current_tenant() IS NULL) OR (tenant_id = app_current_tenant()));

-- ── T8.8 — marketing_send_jobs ────────────────────────────────────────────────
CREATE TABLE marketing_send_jobs (
    id              UUID         DEFAULT gen_random_uuid() NOT NULL,
    tenant_id       UUID         NOT NULL DEFAULT '00000000-0000-0000-0000-000000000001',
    campaign_id     UUID         NOT NULL,
    recipient       VARCHAR(320) NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    attempt_count   INT          NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    sent_at         TIMESTAMPTZ,
    failed_at       TIMESTAMPTZ,
    error_message   TEXT,
    subject         TEXT         NOT NULL,
    body            TEXT         NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_marketing_send_jobs PRIMARY KEY (id),
    CONSTRAINT chk_msj_status CHECK (status IN ('PENDING','SENT','FAILED','OPENED')),
    CONSTRAINT fk_msj_tenant   FOREIGN KEY (tenant_id)   REFERENCES tenants(id),
    CONSTRAINT fk_msj_campaign FOREIGN KEY (campaign_id) REFERENCES marketing_campaigns(id) ON DELETE CASCADE
);

CREATE INDEX idx_msj_tenant_status   ON marketing_send_jobs (tenant_id, status);
CREATE INDEX idx_msj_campaign        ON marketing_send_jobs (campaign_id);
CREATE INDEX idx_msj_next_attempt    ON marketing_send_jobs (next_attempt_at)
    WHERE status IN ('PENDING','FAILED');

ALTER TABLE marketing_send_jobs ENABLE ROW LEVEL SECURITY;
CREATE POLICY rls_marketing_send_jobs ON marketing_send_jobs
    USING  ((app_current_tenant() IS NULL) OR (tenant_id = app_current_tenant()))
    WITH CHECK ((app_current_tenant() IS NULL) OR (tenant_id = app_current_tenant()));
