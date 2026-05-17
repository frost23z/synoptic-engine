ALTER TABLE users
    ADD COLUMN view_permission VARCHAR(20) NOT NULL DEFAULT 'GLOBAL';

CREATE TABLE groups
(
    id          UUID                     NOT NULL DEFAULT gen_random_uuid(),
    name        VARCHAR(100)             NOT NULL,
    description VARCHAR(500),
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_groups PRIMARY KEY (id),
    CONSTRAINT uq_groups_name UNIQUE (name)
);

CREATE TABLE user_groups
(
    user_id  UUID NOT NULL,
    group_id UUID NOT NULL,

    CONSTRAINT pk_user_groups PRIMARY KEY (user_id, group_id),
    CONSTRAINT fk_user_groups_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_groups_group FOREIGN KEY (group_id) REFERENCES groups (id) ON DELETE CASCADE
);

CREATE INDEX idx_user_groups_user ON user_groups (user_id);
CREATE INDEX idx_user_groups_group ON user_groups (group_id);

-- Permissions and role assignments are handled by BootstrapService on startup.
