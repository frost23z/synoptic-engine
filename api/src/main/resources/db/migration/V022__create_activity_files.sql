CREATE TABLE activity_files
(
    id           UUID                     NOT NULL DEFAULT gen_random_uuid(),
    activity_id  UUID                     NOT NULL,
    name         VARCHAR(500)             NOT NULL,
    path         VARCHAR(1000)            NOT NULL,
    size         BIGINT,
    content_type VARCHAR(255),
    tenant_id    UUID                     NOT NULL DEFAULT '00000000-0000-0000-0000-000000000001' REFERENCES tenants (id),
    version      BIGINT                   NOT NULL DEFAULT 0,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_activity_files PRIMARY KEY (id),
    CONSTRAINT fk_activity_files_activity FOREIGN KEY (activity_id) REFERENCES activities (id) ON DELETE CASCADE
);
CREATE INDEX idx_activity_files_activity ON activity_files (activity_id);
CREATE INDEX idx_activity_files_tenant ON activity_files (tenant_id);
