CREATE TABLE data_imports
(
    id            UUID                     NOT NULL DEFAULT gen_random_uuid(),
    name          VARCHAR(255)             NOT NULL,
    file_path     VARCHAR(1000)            NOT NULL,
    entity_type   VARCHAR(100)             NOT NULL,
    status        VARCHAR(50)              NOT NULL DEFAULT 'PENDING',
    error_count   INT                      NOT NULL DEFAULT 0,
    success_count INT                      NOT NULL DEFAULT 0,
    errors        JSONB,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_data_imports PRIMARY KEY (id)
);

CREATE INDEX idx_data_imports_entity_type ON data_imports (entity_type);
CREATE INDEX idx_data_imports_status ON data_imports (status);

-- Permissions and role assignments are handled by BootstrapService on startup.
