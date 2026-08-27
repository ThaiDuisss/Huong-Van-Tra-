CREATE TABLE inventory_count_scope_locations (
    inventory_count_id BINARY(16) NOT NULL,
    location_id BINARY(16) NOT NULL,
    PRIMARY KEY (inventory_count_id, location_id),
    CONSTRAINT fk_count_scope_locations_count
        FOREIGN KEY (inventory_count_id) REFERENCES inventory_counts(id),
    CONSTRAINT fk_count_scope_locations_location
        FOREIGN KEY (location_id) REFERENCES inventory_locations(id)
);

CREATE TABLE inventory_count_scope_items (
    inventory_count_id BINARY(16) NOT NULL,
    inventory_item_id BINARY(16) NOT NULL,
    PRIMARY KEY (inventory_count_id, inventory_item_id),
    CONSTRAINT fk_count_scope_items_count
        FOREIGN KEY (inventory_count_id) REFERENCES inventory_counts(id),
    CONSTRAINT fk_count_scope_items_item
        FOREIGN KEY (inventory_item_id) REFERENCES inventory_items(id)
);
