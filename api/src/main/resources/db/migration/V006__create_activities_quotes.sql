CREATE TABLE activities
(
    id              UUID                     NOT NULL DEFAULT gen_random_uuid(),
    lead_id         UUID,
    user_id         UUID,
    person_id       UUID,
    organization_id UUID,
    title           VARCHAR(255)             NOT NULL,
    type            VARCHAR(50)              NOT NULL,
    comment         TEXT,
    is_done         BOOLEAN                  NOT NULL DEFAULT FALSE,
    schedule_from   TIMESTAMP WITH TIME ZONE NOT NULL,
    schedule_to     TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by      UUID,
    updated_by      UUID,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMP WITH TIME ZONE,

    CONSTRAINT pk_activities PRIMARY KEY (id),
    CONSTRAINT fk_activities_lead FOREIGN KEY (lead_id) REFERENCES leads (id) ON DELETE SET NULL,
    CONSTRAINT fk_activities_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_activities_person FOREIGN KEY (person_id) REFERENCES persons (id) ON DELETE SET NULL,
    CONSTRAINT fk_activities_org FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE SET NULL,
    CONSTRAINT chk_activities_type CHECK (type IN ('CALL', 'EMAIL', 'MEETING', 'TASK', 'NOTE', 'MESSAGE'))
);

CREATE TABLE quotes
(
    id         UUID                     NOT NULL DEFAULT gen_random_uuid(),
    lead_id    UUID                     NOT NULL,
    user_id    UUID,
    title      VARCHAR(255)             NOT NULL,
    status     VARCHAR(50)              NOT NULL DEFAULT 'draft',
    discount   DECIMAL(5, 2)            NOT NULL DEFAULT 0,
    tax        DECIMAL(5, 2)            NOT NULL DEFAULT 0,
    terms      TEXT,
    expired_at DATE,
    created_by UUID,
    updated_by UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP WITH TIME ZONE,

    CONSTRAINT pk_quotes PRIMARY KEY (id),
    CONSTRAINT fk_quotes_lead FOREIGN KEY (lead_id) REFERENCES leads (id) ON DELETE RESTRICT,
    CONSTRAINT fk_quotes_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT chk_quotes_status CHECK (status IN ('draft', 'sent', 'accepted', 'declined'))
);

CREATE TABLE quote_items
(
    id         UUID                     NOT NULL DEFAULT gen_random_uuid(),
    quote_id   UUID                     NOT NULL,
    product_id UUID,
    quantity   INT                      NOT NULL DEFAULT 1,
    unit_price DECIMAL(15, 2)           NOT NULL,
    discount   DECIMAL(5, 2)            NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_quote_items PRIMARY KEY (id),
    CONSTRAINT fk_quote_items_quote FOREIGN KEY (quote_id) REFERENCES quotes (id) ON DELETE CASCADE,
    CONSTRAINT fk_quote_items_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE SET NULL,
    CONSTRAINT chk_quote_items_qty CHECK (quantity > 0),
    CONSTRAINT chk_quote_items_price CHECK (unit_price >= 0)
);

CREATE INDEX idx_activities_lead ON activities (lead_id);
CREATE INDEX idx_activities_user ON activities (user_id);
CREATE INDEX idx_activities_deleted ON activities (deleted_at) WHERE deleted_at IS NULL;
CREATE INDEX idx_quotes_lead ON quotes (lead_id);
CREATE INDEX idx_quotes_deleted ON quotes (deleted_at) WHERE deleted_at IS NULL;
