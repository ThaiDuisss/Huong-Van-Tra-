CREATE TABLE ingredient_recommendation_log (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    generated_at DATETIME(6) NOT NULL,
    model_version VARCHAR(64) NOT NULL,
    items JSON NOT NULL,
    INDEX idx_ingredient_recommendation_generated (generated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
