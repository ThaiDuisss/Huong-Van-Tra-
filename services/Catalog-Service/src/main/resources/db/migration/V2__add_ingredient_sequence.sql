-- Initialize sequence key for INGREDIENT
INSERT INTO business_sequences (sequence_key, current_value) VALUES
('INGREDIENT', 0)
ON DUPLICATE KEY UPDATE current_value = current_value;
