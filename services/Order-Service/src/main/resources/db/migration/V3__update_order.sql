ALTER TABLE order_status_history DROP column fulfillment_status;

UPDATE orders
SET status = TRIM(status);

UPDATE orders
SET status = 'PENDING_PAYMENT'
WHERE status IN ('PENDING', 'pending', 'WAITING_PAYMENT', 'waiting_payment', 'UNPAID', 'unpaid');

UPDATE orders
SET status = 'COMPLETED'
WHERE status IN ('PAID', 'paid', 'DONE', 'done', 'SUCCESS', 'success', 'COMPLETED');

UPDATE orders
SET status = 'CANCELLED'
WHERE status IN ('CANCEL', 'cancel', 'CANCELED', 'canceled', 'CANCELLED', 'cancelled');

