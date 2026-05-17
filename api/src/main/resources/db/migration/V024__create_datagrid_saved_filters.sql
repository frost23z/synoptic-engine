CREATE TABLE datagrid_saved_filters
(
    id         UUID                     NOT NULL DEFAULT gen_random_uuid(),
    user_id    UUID                     NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name       VARCHAR(255)             NOT NULL,
    src        VARCHAR(100)             NOT NULL,
    applied    JSONB                    NOT NULL DEFAULT '{}',
    tenant_id  UUID                     NOT NULL DEFAULT '00000000-0000-0000-0000-000000000001' REFERENCES tenants (id),
    version    BIGINT                   NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_datagrid_saved_filters PRIMARY KEY (id)
);
CREATE INDEX idx_datagrid_filters_user_src ON datagrid_saved_filters (user_id, src);
CREATE INDEX idx_datagrid_filters_tenant ON datagrid_saved_filters (tenant_id);
