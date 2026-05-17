CREATE TABLE emails
(
    id             UUID                     NOT NULL DEFAULT gen_random_uuid(),
    subject        VARCHAR(500),
    source         VARCHAR(100),
    name           VARCHAR(255),
    user_type      VARCHAR(100),
    is_read        BOOLEAN                  NOT NULL DEFAULT FALSE,
    folders        JSONB                    NOT NULL DEFAULT '["inbox"]',
    "from"         JSONB,
    sender         JSONB,
    reply_to       JSONB,
    cc             JSONB,
    bcc            JSONB,
    body           TEXT,
    reply          TEXT,
    unique_id      VARCHAR(500),
    message_id     VARCHAR(500),
    reference_ids  JSONB,
    person_id      UUID,
    parent_id      UUID,
    lead_id        UUID,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_emails PRIMARY KEY (id),
    CONSTRAINT fk_emails_person FOREIGN KEY (person_id) REFERENCES persons (id) ON DELETE SET NULL,
    CONSTRAINT fk_emails_parent FOREIGN KEY (parent_id) REFERENCES emails (id) ON DELETE SET NULL,
    CONSTRAINT fk_emails_lead FOREIGN KEY (lead_id) REFERENCES leads (id) ON DELETE SET NULL
);

CREATE TABLE email_attachments
(
    id                  UUID                     NOT NULL DEFAULT gen_random_uuid(),
    email_id            UUID                     NOT NULL,
    attachment_path     VARCHAR(1000)            NOT NULL,
    attachment_filename VARCHAR(500)             NOT NULL,
    content_type        VARCHAR(255),
    size                BIGINT,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_email_attachments PRIMARY KEY (id),
    CONSTRAINT fk_email_attachments_email FOREIGN KEY (email_id) REFERENCES emails (id) ON DELETE CASCADE
);

CREATE TABLE lead_emails
(
    lead_id  UUID NOT NULL,
    email_id UUID NOT NULL,

    CONSTRAINT pk_lead_emails PRIMARY KEY (lead_id, email_id),
    CONSTRAINT fk_lead_emails_lead FOREIGN KEY (lead_id) REFERENCES leads (id) ON DELETE CASCADE,
    CONSTRAINT fk_lead_emails_email FOREIGN KEY (email_id) REFERENCES emails (id) ON DELETE CASCADE
);

CREATE INDEX idx_emails_lead_id ON emails (lead_id);
CREATE INDEX idx_emails_person_id ON emails (person_id);
CREATE INDEX idx_email_attachments_email ON email_attachments (email_id);
CREATE INDEX idx_lead_emails_lead ON lead_emails (lead_id);

-- Permissions and role assignments are handled by BootstrapService on startup.
