CREATE TABLE tags
(
    id         UUID                     NOT NULL DEFAULT gen_random_uuid(),
    name       VARCHAR(100)             NOT NULL,
    color      VARCHAR(20),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_tags PRIMARY KEY (id),
    CONSTRAINT uq_tags_name UNIQUE (name)
);

CREATE TABLE lead_sources
(
    id         UUID                     NOT NULL DEFAULT gen_random_uuid(),
    name       VARCHAR(100)             NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_lead_sources PRIMARY KEY (id),
    CONSTRAINT uq_lead_sources_name UNIQUE (name)
);

CREATE TABLE lead_types
(
    id         UUID                     NOT NULL DEFAULT gen_random_uuid(),
    name       VARCHAR(100)             NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_lead_types PRIMARY KEY (id),
    CONSTRAINT uq_lead_types_name UNIQUE (name)
);

CREATE TABLE pipelines
(
    id          UUID                     NOT NULL DEFAULT gen_random_uuid(),
    name        VARCHAR(100)             NOT NULL,
    description VARCHAR(500),
    is_active   BOOLEAN                  NOT NULL DEFAULT TRUE,
    is_default  BOOLEAN                  NOT NULL DEFAULT FALSE,
    rotten_days INT                      NOT NULL DEFAULT 30,
    created_by  UUID,
    updated_by  UUID,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted_at  TIMESTAMP WITH TIME ZONE,

    CONSTRAINT pk_pipelines PRIMARY KEY (id)
);

CREATE TABLE stages
(
    id          UUID                     NOT NULL DEFAULT gen_random_uuid(),
    pipeline_id UUID                     NOT NULL,
    name        VARCHAR(100)             NOT NULL,
    sort_order  INT                      NOT NULL DEFAULT 0,
    color       VARCHAR(20),
    probability INT                      NOT NULL DEFAULT 0,
    code        VARCHAR(50),
    created_by  UUID,
    updated_by  UUID,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted_at  TIMESTAMP WITH TIME ZONE,

    CONSTRAINT pk_stages PRIMARY KEY (id),
    CONSTRAINT fk_stages_pipeline FOREIGN KEY (pipeline_id) REFERENCES pipelines (id) ON DELETE CASCADE,
    CONSTRAINT chk_stages_prob CHECK (probability BETWEEN 0 AND 100),
    CONSTRAINT chk_stages_code CHECK (code IN ('won', 'lost') OR code IS NULL)
);

CREATE INDEX idx_stages_pipeline ON stages (pipeline_id);
CREATE INDEX idx_pipelines_default ON pipelines (is_default) WHERE is_default = TRUE;
