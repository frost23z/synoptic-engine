CREATE TABLE marketing_events
(
    id          UUID                     NOT NULL DEFAULT gen_random_uuid(),
    name        VARCHAR(255)             NOT NULL,
    description TEXT,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_marketing_events PRIMARY KEY (id),
    CONSTRAINT uq_marketing_events_name UNIQUE (name)
);

CREATE TABLE marketing_campaigns
(
    id                UUID                     NOT NULL DEFAULT gen_random_uuid(),
    name              VARCHAR(255)             NOT NULL,
    subject           VARCHAR(500)             NOT NULL,
    description       TEXT,
    event_id          UUID,
    email_template_id UUID,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_marketing_campaigns PRIMARY KEY (id),
    CONSTRAINT uq_marketing_campaigns_name UNIQUE (name),
    CONSTRAINT fk_marketing_campaigns_event FOREIGN KEY (event_id) REFERENCES marketing_events (id) ON DELETE SET NULL,
    CONSTRAINT fk_marketing_campaigns_template FOREIGN KEY (email_template_id) REFERENCES email_templates (id) ON DELETE SET NULL
);

CREATE TABLE workflows
(
    id          UUID                     NOT NULL DEFAULT gen_random_uuid(),
    name        VARCHAR(255)             NOT NULL,
    description TEXT,
    event_name  VARCHAR(255)             NOT NULL,
    conditions  JSONB                    NOT NULL DEFAULT '[]',
    actions     JSONB                    NOT NULL DEFAULT '[]',
    is_active   BOOLEAN                  NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_workflows PRIMARY KEY (id)
);

CREATE TABLE webhooks
(
    id          UUID                     NOT NULL DEFAULT gen_random_uuid(),
    name        VARCHAR(255)             NOT NULL,
    payload_url VARCHAR(2048)            NOT NULL,
    events      JSONB                    NOT NULL DEFAULT '[]',
    is_active   BOOLEAN                  NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_webhooks PRIMARY KEY (id)
);

CREATE INDEX idx_workflows_event_name ON workflows (event_name);

-- Permissions and role assignments are handled by BootstrapService on startup.
