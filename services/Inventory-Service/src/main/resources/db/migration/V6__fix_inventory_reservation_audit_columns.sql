ALTER TABLE inventory_reservations
    MODIFY created_by BINARY(16) NULL,
    MODIFY updated_by BINARY(16) NULL;
