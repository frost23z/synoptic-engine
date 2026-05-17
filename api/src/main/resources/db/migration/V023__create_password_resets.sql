CREATE TABLE user_password_resets
(
    email      VARCHAR(255)             NOT NULL,
    token      VARCHAR(255)             NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_user_password_resets PRIMARY KEY (email)
);
CREATE INDEX idx_password_resets_token ON user_password_resets (token);
