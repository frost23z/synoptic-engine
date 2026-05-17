CREATE TABLE attributes
(
    id              UUID                     NOT NULL DEFAULT gen_random_uuid(),
    code            VARCHAR(100)             NOT NULL,
    admin_name      VARCHAR(255)             NOT NULL,
    type            VARCHAR(50)              NOT NULL,
    is_user_defined BOOLEAN                  NOT NULL DEFAULT TRUE,
    lookup          VARCHAR(255),
    entity_type     VARCHAR(100)             NOT NULL,
    sort_order      INT                      NOT NULL DEFAULT 0,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_attributes PRIMARY KEY (id),
    CONSTRAINT uq_attributes_code_entity UNIQUE (code, entity_type)
);

CREATE TABLE attribute_options
(
    id          UUID                     NOT NULL DEFAULT gen_random_uuid(),
    attribute_id UUID                    NOT NULL,
    admin_name  VARCHAR(255)             NOT NULL,
    sort_order  INT                      NOT NULL DEFAULT 0,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_attribute_options PRIMARY KEY (id),
    CONSTRAINT fk_attribute_options_attr FOREIGN KEY (attribute_id) REFERENCES attributes (id) ON DELETE CASCADE
);

CREATE TABLE attribute_values
(
    id           UUID                     NOT NULL DEFAULT gen_random_uuid(),
    attribute_id UUID                     NOT NULL,
    entity_id    UUID                     NOT NULL,
    entity_type  VARCHAR(100)             NOT NULL,
    value        TEXT,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_attribute_values PRIMARY KEY (id),
    CONSTRAINT fk_attribute_values_attr FOREIGN KEY (attribute_id) REFERENCES attributes (id) ON DELETE CASCADE,
    CONSTRAINT uq_attribute_values UNIQUE (attribute_id, entity_id)
);

CREATE TABLE email_templates
(
    id            UUID                     NOT NULL DEFAULT gen_random_uuid(),
    name          VARCHAR(255)             NOT NULL,
    subject       VARCHAR(500)             NOT NULL,
    content       TEXT                     NOT NULL,
    is_predefined BOOLEAN                  NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_email_templates PRIMARY KEY (id),
    CONSTRAINT uq_email_templates_name UNIQUE (name)
);

CREATE TABLE web_forms
(
    id          UUID                     NOT NULL DEFAULT gen_random_uuid(),
    title       VARCHAR(255)             NOT NULL,
    description TEXT,
    is_active   BOOLEAN                  NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_web_forms PRIMARY KEY (id)
);

CREATE TABLE web_form_attributes
(
    id           UUID                     NOT NULL DEFAULT gen_random_uuid(),
    web_form_id  UUID                     NOT NULL,
    attribute_id UUID                     NOT NULL,
    sort_order   INT                      NOT NULL DEFAULT 0,
    is_required  BOOLEAN                  NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_web_form_attributes PRIMARY KEY (id),
    CONSTRAINT fk_web_form_attributes_form FOREIGN KEY (web_form_id) REFERENCES web_forms (id) ON DELETE CASCADE,
    CONSTRAINT fk_web_form_attributes_attr FOREIGN KEY (attribute_id) REFERENCES attributes (id) ON DELETE CASCADE
);

CREATE INDEX idx_attributes_entity_type ON attributes (entity_type);
CREATE INDEX idx_attribute_values_entity ON attribute_values (entity_id, entity_type);
CREATE INDEX idx_attribute_options_attr ON attribute_options (attribute_id);
CREATE INDEX idx_web_form_attributes_form ON web_form_attributes (web_form_id);

-- Permissions and role assignments are handled by BootstrapService on startup.
