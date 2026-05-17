-- Lead ↔ Product association
CREATE TABLE lead_products
(
    id         UUID NOT NULL DEFAULT gen_random_uuid(),
    lead_id    UUID NOT NULL,
    product_id UUID NOT NULL,
    quantity   INT  NOT NULL DEFAULT 1,
    unit_price DECIMAL(15, 2),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_lead_products PRIMARY KEY (id),
    CONSTRAINT uq_lead_products UNIQUE (lead_id, product_id),
    CONSTRAINT fk_lead_products_lead FOREIGN KEY (lead_id) REFERENCES leads (id) ON DELETE CASCADE,
    CONSTRAINT fk_lead_products_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE
);

-- Product tagging
CREATE TABLE product_tags
(
    product_id UUID NOT NULL,
    tag_id     UUID NOT NULL,
    CONSTRAINT pk_product_tags PRIMARY KEY (product_id, tag_id),
    CONSTRAINT fk_product_tags_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
    CONSTRAINT fk_product_tags_tag FOREIGN KEY (tag_id) REFERENCES tags (id) ON DELETE CASCADE
);

-- Warehouse tagging
CREATE TABLE warehouse_tags
(
    warehouse_id UUID NOT NULL,
    tag_id       UUID NOT NULL,
    CONSTRAINT pk_warehouse_tags PRIMARY KEY (warehouse_id, tag_id),
    CONSTRAINT fk_warehouse_tags_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses (id) ON DELETE CASCADE,
    CONSTRAINT fk_warehouse_tags_tag FOREIGN KEY (tag_id) REFERENCES tags (id) ON DELETE CASCADE
);

-- Activity participants (multiple users per activity)
CREATE TABLE activity_participants
(
    activity_id UUID NOT NULL,
    user_id     UUID NOT NULL,
    CONSTRAINT pk_activity_participants PRIMARY KEY (activity_id, user_id),
    CONSTRAINT fk_activity_participants_activity FOREIGN KEY (activity_id) REFERENCES activities (id) ON DELETE CASCADE,
    CONSTRAINT fk_activity_participants_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- Activities on products and warehouses
ALTER TABLE activities
    ADD COLUMN product_id   UUID,
    ADD COLUMN warehouse_id UUID;

ALTER TABLE activities
    ADD CONSTRAINT fk_activities_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_activities_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses (id) ON DELETE SET NULL;

-- System configuration key-value store
CREATE TABLE system_configs
(
    code        VARCHAR(255)             NOT NULL,
    value       TEXT,
    group_name  VARCHAR(100)             NOT NULL DEFAULT 'general',
    label       VARCHAR(255)             NOT NULL,
    type        VARCHAR(50)              NOT NULL DEFAULT 'text',
    is_secret   BOOLEAN                  NOT NULL DEFAULT FALSE,
    sort_order  INT                      NOT NULL DEFAULT 0,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_system_configs PRIMARY KEY (code)
);

-- Seed default system configuration keys
INSERT INTO system_configs (code, label, group_name, type, is_secret, sort_order) VALUES
-- General
('general.company_name',    'Company Name',    'general', 'text',     FALSE, 10),
('general.company_email',   'Company Email',   'general', 'email',    FALSE, 20),
('general.company_phone',   'Company Phone',   'general', 'text',     FALSE, 30),
('general.company_address', 'Company Address', 'general', 'textarea', FALSE, 40),
('general.locale',          'Default Locale',  'general', 'text',     FALSE, 50),
('general.timezone',        'Timezone',        'general', 'text',     FALSE, 60),
-- Mail
('mail.driver',          'Mail Driver',        'mail', 'text',     FALSE, 10),
('mail.host',            'SMTP Host',          'mail', 'text',     FALSE, 20),
('mail.port',            'SMTP Port',          'mail', 'text',     FALSE, 30),
('mail.username',        'SMTP Username',      'mail', 'text',     FALSE, 40),
('mail.password',        'SMTP Password',      'mail', 'password', TRUE,  50),
('mail.encryption',      'Encryption',         'mail', 'text',     FALSE, 60),
('mail.from_name',       'From Name',          'mail', 'text',     FALSE, 70),
('mail.from_address',    'From Address',       'mail', 'email',    FALSE, 80);
