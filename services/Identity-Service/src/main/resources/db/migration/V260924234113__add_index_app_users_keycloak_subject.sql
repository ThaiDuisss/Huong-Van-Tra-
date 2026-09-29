-- Tra cứu user theo Keycloak subject (API batch nội bộ) không dùng được unique index (issuer, keycloak_subject)
CREATE INDEX idx_app_users_keycloak_subject ON app_users(keycloak_subject);
