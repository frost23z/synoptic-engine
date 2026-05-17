CREATE TABLE email_tags
(
    email_id UUID NOT NULL,
    tag_id   UUID NOT NULL,
    CONSTRAINT pk_email_tags PRIMARY KEY (email_id, tag_id),
    CONSTRAINT fk_email_tags_email FOREIGN KEY (email_id) REFERENCES emails (id) ON DELETE CASCADE,
    CONSTRAINT fk_email_tags_tag  FOREIGN KEY (tag_id)   REFERENCES tags   (id) ON DELETE CASCADE
);
