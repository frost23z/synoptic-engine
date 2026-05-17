-- Default pipeline with stages
INSERT INTO pipelines (id, name, description, is_active, is_default, rotten_days)
VALUES ('00000000-0000-0000-0000-000000000010', 'Default Pipeline', 'Standard sales pipeline', TRUE, TRUE, 30);

INSERT INTO stages (id, pipeline_id, name, sort_order, probability, code)
VALUES ('00000000-0000-0000-0000-000000000011', '00000000-0000-0000-0000-000000000010', 'New', 1, 10, NULL),
       ('00000000-0000-0000-0000-000000000012', '00000000-0000-0000-0000-000000000010', 'Qualified', 2, 30, NULL),
       ('00000000-0000-0000-0000-000000000013', '00000000-0000-0000-0000-000000000010', 'Proposal', 3, 60, NULL),
       ('00000000-0000-0000-0000-000000000014', '00000000-0000-0000-0000-000000000010', 'Negotiation', 4, 80, NULL),
       ('00000000-0000-0000-0000-000000000015', '00000000-0000-0000-0000-000000000010', 'Won', 5, 100, 'won'),
       ('00000000-0000-0000-0000-000000000016', '00000000-0000-0000-0000-000000000010', 'Lost', 6, 0, 'lost');

-- Default lead sources
INSERT INTO lead_sources (id, name)
VALUES (gen_random_uuid(), 'Website'),
       (gen_random_uuid(), 'Referral'),
       (gen_random_uuid(), 'Cold Outreach'),
       (gen_random_uuid(), 'Social Media'),
       (gen_random_uuid(), 'Event');

-- Default lead types
INSERT INTO lead_types (id, name)
VALUES (gen_random_uuid(), 'Inbound'),
       (gen_random_uuid(), 'Outbound'),
       (gen_random_uuid(), 'Partner');
