CREATE TABLE leads
(
    id                  UUID                     NOT NULL DEFAULT gen_random_uuid(),
    title               VARCHAR(255)             NOT NULL,
    description         TEXT,
    amount              DECIMAL(15, 2),
    expected_close_date DATE,
    status              VARCHAR(50)              NOT NULL DEFAULT 'open',
    lost_reason         TEXT,
    closed_at           TIMESTAMP WITH TIME ZONE,
    pipeline_id         UUID                     NOT NULL,
    stage_id            UUID                     NOT NULL,
    person_id           UUID,
    organization_id     UUID,
    lead_source_id      UUID,
    lead_type_id        UUID,
    user_id             UUID,
    created_by          UUID,
    updated_by          UUID,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMP WITH TIME ZONE,

    CONSTRAINT pk_leads PRIMARY KEY (id),
    CONSTRAINT fk_leads_pipeline FOREIGN KEY (pipeline_id) REFERENCES pipelines (id) ON DELETE RESTRICT,
    CONSTRAINT fk_leads_stage FOREIGN KEY (stage_id) REFERENCES stages (id) ON DELETE RESTRICT,
    CONSTRAINT fk_leads_person FOREIGN KEY (person_id) REFERENCES persons (id) ON DELETE SET NULL,
    CONSTRAINT fk_leads_org FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE SET NULL,
    CONSTRAINT fk_leads_source FOREIGN KEY (lead_source_id) REFERENCES lead_sources (id) ON DELETE SET NULL,
    CONSTRAINT fk_leads_type FOREIGN KEY (lead_type_id) REFERENCES lead_types (id) ON DELETE SET NULL,
    CONSTRAINT fk_leads_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT chk_leads_status CHECK (status IN ('open', 'won', 'lost', 'abandoned'))
);

CREATE TABLE lead_tags
(
    lead_id UUID NOT NULL,
    tag_id  UUID NOT NULL,

    CONSTRAINT pk_lead_tags PRIMARY KEY (lead_id, tag_id),
    CONSTRAINT fk_lead_tags_lead FOREIGN KEY (lead_id) REFERENCES leads (id) ON DELETE CASCADE,
    CONSTRAINT fk_lead_tags_tag FOREIGN KEY (tag_id) REFERENCES tags (id) ON DELETE CASCADE
);

CREATE TABLE products
(
    id          UUID                     NOT NULL DEFAULT gen_random_uuid(),
    name        VARCHAR(255)             NOT NULL,
    description TEXT,
    price       DECIMAL(15, 2)           NOT NULL DEFAULT 0,
    sku         VARCHAR(100),
    is_active   BOOLEAN                  NOT NULL DEFAULT TRUE,
    created_by  UUID,
    updated_by  UUID,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted_at  TIMESTAMP WITH TIME ZONE,

    CONSTRAINT pk_products PRIMARY KEY (id),
    CONSTRAINT uq_products_sku UNIQUE (sku)
);

CREATE INDEX idx_leads_pipeline ON leads (pipeline_id);
CREATE INDEX idx_leads_stage ON leads (stage_id);
CREATE INDEX idx_leads_user ON leads (user_id);
CREATE INDEX idx_leads_status ON leads (status);
CREATE INDEX idx_leads_deleted ON leads (deleted_at) WHERE deleted_at IS NULL;
CREATE INDEX idx_products_deleted ON products (deleted_at) WHERE deleted_at IS NULL;
