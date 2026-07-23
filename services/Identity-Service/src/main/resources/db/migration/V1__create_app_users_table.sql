CREATE TABLE app_users (
    id BINARY(16) NOT NULL PRIMARY KEY,
    keycloak_subject VARCHAR(255) NOT NULL,
    issuer VARCHAR(255) NOT NULL,
    username_snapshot VARCHAR(255),
    email_snapshot VARCHAR(320),
    full_name_snapshot VARCHAR(255),
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    last_authenticated_at TIMESTAMP(3),
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_by BINARY(16),
    updated_by BINARY(16),
    created_at TIMESTAMP(3) NOT NULL,
    updated_at TIMESTAMP(3) NOT NULL,
    version BIGINT,
    CONSTRAINT uk_identity_users_issuer_subject UNIQUE (issuer, keycloak_subject)
);

-- CREATE INDEX idx_app_users_is_deleted ON app_users(is_deleted);
-- CREATE INDEX idx_app_users_status ON app_users(status);
-- CREATE INDEX idx_app_users_issuer ON app_users(issuer);
