ALTER TABLE order_returns
    ADD COLUMN refund_required BOOLEAN NOT NULL DEFAULT TRUE AFTER status;
