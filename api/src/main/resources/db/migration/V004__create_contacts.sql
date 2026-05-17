CREATE TABLE organizations
(
    id         UUID                     NOT NULL DEFAULT gen_random_uuid(),
    name       VARCHAR(255)             NOT NULL,
    email      VARCHAR(255),
    phone      VARCHAR(50),
    website    VARCHAR(255),
    address    TEXT,
    created_by UUID,
    updated_by UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP WITH TIME ZONE,

    CONSTRAINT pk_organizations PRIMARY KEY (id)
);

CREATE TABLE persons
(
    id              UUID                     NOT NULL DEFAULT gen_random_uuid(),
    organization_id UUID,
    first_name      VARCHAR(100)             NOT NULL,
    last_name       VARCHAR(100)             NOT NULL,
    email           VARCHAR(255),
    phone           VARCHAR(50),
    job_title       VARCHAR(100),
    created_by      UUID,
    updated_by      UUID,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMP WITH TIME ZONE,

    CONSTRAINT pk_persons PRIMARY KEY (id),
    CONSTRAINT fk_persons_organization FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE SET NULL
);

CREATE TABLE person_tags
(
    person_id UUID NOT NULL,
    tag_id    UUID NOT NULL,

    CONSTRAINT pk_person_tags PRIMARY KEY (person_id, tag_id),
    CONSTRAINT fk_person_tags_person FOREIGN KEY (person_id) REFERENCES persons (id) ON DELETE CASCADE,
    CONSTRAINT fk_person_tags_tag FOREIGN KEY (tag_id) REFERENCES tags (id) ON DELETE CASCADE
);

CREATE INDEX idx_persons_organization ON persons (organization_id);
CREATE INDEX idx_persons_deleted ON persons (deleted_at) WHERE deleted_at IS NULL;
CREATE INDEX idx_organizations_deleted ON organizations (deleted_at) WHERE deleted_at IS NULL;
