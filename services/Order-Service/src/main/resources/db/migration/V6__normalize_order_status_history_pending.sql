UPDATE order_status_history
SET from_status = 'PENDING_PAYMENT'
WHERE from_status = 'PENDING';

UPDATE order_status_history
SET to_status = 'PENDING_PAYMENT'
WHERE to_status = 'PENDING';